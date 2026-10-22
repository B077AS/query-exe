package com.queryexe.components.modals.shell;

import atlantafx.base.theme.Styles;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.utils.IconColorUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;


/** Small building blocks shared by the {@link SidebarModal} tabs so every dialog looks the same. */
public final class ModalUi {

    private ModalUi() {}

    /** Transparent, vertically scrolling wrapper for a tab's content. */
    public static ScrollPane scroll(Region content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    /** Standard padded vertical content area of a tab. */
    public static VBox content() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18, 24, 24, 24));
        return box;
    }

    /**
     * A tab made of a scrolling list. The scroll area spans the whole tab width and the
     * list rows run edge to edge (rows pad themselves), so dividers reach both sides and the scrollbar sits at the modal edge.
     */
    public static VBox listTab(VBox list) {
        list.setPadding(new Insets(6, 0, 16, 0));
        return new VBox(scroll(list));
    }


    /** Small ghost button for a tab's header: accent text, no fill until hovered. */
    public static Button addAction(String text, Runnable action) {
        Button button = new Button(text, new FontIcon(MaterialDesignP.PLUS));
        button.getStyleClass().add("modal-add-action");
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * A list entry as a small block, since names, types and relations are too long for fixed columns: the name in
     * bold with its badges on the right (a name that still doesn't fit is cut with the full text in a tooltip),
     * and the detail underneath, which wraps instead of being cut off.
     */
    public static Node block(String title, HBox badges, Node detail) {
        Label name = ModalUi.boldLabel(title);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(title));
        HBox.setHgrow(name, Priority.ALWAYS);
        badges.setMinWidth(Region.USE_PREF_SIZE);

        VBox block = new VBox(5, new HBox(12, name, badges), detail);
        block.getStyleClass().add("modal-item");
        block.setMaxWidth(Double.MAX_VALUE);
        return block;
    }
    /** "Schema" on PostgreSQL, which lists schemas where the other engines list databases (same rule as the tree's menus). */
    public static String containerLabel() {
        return DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType()
                .equals(ConnectionTypes.PostgreSQL.toString()) ? "Schema" : "Database";
    }

    public static Label accentBadge(String text) {
        Label badge = badge(text);
        badge.getStyleClass().add("modal-badge-accent");
        return badge;
    }


    /** A bordered card with an optional icon + title row; add the body to the returned box. */
    public static VBox card(String title, Ikon icon) {
        VBox card = new VBox(0);
        card.getStyleClass().add("modal-card");
        if (title != null) {
            Label label = new Label(title);
            label.getStyleClass().add("modal-card-title");
            if (icon != null) label.setGraphic(IconColorUtil.colored(icon, "-color-accent-emphasis", 16));
            label.setGraphicTextGap(8);
            card.getChildren().add(label);
        }
        return card;
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    /** Small caps section label, as used above lists. */
    public static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        return label;
    }


    public static Label boldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    /** Keeps the divider line off the last row of a list: tags the last child and follows later changes. */
    public static void markLast(VBox box) {
        Runnable apply = () -> {
            box.getChildren().forEach(n -> n.getStyleClass().remove("last-item"));
            if (!box.getChildren().isEmpty()) box.getChildren().get(box.getChildren().size() - 1).getStyleClass().add("last-item");
        };
        box.getChildren().addListener((javafx.collections.ListChangeListener<Node>) c -> apply.run());
        apply.run();
    }

    public static Label mono(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("modal-mono");
        return label;
    }

    public static Label badge(String text) {
        Label badge = new Label(text);
        badge.getStyleClass().add("modal-badge");
        return badge;
    }

    /** Label with a leading icon, used above form controls. */
    public static Label fieldLabel(String text, Ikon icon) {
        Label label = new Label(text);
        label.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        label.setGraphic(IconColorUtil.colored(icon, "-color-fg-muted", 13));
        label.setGraphicTextGap(6);
        return label;
    }

    public static Label dash() {
        Label label = new Label("—");
        label.getStyleClass().add(Styles.TEXT_SUBTLE);
        return label;
    }

    /** Centered placeholder for a list without entries. */
    public static VBox emptyState(Ikon icon, String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_MUTED);
        VBox box = new VBox(10, IconColorUtil.colored(icon, "-color-fg-subtle", 34), label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 0, 40, 0));
        return box;
    }

    /** Two-column key/value grid for "details" cards. */
    public static GridPane infoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(12);
        grid.setPadding(new Insets(4, 16, 16, 16));
        return grid;
    }

    public static void infoRow(GridPane grid, int row, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add(Styles.TEXT_MUTED);
        k.setMinWidth(130);
        Label v = new Label(value);
        v.setStyle("-fx-font-weight: bold;");
        v.setWrapText(true);
        grid.add(k, 0, row);
        grid.add(v, 1, row);
    }

    public static HBox centered(Node node) {
        HBox box = new HBox(node);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static void error(String title, String message) {
        new CustomNotification(title, message, new FontIcon(MaterialDesignT.TABLE_CANCEL)).showNotification();
    }

    public static void warn(String title, String message) {
        new CustomNotification(title, message, new FontIcon(MaterialDesignA.ALERT_OUTLINE)).showNotification();
    }
}
