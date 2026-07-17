package com.queryexe.components.extra;

import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

public class CustomProgressIndicator extends StackPane {

    private ProgressIndicator progressIndicator;

    public CustomProgressIndicator(double width, double height) {
        this.setAlignment(Pos.CENTER);
        this.setTranslateY(1);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMinSize(width, height);
        progressIndicator.setMaxSize(width, height);

        this.getChildren().add(progressIndicator);
    }

    public void setProgress(double progress) {
        progressIndicator.setProgress(progress);
    }
}
