package com.queryexe.service;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.queryexe.queryexe.Launcher;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * The SQL files opened or saved recently, each with the connection it was last used on, kept in
 * {@code recent-files.json} under the app data directory. Connections are referenced by id, so renaming
 * one does not break the link and a deleted one simply leaves the file without a remembered connection.
 */
@Slf4j
public class RecentFilesManager {

    /** One remembered file. {@code connectionId} is null when it is not known. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String path;
        private String connectionId;
        private long lastOpened;
    }

    private static RecentFilesManager instance;
    private static final int MAX_RECENT_FILES = 10;
    private static final String LEGACY_PREFS_KEY = "recentFiles";
    private static final String LEGACY_SEPARATOR = "|";

    private final Path storePath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private RecentFilesManager() {
        this.storePath = Launcher.getConfigDirectory().resolve("recent-files.json");
        migrateLegacyPreferences();
    }

    public static synchronized RecentFilesManager getInstance() {
        if (instance == null) {
            instance = new RecentFilesManager();
        }
        return instance;
    }

    /** Records the file as just used, keeping whichever connection it was already tied to. */
    public void addRecentFile(File file) {
        addRecentFile(file, null);
    }

    /** Records the file as just used on {@code connectionId} (null keeps the connection it already had). */
    public synchronized void addRecentFile(File file, String connectionId) {
        if (file == null || !file.exists()) {
            return;
        }

        String path = file.getAbsolutePath();
        List<Entry> entries = load();
        String keptConnection = connectionId;
        for (Entry entry : entries) {
            if (entry.getPath().equals(path)) {
                if (keptConnection == null) {
                    keptConnection = entry.getConnectionId();
                }
                break;
            }
        }
        entries.removeIf(entry -> entry.getPath().equals(path));
        entries.add(0, new Entry(path, keptConnection, System.currentTimeMillis()));

        if (entries.size() > MAX_RECENT_FILES) {
            entries = new ArrayList<>(entries.subList(0, MAX_RECENT_FILES));
        }
        save(entries);
    }

    /** The remembered files, newest first; files that no longer exist are dropped. */
    public synchronized List<Entry> getRecentEntries() {
        List<Entry> entries = load();
        int before = entries.size();
        entries.removeIf(entry -> !new File(entry.getPath()).exists());
        if (entries.size() != before) {
            save(entries);
        }
        return entries;
    }

    public List<String> getRecentFiles() {
        List<String> paths = new ArrayList<>();
        for (Entry entry : getRecentEntries()) {
            paths.add(entry.getPath());
        }
        return paths;
    }

    public synchronized void clearRecentFiles() {
        save(new ArrayList<>());
    }

    public synchronized void removeRecentFile(String filePath) {
        List<Entry> entries = load();
        if (entries.removeIf(entry -> entry.getPath().equals(filePath))) {
            save(entries);
        }
    }

    private List<Entry> load() {
        if (!Files.exists(storePath)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(storePath)) {
            List<Entry> entries = gson.fromJson(reader, new TypeToken<List<Entry>>() { }.getType());
            return entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        } catch (Exception e) {
            log.error("Error reading recent files", e);
            return new ArrayList<>();
        }
    }

    private void save(List<Entry> entries) {
        try {
            Files.createDirectories(storePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storePath)) {
                gson.toJson(entries, writer);
            }
        } catch (IOException e) {
            log.error("Error saving recent files", e);
        }
    }

    /** The list used to live in Java preferences as one joined string; carry it over once. */
    private void migrateLegacyPreferences() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(RecentFilesManager.class);
            String stored = prefs.get(LEGACY_PREFS_KEY, "");
            if (stored.isEmpty()) {
                return;
            }
            if (!Files.exists(storePath)) {
                List<Entry> entries = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (String path : stored.split("\\" + LEGACY_SEPARATOR)) {
                    if (!path.isBlank() && new File(path).exists() && entries.size() < MAX_RECENT_FILES) {
                        entries.add(new Entry(path, null, now - entries.size()));
                    }
                }
                save(entries);
            }
            prefs.remove(LEGACY_PREFS_KEY);
        } catch (Exception e) {
            log.warn("Could not migrate the old recent files list", e);
        }
    }
}
