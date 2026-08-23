package com.queryexe.components.editor;

import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import com.queryexe.components.tree.CustomTree;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.Async;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.service.QueryService;
import com.queryexe.service.SQLFormatterUtils;
import com.queryexe.queryexe.App;

import java.time.Duration;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SQLEditor extends CodeArea {

    private String KEYWORD_PATTERN;
    private String STRING_PATTERN;
    private String COMMENT_PATTERN;
    private String NUMBER_PATTERN;
    private Pattern PATTERN;
    private FindPopup findPopup;
    private AutocompletePopup autocompletePopup;
    private final boolean[] isAutocompleteActive = {false};
    private final long[] lastUpdateTime = {0};

    // Restartable suggestion lookup: a new keystroke cancels the previous
    // computation, so only suggestions for the latest word reach the popup.
    private String suggestionWord = "";
    private boolean suggestionRequestedManually;
    private final Service<List<String>> suggestionService = new Service<>() {
        @Override
        protected Task<List<String>> createTask() {
            String word = suggestionWord;
            return new Task<>() {
                @Override
                protected List<String> call() {
                    return getSuggestions(word);
                }
            };
        }
    };

    public SQLEditor() {
        super();
        setupSuggestionService();
        initializePatterns();
        initializeEditor();
        setupEventHandlers();
        setupContextMenu();
    }

    private void setupSuggestionService() {
        suggestionService.setExecutor(Async.VIRTUAL_EXECUTOR);
        suggestionService.setOnSucceeded(event -> {
            List<String> suggestions = suggestionService.getValue();
            if (suggestionRequestedManually) {
                if (!suggestions.isEmpty()) {
                    Optional<Bounds> caretBounds = this.getCaretBounds();
                    if (caretBounds.isPresent()) {
                        Bounds bounds = caretBounds.get();
                        autocompletePopup.show(suggestions, bounds.getMinX(), bounds.getMaxY() + 5);
                        isAutocompleteActive[0] = true;
                    }
                }
            } else if (isAutocompleteActive[0]) {
                if (suggestions.isEmpty()) {
                    autocompletePopup.hide();
                    isAutocompleteActive[0] = false;
                } else {
                    autocompletePopup.updateSuggestions(suggestions);
                }
            }
        });
    }

    private void requestSuggestions(String word, boolean manual) {
        suggestionWord = word;
        suggestionRequestedManually = manual;
        suggestionService.restart();
    }

    private void initializePatterns() {
        KEYWORD_PATTERN = "\\b(" + String.join("|", DatabaseConnection.getInstance().getConnectionObject().getKEYWORDS()) + ")\\b";
        STRING_PATTERN = "'[^'\\\\]*(\\\\.[^'\\\\]*)*'";
        COMMENT_PATTERN = "--[^\n]*|/\\*.*?\\*/";
        NUMBER_PATTERN = "\\b\\d+\\b";

        PATTERN = Pattern.compile(
                "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                        + "|(?<STRING>" + STRING_PATTERN + ")"
                        + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                        + "|(?<NUMBER>" + NUMBER_PATTERN + ")",
                Pattern.CASE_INSENSITIVE
        );
    }

    private void initializeEditor() {
        autocompletePopup = new AutocompletePopup(this);
        findPopup = new FindPopup(this);

        this.setParagraphGraphicFactory(lineNumberFactory(this));
        this.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(ignore -> this.setStyleSpans(0, computeHighlighting(this.getText())));
        this.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: -color-bg-default; ");
    }

    /**
     * RichTextFX's stock LineNumberFactory sets the gutter's background, text
     * fill and font via imperative setters (setBackground/setTextFill/setFont),
     * which permanently marks those properties as user-set and makes them immune
     * to CSS overrides. This factory leaves all styling to the ".lineno" rule in
     * style.css instead.
     */
    private static IntFunction<Node> lineNumberFactory(CodeArea area) {
        Val<Integer> nParagraphs = LiveList.sizeOf(area.getParagraphs());
        return idx -> {
            Label lineNo = new Label();
            lineNo.getStyleClass().add("lineno");
            lineNo.setAlignment(Pos.CENTER_RIGHT);
            lineNo.setMaxHeight(Double.MAX_VALUE);
            Val<String> formatted = nParagraphs.map(n -> formatLineNumber(idx + 1, n));
            lineNo.textProperty().bind(formatted.conditionOnShowing(lineNo));
            return lineNo;
        };
    }

    private static String formatLineNumber(int line, int total) {
        int digits = Math.max(2, (int) Math.floor(Math.log10(Math.max(1, total))) + 1);
        return String.format("%" + digits + "d", line);
    }

    private void setupEventHandlers() {
        // Text change listener for autocomplete
        this.textProperty().addListener((obs, oldText, newText) -> {
            if (!isAutocompleteActive[0] || !autocompletePopup.isShowing()) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastUpdateTime[0] < 150) {
                return;
            }
            lastUpdateTime[0] = now;

            String currentWord = getCurrentWord();
            if (currentWord.isEmpty()) {
                autocompletePopup.hide();
                isAutocompleteActive[0] = false;
                return;
            }

            requestSuggestions(currentWord, false);
        });

        // Keyboard event handler
        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                handleCtrlSpace(event);
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.F) {
                handleCtrlShiftF(event);
            } else if (event.isControlDown() && event.getCode() == KeyCode.F) {
                handleCtrlF(event);
            } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
                handleCtrlD(event);
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.ENTER) {
                handleCtrlShiftEnter(event);
            } else if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                handleCtrlEnter(event);
            } else if (autocompletePopup.isShowing()) {
                handleAutocompleteKeys(event);
            }
        });

        // Focus listener
        this.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && autocompletePopup.isShowing()) {
                autocompletePopup.hide();
                isAutocompleteActive[0] = false;
            }
        });

        // Autocomplete selection handler
        autocompletePopup.getListView().setOnMouseClicked(e -> {
            String selected = autocompletePopup.getSelectedItem();
            if (selected != null) {
                insertSuggestion(selected);
                autocompletePopup.hide();
                isAutocompleteActive[0] = false;
                this.requestFocus();
            }
        });
    }

    private void setupContextMenu() {
        MenuItem undoItem = new MenuItem("Undo", menuIcon(Feather.CORNER_DOWN_LEFT));
        undoItem.setOnAction(event -> this.undo());

        MenuItem redoItem = new MenuItem("Redo", menuIcon(Feather.CORNER_DOWN_RIGHT));
        redoItem.setOnAction(event -> this.redo());

        MenuItem cutItem = new MenuItem("Cut", menuIcon(Feather.SCISSORS));
        cutItem.setOnAction(event -> this.cut());

        MenuItem copyItem = new MenuItem("Copy", menuIcon(MaterialDesignC.CONTENT_COPY));
        copyItem.setOnAction(event -> this.copy());

        MenuItem pasteItem = new MenuItem("Paste", menuIcon(MaterialDesignC.CONTENT_PASTE));
        pasteItem.setOnAction(event -> this.paste());

        MenuItem selectAllItem = new MenuItem("Select All", menuIcon(MaterialDesignS.SELECT_ALL));
        selectAllItem.setOnAction(event -> this.selectAll());

        MenuItem formatItem = new MenuItem("Format", menuIcon(MaterialDesignA.AUTO_FIX));
        formatItem.setOnAction(event -> formatEditorContent());

        ContextMenu editorContextMenu = new ContextMenu(
                undoItem, redoItem,
                new SeparatorMenuItem(),
                cutItem, copyItem, pasteItem,
                new SeparatorMenuItem(),
                selectAllItem, formatItem
        );

        editorContextMenu.setOnShowing(event -> {
            undoItem.setDisable(!this.isUndoAvailable());
            redoItem.setDisable(!this.isRedoAvailable());

            boolean hasSelection = !this.getSelectedText().isEmpty();
            cutItem.setDisable(!hasSelection);
            copyItem.setDisable(!hasSelection);

            pasteItem.setDisable(!Clipboard.getSystemClipboard().hasString());
        });

        this.setContextMenu(editorContextMenu);
    }

    private static FontIcon menuIcon(Ikon ikon) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(14);
        return icon;
    }

    private void handleCtrlSpace(KeyEvent event) {
        event.consume();
        requestSuggestions(getCurrentWord(), true);
    }

    private void handleCtrlF(KeyEvent event) {
        event.consume();
        if (!findPopup.isShowing()) {
            double x = this.localToScreen(this.getBoundsInLocal()).getMaxX() - 350;
            double y = this.localToScreen(this.getBoundsInLocal()).getMinY() + 10;
            findPopup.show(x, y);
        }
    }

    private void handleCtrlD(KeyEvent event) {
        event.consume();
        duplicateCurrentLine();
    }

    private void handleCtrlEnter(KeyEvent event) {
        event.consume();
        QueryService.getInstance().runStatement();
    }

    private void handleCtrlShiftEnter(KeyEvent event){
        event.consume();
        QueryService.getInstance().runQuery();
    }

    private void handleCtrlShiftF(KeyEvent event) {
        event.consume();
        formatEditorContent();
    }

    private void formatEditorContent() {
        ConnectionObject connectionObject = DatabaseConnection.getInstance().getConnectionObject();
        IndexRange selection = this.getSelection();

        if (selection.getLength() > 0) {
            String formatted = SQLFormatterUtils.format(this.getSelectedText(), connectionObject);
            this.replaceText(selection, formatted);
        } else {
            String formatted = SQLFormatterUtils.format(this.getText(), connectionObject);
            this.replaceText(formatted);
        }
    }

    private void handleAutocompleteKeys(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE || event.getCode() == KeyCode.SPACE) {
            autocompletePopup.hide();
            isAutocompleteActive[0] = false;
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
            }
        } else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
            String selected = autocompletePopup.getSelectedItem();
            if (selected != null) {
                insertSuggestion(selected);
                autocompletePopup.hide();
                isAutocompleteActive[0] = false;
            }
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            autocompletePopup.selectNext();
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            autocompletePopup.selectPrevious();
            event.consume();
        }
    }

    private List<String> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();

        boolean endsWithDot = lowerPrefix.endsWith(".");
        String[] parts = lowerPrefix.split("\\.");

        if (endsWithDot) {
            String[] newParts = new String[parts.length + 1];
            System.arraycopy(parts, 0, newParts, 0, parts.length);
            newParts[parts.length] = "";
            parts = newParts;
        }

        try {
            ConnectionObject connObj = DatabaseConnection.getInstance().getConnectionObject();
            CustomTree.DatabaseStructureCache cache = App.getDatabaseTreeForConnection(connObj).getStructureCache();

            if (connObj.getDbType().equals(ConnectionTypes.PostgreSQL.toString())) {
                handlePostgresSuggestionsFromCache(cache, parts, suggestions);
            } else {
                handleDefaultSuggestionsFromCache(cache, parts, suggestions);
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }

        String lastPart = parts[parts.length - 1];
        return suggestions.stream()
                .distinct()
                .sorted((s1, s2) -> compareRelevance(s1, s2, lastPart))
                .limit(50)
                .collect(Collectors.toList());
    }

    private void handlePostgresSuggestionsFromCache(CustomTree.DatabaseStructureCache cache,
                                                    String[] parts, List<String> suggestions) {
        ArrayList<String> schemas = cache.getPostgresSchemas();
        String lastPart = parts[parts.length - 1];

        if (parts.length == 1) {
            for (String schema : schemas) {
                if (schema.toLowerCase().startsWith(lastPart)) {
                    suggestions.add(schema);
                }

                LinkedHashMap<String, ArrayList<ColumnData>> tables = cache.getPostgresSchema(schema);
                if (tables != null) {
                    for (Map.Entry<String, ArrayList<ColumnData>> entry : tables.entrySet()) {
                        if (entry.getKey().toLowerCase().startsWith(lastPart)) {
                            suggestions.add(entry.getKey());
                        }
                        for (ColumnData col : entry.getValue()) {
                            if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                suggestions.add(col.getColumnName());
                            }
                        }
                    }
                }
            }
        } else if (parts.length == 2) {
            String firstPart = parts[0];

            for (String schema : schemas) {
                if (schema.toLowerCase().equals(firstPart)) {
                    LinkedHashMap<String, ArrayList<ColumnData>> tables = cache.getPostgresSchema(schema);
                    if (tables != null) {
                        for (String tableName : tables.keySet()) {
                            if (tableName.toLowerCase().startsWith(lastPart)) {
                                suggestions.add(schema + "." + tableName);
                            }
                        }
                    }
                    return;
                }
            }

            for (String schema : schemas) {
                LinkedHashMap<String, ArrayList<ColumnData>> tables = cache.getPostgresSchema(schema);
                if (tables != null) {
                    ArrayList<ColumnData> columns = tables.get(firstPart);
                    if (columns != null) {
                        for (ColumnData col : columns) {
                            if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                suggestions.add(firstPart + "." + col.getColumnName());
                            }
                        }
                    }
                }
            }
        } else if (parts.length == 3) {
            String schemaName = parts[0];
            String tableName = parts[1];

            for (String schema : schemas) {
                if (schema.toLowerCase().equals(schemaName)) {
                    LinkedHashMap<String, ArrayList<ColumnData>> tables = cache.getPostgresSchema(schema);
                    if (tables != null) {
                        ArrayList<ColumnData> columns = tables.get(tableName);
                        if (columns != null) {
                            for (ColumnData col : columns) {
                                if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                    suggestions.add(schemaName + "." + tableName + "." + col.getColumnName());
                                }
                            }
                        }
                    }
                    return;
                }
            }
        }
    }

    private void handleDefaultSuggestionsFromCache(CustomTree.DatabaseStructureCache cache,
                                                   String[] parts, List<String> suggestions) {
        ArrayList<String> databases = cache.getDatabases();
        String lastPart = parts[parts.length - 1];

        if (parts.length == 1) {
            for (String database : databases) {
                if (database.toLowerCase().startsWith(lastPart)) {
                    suggestions.add(database);
                }

                Map<String, ArrayList<ColumnData>> tables = cache.getDatabase(database);
                if (tables != null) {
                    for (Map.Entry<String, ArrayList<ColumnData>> entry : tables.entrySet()) {
                        String tableName = entry.getKey();
                        String displayName = tableName.contains(".") ?
                                tableName.substring(tableName.lastIndexOf('.') + 1) : tableName;

                        if (tableName.toLowerCase().startsWith(lastPart) ||
                                displayName.toLowerCase().startsWith(lastPart)) {
                            suggestions.add(tableName);
                        }

                        for (ColumnData col : entry.getValue()) {
                            if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                suggestions.add(col.getColumnName());
                            }
                        }
                    }
                }
            }
        } else if (parts.length == 2) {
            String firstPart = parts[0];

            for (String database : databases) {
                if (database.toLowerCase().equals(firstPart)) {
                    Map<String, ArrayList<ColumnData>> tables = cache.getDatabase(database);
                    if (tables != null) {
                        for (String tableName : tables.keySet()) {
                            if (tableName.toLowerCase().startsWith(lastPart)) {
                                suggestions.add(database + "." + tableName);
                            }
                        }
                    }
                    return;
                }
            }

            for (String database : databases) {
                Map<String, ArrayList<ColumnData>> tables = cache.getDatabase(database);
                if (tables != null) {
                    for (String tableName : tables.keySet()) {
                        if (tableName.contains(".")) {
                            String[] tableParts = tableName.split("\\.", 2);
                            if (tableParts[0].toLowerCase().equals(firstPart) &&
                                    tableParts[1].toLowerCase().startsWith(lastPart)) {
                                suggestions.add(tableName);
                            }
                        }
                    }
                }
            }

            for (String database : databases) {
                Map<String, ArrayList<ColumnData>> tables = cache.getDatabase(database);
                if (tables != null) {
                    ArrayList<ColumnData> columns = tables.get(firstPart);
                    if (columns != null) {
                        for (ColumnData col : columns) {
                            if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                suggestions.add(firstPart + "." + col.getColumnName());
                            }
                        }
                    }
                }
            }
        } else if (parts.length == 3) {
            String firstPart = parts[0];
            String secondPart = parts[1];

            for (String database : databases) {
                if (database.toLowerCase().equals(firstPart)) {
                    Map<String, ArrayList<ColumnData>> tables = cache.getDatabase(database);
                    if (tables != null) {
                        ArrayList<ColumnData> columns = tables.get(secondPart);
                        if (columns != null) {
                            for (ColumnData col : columns) {
                                if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                    suggestions.add(firstPart + "." + secondPart + "." + col.getColumnName());
                                }
                            }
                            return;
                        }
                    }
                }
            }

            String schemaTable = firstPart + "." + secondPart;
            for (String database : databases) {
                Map<String, ArrayList<ColumnData>> tables = cache.getDatabase(database);
                if (tables != null) {
                    for (Map.Entry<String, ArrayList<ColumnData>> entry : tables.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(schemaTable)) {
                            ArrayList<ColumnData> columns = entry.getValue();
                            for (ColumnData col : columns) {
                                if (col.getColumnName().toLowerCase().startsWith(lastPart)) {
                                    suggestions.add(entry.getKey() + "." + col.getColumnName());
                                }
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private int compareRelevance(String s1, String s2, String prefix) {
        String s1Name = s1.contains(".") ? s1.substring(s1.lastIndexOf('.') + 1) : s1;
        String s2Name = s2.contains(".") ? s2.substring(s2.lastIndexOf('.') + 1) : s2;

        s1Name = s1Name.toLowerCase();
        s2Name = s2Name.toLowerCase();

        if (s1Name.equals(prefix)) return -1;
        if (s2Name.equals(prefix)) return 1;

        boolean s1Starts = s1Name.startsWith(prefix);
        boolean s2Starts = s2Name.startsWith(prefix);

        if (s1Starts && !s2Starts) return -1;
        if (!s1Starts && s2Starts) return 1;
        if (s1Starts && s2Starts && s1Name.length() != s2Name.length()) {
            return s1Name.length() - s2Name.length();
        }

        int s1Dots = (int) s1.chars().filter(ch -> ch == '.').count();
        int s2Dots = (int) s2.chars().filter(ch -> ch == '.').count();
        if (s1Dots != s2Dots) return s1Dots - s2Dots;

        return s1.compareTo(s2);
    }

    private String getCurrentWord() {
        int caretPos = this.getCaretPosition();
        String text = this.getText();

        if (caretPos == 0 || caretPos > text.length()) {
            return "";
        }

        int start = caretPos - 1;
        while (start >= 0 && isWordChar(text.charAt(start))) {
            start--;
        }
        start++;

        return text.substring(start, caretPos);
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private void insertSuggestion(String suggestion) {
        int caretPos = this.getCaretPosition();
        String text = this.getText();

        int start = caretPos - 1;
        while (start >= 0 && isWordChar(text.charAt(start))) {
            start--;
        }
        start++;

        this.replaceText(start, caretPos, suggestion);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("STRING") != null ? "string" :
                                    matcher.group("COMMENT") != null ? "comment" :
                                            matcher.group("NUMBER") != null ? "number" :
                                                    null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);

        return spansBuilder.create();
    }

    private void duplicateCurrentLine() {
        int caretPos = this.getCaretPosition();
        String text = this.getText();

        int lineStart = caretPos;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        int lineEnd = caretPos;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
            lineEnd++;
        }

        String currentLine = text.substring(lineStart, lineEnd);
        String insertion = "\n" + currentLine;
        this.insertText(lineEnd, insertion);

        int relativePos = caretPos - lineStart;
        this.moveTo(lineEnd + 1 + relativePos);
    }

    public void replaceTextWithStyle(String newText) {
        Platform.runLater(() -> {
            this.replaceText(newText);
        });
    }

    public void shutdown() {
        suggestionService.cancel();
        if (findPopup != null && findPopup.isShowing()) {
            findPopup.hide();
        }
    }
}