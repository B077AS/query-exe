package io.github.b077as.queryexe.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point of the launcher: the small window that runs before the QueryExe client.
 *
 * <p>The jpack stub that started it (QueryExe.exe / the AppImage) owns the installation. The launcher asks the stub for
 * updates, shows the download as a filling database, and hands over to the client entry (see {@link UpdateManager}). It uses
 * the same per-OS app data folder as the client, so logs and the saved theme are shared. It has no logger of its own here:
 * the log folder is set up first, and only then is logback started.
 */
public final class Boot {

    private static final String APP_NAME = "QueryExe";

    /** When the JVM started running this class, for the start-up timing in the log. */
    static final long STARTED = System.currentTimeMillis();

    private Boot() {
    }

    /** The folder the client uses too; {@code -Dqueryexe.data=<folder>} replaces it (dev runs). */
    public static Path appDataDirectory() {
        String override = System.getProperty("queryexe.data", "");
        if (!override.isBlank()) {
            return Paths.get(override);
        }
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData != null ? Paths.get(appData, APP_NAME) : Paths.get(home, "AppData", "Roaming", APP_NAME);
        }
        // Linux. macOS is not a supported target.
        return Paths.get(home, ".config", APP_NAME);
    }

    public static void main(String[] args) {
        System.setProperty("slf4j.provider", "ch.qos.logback.classic.spi.LogbackServiceProvider");
        Path logs = appDataDirectory().resolve("logs");
        System.setProperty("queryexe.logDir", logs.toAbsolutePath().toString());
        try {
            Files.createDirectories(logs);
            Files.createDirectories(appDataDirectory().resolve("config"));
        } catch (IOException ignored) {
            // The launcher still works without a log file.
        }
        LauncherApp.run(Boolean.getBoolean("launcher.demo"));
    }
}
