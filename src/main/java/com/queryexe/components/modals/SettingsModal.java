package com.queryexe.components.modals;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import atlantafx.base.theme.Styles;

import com.queryexe.queryexe.App;
import com.queryexe.theme.AppTheme;
import com.queryexe.theme.ThemeManager;

public class SettingsModal extends VBox {

    private FlowPane swatchGrid;

    public SettingsModal() {
        this.setAlignment(Pos.TOP_CENTER);
        this.getStyleClass().add("modal-container");
        this.setMaxSize(560, 420);
        this.setMinSize(560, 420);
        this.setPrefSize(560, 420);
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

        VBox content = new VBox(16);
        content.setPadding(new Insets(0, 30, 30, 30));
        content.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Settings");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        VBox sectionBox = new VBox(10);
        sectionBox.setAlignment(Pos.TOP_LEFT);
        sectionBox.setMaxWidth(480);

        Label sectionLabel = new Label("Color Scheme");
        sectionLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");

        swatchGrid = new FlowPane(14, 20);
        swatchGrid.setPadding(new Insets(4, 0, 0, 0));
        populateSwatches();

        sectionBox.getChildren().addAll(sectionLabel, swatchGrid);

        ScrollPane scrollPane = new ScrollPane(sectionBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        content.getChildren().addAll(titleLabel, scrollPane);

        this.getChildren().addAll(headerBox, content);
    }

    private void populateSwatches() {
        swatchGrid.getChildren().clear();
        for (AppTheme theme : AppTheme.values()) {
            swatchGrid.getChildren().add(buildSwatch(theme));
        }
    }

    private VBox buildSwatch(AppTheme theme) {
        boolean active = ThemeManager.get().getCurrentTheme() == theme;

        Circle colorCircle = new Circle(20);
        colorCircle.setStyle("-fx-fill: " + theme.getSwatchColor() + ";");

        StackPane circlePane = new StackPane(colorCircle);
        if (active) {
            circlePane.getChildren().add(new FontIcon(MaterialDesignC.CHECK));
        }

        Label nameLabel = new Label(theme.getDisplayName());
        nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        VBox box = new VBox(8, circlePane, nameLabel);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(84);
        box.setPadding(new Insets(10));

        if (active) {
            box.setStyle(
                    "-fx-background-color: -color-bg-subtle;" +
                    "-fx-background-radius: 8px;" +
                    "-fx-border-color: " + theme.getSwatchColor() + ";" +
                    "-fx-border-radius: 8px;" +
                    "-fx-border-width: 2px;");
        } else {
            applyIdleStyle(box);
            box.setOnMouseEntered(e -> box.setStyle(
                    "-fx-background-color: -color-bg-subtle;" +
                    "-fx-background-radius: 8px;" +
                    "-fx-border-color: transparent;" +
                    "-fx-border-radius: 8px;" +
                    "-fx-border-width: 2px;" +
                    "-fx-cursor: hand;"));
            box.setOnMouseExited(e -> applyIdleStyle(box));
        }

        box.setOnMouseClicked(e -> {
            ThemeManager.get().apply(theme, App.getScene());
            populateSwatches();
        });

        return box;
    }

    private static void applyIdleStyle(VBox box) {
        box.setStyle(
                "-fx-background-radius: 8px;" +
                "-fx-border-color: transparent;" +
                "-fx-border-radius: 8px;" +
                "-fx-border-width: 2px;" +
                "-fx-cursor: hand;");
    }
}
