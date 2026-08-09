package com.queryexe.queryexe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {

    private static final String APP_NAME = "QueryExe";

    /**
     * Gets the application data directory based on the operating system:
     * - Windows: %APPDATA%\QueryExe (C:\Users\Username\AppData\Roaming\QueryExe)
     * - macOS: ~/Library/Application Support/QueryExe
     * - Linux: ~/.config/QueryExe
     */
    public static Path getAppDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Paths.get(appData, APP_NAME);
            }
            return Paths.get(userHome, "AppData", "Roaming", APP_NAME);

        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", APP_NAME);

        } else {
            return Paths.get(userHome, ".config", APP_NAME);
        }
    }

    public static Path getDataDirectory() {
        return getAppDataDirectory().resolve("data");
    }

    public static Path getJdbcDriversDirectory() {
        return getAppDataDirectory().resolve("jdbc-drivers");
    }

    public static Path getLogsDirectory() {
        return getAppDataDirectory().resolve("logs");
    }

    public static Path getConfigDirectory() {
        return getAppDataDirectory().resolve("config");
    }

    public static void main(String[] args) {
        // Must run before the first logger is created: logback resolves the log
        // directory from these properties. Launcher therefore has no @Slf4j
        // (its static logger would initialize logback too early).
        initializeSystemProperties();
        initializeDirectories();

        App.appStart(args);
    }

    private static void initializeSystemProperties() {
        System.setProperty("slf4j.provider", "ch.qos.logback.classic.spi.LogbackServiceProvider");
        System.setProperty("queryexe.logDir", getLogsDirectory().toAbsolutePath().toString());
    }

    private static void initializeDirectories() {
        Logger log = LoggerFactory.getLogger(Launcher.class);
        try {
            createDirectory(getAppDataDirectory(), log);
            createDirectory(getDataDirectory(), log);
            createDirectory(getJdbcDriversDirectory(), log);
            createDirectory(getLogsDirectory(), log);
            createDirectory(getConfigDirectory(), log);
        } catch (Exception e) {
            log.error("Error creating application directories", e);
        }
    }

    private static void createDirectory(Path path, Logger log) throws Exception {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("Created directory: {}", path);
        }
    }
}
