package com.queryexe.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class RecentFilesManager {
    
    private static RecentFilesManager instance;
    private static final int MAX_RECENT_FILES = 10;
    private static final String RECENT_FILES_KEY = "recentFiles";
    private static final String SEPARATOR = "|";
    
    private final Preferences prefs;
    
    private RecentFilesManager() {
        prefs = Preferences.userNodeForPackage(RecentFilesManager.class);
    }
    
    public static RecentFilesManager getInstance() {
        if (instance == null) {
            instance = new RecentFilesManager();
        }
        return instance;
    }

    public void addRecentFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        
        String filePath = file.getAbsolutePath();
        List<String> recentFiles = getRecentFiles();

        recentFiles.remove(filePath);
        recentFiles.add(0, filePath);

        if (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles = recentFiles.subList(0, MAX_RECENT_FILES);
        }
        
        saveRecentFiles(recentFiles);
    }

    public List<String> getRecentFiles() {
        String stored = prefs.get(RECENT_FILES_KEY, "");
        List<String> files = new ArrayList<>();
        
        if (!stored.isEmpty()) {
            String[] paths = stored.split("\\" + SEPARATOR);
            for (String path : paths) {
                File file = new File(path);
                if (file.exists()) {
                    files.add(path);
                }
            }

            if (files.size() != paths.length) {
                saveRecentFiles(files);
            }
        }
        
        return files;
    }

    public void clearRecentFiles() {
        prefs.remove(RECENT_FILES_KEY);
    }

    public void removeRecentFile(String filePath) {
        List<String> recentFiles = getRecentFiles();
        if (recentFiles.remove(filePath)) {
            saveRecentFiles(recentFiles);
        }
    }

    private void saveRecentFiles(List<String> files) {
        String joined = String.join(SEPARATOR, files);
        prefs.put(RECENT_FILES_KEY, joined);
    }
}