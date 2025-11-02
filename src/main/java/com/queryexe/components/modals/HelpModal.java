package com.queryexe.components.modals;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import com.queryexe.queryexe.App;

public class HelpModal extends VBox {

    public HelpModal() {
        this.setAlignment(Pos.TOP_CENTER);
        this.setStyle("-fx-background-color: -color-bg-overlay; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-border-color: -color-border-default; -fx-border-width: 1px;");
        this.setMaxSize(500, 550);
        this.setMinSize(500, 550);
        this.setPrefSize(500, 550);
        this.setTranslateY(10);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(10, 10, 0, 0));

        Region headerFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> App.closeModal());

        headerBox.getChildren().addAll(headerFillerRegion, closeButton);
        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10, 10, 30, 10));
        content.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Keyboard Shortcuts");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Speed up your workflow with these shortcuts");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: gray;");

        VBox editorSection = createShortcutCard("Editor", MaterialDesignC.CODE_TAGS, new String[][]{
                {"Ctrl + Space", "Trigger autocomplete"},
                {"Ctrl + D", "Duplicate current line"},
                {"Ctrl + F", "Find and replace"},
                {"Ctrl + O", "Open file"},
                {"Ctrl + S", "Save file"}
        });

        VBox findSection = createShortcutCard("Find & Replace", MaterialDesignM.MAGNIFY, new String[][]{
                {"Enter", "Find next match"},
                {"Shift + Enter", "Find previous match"},
                {"Ctrl + Enter", "Replace all occurrences"},
                {"Enter (replace field)", "Replace current match"},
                {"Escape", "Close find popup"}
        });

        VBox autocompleteSection = createShortcutCard("Autocomplete", MaterialDesignA.AUTO_FIX, new String[][]{
                {"Enter / Tab", "Insert selected suggestion"},
                {"↑ / ↓", "Navigate through suggestions"},
                {"Escape / Space", "Close autocomplete popup"}
        });

        content.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                editorSection,
                findSection,
                autocompleteSection
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPadding(new Insets(0, 5, 0, 5));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, scrollPane);
    }

    private VBox createShortcutCard(String title, Ikon icon, String[][] shortcuts) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle(
                "-fx-background-color: -color-bg-default; " +
                        "-fx-border-color: -color-border-default; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 6px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-padding: 16;"
        );

        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon sectionIcon = new FontIcon(icon);
        sectionIcon.getStyleClass().add("custom-20-icon");

        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        headerBox.getChildren().addAll(sectionIcon, sectionTitle);

        VBox shortcutsList = new VBox(8);

        for (String[] shortcut : shortcuts) {
            HBox shortcutRow = createShortcutRow(shortcut[0], shortcut[1]);
            shortcutsList.getChildren().add(shortcutRow);
        }

        card.getChildren().addAll(headerBox, shortcutsList);
        return card;
    }

    private HBox createShortcutRow(String keys, String description) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);

        Label keysLabel = new Label(keys);
        keysLabel.setStyle(
                "-fx-font-family: 'Consolas', monospace; " +
                        "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: -color-bg-overlay; " +
                        "-fx-text-fill: -color-accent-emphasis; " +
                        "-fx-padding: 5 10; " +
                        "-fx-border-color: -color-accent-muted; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-min-width: 170px;"
        );
        keysLabel.setAlignment(Pos.CENTER);

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-fg-default;");
        descLabel.setWrapText(true);
        HBox.setHgrow(descLabel, Priority.ALWAYS);

        row.getChildren().addAll(keysLabel, descLabel);
        return row;
    }
}