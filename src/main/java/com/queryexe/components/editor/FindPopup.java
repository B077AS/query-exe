package com.queryexe.components.editor;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindPopup {
    private final Popup popup;
    private final TextField searchField;
    private final TextField replaceField;
    private final CheckBox caseSensitiveCheckBox;
    private final Label matchLabel;
    private final CodeArea codeArea;
    private final List<Match> matches = new ArrayList<>();
    private int currentMatchIndex = -1;

    private static class Match {
        int start;
        int end;

        Match(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public FindPopup(CodeArea codeArea) {
        this.codeArea = codeArea;
        this.popup = new Popup();
        this.popup.setAutoHide(true);
        this.popup.setHideOnEscape(true);

        VBox container = new VBox(8);
        container.setStyle(
                "-fx-background-color: -color-bg-overlay; " +
                        "-fx-background-radius: 5; " +
                        "-fx-padding: 12; " +
                        "-fx-border-color: -color-border-default;" +
                        "-fx-border-width: 1px;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 2);"
        );
        container.setPrefWidth(400);

        // Find row
        HBox findRow = new HBox(8);
        findRow.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Find...");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.setFocusTraversable(false);
        closeButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        findRow.getChildren().addAll(searchField, closeButton);

        // Replace row
        HBox replaceRow = new HBox(8);
        replaceRow.setAlignment(Pos.CENTER_LEFT);

        replaceField = new TextField();
        replaceField.setPromptText("Replace...");
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        // Invisible spacer to align with close button width
        Label spacer = new Label();
        spacer.setPrefWidth(32);
        spacer.setMinWidth(32);
        spacer.setMaxWidth(32);

        replaceRow.getChildren().addAll(replaceField, spacer);

        // Navigation and options row
        HBox navRow = new HBox(8);
        navRow.setAlignment(Pos.CENTER_LEFT);

        caseSensitiveCheckBox = new CheckBox("Match case");
        caseSensitiveCheckBox.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 12px;");
        caseSensitiveCheckBox.setSelected(false);

        HBox navSpacer = new HBox();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        matchLabel = new Label("0/0");
        matchLabel.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 11px;");
        matchLabel.setMinWidth(50);
        matchLabel.setAlignment(Pos.CENTER);

        Button prevButton = new Button(null, new FontIcon(MaterialDesignA.ARROW_UP_THIN));
        prevButton.setFocusTraversable(false);
        prevButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        Button nextButton = new Button(null, new FontIcon(MaterialDesignA.ARROW_DOWN_THIN));
        nextButton.setFocusTraversable(false);
        nextButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        navRow.getChildren().addAll(caseSensitiveCheckBox, navSpacer, matchLabel, prevButton, nextButton);

        // Button row
        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        Button replaceButton = new Button("Replace");
        replaceButton.setDefaultButton(false);
        replaceButton.setFocusTraversable(false);
        replaceButton.getStyleClass().add(Styles.ACCENT);
        replaceButton.setStyle("-fx-padding: 6 12; -fx-font-size: 12px;");

        Button replaceAllButton = new Button("Replace All");
        replaceAllButton.setDefaultButton(false);
        replaceAllButton.setFocusTraversable(false);
        replaceAllButton.getStyleClass().add(Styles.ACCENT);
        replaceAllButton.setStyle("-fx-padding: 6 12; -fx-font-size: 12px;");

        buttonRow.getChildren().addAll(replaceButton, replaceAllButton);

        container.getChildren().addAll(findRow, replaceRow, navRow, buttonRow);
        popup.getContent().add(container);

        // Event handlers
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            performSearch(newVal);
        });

        caseSensitiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            performSearch(searchField.getText());
        });

        prevButton.setOnAction(e -> previousMatch());
        nextButton.setOnAction(e -> nextMatch());
        closeButton.setOnAction(e -> hide());
        replaceButton.setOnAction(e -> replaceCurrent());
        replaceAllButton.setOnAction(e -> replaceAll());

        searchField.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    if (event.isShiftDown()) {
                        previousMatch();
                    } else {
                        nextMatch();
                    }
                    event.consume();
                    break;
                case ESCAPE:
                    hide();
                    event.consume();
                    break;
            }
        });

        replaceField.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    if (event.isControlDown()) {
                        replaceAll();
                    } else {
                        replaceCurrent();
                    }
                    event.consume();
                    break;
                case ESCAPE:
                    hide();
                    event.consume();
                    break;
            }
        });
    }

    public void show(double x, double y) {
        if (!popup.isShowing()) {
            popup.show(codeArea.getScene().getWindow(), x, y);
            searchField.requestFocus();
        }
    }

    public void hide() {
        popup.hide();
        clearHighlights();
        matches.clear();
        currentMatchIndex = -1;
        searchField.clear();
        replaceField.clear();
        codeArea.requestFocus();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    private void performSearch(String searchText) {
        matches.clear();
        currentMatchIndex = -1;
        clearHighlights();

        if (searchText == null || searchText.trim().isEmpty()) {
            updateMatchLabel();
            return;
        }

        String text = codeArea.getText();
        int flags = caseSensitiveCheckBox.isSelected() ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern = Pattern.compile(Pattern.quote(searchText), flags);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            matches.add(new Match(matcher.start(), matcher.end()));
        }

        updateMatchLabel();

        if (!matches.isEmpty()) {
            currentMatchIndex = 0;
            highlightCurrentMatch();
        }
    }

    private void nextMatch() {
        if (matches.isEmpty()) return;

        currentMatchIndex = (currentMatchIndex + 1) % matches.size();
        highlightCurrentMatch();
        updateMatchLabel();
    }

    private void previousMatch() {
        if (matches.isEmpty()) return;

        currentMatchIndex = (currentMatchIndex - 1 + matches.size()) % matches.size();
        highlightCurrentMatch();
        updateMatchLabel();
    }

    private void replaceCurrent() {
        if (matches.isEmpty() || currentMatchIndex < 0 || currentMatchIndex >= matches.size()) {
            return;
        }

        String replaceText = replaceField.getText();
        if (replaceText == null) {
            replaceText = "";
        }

        Match match = matches.get(currentMatchIndex);

        codeArea.replaceText(match.start, match.end, replaceText);

        String searchText = searchField.getText();
        performSearch(searchText);

        if (!matches.isEmpty()) {
            if (currentMatchIndex >= matches.size()) {
                currentMatchIndex = matches.size() - 1;
            }
            if (currentMatchIndex >= 0) {
                highlightCurrentMatch();
            }
        }
    }

    private void replaceAll() {
        if (matches.isEmpty()) {
            return;
        }

        String searchText = searchField.getText();
        String replaceText = replaceField.getText();

        if (searchText == null || searchText.trim().isEmpty()) {
            return;
        }

        if (replaceText == null) {
            replaceText = "";
        }

        String text = codeArea.getText();

        int flags = caseSensitiveCheckBox.isSelected() ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern = Pattern.compile(Pattern.quote(searchText), flags);
        Matcher matcher = pattern.matcher(text);
        String newText = matcher.replaceAll(replaceText);

        codeArea.replaceText(newText);

        matches.clear();
        currentMatchIndex = -1;
        clearHighlights();
        updateMatchLabel();
    }

    private void highlightCurrentMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= matches.size()) return;

        Match match = matches.get(currentMatchIndex);
        codeArea.selectRange(match.start, match.end);
        codeArea.requestFollowCaret();
    }

    private void clearHighlights() {
        codeArea.deselect();
    }

    private void updateMatchLabel() {
        if (matches.isEmpty()) {
            matchLabel.setText("0/0");
        } else {
            matchLabel.setText((currentMatchIndex + 1) + "/" + matches.size());
        }
    }
}