package com.queryexe.update;

import com.google.gson.Gson;
import com.sun.jna.Platform;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Checks GitHub, once per run, for a newer query-exe-launcher release than the
 * one that started this client, and swaps it in place in the background if so.
 * There is no hub — {@code B077AS/query-exe-launcher}'s releases are queried
 * directly, mirroring how {@code UpdateManager} in the launcher itself checks
 * {@code B077AS/query-exe}.
 *
 * <p>The launcher forwards its own version as {@code -Dlauncher.version=...}
 * when it spawns the client — absent entirely on an old/unpatched launcher,
 * which this treats the same as "definitely outdated" (mirrors how the
 * launcher's own {@code UpdateManager} treats a missing/mismatched client version).
 *
 * <p>The swap itself is passive: on Windows it overwrites
 * {@code <installRoot>/app/query-exe-launcher.jar} (safe even while this client is
 * running, since the launcher process that loaded that jar already exited
 * before spawning the client); on Linux it overwrites the {@code .AppImage}
 * file at {@code $APPIMAGE} (safe even while the current one is mounted). No
 * UI, no restart prompt — the new version is picked up next time the user
 * launches through the (already-updated) launcher. Best-effort throughout:
 * any failure is logged and swallowed, since this must never interfere with
 * the client actually running.
 */
@Slf4j
public class LauncherUpdateService {

    private static final int INITIAL_DELAY_SECONDS = 5;
    private static final String USER_AGENT = "query-exe-client";

    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ScheduledExecutorService scheduler;

    public void start() {
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "launcher-update-check");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(this::checkAndSwap, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = null;
    }

    private void checkAndSwap() {
        try {
            String currentVersion = System.getProperty("launcher.version");
            boolean windows = Platform.isWindows();
            String assetName = windows ? "query-exe-launcher-windows.jar" : "query-exe-launcher-linux.AppImage";

            GitHubRelease latest = fetchLatestLauncherRelease();
            if (latest == null || latest.version() == null) {
                log.debug("No launcher release info from GitHub; skipping update check");
                return;
            }

            if (latest.version().equals(currentVersion)) {
                log.debug("Launcher is up to date (version {})", currentVersion);
                return;
            }

            GitHubAsset asset = latest.findAssetExact(assetName);
            if (asset == null) {
                log.debug("Latest launcher release has no {} asset; skipping", assetName);
                return;
            }

            log.info("Launcher update available: {} -> {}", currentVersion, latest.version());
            byte[] bytes = downloadBinary(asset.getBrowser_download_url());
            if (bytes == null || bytes.length == 0) {
                log.warn("Launcher update download was empty; skipping swap");
                return;
            }

            String expectedSha256 = fetchSha256Sidecar(latest, assetName);
            if (expectedSha256 != null && !expectedSha256.isBlank()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String actual = HexFormat.of().formatHex(digest.digest(bytes));
                if (!expectedSha256.trim().equalsIgnoreCase(actual)) {
                    log.warn("Downloaded launcher update failed integrity check; skipping swap");
                    return;
                }
            }

            if (windows) {
                swapWindowsLauncherJar(bytes);
            } else {
                swapLinuxAppImage(bytes);
            }
        } catch (Exception e) {
            // Best-effort: a failed launcher self-update must never disrupt the running client.
            log.debug("Launcher update check failed (non-fatal): {}", e.toString());
        }
    }

    private GitHubRelease fetchLatestLauncherRelease() throws Exception {
        Properties props = new Properties();
        try (InputStream is = LauncherUpdateService.class.getResourceAsStream("/app.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            log.debug("Could not load app.properties: {}", e.toString());
        }
        String owner = props.getProperty("github.owner", "B077AS");
        String repo = props.getProperty("github.repo.launcher", "query-exe-launcher");

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.debug("GitHub returned HTTP {} for {}", res.statusCode(), url);
            return null;
        }
        return gson.fromJson(res.body(), GitHubRelease.class);
    }

    /** Downloads and trims the {@code <assetName>.sha256} sidecar, or null if absent. */
    private String fetchSha256Sidecar(GitHubRelease release, String assetName) {
        try {
            GitHubAsset sidecar = release.findAssetExact(assetName + ".sha256");
            if (sidecar == null) return null;
            String body = new String(downloadBinary(sidecar.getBrowser_download_url()));
            body = body.trim();
            return body.isEmpty() ? null : body.split("\\s+")[0];
        } catch (Exception e) {
            log.debug("Could not fetch sha256 sidecar for {}: {}", assetName, e.toString());
            return null;
        }
    }

    private byte[] downloadBinary(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Download failed: HTTP " + res.statusCode());
        }
        return res.body();
    }

    /** The client always runs from {@code <installRoot>/runtime}; the launcher's
     *  own jar lives alongside it at {@code <installRoot>/app/query-exe-launcher.jar}. */
    private void swapWindowsLauncherJar(byte[] bytes) throws Exception {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path installRoot = javaHome.getParent();
        if (installRoot == null) return;
        Path appDir = installRoot.resolve("app");
        if (!Files.isDirectory(appDir)) {
            // Not a packaged install (e.g. a dev run) — nothing to swap.
            return;
        }
        Path target = appDir.resolve("query-exe-launcher.jar");
        Path tmp = appDir.resolve("query-exe-launcher.jar.download");
        Files.write(tmp, bytes);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Swapped launcher jar at {}", target);
    }

    private void swapLinuxAppImage(byte[] bytes) throws Exception {
        String appImagePath = System.getenv("APPIMAGE");
        if (appImagePath == null || appImagePath.isBlank()) {
            // Not running from an AppImage (dev run, or a from-source install) — nothing to swap.
            return;
        }
        Path target = Paths.get(appImagePath);
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".download");
        Files.write(tmp, bytes);
        // AppImages must stay executable — a plain write defaults to non-executable permissions.
        tmp.toFile().setExecutable(true, false);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Swapped AppImage at {}", target);
    }
}
