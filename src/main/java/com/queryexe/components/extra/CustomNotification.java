package com.queryexe.components.extra;

import org.kordamp.ikonli.javafx.FontIcon;
import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import com.queryexe.queryexe.App;

public class CustomNotification {

    private final Notification notification;
    private final Timeline slideInAnimation;
    private final Timeline slideOutAnimation;
    private final PauseTransition pause;
    private StackPane customPane;

    public CustomNotification(String content, FontIcon icon) {
        notification = new Notification(content, icon);
        notification.getStyleClass().add(Styles.ELEVATED_1);
        notification.getStyleClass().add(Styles.ACCENT);
        notification.setPrefHeight(Region.USE_PREF_SIZE);
        notification.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane.setAlignment(notification, Pos.TOP_RIGHT);
        StackPane.setMargin(notification, new Insets(10, 10, 0, 0));

        slideInAnimation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(notification.translateXProperty(), 300)),
                new KeyFrame(Duration.millis(200), new KeyValue(notification.translateXProperty(), 0)));

        slideOutAnimation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(notification.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(250), new KeyValue(notification.translateXProperty(), 300)));

        pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> dismissNotification());

        notification.setOnClose(e -> dismissNotification());

        slideInAnimation.setOnFinished(e -> pause.play());
        slideOutAnimation.setOnFinished(e -> {
            if (customPane != null) {
                customPane.getChildren().remove(notification);
            } else {
                App.getStackPane().getChildren().remove(notification);
            }
        });
    }

    public void showNotification() {
        notification.setTranslateX(300);

        if (App.getStackPane().getChildren().contains(notification)) {
            App.getStackPane().getChildren().remove(notification);
        }
        Platform.runLater(() -> {
            App.getStackPane().getChildren().add(notification);
        });
        slideInAnimation.playFromStart();
    }

    public void showNotificationOnCustomPane(StackPane pane) {
        this.customPane = pane;

        StackPane.setMargin(notification, new Insets(10 + App.getHeaderBox().getHeight(), 10, 0, 0));

        notification.setTranslateX(300);

        if (pane.getChildren().contains(notification)) {
            pane.getChildren().remove(notification);
        }
        Platform.runLater(() -> {
            pane.getChildren().add(notification);
        });
        slideInAnimation.playFromStart();
    }

    private void dismissNotification() {
        slideInAnimation.stop();
        pause.stop();

        slideOutAnimation.playFromStart();
    }

    public Notification getNotification() {
        return notification;
    }
}