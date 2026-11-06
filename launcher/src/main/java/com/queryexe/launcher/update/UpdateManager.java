package com.queryexe.launcher.update;

import com.google.gson.Gson;
import com.sun.jna.Platform;
import com.queryexe.launcher.Launcher;
import com.queryexe.launcher.LauncherConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Drives the whole launcher sequence on a background thread:
 *
 * <ol>
 *   <li>ask GitHub for the client's latest release
 *       ({@code GET /repos/{owner}/query-exe/releases/latest}) — there is no
 *       hub, this repo is queried directly,</li>
 *   <li>compare its tag (minus a leading "v") to the {@code app.version}
 *       embedded in the installed jar's {@code app.properties},</li>
 *   <li>download + replace the jar if it's missing or stale, verifying it
 *       against a {@code <asset>.sha256} sidecar release asset,</li>
 *   <li>launch the client jar and exit.</li>
 * </ol>
 *
 * <p>Every update is mandatory: once GitHub has announced a newer version, a
 * failed download shows an error instead of falling back to the stale jar. Only
 * when GitHub itself is unreachable does an already-installed jar launch as-is.
 *
 * <p>All progress is reported through {@link Listener}; callbacks fire on the
 * background thread, so the UI marshals them onto the FX thread itself.
 *
 * <p>A scripted {@link #runDemo()} reproduces the same visuals without a
 * network call or a real jar, so the look &amp; feel can be reviewed in isolation.
 */
@Slf4j
public class UpdateManager {

    /** Callbacks for the UI. All fire on the background worker thread. */
    public interface Listener {
        void onPhase(Phase phase);

        /** Determinate download progress, 0..1. */
        void onProgress(double fraction);

        /** Terminal failure; no client was launched. */
        void onError(String message);

        /** The client jar was launched; the launcher should now close. */
        void onLaunched();
    }

    /** Asset uploaded by query-exe's release workflow, e.g. query-exe-0.0.1.jar. */
    private static final String JAR_ASSET_REGEX = "^query-exe-.*\\.jar$";
    private static final String USER_AGENT = "query-exe-launcher";

    private final LauncherConfig config = LauncherConfig.getInstance();
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Listener listener;
    private final String[] clientArgs;

    public UpdateManager(Listener listener, String[] clientArgs) {
        this.listener = listener;
        this.clientArgs = clientArgs == null ? new String[0] : clientArgs;
    }

    // ── Real flow ─────────────────────────────────────────────────────────────

    public void run() {
        BundledClientSeeder.seedIfMissing();

        Path jar = Launcher.getClientJar();
        // Flipped once GitHub has announced a version we don't have; from that
        // point on the stale jar is never launched as a fallback.
        boolean updateRequired = false;

        try {
            listener.onPhase(Phase.CHECKING);
            pause(450); // let the phrase land, Discord-style

            GitHubRelease latest = fetchLatest();
            String localVersion = readVersionFromJar(jar);
            log.info("Local version={}, latest version={}", localVersion, latest.version());

            boolean upToDate = localVersion != null
                    && latest.version() != null
                    && latest.version().equals(localVersion);

            if (upToDate) {
                listener.onPhase(Phase.UP_TO_DATE);
                pause(550);
            } else {
                updateRequired = true;
                downloadAndInstall(latest);
            }

            listener.onPhase(Phase.STARTING);
            pause(350);
            launchClient(jar);
            //pauseForClientHandoff();
            listener.onLaunched();

        } catch (Exception e) {
            log.warn("Update flow failed: {}", e.toString());
            // GitHub unreachable (offline, rate-limited, GitHub down): run what we
            // already have. A known-stale jar is never launched — the error stays on screen.
            if (!updateRequired && Files.exists(jar)) {
                try {
                    listener.onPhase(Phase.STARTING);
                    pause(300);
                    launchClient(jar);
                    pauseForClientHandoff();
                    listener.onLaunched();
                    return;
                } catch (Exception launchEx) {
                    log.error("Failed to launch existing jar: {}", launchEx.toString());
                }
            }
            listener.onError(friendlyError(e));
        }
    }

    private GitHubRelease fetchLatest() throws Exception {
        String url = "https://api.github.com/repos/" + config.getGithubOwner() + "/"
                + config.getGithubRepoClient() + "/releases/latest";
        log.debug("GET {}", url);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("GitHub returned HTTP " + res.statusCode());
        }
        GitHubRelease parsed = gson.fromJson(res.body(), GitHubRelease.class);
        if (parsed == null || parsed.version() == null) {
            throw new IllegalStateException("Malformed release response");
        }
        return parsed;
    }

    private void downloadAndInstall(GitHubRelease latest) throws Exception {
        GitHubAsset jarAsset = latest.findAsset(JAR_ASSET_REGEX);
        if (jarAsset == null) {
            throw new IllegalStateException("Latest release has no client jar asset");
        }

        listener.onPhase(Phase.DOWNLOADING);
        listener.onProgress(0);

        Path bin = Launcher.getBinDirectory();
        Files.createDirectories(bin);
        Path tmp = bin.resolve("query-exe.jar.download");

        HttpRequest req = HttpRequest.newBuilder(URI.create(jarAsset.getBrowser_download_url()))
                .header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Download failed: HTTP " + res.statusCode());
        }

        long contentLength = res.headers().firstValueAsLong("Content-Length").orElse(-1);
        MessageDigest digest = newSha256();
        try (InputStream in = res.body();
             var out = Files.newOutputStream(tmp)) {
            byte[] buffer = new byte[1 << 16];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (digest != null) digest.update(buffer, 0, read);
                total += read;
                if (contentLength > 0) {
                    listener.onProgress((double) total / contentLength);
                }
            }
        }
        listener.onProgress(1.0);

        listener.onPhase(Phase.INSTALLING);
        pause(400);
        // The jar must be able to state its own version, or the next startup's
        // comparison would break — a truncated/corrupt download fails here
        // instead of replacing a working install.
        String downloadedVersion = readVersionFromJar(tmp);
        if (downloadedVersion == null) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("Downloaded jar is corrupt (no readable version)");
        }
        // Cryptographic check against the .sha256 sidecar the release workflow
        // uploaded alongside the jar — skipped if it's missing.
        String expectedSha256 = fetchSha256(latest, jarAsset.getName());
        if (expectedSha256 != null && !expectedSha256.isBlank() && digest != null) {
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (!expectedSha256.trim().equalsIgnoreCase(actualSha256)) {
                Files.deleteIfExists(tmp);
                throw new IllegalStateException("Downloaded jar failed integrity check");
            }
        }
        Path jar = Launcher.getClientJar();
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        log.info("Installed client jar version {} -> {}", downloadedVersion, jar);
    }

    /** Downloads and trims the {@code <assetName>.sha256} sidecar, or null if absent. */
    private String fetchSha256(GitHubRelease release, String assetName) {
        try {
            GitHubAsset sidecar = release.findAssetExact(assetName + ".sha256");
            if (sidecar == null) return null;
            HttpRequest req = HttpRequest.newBuilder(URI.create(sidecar.getBrowser_download_url()))
                    .header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            String body = res.body().trim();
            return body.isEmpty() ? null : body.split("\\s+")[0];
        } catch (Exception e) {
            log.debug("Could not fetch sha256 sidecar for {}: {}", assetName, e.toString());
            return null;
        }
    }

    private void launchClient(Path jar) throws Exception {
        if (!Files.exists(jar)) {
            throw new IllegalStateException("Client jar not found at " + jar);
        }
        String javaBin = resolveJavaBinary();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        // Lets the client tell whether the launcher that started it needs updating —
        // absent entirely on an old/unpatched launcher, which the client treats as stale.
        String launcherVersion = config.getLauncherVersion();
        if (launcherVersion != null && !launcherVersion.isBlank()) {
            command.add("-Dlauncher.version=" + launcherVersion);
        }
        command.add("-jar");
        command.add(jar.toAbsolutePath().toString());
        for (String a : clientArgs) command.add(a);

        log.info("Launching client: {}", String.join(" ", command));
        // Not .inheritIO(): this launcher is a GUI-subsystem app (double-clicked, no
        // console), so it has no real stdio handles to hand the child — inheriting
        // that null/invalid console can make the client fail early in JVM bootstrap,
        // before it even reaches its own logging. Redirect to a file instead: safe
        // regardless of the parent's console state, and gives a real crash trace if
        // the client ever does fail after this point.
        Path clientOutLog = Launcher.getLogsDirectory().resolve("client-stdout.log");
        Process process = new ProcessBuilder(command)
                .directory(jar.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(clientOutLog.toFile()))
                .start();
        guardAppImageMount(process);
    }

    /** True once the client was started from inside an AppImage; the launcher JVM
     *  must then outlive the window (see {@link #guardAppImageMount}). */
    private static volatile boolean appImageMountGuarded = false;

    public static boolean isAppImageMountGuarded() {
        return appImageMountGuarded;
    }

    /**
     * Running from an AppImage, this process's exit unmounts the FUSE image — and
     * with it the private runtime the client was just launched with. So linger
     * invisibly (the window still closes via {@code Platform.exit()}) on a
     * non-daemon thread until the client exits, then let the JVM die.
     */
    private static void guardAppImageMount(Process client) {
        if (System.getenv("APPIMAGE") == null) return;
        appImageMountGuarded = true;
        Thread keeper = new Thread(() -> {
            try {
                client.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "appimage-mount-keeper");
        keeper.setDaemon(false);
        keeper.start();
        log.info("AppImage detected; keeping the mount alive until the client exits");
    }

    /**
     * Prefer javaw.exe on Windows so the client has no stray console window. Packaged
     * builds carry a rebranded copy of javaw.exe (runtime/bin/QueryExe.exe, see the
     * installer profile's rebrand-client-exe step) whose version resource reads
     * "QueryExe" instead of stock javaw.exe's "Java Platform SE binary" — used when
     * present; dev runs fall back to plain javaw.exe.
     */
    private String resolveJavaBinary() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        boolean win = Platform.isWindows();
        if (win) {
            Path branded = javaHome.resolve("bin").resolve("QueryExe.exe");
            if (Files.exists(branded)) return branded.toAbsolutePath().toString();
        }
        Path candidate = javaHome.resolve("bin").resolve(win ? "javaw.exe" : "java");
        if (Files.exists(candidate)) return candidate.toAbsolutePath().toString();
        return win ? "javaw" : "java";
    }

    /** SHA-256 instance for hashing a download in-flight, or null if unavailable
     *  (every JDK provides it, but the integrity check is best-effort either way). */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            log.warn("SHA-256 unavailable, skipping download integrity check: {}", e.toString());
            return null;
        }
    }

    /** Jar entry + property carrying the client's version. */
    private static final String PROPERTIES_ENTRY = "app.properties";
    private static final String VERSION_PROPERTY = "app.version";

    /** Version embedded in the given jar, or null if the jar is missing or unreadable. */
    private static String readVersionFromJar(Path jar) {
        if (!Files.exists(jar)) return null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(PROPERTIES_ENTRY);
            if (entry == null) return null;
            Properties props = new Properties();
            try (InputStream in = zip.getInputStream(entry)) {
                props.load(in);
            }
            String version = props.getProperty(VERSION_PROPERTY);
            return version == null || version.isBlank() ? null : version.trim();
        } catch (Exception e) {
            log.debug("Could not read version from {}: {}", jar, e.toString());
            return null;
        }
    }

    private static String friendlyError(Exception e) {
        if (e instanceof java.net.ConnectException
                || e instanceof java.net.http.HttpConnectTimeoutException
                || e instanceof java.net.UnknownHostException) {
            return "Can't reach GitHub";
        }
        return "Update failed — please try again";
    }

    // ── Demo flow ─────────────────────────────────────────────────────────────

    /** Scripted sequence so the visuals can be reviewed without a network call or a real jar. */
    public void runDemo() {
        try {
            listener.onPhase(Phase.CHECKING);
            pause(1100);

            listener.onPhase(Phase.DOWNLOADING);
            for (int i = 0; i <= 100; i += 2) {
                listener.onProgress(i / 100.0);
                pause(45);
            }

            listener.onPhase(Phase.INSTALLING);
            pause(1200);

            listener.onPhase(Phase.STARTING);
            pause(1000);

            listener.onPhase(Phase.DONE);
        } catch (Exception e) {
            listener.onError("Demo interrupted");
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gives the freshly spawned client's JavaFX/Glass init a clear head start before this
     * launcher tears down its own Platform. Without this gap, {@code onLaunched()} triggers
     * {@code Platform.exit()} here almost immediately after the child process starts — two
     * JavaFX toolkit instances initializing/shutting down back-to-back on Windows have been
     * observed to race on native Glass window/graphics setup, silently hanging the child before
     * it logs anything.
     */
    private static void pauseForClientHandoff() {
        pause(700);
    }
}
