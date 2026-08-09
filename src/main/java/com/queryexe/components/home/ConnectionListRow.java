package com.queryexe.components.home;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.extra.CustomProgressIndicator;
import com.queryexe.components.modals.AddConnectionModal;
import com.queryexe.components.modals.ConfirmationModal;
import com.queryexe.service.ConnectionService;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;

/** A compact, single-row rendering of a {@link ConnectionObject}, used by the list view in {@link ConnectionsPane}. */
public class ConnectionListRow extends HBox {

    private final ConnectionObject connection;
    private final CustomProgressIndicator progressIndicator;

    public ConnectionListRow(ConnectionObject connection) {
        this.connection = connection;
        this.progressIndicator = new CustomProgressIndicator(16, 16);
        this.progressIndicator.setVisible(false);

        this.setSpacing(14);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(10, 14, 10, 14));
        this.getStyleClass().add("connection-lineitem-card");
        this.setOnMouseClicked(this::handleRowClick);

        this.getChildren().addAll(createInfoSection(), progressIndicator, createActionButtons());
    }

    private VBox createInfoSection() {
        VBox info = new VBox(3);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(connection.getConnectionName());
        nameLabel.setGraphic(new FontIcon(MaterialDesignD.DATABASE));
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        HBox metaRow = new HBox(10);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label dbBadge = new Label(connection.getDbType().toUpperCase());
        dbBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        dbBadge.setStyle("-fx-background-color: -color-border-muted; -fx-padding: 2 8 2 8; "
                + "-fx-background-radius: 12; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label userLabel = new Label(connection.getUsername());
        userLabel.setGraphic(new FontIcon(MaterialDesignA.ACCOUNT));
        userLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        String urlText = (connection.getHost() == null || connection.getHost().isBlank())
                ? connection.getFullUrl() : connection.getSimpleURL();
        Label urlLabel = new Label(urlText);
        urlLabel.setGraphic(new FontIcon(MaterialDesignL.LAN));
        urlLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        metaRow.getChildren().addAll(dbBadge, userLabel, urlLabel);
        info.getChildren().addAll(nameLabel, metaRow);
        return info;
    }

    private HBox createActionButtons() {
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button editButton = new Button();
        editButton.setGraphic(new FontIcon(Feather.SETTINGS));
        editButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        editButton.setOnAction(event -> showEditModal());

        Button duplicateButton = new Button();
        duplicateButton.setGraphic(new FontIcon(MaterialDesignC.CONTENT_COPY));
        duplicateButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        duplicateButton.setOnAction(event -> cloneConnection());

        Button deleteButton = new Button();
        deleteButton.setGraphic(new FontIcon(Feather.TRASH_2));
        deleteButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL, Styles.ACCENT);
        deleteButton.setOnAction(event -> showDeleteConfirmation());

        Separator separator = new Separator(Orientation.VERTICAL);
        separator.setMaxHeight(18);

        Region chevronSpacer = new Region();
        FontIcon chevron = new FontIcon(MaterialDesignC.CHEVRON_RIGHT);
        chevron.getStyleClass().add(Styles.TEXT_MUTED);

        buttonBox.getChildren().addAll(editButton, duplicateButton, separator, deleteButton, chevronSpacer, chevron);
        return buttonBox;
    }

    private void handleRowClick(MouseEvent event) {
        if (event.getTarget() instanceof Button ||
                event.getTarget() instanceof FontIcon && ((FontIcon) event.getTarget()).getParent() instanceof Button) {
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
