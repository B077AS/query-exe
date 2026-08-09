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
