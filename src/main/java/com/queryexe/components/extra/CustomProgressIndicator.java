package com.queryexe.components.extra;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import com.queryexe.service.SingleExecutorService;

public class CustomProgressIndicator extends StackPane {

    private ProgressIndicator progressIndicator;
    private Button cancelButton;

    public CustomProgressIndicator(double width, double height) {
        this.setAlignment(Pos.CENTER);
        this.setTranslateY(1);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMinSize(width, height);
        progressIndicator.setMaxSize(width, height);

        cancelButton = new Button(null, new FontIcon(MaterialDesignS.STOP));
        cancelButton.setVisible(false);
        cancelButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        cancelButton.setOnMouseClicked(event -> {
            SingleExecutorService.stopRunning();
            this.setVisible(false);
        });

        this.getChildren().addAll(progressIndicator/*, cancelButton*/);

        /*this.setOnMouseEntered(event -> {
            progressIndicator.setVisible(false);
            cancelButton.setVisible(true);
        });

        this.setOnMouseExited(event -> {
            progressIndicator.setVisible(true);
            cancelButton.setVisible(false);
        });*/
    }

    public void setProgress(double progress) {
        progressIndicator.setProgress(progress);
    }
}