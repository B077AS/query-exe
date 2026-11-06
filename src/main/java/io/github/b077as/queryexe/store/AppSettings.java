package io.github.b077as.queryexe.store;

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

import io.github.b077as.queryexe.Launcher;

/**
 * Global application settings (theme, and future app-wide preferences),
 * persisted as a single JSON file under the app data directory. Unlike
 * per-connection data, this file isn't scoped to a user/profile - there is
 * exactly one settings.json shared by anyone using this machine.
 */
@Slf4j
public class AppSettings {

    private static final String THEME_KEY = "theme";
    private static final String FOLDER_COLORS_KEY = "connections.folderColors";

    private static volatile AppSettings instance;

    private Path settingsPath;
    private Gson gson;

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

    public void setTheme(String themeName) {
        JsonObject settings = read();
        settings.addProperty(THEME_KEY, themeName);
        write(settings);
    }

    public java.util.List<String> getStringList(String key) {
        JsonObject settings = read();
        java.util.List<String> values = new java.util.ArrayList<>();
        if (settings.has(key) && settings.get(key).isJsonArray()) {
            settings.getAsJsonArray(key).forEach(e -> values.add(e.getAsString()));
        }
        return values;
    }

    public void setStringList(String key, java.util.List<String> values) {
        JsonObject settings = read();
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        values.forEach(array::add);
        settings.add(key, array);
        write(settings);
    }

    /** The colour (an accent key such as "teal") chosen for each connection folder, by folder name. */
    public java.util.Map<String, String> getFolderColors() {
        java.util.Map<String, String> colors = new java.util.LinkedHashMap<>();
        JsonObject settings = read();
        if (settings.has(FOLDER_COLORS_KEY) && settings.get(FOLDER_COLORS_KEY).isJsonObject()) {
            settings.getAsJsonObject(FOLDER_COLORS_KEY).entrySet().forEach(e -> colors.put(e.getKey(), e.getValue().getAsString()));
        }
        return colors;
    }

    public void setFolderColors(java.util.Map<String, String> colors) {
        JsonObject settings = read();
        JsonObject object = new JsonObject();
        colors.forEach(object::addProperty);
        settings.add(FOLDER_COLORS_KEY, object);
        write(settings);
    }

    /** Any value stored under this key, or null. */
    public JsonElement getRaw(String key) {
        JsonObject settings = read();
        return settings.has(key) ? settings.get(key) : null;
    }

    public void setRaw(String key, JsonElement value) {
        JsonObject settings = read();
        settings.add(key, value);
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
