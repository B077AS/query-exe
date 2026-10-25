package com.queryexe.components.home;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.extra.CustomProgressIndicator;
import com.queryexe.components.modals.AddConnectionModal;
import com.queryexe.components.modals.ConfirmationModal;
import com.queryexe.service.ConnectionService;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;
import com.queryexe.utils.IconColorUtil;

import java.util.List;

/**
 * A compact, single-row rendering of a {@link ConnectionObject}, used by the list view in {@link ConnectionsPane}.
 * Rows can be dragged onto a folder header to file them, and offer the same actions through a context menu.
 */
public class ConnectionListRow extends GridPane {

    /** Prefix of the drag payload, so drops from elsewhere are ignored. */
    public static final String DRAG_PREFIX = "queryexe-connection:";

    private final ConnectionObject connection;
    private final CustomProgressIndicator progressIndicator;
    private final HBox hoverActions = new HBox(2);

    /** Column layout shared by all rows so they line up. The actions column is as wide as its buttons. */
    public static void applyColumns(GridPane grid) {
        ColumnConstraints name = new ColumnConstraints();
        name.setPercentWidth(30);
        name.setMinWidth(120);
        ColumnConstraints engine = new ColumnConstraints(110);
        ColumnConstraints user = new ColumnConstraints();
        user.setPercentWidth(14);
        user.setMinWidth(60);
        ColumnConstraints host = new ColumnConstraints();
        host.setHgrow(Priority.ALWAYS);
        host.setMinWidth(80);
        ColumnConstraints actions = new ColumnConstraints();
        actions.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        grid.getColumnConstraints().setAll(name, engine, user, host, actions);
    }

    public ConnectionListRow(ConnectionObject connection) {
        this.connection = connection;
        this.progressIndicator = new CustomProgressIndicator(16, 16);
        this.progressIndicator.setVisible(false);

        applyColumns(this);
        this.setHgap(14);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(10, 14, 10, 14));
        this.getStyleClass().add("connection-lineitem-card");
        this.setOnMouseClicked(this::handleRowClick);

        Label nameLabel = new Label(connection.getConnectionName());
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // The tile anchors the row so connection names stand out from the muted columns beside them.
        FontIcon dbIcon = new FontIcon(MaterialDesignD.DATABASE);
        IconColorUtil.apply(dbIcon, "-color-accent-emphasis", 18);
        StackPane iconTile = new StackPane(dbIcon);
        iconTile.setMinSize(34, 34);
        iconTile.setMaxSize(34, 34);
        iconTile.setStyle("-fx-background-color: -color-accent-subtle; -fx-background-radius: 9px;");

        HBox nameCell = new HBox(12, iconTile, nameLabel);
        nameCell.setAlignment(Pos.CENTER_LEFT);
        nameCell.setMinWidth(0);

        Label dbBadge = new Label(connection.getDbType().toUpperCase());
        dbBadge.getStyleClass().add("db-type-badge");

        String username = connection.getUsername();
        Label userLabel = new Label(username == null || username.isBlank() ? "-" : username);
        userLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        userLabel.setMinWidth(0);

        String urlText = (connection.getHost() == null || connection.getHost().isBlank())
                ? connection.getFullUrl() : connection.getSimpleURL();
        Label urlLabel = new Label(urlText);
        urlLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        urlLabel.setMinWidth(0);
        urlLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);

        this.add(nameCell, 0, 0);
        this.add(dbBadge, 1, 0);
        this.add(userLabel, 2, 0);
        this.add(urlLabel, 3, 0);
        this.add(createActionButtons(), 4, 0);

        setupDragSource();
        this.setOnContextMenuRequested(e -> {
            buildContextMenu().show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private HBox createActionButtons() {
        HBox buttonBox = new HBox(2);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button editButton = iconButton(Feather.SETTINGS, "Edit");
        editButton.setOnAction(event -> showEditModal());

        Button duplicateButton = iconButton(MaterialDesignC.CONTENT_COPY, "Duplicate");
        duplicateButton.setOnAction(event -> cloneConnection());

        Button deleteButton = iconButton(Feather.TRASH_2, "Delete");
        deleteButton.getStyleClass().add(Styles.ACCENT);
        deleteButton.setOnAction(event -> showDeleteConfirmation());

        // Hover-only, so the resting row stays calm; they keep their space to avoid the row jumping.
        hoverActions.getChildren().addAll(editButton, duplicateButton, deleteButton);
        hoverActions.setVisible(false);
        this.hoverProperty().addListener((obs, was, now) -> hoverActions.setVisible(now));

        Button moreButton = iconButton(Feather.MORE_HORIZONTAL, "More actions");
        moreButton.setOnAction(event -> buildContextMenu().show(moreButton, Side.BOTTOM, 0, 0));

        buttonBox.getChildren().addAll(progressIndicator, hoverActions, moreButton);
        return buttonBox;
    }

    private static Button iconButton(org.kordamp.ikonli.Ikon ikon, String tooltip) {
        Button button = new Button();
        button.setGraphic(new FontIcon(ikon));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private void setupDragSource() {
        this.setOnDragDetected(event -> {
            Dragboard dragboard = this.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(DRAG_PREFIX + connection.getId());
            dragboard.setContent(content);
            dragboard.setDragView(this.snapshot(null, null));
            event.consume();
        });
    }

    private ContextMenu buildContextMenu() {
        ConnectionsPane pane = App.getConnectionsPane();

        MenuItem connectItem = new MenuItem("Connect", new FontIcon(Feather.PLAY));
        connectItem.setOnAction(e -> connect());

        MenuItem editItem = new MenuItem("Edit…", new FontIcon(Feather.SETTINGS));
        editItem.setOnAction(e -> showEditModal());

        MenuItem duplicateItem = new MenuItem("Duplicate", new FontIcon(MaterialDesignC.CONTENT_COPY));
        duplicateItem.setOnAction(e -> cloneConnection());

        MenuItem pinItem = new MenuItem(connection.isPinned() ? "Unpin" : "Pin to top",
                new FontIcon(connection.isPinned() ? MaterialDesignP.PIN_OFF_OUTLINE : MaterialDesignP.PIN_OUTLINE));
        pinItem.setOnAction(e -> {
            if (pane != null) {
                pane.setPinned(connection.getId(), !connection.isPinned());
            }
        });

        MenuItem deleteItem = new MenuItem("Delete…", new FontIcon(Feather.TRASH_2));
        deleteItem.setOnAction(e -> showDeleteConfirmation());

        ContextMenu menu = new ContextMenu(connectItem, editItem, duplicateItem,
                new SeparatorMenuItem(), pinItem);
        if (pane != null) {
            menu.getItems().add(buildMoveMenu(pane));
        }
        menu.getItems().addAll(new SeparatorMenuItem(), deleteItem);
        return menu;
    }

    private Menu buildMoveMenu(ConnectionsPane pane) {
        Menu move = new Menu("Move to folder", new FontIcon(MaterialDesignF.FOLDER_MOVE_OUTLINE));
        List<String> folders = pane.getFolderNames();
        String current = connection.getFolder();

        for (String folder : folders) {
            MenuItem item = new MenuItem(folder, folder.equals(current) ? new FontIcon(Feather.CHECK) : null);
            item.setOnAction(e -> pane.moveConnection(connection.getId(), folder));
            move.getItems().add(item);
        }
        MenuItem ungrouped = new MenuItem("Ungrouped", current == null ? new FontIcon(Feather.CHECK) : null);
        ungrouped.setOnAction(e -> pane.moveConnection(connection.getId(), null));
        MenuItem newFolder = new MenuItem("New folder…", new FontIcon(Feather.PLUS));
        newFolder.setOnAction(e -> pane.promptNewFolder(connection.getId()));

        if (!folders.isEmpty()) {
            move.getItems().add(new SeparatorMenuItem());
        }
        move.getItems().addAll(ungrouped, new SeparatorMenuItem(), newFolder);
        return move;
    }

    private void handleRowClick(MouseEvent event) {
        if (event.getTarget() instanceof Button ||
                event.getTarget() instanceof FontIcon && ((FontIcon) event.getTarget()).getParent() instanceof Button) {
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        connect();
    }

    private void connect() {
        ConnectionService.getInstance().connect(
                connection,
                () -> progressIndicator.setVisible(true),
                () -> progressIndicator.setVisible(false),
                () -> App.connect(),
                e -> {
                }
        );
    }

    private void showEditModal() {
        StackPane tempPane = new StackPane();
        tempPane.setOnMouseClicked(eventStack -> App.closeModal());

        AddConnectionModal modal = new AddConnectionModal();
        modal.setupFields(this.connection);
        modal.addEventFilter(MouseEvent.MOUSE_CLICKED, eventModal -> eventModal.consume());

        tempPane.getChildren().add(modal);
        App.showModal(tempPane);
    }

    private void cloneConnection() {
        String newId = ConnectionService.getInstance().cloneConnection(
                this.connection.getId(), this.connection.getConnectionName() + " (copy)");

        ConnectionsPane connectionsPane = App.getConnectionsPane();
        if (connectionsPane != null) {
            connectionsPane.addConnection(newId);
        }

        new CustomNotification("Connection Duplicated", "A copy of the connection was created.", new FontIcon(MaterialDesignC.CONTENT_COPY))
                .showNotification();
    }

    private void showDeleteConfirmation() {
        ConfirmationModal deleteModal = new ConfirmationModal("Delete Connection",
                "Do you want to delete this connection?", new FontIcon(MaterialDesignD.DELETE_ALERT_OUTLINE),
                this::deleteConnection);
        deleteModal.setTranslateY(10);
        App.showModal(deleteModal);
    }

    private void deleteConnection() {
        if (ConnectionService.getInstance().deleteConnection(connection.getId())) {
            App.closeModal();

            ConnectionsPane connectionsPane = App.getConnectionsPane();
            if (connectionsPane != null) {
                connectionsPane.removeConnection(connection.getId());
            }

            new CustomNotification("Connection Deleted", "The connection was removed successfully.", new FontIcon(MaterialDesignD.DELETE_EMPTY_OUTLINE))
                    .showNotification();
        }
    }
}
