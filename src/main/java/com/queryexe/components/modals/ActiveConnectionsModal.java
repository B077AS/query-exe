package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.extra.CustomProgressIndicator;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.service.SingleExecutorService;
import com.queryexe.queryexe.App;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ActiveConnectionsModal extends VBox {

    private VBox connectionsList;
    private Label emptyStateLabel;
    private Map<String, HBox> connectionItems;

    public ActiveConnectionsModal() {
        this.connectionItems = new HashMap<>();

        this.setAlignment(Pos.CENTER);
        this.setStyle("-fx-background-color: -color-bg-default; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-border-color: -color-border-default; -fx-border-width: 1px;");
        this.setMaxSize(600, 540);
        this.setMinSize(600, 540);
        this.setPrefSize(600, 540);

        VBox titleSection = new VBox();
        titleSection.setPadding(new Insets(0, 0, 10, 0));

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(20, 10, 0, 10));
        headerBox.setMinHeight(40);
        headerBox.setMaxHeight(40);

        Region headerFillerRegion = new Region();
        Region headerSecondFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> {
            App.closeModal();
        });

        Button fakeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        fakeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        fakeButton.setVisible(false);

        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);
        HBox.setHgrow(headerSecondFillerRegion, Priority.ALWAYS);

        Label title = new Label("Active Connections");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        title.setAlignment(Pos.CENTER);

        headerBox.getChildren().addAll(fakeButton, headerFillerRegion, title, headerSecondFillerRegion, closeButton);

        Label subtitle = new Label("Manage your active database connections");
        subtitle.getStyleClass().addAll(Styles.TEXT_MUTED);
        subtitle.setStyle("-fx-font-size: 13px;");
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setPadding(new Insets(5, 20, 0, 20));

        titleSection.getChildren().addAll(headerBox, subtitle);

        connectionsList = new VBox(8);
        connectionsList.setAlignment(Pos.TOP_CENTER);
        connectionsList.setPadding(new Insets(0, 10, 0, 10));

        emptyStateLabel = new Label("No active connections");
        emptyStateLabel.getStyleClass().addAll(Styles.TEXT_MUTED);
        emptyStateLabel.setStyle("-fx-font-size: 14px; -fx-padding: 40 0 40 0;");
        emptyStateLabel.setAlignment(Pos.CENTER);

        loadActiveConnections();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        if (connectionsList.getChildren().isEmpty()) {
            VBox emptyContainer = new VBox();
            emptyContainer.setAlignment(Pos.CENTER);
            emptyContainer.getChildren().add(emptyStateLabel);
            scrollPane.setContent(emptyContainer);
            VBox.setVgrow(emptyContainer, Priority.ALWAYS);
        } else {
            scrollPane.setContent(connectionsList);
        }

        VBox scrollContainer = new VBox();
        scrollContainer.setPadding(new Insets(10));
        scrollContainer.getChildren().add(scrollPane);
        VBox.setVgrow(scrollContainer, Priority.ALWAYS);

        this.getChildren().addAll(titleSection, scrollContainer);
    }

    private void loadActiveConnections() {
        DatabaseConnection dbConnection = DatabaseConnection.getInstance();
        Set<String> connectionIds = dbConnection.getAllConnectionIds();

        for (String connectionId : connectionIds) {
            ConnectionObject connectionObject = dbConnection.getConnectionObject(connectionId);
            if (connectionObject != null) {
                HBox listItem = createConnectionListItem(connectionObject, connectionId);
                connectionsList.getChildren().add(listItem);
                connectionItems.put(connectionId, listItem);
            }
        }
    }

    private HBox createConnectionListItem(ConnectionObject connection, String connectionId) {
        HBox listItem = new HBox();
        listItem.setAlignment(Pos.CENTER_LEFT);
        listItem.setPadding(new Insets(12, 15, 12, 15));
        listItem.setSpacing(10);
        listItem.getStyleClass().add("connection-list-item");
        listItem.setStyle("-fx-background-radius: 8px; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 8px;");

        boolean isCurrent = connectionId.equals(DatabaseConnection.getInstance().getCurrentConnectionId());

        VBox infoSection = new VBox(4);
        infoSection.setAlignment(Pos.CENTER_LEFT);

        VBox nameBox = new VBox(2);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label connectionName = new Label(connection.getConnectionName());
        connectionName.setGraphic(new FontIcon(MaterialDesignD.DATABASE));
        connectionName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        if (isCurrent) {
            Label currentBadge = new Label("CURRENT");
            currentBadge.getStyleClass().addAll(Styles.TEXT_SMALL);
            currentBadge.setStyle("-fx-background-color: -color-success-emphasis; " +
                    "-fx-text-fill: -color-bg-default; " +
                    "-fx-padding: 2 6 2 6; " +
                    "-fx-background-radius: 10; " +
                    "-fx-font-size: 9px; " +
                    "-fx-font-weight: bold;");
            topRow.getChildren().addAll(connectionName, currentBadge);
        } else {
            topRow.getChildren().add(connectionName);
        }

        Label dbTypeBadge = new Label(connection.getDbType().toUpperCase());
        dbTypeBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        dbTypeBadge.setStyle("-fx-background-color: -color-border-muted; " +
                "-fx-padding: 2 6 2 6; " +
                "-fx-background-radius: 10; " +
                "-fx-font-size: 9px; " +
                "-fx-font-weight: bold;");

        nameBox.getChildren().addAll(topRow, dbTypeBadge);

        Label details = new Label();
        if (connection.getHost() == null || connection.getHost().isBlank()) {
            details.setText(connection.getFullUrl());
        } else {
            details.setText(connection.getSimpleURL());
        }
        details.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        details.setStyle("-fx-font-size: 11px;");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        statusLabel.setStyle("-fx-font-size: 10px;");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection(connectionId);
            if (conn != null && !conn.isClosed()) {
                statusLabel.setText("● Connected");
                statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-success-emphasis;");
            } else {
                statusLabel.setText("● Disconnected");
                statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-danger-emphasis;");
            }
        } catch (SQLException e) {
            statusLabel.setText("● Error");
            statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-danger-emphasis;");
        }

        infoSection.getChildren().addAll(nameBox, details, statusLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonContainer = new HBox(4);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);

        CustomProgressIndicator progressIndicator = new CustomProgressIndicator(20, 20);
        progressIndicator.setVisible(false);

        Button reconnectButton = new Button();
        reconnectButton.setGraphic(new FontIcon(MaterialDesignR.REFRESH));
        reconnectButton.getStyleClass().addAll(Styles.FLAT, Styles.SMALL, Styles.BUTTON_ICON);
        reconnectButton.setMinWidth(Region.USE_PREF_SIZE);
        reconnectButton.setMinHeight(Region.USE_PREF_SIZE);
        reconnectButton.setTooltip(new Tooltip("Reconnect"));
        reconnectButton.setOnAction(event -> handleReconnect(connectionId, progressIndicator, statusLabel));

        Button closeButton = new Button();
        closeButton.setGraphic(new FontIcon(MaterialDesignC.CLOSE_CIRCLE_OUTLINE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.SMALL, Styles.BUTTON_ICON, Styles.DANGER);
        closeButton.setMinWidth(Region.USE_PREF_SIZE);
        closeButton.setMinHeight(Region.USE_PREF_SIZE);
        closeButton.setTooltip(new Tooltip("Close Connection"));
        closeButton.setOnAction(event -> handleCloseConnection(connectionId, listItem));

        buttonContainer.getChildren().addAll(progressIndicator, reconnectButton, closeButton);

        listItem.getChildren().addAll(infoSection, spacer, buttonContainer);

        return listItem;
    }

    private void handleReconnect(String connectionId, CustomProgressIndicator progressIndicator, Label statusLabel) {
        if (!SingleExecutorService.tryStartRunning()) {
            return;
        }

        progressIndicator.setVisible(true);
        App.getModalPane().setPersistent(true);

        SingleExecutorService.getExecutor().execute(() -> {
            try {
                DatabaseConnection.getInstance().reconnect(connectionId);

                Platform.runLater(() -> {
                    statusLabel.setText("● Connected");
                    statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-success-emphasis;");

                    CustomNotification notification = new CustomNotification(
                            "Connection refreshed successfully",
                            new FontIcon(MaterialDesignL.LAN_CONNECT)
                    );
                    notification.showNotificationOnCustomPane((StackPane) this.getParent());
                });
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("● Disconnected");
                    statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-danger-emphasis;");

                    CustomNotification notification = new CustomNotification(
                            "Reconnection failed!\n" + e.getMessage(),
                            new FontIcon(MaterialDesignL.LAN_DISCONNECT)
                    );
                    notification.showNotificationOnCustomPane((StackPane) this.getParent());
                });
            } finally {
                SingleExecutorService.finishRunning();
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    App.getModalPane().setPersistent(false);
                });
            }
        });
    }

    private void handleCloseConnection(String connectionId, HBox listItem) {
        try {
            DatabaseConnection dbConnection = DatabaseConnection.getInstance();
            String currentId = dbConnection.getCurrentConnectionId();

            dbConnection.close(connectionId);

            connectionsList.getChildren().remove(listItem);
            connectionItems.remove(connectionId);

            if (connectionsList.getChildren().isEmpty()) {
                connectionsList.getChildren().add(emptyStateLabel);
            }

            Platform.runLater(() -> {
                ConnectionObject closedConnection = dbConnection.getConnectionObject(connectionId);
                if (closedConnection != null) {
                    App.removeDatabaseTreeForConnection(closedConnection);
                }

                CustomNotification notification = new CustomNotification(
                        "Connection closed successfully",
                        new FontIcon(MaterialDesignL.LAN_DISCONNECT)
                );
                notification.showNotificationOnCustomPane((StackPane) this.getParent());

                if (connectionId.equals(currentId)) {
                    Set<String> remainingIds = dbConnection.getAllConnectionIds();
                    if (!remainingIds.isEmpty()) {
                        String newCurrentId = remainingIds.iterator().next();
                        dbConnection.setCurrentConnectionId(newCurrentId);
                    }
                }
            });

        } catch (Exception e) {
            CustomNotification notification = new CustomNotification("Failed to close connection!\n" + e.getMessage(), new FontIcon(MaterialDesignC.CLOSE_CIRCLE));
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
        }
    }

    public void refresh() {
        connectionsList.getChildren().clear();
        connectionItems.clear();
        loadActiveConnections();

        if (connectionsList.getChildren().isEmpty()) {
            connectionsList.getChildren().add(emptyStateLabel);
        }
    }
}