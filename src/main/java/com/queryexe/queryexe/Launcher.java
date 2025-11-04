package com.queryexe.queryexe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    public static void main(String[] args) {
        try {
            Path appDataDir = getAppDataDirectory();
            if (!Files.exists(appDataDir)) {
                Files.createDirectories(appDataDir);
                System.out.println("Created app directory: " + appDataDir);
            }

            Path dataDir = getDataDirectory();
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                System.out.println("Created data directory: " + dataDir);
            }

            Path jdbcDir = getJdbcDriversDirectory();
            if (!Files.exists(jdbcDir)) {
                Files.createDirectories(jdbcDir);
                System.out.println("Created JDBC drivers directory: " + jdbcDir);
            }

        } catch (Exception e) {
            System.err.println("Error creating application directories: " + e.getMessage());
            e.printStackTrace();
        }

        App.appStart(args);
    }
}