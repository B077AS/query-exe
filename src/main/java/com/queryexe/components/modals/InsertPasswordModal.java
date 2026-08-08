package com.queryexe.components.modals;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;
import com.queryexe.service.ConnectionService;
import com.queryexe.utils.IconColorUtil;

public class InsertPasswordModal extends VBox {

    private ConnectionObject connectionObject;
    private Runnable onPasswordConfirmed;
    private Button confirmButton;
    private CheckBox savePasswordCheckBox;
    private PasswordTextField passwordField;

    public InsertPasswordModal(ConnectionObject connectionObject, Runnable onPasswordConfirmed) {
        this.connectionObject = connectionObject;
        this.onPasswordConfirmed = onPasswordConfirmed;
        initialize();
        setConnectionCardEvent();
    }

    private void initialize() {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(525, 275);
        this.setMinSize(525, 275);
        this.setPrefSize(525, 275);

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

        VBox mainBox = new VBox();
        mainBox.setMaxWidth(Double.MAX_VALUE);
        mainBox.setAlignment(Pos.CENTER_LEFT);
        mainBox.setSpacing(10);
        Label titleLabel = new Label("Password Required");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label textLabel = new Label("Insert your password");
        textLabel.setStyle("-fx-font-size: 15px;");
        textLabel.setWrapText(true);

        passwordField = new PasswordTextField();
        FontIcon hidePasswordIcon = new FontIcon(MaterialDesignE.EYE_OFF);

        Button hidePasswordButton = new Button(null, hidePasswordIcon);
        hidePasswordButton.getStyleClass().addAll(Styles.FLAT, "no-hover");
        hidePasswordButton.setOnAction(e -> {
            hidePasswordIcon.setIconCode(passwordField.getRevealPassword() ? MaterialDesignE.EYE_OFF : MaterialDesignE.EYE);
            passwordField.setRevealPassword(!passwordField.getRevealPassword());
        });

        HBox passwordBox = new HBox();
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setSpacing(5);

        HBox.setHgrow(passwordField, Priority.ALWAYS);
        passwordBox.getChildren().addAll(passwordField, hidePasswordButton);

        savePasswordCheckBox = new CheckBox("Save Password");

        FontIcon passwordIcon = new FontIcon(MaterialDesignL.LOCK_ALERT);
        IconColorUtil.apply(passwordIcon, "-color-fg-default", 50);

        mainBox.getChildren().addAll(titleLabel, textLabel, passwordBox, savePasswordCheckBox);

        HBox.setHgrow(mainBox, Priority.ALWAYS);
        upperBox.getChildren().addAll(passwordIcon, mainBox);

        HBox buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setSpacing(10);
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));

        confirmButton = new Button("Confirm");
        confirmButton.setPrefWidth(75);
        confirmButton.setDefaultButton(true);

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(75);
        cancelButton.setOnAction(event -> {
            App.closeModal();
        });

        buttonsBox.getChildren().addAll(confirmButton, cancelButton);

        VBox.setVgrow(buttonsBox, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, upperBox, buttonsBox);
    }

    private void setConnectionCardEvent() {
        confirmButton.setOnAction(event -> {
            String newPassword = passwordField.getPassword();
            App.closeModal();
            connectionObject.setPassword(newPassword);

            if (onPasswordConfirmed != null) {
                onPasswordConfirmed.run();
            }

            if (savePasswordCheckBox.isSelected()) {
                ConnectionService.getInstance().updateConnectionProperty(connectionObject.getId(), "password", newPassword);
            }
        });
    }
}