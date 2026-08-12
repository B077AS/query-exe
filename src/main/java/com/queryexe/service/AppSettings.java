package com.queryexe.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import com.queryexe.queryexe.Launcher;

/**
 * Global application settings (theme, and future app-wide preferences),
 * persisted as a single JSON file under the app data directory. Unlike
 * per-connection data, this file isn't scoped to a user/profile - there is
 * exactly one settings.json shared by anyone using this machine.
 */
@Slf4j
public class AppSettings {

    private static final String THEME_KEY = "theme";
    private static final String CONNECTIONS_VIEW_MODE_KEY = "connectionsViewMode";
    private static final String TREE_SPLIT_WIDTH_KEY = "ui.treeSplitWidth";
    private static final String RESULT_SPLIT_HEIGHT_KEY = "ui.resultSplitHeight";

    private static volatile AppSettings instance;

    private final Path settingsPath;
    private final Gson gson;

    private AppSettings() {
        this.settingsPath = Launcher.getConfigDirectory().resolve("settings.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static AppSettings get() {
        if (instance == null) {
            synchronized (AppSettings.class) {
                if (instance == null) {
                    instance = new AppSettings();
                }
            }
        }
        return instance;
    }

    public String getTheme() {
        JsonObject settings = read();
        return settings.has(THEME_KEY) ? settings.get(THEME_KEY).getAsString() : null;
    }

    public void setTheme(String themeName) {
        JsonObject settings = read();
        settings.addProperty(THEME_KEY, themeName);
        write(settings);
    }

    public String getConnectionsViewMode() {
        JsonObject settings = read();
        return settings.has(CONNECTIONS_VIEW_MODE_KEY) ? settings.get(CONNECTIONS_VIEW_MODE_KEY).getAsString() : null;
    }

    public void setConnectionsViewMode(String viewMode) {
        JsonObject settings = read();
        settings.addProperty(CONNECTIONS_VIEW_MODE_KEY, viewMode);
        write(settings);
    }

    public Double getTreeSplitWidth() {
        JsonObject settings = read();
        return settings.has(TREE_SPLIT_WIDTH_KEY) ? settings.get(TREE_SPLIT_WIDTH_KEY).getAsDouble() : null;
    }

    public void setTreeSplitWidth(double pixelWidth) {
        JsonObject settings = read();
        settings.addProperty(TREE_SPLIT_WIDTH_KEY, pixelWidth);
        write(settings);
    }

    public Double getResultSplitHeight() {
        JsonObject settings = read();
        return settings.has(RESULT_SPLIT_HEIGHT_KEY) ? settings.get(RESULT_SPLIT_HEIGHT_KEY).getAsDouble() : null;
    }

    public void setResultSplitHeight(double pixelHeight) {
        JsonObject settings = read();
        settings.addProperty(RESULT_SPLIT_HEIGHT_KEY, pixelHeight);
        write(settings);
    }

    private JsonObject read() {
        if (!Files.exists(settingsPath)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(settingsPath)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (IOException e) {
            log.error("Error reading settings file", e);
            return new JsonObject();
        }
    }

    private void write(JsonObject settings) {
        try {
            Files.createDirectories(settingsPath.getParent());
            try (Writer writer = Files.newBufferedWriter(settingsPath)) {
                gson.toJson(settings, writer);
            }
        } catch (IOException e) {
            log.error("Error saving settings file", e);
        }
    }
}
