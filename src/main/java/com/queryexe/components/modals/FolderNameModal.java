package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import com.queryexe.queryexe.App;
import com.queryexe.utils.IconColorUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;

import java.util.function.Consumer;

/** Asks for a folder name; used for both creating and renaming connection folders. */
public class FolderNameModal extends VBox {

    private final TextField nameField = new TextField();

    public FolderNameModal(String title, String initialName, String confirmLabel, Consumer<String> onConfirm) {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(450, 220);
        this.setMinSize(450, 220);
        this.setPrefSize(450, 220);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(5, 5, 0, 0));
        Region headerFiller = new Region();
        HBox.setHgrow(headerFiller, Priority.ALWAYS);
        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> App.closeModal());
        headerBox.getChildren().addAll(headerFiller, closeButton);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label instructionLabel = new Label("Folders keep the connection list tidy.");
        instructionLabel.setStyle("-fx-font-size: 15px;");

        nameField.setPromptText("Folder name");
        nameField.setPrefWidth(350);
        if (initialName != null) {
            nameField.setText(initialName);
        }

        VBox contentBox = new VBox(15, titleLabel, instructionLabel, nameField);
        contentBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon folderIcon = new FontIcon(MaterialDesignF.FOLDER_PLUS_OUTLINE);
        IconColorUtil.apply(folderIcon, "-color-fg-default", 50);

        HBox upperBox = new HBox(20, folderIcon, contentBox);
        upperBox.setPadding(new Insets(0, 10, 0, 10));
        upperBox.setAlignment(Pos.CENTER_LEFT);

        Button confirmButton = new Button(confirmLabel);
        confirmButton.getStyleClass().add(Styles.SMALL);
        confirmButton.setDefaultButton(true);
        confirmButton.setOnAction(event -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                App.closeModal();
                onConfirm.accept(name);
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add(Styles.SMALL);
        cancelButton.setOnAction(event -> App.closeModal());

        HBox buttonsBox = new HBox(10, confirmButton, cancelButton);
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));
        VBox.setVgrow(buttonsBox, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, upperBox, buttonsBox);
    }

    public void focusField() {
        nameField.requestFocus();
        nameField.selectAll();
    }
}
