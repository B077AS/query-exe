package com.queryexe.components.modals;

import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.ParallelTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import com.queryexe.queryexe.App;

public class HelpModal extends VBox {
    private int currentIndex = 0;
    private VBox[] cards;
    private StackPane carouselPane;

    public HelpModal() {
        this.setAlignment(Pos.TOP_CENTER);
        this.setStyle("-fx-background-color: -color-bg-overlay; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-border-color: -color-border-default; -fx-border-width: 1px;");
        this.setMaxSize(1000, 550);
        this.setMinSize(1000, 550);
        this.setPrefSize(1000, 550);
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

        VBox content = new VBox(20);
        content.setPadding(new Insets(10, 10, 30, 10));
        content.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Keyboard Shortcuts");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Speed up your workflow with these shortcuts");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: gray;");

        // Create carousel pane
        carouselPane = new StackPane();
        carouselPane.setAlignment(Pos.CENTER);
        carouselPane.setPrefHeight(380);
        carouselPane.setMaxHeight(380);

        // Create all cards
        cards = new VBox[3];
        cards[0] = createShortcutCard("Editor", MaterialDesignC.CODE_TAGS, new String[][]{
                {"Ctrl + Space", "Trigger autocomplete"},
                {"Ctrl + Enter", "Run current statement"},
                {"Ctrl + Shift + Enter", "Run entire query/script"},
                {"Ctrl + D", "Duplicate current line"},
                {"Ctrl + F", "Find and replace"},
                {"Ctrl + O", "Open file"},
                {"Ctrl + S", "Save file"}
        });

        cards[1] = createShortcutCard("Find & Replace", MaterialDesignM.MAGNIFY, new String[][]{
                {"Enter", "Find next match"},
                {"Shift + Enter", "Find previous match"},
                {"Ctrl + Enter", "Replace all occurrences"},
                {"Enter (replace field)", "Replace current match"},
                {"Escape", "Close find popup"}
        });

        cards[2] = createShortcutCard("Autocomplete", MaterialDesignA.AUTO_FIX, new String[][]{
                {"Enter / Tab", "Insert selected suggestion"},
                {"↑ / ↓", "Navigate through suggestions"},
                {"Escape / Space", "Close autocomplete popup"}
        });

        // Add all cards to carousel
        for (VBox card : cards) {
            carouselPane.getChildren().add(card);
        }

        // Navigation buttons
        HBox navBox = new HBox(20);
        navBox.setAlignment(Pos.CENTER);

        Button prevButton = new Button(null, new FontIcon(MaterialDesignC.CHEVRON_LEFT));
        prevButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.ACCENT);
        prevButton.setOnAction(e -> navigateCarousel(-1));

        Button nextButton = new Button(null, new FontIcon(MaterialDesignC.CHEVRON_RIGHT));
        nextButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.ACCENT);
        nextButton.setOnAction(e -> navigateCarousel(1));

        navBox.getChildren().addAll(prevButton, nextButton);

        content.getChildren().addAll(titleLabel, subtitleLabel, carouselPane, navBox);

        this.getChildren().addAll(headerBox, content);

        // Initial positioning
        updateCarouselPositions(false);
    }

    private void navigateCarousel(int direction) {
        currentIndex = (currentIndex + direction + cards.length) % cards.length;
        updateCarouselPositions(true);
    }

    private void updateCarouselPositions(boolean animate) {
        for (int i = 0; i < cards.length; i++) {
            int relativePos = i - currentIndex;

            // Wrap around for circular carousel
            if (relativePos > 1) relativePos -= cards.length;
            if (relativePos < -1) relativePos += cards.length;

            double targetX = relativePos * 350;
            double targetScale = (relativePos == 0) ? 1.0 : 0.6;
            double targetOpacity = (relativePos == 0) ? 1.0 : 0.35;

            // Set z-order: center card on top
            cards[i].setViewOrder(relativePos == 0 ? -1 : 0);

            if (animate) {
                TranslateTransition translateTransition = new TranslateTransition(Duration.millis(400), cards[i]);
                translateTransition.setToX(targetX);

                ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(400), cards[i]);
                scaleTransition.setToX(targetScale);
                scaleTransition.setToY(targetScale);

                ParallelTransition parallel = new ParallelTransition(translateTransition, scaleTransition);
                parallel.play();

                cards[i].setOpacity(targetOpacity);
            } else {
                cards[i].setTranslateX(targetX);
                cards[i].setScaleX(targetScale);
                cards[i].setScaleY(targetScale);
                cards[i].setOpacity(targetOpacity);
            }
        }
    }

    private VBox createShortcutCard(String title, Ikon icon, String[][] shortcuts) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(450);
        card.setMinWidth(450);
        card.setPrefWidth(450);
        card.setStyle(
                "-fx-background-color: -color-bg-default; " +
                        "-fx-border-color: -color-border-default; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 6px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-padding: 20;"
        );

        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon sectionIcon = new FontIcon(icon);
        sectionIcon.getStyleClass().add("custom-20-icon");

        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        headerBox.getChildren().addAll(sectionIcon, sectionTitle);

        VBox shortcutsList = new VBox(10);

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