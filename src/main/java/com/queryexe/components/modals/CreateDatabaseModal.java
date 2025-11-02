package com.queryexe.components.modals;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.queryexe.queryexe.App;

import java.util.function.Consumer;

public class CreateDatabaseModal extends VBox {

    private TextField databaseNameField;

    public CreateDatabaseModal(Consumer<String> onCreateDatabase, boolean isDatabase) {
        this.setStyle("-fx-background-color: -color-bg-default; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-border-color: -color-border-default; -fx-border-width: 1px;");
        this.setMaxSize(450, 220);
        this.setMinSize(450, 220);
        this.setPrefSize(450, 220);

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

        VBox contentBox = new VBox();
        contentBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.setSpacing(15);

        Label titleLabel = new Label(isDatabase ? "Create Database" : "Create Schema");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label instructionLabel = new Label("Enter the name for the new " + (isDatabase ? "database" : "schema") + ":");
        instructionLabel.setStyle("-fx-font-size: 15px;");

        databaseNameField = new TextField();
        databaseNameField.setPromptText(isDatabase ? "Database name" : "Schema name");
        databaseNameField.setPrefWidth(350);

        contentBox.getChildren().addAll(titleLabel, instructionLabel, databaseNameField);

        FontIcon databaseIcon = new FontIcon(MaterialDesignD.DATABASE_PLUS);
        databaseIcon.getStyleClass().add("custom-alert-icon");

        upperBox.getChildren().addAll(databaseIcon, contentBox);

        HBox buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setSpacing(10);
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));

        Button createButton = new Button("Create");
        createButton.setDefaultButton(true);
        createButton.setOnAction(event -> {
            String dbName = databaseNameField.getText().trim();
            if (!dbName.isEmpty()) {
                onCreateDatabase.accept(databaseNameField.getText());
                App.closeModal();
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            App.closeModal();
        });

        buttonsBox.getChildren().addAll(createButton, cancelButton);
        VBox.setVgrow(buttonsBox, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, upperBox, buttonsBox);
    }
}