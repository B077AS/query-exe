package io.github.b077as.queryexe.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.b077as.jolt.AppOptions;
import io.github.b077as.jolt.Application;
import io.github.b077as.jolt.ColorScheme;
import io.github.b077as.jolt.WebWindow;
import io.github.b077as.jolt.WindowOptions;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The launcher window: a small frameless Jolt window whose page ({@code launcher.html}) draws a database that fills with
 * water as the update downloads. Java owns the update ({@link UpdateManager}); the page only draws what it is told.
 *
 * <p>Messages are JSON strings. Java to page: {@code {type:"phase", phase, ...}}, {@code update}, {@code progress},
 * {@code note}, {@code error}. Page to Java: {@code ready} (the first frame is on screen), {@code retry}, {@code quit}.
 */
@Slf4j
final class LauncherApp implements UpdateManager.Listener {

    private static final String APP_ID = "com.queryexe.launcher";
    private static final int WIDTH = 360;
    private static final int HEIGHT = 340;
    /** Show the window even if the page never says it is ready, so a broken page cannot leave a hidden process behind. */
    private static final Duration SHOW_ANYWAY_AFTER = Duration.ofSeconds(6);
    private static final Gson GSON = new Gson();

    private final Application app;
    private final boolean demo;
    private WebWindow window;

    private LauncherApp(Application app, boolean demo) {
        this.app = app;
        this.demo = demo;
    }

    static void run(boolean demo) {
        Appearance look = Appearance.read(Boot.appDataDirectory().resolve("config"));
        AppOptions options = AppOptions.of(APP_ID).withQuietNativeLogs(true)
                .withDataDirectory(Boot.appDataDirectory().resolve("launcher-webview"));
        Application app = Application.create(options);
        log.info("Application created {} ms after the JVM started", System.currentTimeMillis() - Boot.STARTED);
        LauncherApp launcher = new LauncherApp(app, demo);
        try {
            app.run(() -> launcher.open(look));
        } finally {
            app.close();
        }
        // The stub waits for this process to end before it swaps files in: leave at once, whatever threads are still around.
        System.exit(0);
    }

    private void open(Appearance look) {
        WindowOptions options = WindowOptions.defaults()
                .withTitle("QueryExe")
                .withSize(WIDTH, HEIGHT)
                .withMinSize(WIDTH, HEIGHT)
                .withFrameless(true)
                .withCentered(true)
                .withVisible(false)
                .withIcon(readResource("/icon.ico"))
                .withTitleBarScheme("dark".equals(look.getScheme()) ? ColorScheme.DARK : ColorScheme.LIGHT)
                .withBackgroundColor(look.getBackground());
        log.info("Creating the window {} ms after the JVM started", System.currentTimeMillis() - Boot.STARTED);
        window = app.createWindow(options);
        log.info("Window created {} ms after the JVM started", System.currentTimeMillis() - Boot.STARTED);

        Map<String, Object> boot = new LinkedHashMap<>();
        boot.put("scheme", look.getScheme());
        boot.put("accent", look.getAccent());
        window.addInitScript("window.__LAUNCHER__ = " + GSON.toJson(boot) + ";");
        window.onMessage(this::fromPage);
        window.loadHtml(new String(readResource("/launcher.html"), StandardCharsets.UTF_8));

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(SHOW_ANYWAY_AFTER);
            } catch (InterruptedException e) {
                return;
            }
            window.show();
        });
    }

    // ------------------------------------------------------------------------------------ page to Java

    private void fromPage(String message) {
        switch (message) {
            case "ready" -> {
                log.info("Page ready {} ms after the JVM started", System.currentTimeMillis() - Boot.STARTED);
                window.show();
                startFlow();
            }
            case "retry" -> startFlow();
            case "quit" -> app.dispatch(app::quit);
            default -> log.debug("Ignored a message from the page: {}", message);
        }
    }

    private void startFlow() {
        UpdateManager manager = new UpdateManager(this);
        Thread.ofVirtual().name("update-flow").start(demo ? manager::runDemo : manager::run);
    }

    // ------------------------------------------------------------------------------------ Java to page (UpdateManager.Listener)

    @Override
    public void onInstalled(String version) {
        send("installed", Map.of("version", version));
    }

    @Override
    public void onPhase(Phase phase) {
        send("phase", Map.of("phase", phase.name().toLowerCase()));
    }

    @Override
    public void onUpdate(String current, String latest, long bytes) {
        send("update", Map.of("current", current, "latest", latest, "bytes", bytes));
    }

    @Override
    public void onProgress(long received, long total) {
        send("progress", Map.of("received", received, "total", total));
    }

    @Override
    public void onNote(String message) {
        send("note", Map.of("message", message));
    }

    @Override
    public void onError(String message) {
        send("error", Map.of("message", message));
    }

    @Override
    public void onLaunched() {
        app.dispatch(app::quit);
    }

    private void send(String type, Map<String, ?> fields) {
        JsonObject o = GSON.toJsonTree(fields).getAsJsonObject();
        o.addProperty("type", type);
        window.postMessage(o.toString());
    }

    private static byte[] readResource(String name) {
        try (InputStream in = LauncherApp.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is missing from the launcher jar");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + name, e);
        }
    }
}
