package com.queryexe.components.extra;

import org.kordamp.ikonli.javafx.FontIcon;
import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import com.queryexe.queryexe.App;


public class CustomNotification {

    private static final double NOTIFICATION_VIEW_ORDER = -1000;

    private final Notification notification;
    private final Timeline slideInAnimation;
    private final Timeline slideOutAnimation;
    private final PauseTransition pause;
    private StackPane customPane;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Title + description. This is the preferred way to build a notification. */
    public CustomNotification(String title, String content, FontIcon icon) {
        this(new UpgradedNotification(buildTitledText(title, content), icon));
    }

    /** Title + an arbitrary content node underneath it. */
    public CustomNotification(String title, Node content, FontIcon icon) {
        this(new UpgradedNotification(buildTitled(title, content), icon));
    }

    /** Single-line message. Prefer the title + description constructor. */
    public CustomNotification(String content, FontIcon icon) {
        this(new UpgradedNotification(content, icon));
    }

    /** Pre-built content node. */
    public CustomNotification(Node content, FontIcon icon) {
        this(new UpgradedNotification(content, icon));
    }

    private static Node buildTitledText(String title, String content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(Styles.TEXT_BOLD);

        Label contentLabel = new Label(content);
        contentLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        contentLabel.setWrapText(true);

        return new VBox(2, titleLabel, contentLabel);
    }

    private static Node buildTitled(String title, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(Styles.TEXT_BOLD);
        return new VBox(2, titleLabel, content);
    }

    private CustomNotification(Notification notification) {
        this.notification = notification;
        this.notification.getStyleClass().add(Styles.ELEVATED_1);
        this.notification.getStyleClass().add(Styles.ACCENT);
        this.notification.setPrefHeight(Region.USE_PREF_SIZE);
        this.notification.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane.setAlignment(this.notification, Pos.TOP_RIGHT);
        StackPane.setMargin(this.notification, new Insets(10, 10, 0, 0));

        slideInAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(this.notification.translateXProperty(), 300)),
                new KeyFrame(Duration.millis(200), new KeyValue(this.notification.translateXProperty(), 0)));

        slideOutAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(this.notification.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(250), new KeyValue(this.notification.translateXProperty(), 300)));

        pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> dismissNotification());

        this.notification.setOnMouseEntered(event -> {
            App.getModalPane().setPersistent(true);
            pause.pause();
            slideOutAnimation.pause();
        });

        this.notification.setOnMouseExited(event -> {
            App.getModalPane().setPersistent(false);
            pause.play();
            if (slideOutAnimation.getStatus() == Animation.Status.PAUSED) {
                slideOutAnimation.play();
            }
        });

        this.notification.setOnClose(e -> dismissNotification());

        slideInAnimation.setOnFinished(e -> pause.play());
        slideOutAnimation.setOnFinished(e -> {
            if (customPane != null) {
                customPane.getChildren().remove(this.notification);
            } else {
                App.getStackPane().getChildren().remove(this.notification);
            }
        });
    }

    /** Makes the whole notification clickable; the action runs after it is dismissed. */
    public CustomNotification withOnClick(Runnable action) {
        notification.setCursor(Cursor.HAND);
        notification.setOnMouseClicked(e -> {
            dismissNotification();
            action.run();
        });
        return this;
    }

    // ── Display ────────────────────────────────────────────────────────────────

    /**
     * Shows the notification, automatically placing it over the top-most modal
     * if one is open, otherwise over the main content area.
     */
    public void showNotification() {
        StackPane target = App.getStackPane();
        boolean overModal = false;

        ModalPane top = App.getTopModalPane();
        if (top != null && top.isDisplay()) {
            Node content = top.getContent();
            if (content != null && content.getParent() instanceof StackPane modalParent) {
                target = modalParent;
                overModal = true;
            }
        }

        showOn(target, overModal);
    }

    /** Shows the notification over an explicit pane (e.g. a specific modal's parent). */
    public void showNotificationOnCustomPane(StackPane pane) {
        showOn(pane, true);
    }

    private void showOn(StackPane pane, boolean overModal) {
        this.customPane = pane;

        double topMargin = overModal ? 10 + App.getHeaderBox().getHeight() : 10;
        StackPane.setMargin(notification, new Insets(topMargin, 10, 0, 0));

        notification.setViewOrder(NOTIFICATION_VIEW_ORDER);
        notification.setTranslateX(300);

        pane.getChildren().remove(notification);
        Platform.runLater(() -> pane.getChildren().add(notification));
        slideInAnimation.playFromStart();
    }

    private void dismissNotification() {
        slideInAnimation.stop();
        pause.stop();
        slideOutAnimation.playFromStart();
    }
}
