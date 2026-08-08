package com.queryexe.components.modals;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import com.queryexe.utils.IconColorUtil;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.queryexe.queryexe.App;

public class ConfirmationModal extends VBox {

    public ConfirmationModal(String title, String message, FontIcon icon, Runnable onConfirmAction) {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(450, 200);
        this.setMinSize(450, 200);
        this.setPrefSize(450, 200);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(5, 5, 0, 0));

        Region headerFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> {
            App.closeModal();
        });

        headerBox.getChildren().addAll(headerFillerRegion, closeButton);
        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);

        HBox upperBox = new HBox();
        upperBox.setPadding(new Insets(0, 10, 0, 10));
        upperBox.setAlignment(Pos.CENTER_LEFT);
        upperBox.setSpacing(20);

        VBox textBox = new VBox();
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setSpacing(10);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label textLabel = new Label(message);
        textLabel.setStyle("-fx-font-size: 15px;");
        textLabel.setWrapText(true);
        textBox.getChildren().addAll(titleLabel, textLabel);

        IconColorUtil.apply(icon, "-color-fg-default", 50);
        upperBox.getChildren().addAll(icon, textBox);

        HBox buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setSpacing(10);
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));

        Button confirmButton = new Button("Confirm");
        confirmButton.setDefaultButton(true);
        confirmButton.setOnAction(event -> {
            if (onConfirmAction != null) {
                onConfirmAction.run();
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            App.closeModal();
        });

        buttonsBox.getChildren().addAll(confirmButton, cancelButton);

        VBox.setVgrow(buttonsBox, Priority.ALWAYS);
        this.getChildren().addAll(headerBox, upperBox, buttonsBox);
    }
}
