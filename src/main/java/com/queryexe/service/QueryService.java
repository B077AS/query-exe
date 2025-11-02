package com.queryexe.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Tab;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.components.results.ResultBox;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;

public class QueryService {

    private static volatile QueryService instance;
    private Map<CustomTab<VirtualizedScrollPane<CodeArea>>, File> filePathMap;

    private QueryService() {
        filePathMap = new HashMap<>();
    }

    public static QueryService getInstance() {
        if (instance == null) {
            synchronized (QueryService.class) {
                if (instance == null) {
                    instance = new QueryService();
                }
            }
        }
        return instance;
    }

    public CustomTab<VirtualizedScrollPane<CodeArea>> newQuery(String queryName, String queryText) {
        SQLEditor codeArea = new SQLEditor();
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);

        CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(
                scroll,
                App.getTabPane(),
                DatabaseConnection.getInstance().getCurrentConnectionObject()
        );

        if (queryName != null) {
            queryTab.setCustomText(queryName);
        } else {
            queryTab.setCustomText(generateQueryName());
        }

        App.getTabPane().getTabs().add(queryTab);
        App.getTabPane().getSelectionModel().select(queryTab);

        if (queryText != null) {
            codeArea.replaceText(queryText);
            codeArea.layout();
        }

        return queryTab;
    }

    public CustomTab<VirtualizedScrollPane<CodeArea>> newQueryForConnection(ConnectionObject connection) {
        SQLEditor codeArea = new SQLEditor();
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);

        CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(
                scroll,
                App.getTabPane(),
                connection
        );

        queryTab.setCustomText(generateQueryName());

        return queryTab;
    }

    private String generateQueryName() {
        ObservableList<Tab> tabs = FXCollections.observableArrayList();

        for (Tab tab : App.getTabPane().getTabs()) {
            if (!filePathMap.containsKey(tab)) {
                tabs.add(tab);
            }
        }

        tabs.removeIf(tab -> {
            try {
                @SuppressWarnings("unchecked")
                CustomTab<VirtualizedScrollPane<CodeArea>> customTab =
                        (CustomTab<VirtualizedScrollPane<CodeArea>>) tab;
                String labelText = customTab.getTabLabel().getText();
                String[] parts = labelText.split("Query ");
                if (parts.length > 1) {
                    Integer.parseInt(parts[1].trim());
                    return false;
                }
                return true;
            } catch (Exception e) {
                return true;
            }
        });

        tabs.sort((tab1, tab2) -> {
            @SuppressWarnings("unchecked")
            CustomTab<VirtualizedScrollPane<CodeArea>> newTab1 =
                    (CustomTab<VirtualizedScrollPane<CodeArea>>) tab1;
            @SuppressWarnings("unchecked")
            CustomTab<VirtualizedScrollPane<CodeArea>> newTab2 =
                    (CustomTab<VirtualizedScrollPane<CodeArea>>) tab2;

            String text1 = newTab1.getTabLabel().getText().split("Query ")[1].trim();
            String text2 = newTab2.getTabLabel().getText().split("Query ")[1].trim();

            return Integer.compare(Integer.parseInt(text1), Integer.parseInt(text2));
        });

        int nextNumber = 1;

        if (!tabs.isEmpty()) {
            @SuppressWarnings("unchecked")
            CustomTab<VirtualizedScrollPane<CodeArea>> lastQuery =
                    (CustomTab<VirtualizedScrollPane<CodeArea>>) tabs.getLast();

            try {
                String labelText = lastQuery.getTabLabel().getText();
                String[] parts = labelText.split("Query ");
                nextNumber = Integer.parseInt(parts[1].trim()) + 1;
            } catch (Exception e) {
                nextNumber = 1;
            }
        }

        ConnectionObject currentConnection = DatabaseConnection.getInstance().getCurrentConnectionObject();
        if (currentConnection != null) {
            return currentConnection.getConnectionName() + " - Query " + nextNumber;
        } else {
            return "Query " + nextNumber;
        }
    }

    public void openQuery() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select SQL File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQL Files", "*.sql")
        );

        String userHome = System.getProperty("user.home");
        fileChooser.setInitialDirectory(new File(userHome));

        try {
            File selectedFile = fileChooser.showOpenDialog(App.getScene().getWindow());

            if (selectedFile != null) {
                openFileInTab(selectedFile);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    public void openRecentFile(File file) {
        try {
            openFileInTab(file);
        } catch (IOException e) {
            CustomNotification errorNotification = new CustomNotification(
                    "Error opening file: " + e.getMessage(),
                    new FontIcon(MaterialDesignC.CLOSE_CIRCLE_OUTLINE)
            );
            errorNotification.showNotification();
        }
    }

    private void openFileInTab(File file) throws IOException {
        String fileName = file.getName().split("\\.")[0];
        String content = Files.readString(file.toPath());
        CustomTab<VirtualizedScrollPane<CodeArea>> newTab = newQuery(fileName, content);
        filePathMap.put(newTab, file);

        // Add to recent files
        RecentFilesManager.getInstance().addRecentFile(file);
    }

    public void saveQuery() {
        @SuppressWarnings("unchecked")
        CustomTab<VirtualizedScrollPane<CodeArea>> tab =
                (CustomTab<VirtualizedScrollPane<CodeArea>>) App.getTabPane().getSelectionModel().getSelectedItem();

        if (tab == null) return;

        @SuppressWarnings("unchecked")
        VirtualizedScrollPane<CodeArea> scroll = (VirtualizedScrollPane<CodeArea>) tab.getContent();

        File associatedFile = filePathMap.get(tab);

        if (associatedFile == null) {
            associatedFile = showSaveDialog(App.getScene().getWindow());
            if (associatedFile != null) {
                filePathMap.put(tab, associatedFile);
                String fileName = associatedFile.getName().split("\\.")[0];
                tab.setCustomText(fileName);
            } else {
                return;
            }
        }

        String content = ((CodeArea) scroll.getContent()).getText();

        try (FileWriter writer = new FileWriter(associatedFile)) {
            writer.write(content);

            // Add to recent files
            RecentFilesManager.getInstance().addRecentFile(associatedFile);

            CustomNotification fileSavedNotification = new CustomNotification(
                    "File saved successfully to: " + associatedFile.getAbsolutePath(),
                    new FontIcon(MaterialDesignC.CONTENT_SAVE)
            );
            fileSavedNotification.showNotification();

        } catch (IOException e) {
            CustomNotification errorNotification = new CustomNotification(
                    "Error saving file: " + e.getMessage(),
                    new FontIcon(MaterialDesignC.CONTENT_SAVE_ALERT)
            );
            errorNotification.showNotification();
        }
    }

    public void saveQueryAs() {
        @SuppressWarnings("unchecked")
        CustomTab<VirtualizedScrollPane<CodeArea>> tab =
                (CustomTab<VirtualizedScrollPane<CodeArea>>) App.getTabPane().getSelectionModel().getSelectedItem();

        if (tab == null) return;

        @SuppressWarnings("unchecked")
        VirtualizedScrollPane<CodeArea> scroll = (VirtualizedScrollPane<CodeArea>) tab.getContent();

        // Always show save dialog, regardless of whether file exists
        File newFile = showSaveDialog(App.getScene().getWindow());

        if (newFile == null) {
            return; // User cancelled
        }

        // Update the file association
        filePathMap.put(tab, newFile);
        String fileName = newFile.getName().split("\\.")[0];
        tab.setCustomText(fileName);

        String content = ((CodeArea) scroll.getContent()).getText();

        try (FileWriter writer = new FileWriter(newFile)) {
            writer.write(content);

            // Add to recent files
            RecentFilesManager.getInstance().addRecentFile(newFile);

            CustomNotification fileSavedNotification = new CustomNotification(
                    "File saved successfully to: " + newFile.getAbsolutePath(),
                    new FontIcon(MaterialDesignC.CONTENT_SAVE)
            );
            fileSavedNotification.showNotification();

        } catch (IOException e) {
            CustomNotification errorNotification = new CustomNotification(
                    "Error saving file: " + e.getMessage(),
                    new FontIcon(MaterialDesignC.CONTENT_SAVE_ALERT)
            );
            errorNotification.showNotification();
        }
    }

    public void runQuery() {
        @SuppressWarnings("unchecked")
        CustomTab<VirtualizedScrollPane<CodeArea>> tab = (CustomTab<VirtualizedScrollPane<CodeArea>>) App.getTabPane().getSelectionModel().getSelectedItem();
        tab.setLoading();
        @SuppressWarnings("unchecked")
        VirtualizedScrollPane<CodeArea> scroll = (VirtualizedScrollPane<CodeArea>) tab.getContent();

        if (tab.getExecutor() == null) {
            tab.setExecutor(Executors.newSingleThreadExecutor());
            tab.getExecutor().execute(() -> {
                try {
                    String query = "";
                    if (!((CodeArea) scroll.getContent()).getSelectedText().isEmpty()) {
                        query = ((CodeArea) scroll.getContent()).getSelectedText();
                    } else {
                        query = ((CodeArea) scroll.getContent()).getText();
                    }

                    ResultBox customResultTable = new ResultBox(query, tab);

                    Platform.runLater(() -> {
                        tab.setResultBox(customResultTable);
                    });
                } finally {
                    tab.setCurrentStatement(null);
                    tab.getExecutor().shutdown();
                    tab.setExecutor(null);
                    Platform.runLater(() -> {
                        tab.stopLoading();
                    });
                }
            });
        }
    }

    public void runStatement() {
        @SuppressWarnings("unchecked")
        CustomTab<VirtualizedScrollPane<CodeArea>> tab = (CustomTab<VirtualizedScrollPane<CodeArea>>) App.getTabPane().getSelectionModel().getSelectedItem();
        tab.setLoading();

        @SuppressWarnings("unchecked")
        VirtualizedScrollPane<CodeArea> scroll = (VirtualizedScrollPane<CodeArea>) tab.getContent();
        CodeArea codeArea = (CodeArea) scroll.getContent();

        if (tab.getExecutor() == null) {
            tab.setExecutor(Executors.newSingleThreadExecutor());
            tab.getExecutor().execute(() -> {
                try {
                    String query = extractStatementAtCaret(codeArea);

                    if (query == null || query.trim().isEmpty()) {
                        Platform.runLater(() -> {
                            tab.stopLoading();
                        });
                        return;
                    }

                    ResultBox customResultTable = new ResultBox(query, tab);
                    Platform.runLater(() -> {
                        tab.setResultBox(customResultTable);
                    });
                } finally {
                    tab.setCurrentStatement(null);
                    tab.getExecutor().shutdown();
                    tab.setExecutor(null);
                    Platform.runLater(() -> {
                        tab.stopLoading();
                    });
                }
            });
        }
    }

    private String extractStatementAtCaret(CodeArea codeArea) {
        String text = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();

        if (text.isEmpty()) {
            return null;
        }

        int startPos = text.lastIndexOf(';', caretPosition - 1);
        startPos = (startPos == -1) ? 0 : startPos + 1;

        int endPos = text.indexOf(';', caretPosition);
        endPos = (endPos == -1) ? text.length() : endPos;

        return text.substring(startPos, endPos).trim();
    }

    private File showSaveDialog(Window owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save SQL File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQL Files", "*.sql")
        );

        String userHome = System.getProperty("user.home");
        fileChooser.setInitialDirectory(new File(userHome));

        File file = fileChooser.showSaveDialog(owner);

        if (file != null) {
            String filePath = file.getPath();
            if (!filePath.toLowerCase().endsWith(".sql")) {
                filePath += ".sql";
                file = new File(filePath);
            }
        }

        return file;
    }
}