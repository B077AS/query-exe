package com.queryexe.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads the bundled {@code app.properties}. There is no hub to point at — the
 * only values the launcher needs are its own version (forwarded to the client)
 * and the GitHub owner/repo it checks for client updates.
 */
@Slf4j
public class LauncherConfig {

    private static LauncherConfig instance;
    private final Properties properties = new Properties();

    private LauncherConfig() {
        loadProperties();
    }

    public static LauncherConfig getInstance() {
        if (instance == null) {
            synchronized (LauncherConfig.class) {
                if (instance == null) {
                    instance = new LauncherConfig();
                }
            }
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream input = LauncherConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (input != null) {
                properties.load(input);
                log.debug("Loaded launcher properties from classpath: app.properties");
            } else {
                log.error("app.properties not found in classpath");
            }
        } catch (IOException e) {
            log.error("Error loading properties: {}", e.getMessage());
        }
    }

    /** This launcher's own version, forwarded to the client as -Dlauncher.version. */
    public String getLauncherVersion() {
        return properties.getProperty("launcher.version");
    }

    public String getGithubOwner() {
        return properties.getProperty("github.owner");
    }

    public String getGithubRepoClient() {
        return properties.getProperty("github.repo.client");
    }
}
