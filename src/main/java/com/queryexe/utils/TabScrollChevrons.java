package com.queryexe.utils;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Replaces a TabPane's native (barely visible) tab overflow arrow with a
 * proper prev/next chevron pair that scrolls the tab strip. Reuses the same
 * reserved header slot the native arrow occupies, so no dead space is left
 * when tabs don't overflow, and the pair only shows up when they do.
 */
public class TabScrollChevrons {

    public static void install(TabPane tabPane) {
        tabPane.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> installChevrons(tabPane));
            }
        });
    }

    private static void installChevrons(TabPane tabPane) {
        Node downButtonNode = tabPane.lookup(".tab-down-button");
        if (!(downButtonNode instanceof Pane) || downButtonNode.getProperties().containsKey("chevronsInstalled")) {
            return;
        }
        Pane downButton = (Pane) downButtonNode;
        downButton.getProperties().put("chevronsInstalled", true);

        StackPane prevButton = createChevronButton(Feather.CHEVRON_LEFT);
        StackPane nextButton = createChevronButton(Feather.CHEVRON_RIGHT);

        HBox chevronBox = new HBox(2, prevButton, nextButton);
        chevronBox.getStyleClass().add("tab-nav-box");
        chevronBox.setPickOnBounds(false);

        downButton.getChildren().add(chevronBox);
        chevronBox.layoutXProperty().bind(downButton.widthProperty().subtract(chevronBox.widthProperty()).divide(2));
        chevronBox.layoutYProperty().bind(
                downButton.heightProperty().subtract(chevronBox.heightProperty()).divide(2).subtract(2));

        bindScrollRepeat(prevButton, tabPane, true);
        bindScrollRepeat(nextButton, tabPane, false);
    }

    private static StackPane createChevronButton(Ikon icon) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.getStyleClass().add("tab-nav-chevron");
        StackPane button = new StackPane(fontIcon);
        button.getStyleClass().add("tab-nav-chevron-btn");
        return button;
    }

    private static void bindScrollRepeat(StackPane button, TabPane tabPane, boolean toLeft) {
        Timeline repeat = new Timeline(new KeyFrame(Duration.millis(70), e -> scrollHeader(tabPane, toLeft)));
        repeat.setDelay(Duration.millis(350));
        repeat.setCycleCount(Timeline.INDEFINITE);

        button.setOnMousePressed(event -> {
            scrollHeader(tabPane, toLeft);
            repeat.playFromStart();
            event.consume();
        });
        button.setOnMouseReleased(event -> {
            repeat.stop();
            event.consume();
        });
        button.setOnMouseExited(event -> repeat.stop());
        button.setOnMouseClicked(Event::consume);
    }

    private static void scrollHeader(TabPane tabPane, boolean toLeft) {
        Node headerArea = tabPane.lookup(".tab-header-area");
        if (headerArea == null) {
            return;
        }
        double deltaY = toLeft ? 90 : -90;
        ScrollEvent scrollEvent = new ScrollEvent(
                ScrollEvent.SCROLL,
                0, 0, 0, 0,
                false, false, false, false,
                true, false,
                0, deltaY,
                0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null);
        headerArea.fireEvent(scrollEvent);
    }
}
