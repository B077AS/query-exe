package com.queryexe.components.extra;

import atlantafx.base.controls.Notification;
import atlantafx.base.controls.NotificationSkin;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class UpgradedNotification extends Notification {

    private final Node contentNode;

    public UpgradedNotification(String message, Node graphic) {
        super(message, graphic);
        this.contentNode = null;
    }

    public UpgradedNotification(Node contentNode, Node graphic) {
        super(null, graphic);
        this.contentNode = contentNode;
    }

    public Node getContentNode() {
        return contentNode;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new KommNotificationSkin(this);
    }

    static class KommNotificationSkin extends NotificationSkin {

        KommNotificationSkin(UpgradedNotification control) {
            super(control);
            Node content = control.getContentNode();
            if (content != null) {
                HBox.setHgrow(content, Priority.ALWAYS);
                // NotificationSkin's own messageText (the node we're replacing) carries this same
                // setMaxWidth(MAX_VALUE), with the base class's comment "text wrapping won't work
                // without this" — header (an HBox) never grows an hgrow-ALWAYS child past its
                // default computed max width, which for a wrapped Label/VBox resolves to its
                // unwrapped single-line width; without lifting that cap here too, our swapped-in
                // content never gets resized wide enough to actually wrap and just ellipsizes.
                if (content instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                    // Even with the maxWidth lifted above, header (an HBox) assigns this region
                    // its final width without ever re-querying prefHeight() at that width — a
                    // known JavaFX gotcha for a plain container reporting bias by delegation
                    // (through a wrapped Label) rather than being a bias-reporting leaf itself
                    // like NotificationSkin's own TextFlow-based messageText. Without this, the
                    // row is laid out at the stale single-line height from an earlier
                    // unconstrained (width=-1) measurement, so a wrapped multi-line message just
                    // gets clipped down to one ellipsized line. Recomputing prefHeight at the
                    // real width and pushing it back in forces a re-layout at the correct,
                    // fully-wrapped height.
                    region.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                        double width = newWidth.doubleValue();
                        if (width > 0) {
                            region.setPrefHeight(region.prefHeight(width));
                        }
                    });
                }
                int idx = header.getChildren().indexOf(messageText);
                if (idx >= 0) {
                    header.getChildren().set(idx, content);
                }
                // TOP_LEFT (not CENTER_LEFT) so the icon stays level with the title instead of
                // drifting to the vertical center of the whole row once the message wraps onto a
                // second line.
                header.setAlignment(Pos.TOP_LEFT);
                HBox.setMargin(actionsBox, new Insets(0, -8, 0, 0));
            }
        }
    }
}
