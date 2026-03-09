package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.feather.Feather;
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
    private Button closeButton;

    public ActiveConnectionsModal() {
        this.connectionItems = new HashMap<>();

        this.setAlignment(Pos.TOP_CENTER);
        this.getStyleClass().add("modal-container");
        this.setMaxSize(720, 560);
        this.setMinSize(720, 560);
        this.setPrefSize(720, 560);

        VBox header = buildHeader();

        HBox searchRow = buildSearchBar();

        Separator separator = new Separator();
        separator.setPadding(new Insets(0));

        connectionsList = new VBox(6);
        connectionsList.setPadding(new Insets(12, 16, 12, 16));

        emptyStateLabel = new Label("No active connections");
        emptyStateLabel.getStyleClass().add(Styles.TEXT_MUTED);
        emptyStateLabel.setStyle("-fx-font-size: 13px; -fx-padding: 40 0 40 0;");
        emptyStateLabel.setMaxWidth(Double.MAX_VALUE);
        emptyStateLabel.setAlignment(Pos.CENTER);

        loadActiveConnections();

        if (connectionsList.getChildren().isEmpty()) {
            connectionsList.setAlignment(Pos.CENTER);
            connectionsList.getChildren().add(emptyStateLabel);
        }

        ScrollPane scrollPane = new ScrollPane(connectionsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox footer = buildFooter(connectionItems.size());

        this.getChildren().addAll(header, searchRow, separator, scrollPane, footer);
    }

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.setPadding(new Insets(18, 16, 12, 16));

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER);

        Button phantom = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        phantom.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        phantom.setVisible(false);

        Region spacerLeft = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        Label title = new Label("Active Connections");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(e -> App.closeModal());

        topRow.getChildren().addAll(phantom, spacerLeft, title, spacerRight, closeButton);

        Label subtitle = new Label("Manage your active database connections");
        subtitle.getStyleClass().add(Styles.TEXT_MUTED);
        subtitle.setStyle("-fx-font-size: 12px;");
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setTextAlignment(TextAlignment.CENTER);
        subtitle.setAlignment(Pos.CENTER);

        header.getChildren().addAll(topRow, subtitle);
        return header;
    }

    private HBox buildSearchBar() {
        HBox row = new HBox();
        row.setPadding(new Insets(0, 16, 12, 16));

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(6, 12, 6, 12));
        searchBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6;");
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        FontIcon searchIcon = new FontIcon(Feather.SEARCH);
        searchIcon.setIconSize(14);
        searchIcon.getStyleClass().add(Styles.TEXT_MUTED);

        TextField searchField = new TextField();
        searchField.setPromptText("Search connections…");
        searchField.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                filterConnections(newVal.trim().toLowerCase()));

        searchBox.getChildren().addAll(searchIcon, searchField);
        row.getChildren().add(searchBox);
        return row;
    }

    private void filterConnections(String query) {
        connectionsList.getChildren().forEach(node -> {
            if (node instanceof HBox card) {
                Object tag = card.getUserData();
                if (tag instanceof ConnectionObject conn) {
                    String displayUrl = (conn.getHost() == null || conn.getHost().isBlank())
                            ? conn.getFullUrl() : conn.getSimpleURL();
                    boolean matches = query.isEmpty()
                            || conn.getConnectionName().toLowerCase().contains(query)
                            || conn.getDbType().toLowerCase().contains(query)
                            || displayUrl.toLowerCase().contains(query);
                    card.setVisible(matches);
                    card.setManaged(matches);
                }
            }
        });
    }

    private void loadActiveConnections() {
        DatabaseConnection dbConnection = DatabaseConnection.getInstance();
        Set<String> connectionIds = dbConnection.getAllConnectionIds();

        for (String connectionId : connectionIds) {
            ConnectionObject connectionObject = dbConnection.getConnectionObject(connectionId);
            if (connectionObject != null) {
                HBox listItem = createConnectionCard(connectionObject, connectionId);
                connectionsList.getChildren().add(listItem);
                connectionItems.put(connectionId, listItem);
            }
        }
    }

    private HBox createConnectionCard(ConnectionObject connection, String connectionId) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setUserData(connection);
        card.getStyleClass().add("connection-lineitem-card");

        boolean isCurrent = connectionId.equals(DatabaseConnection.getInstance().getCurrentConnectionId());

        VBox info = new VBox(3);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(connection.getConnectionName());
        nameLabel.setGraphic(new FontIcon(MaterialDesignD.DATABASE));
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        nameRow.getChildren().add(nameLabel);

        if (isCurrent) {
            Label currentBadge = new Label("CURRENT");
            currentBadge.setStyle("""
                    -fx-font-size: 9px;
                    -fx-font-weight: bold;
                    -fx-background-color: -color-success-emphasis;
                    -fx-background-radius: 4px;
                    -fx-padding: 2 6 2 6;
                    -fx-text-fill: -color-bg-default;
                    """);
            nameRow.getChildren().add(currentBadge);
        }

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label dbBadge = new Label(connection.getDbType().toUpperCase());
        dbBadge.setStyle("""
                -fx-font-size: 9px;
                -fx-font-weight: bold;
                -fx-background-color: -color-border-muted;
                -fx-background-radius: 4px;
                -fx-padding: 2 6 2 6;
                -fx-text-fill: -color-fg-muted;
                """);

        String displayUrl = (connection.getHost() == null || connection.getHost().isBlank())
                ? connection.getFullUrl() : connection.getSimpleURL();
        Label urlLabel = new Label(displayUrl);
        urlLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        urlLabel.setStyle("-fx-font-size: 11px;");
        urlLabel.setMaxWidth(360);

        Label statusLabel = new Label();
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

        metaRow.getChildren().addAll(dbBadge, urlLabel, statusLabel);
        info.getChildren().addAll(nameRow, metaRow);

        CustomProgressIndicator progressIndicator = new CustomProgressIndicator(18, 18);
        progressIndicator.setVisible(false);

        HBox buttonContainer = new HBox(4);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);

        Button reconnectButton = new Button(null, new FontIcon(MaterialDesignR.REFRESH));
        reconnectButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        reconnectButton.setTooltip(new Tooltip("Reconnect"));
        reconnectButton.setOnAction(e -> handleReconnect(connectionId, progressIndicator, statusLabel));

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE_CIRCLE_OUTLINE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE, Styles.DANGER);
        closeButton.setTooltip(new Tooltip("Close Connection"));
        closeButton.setOnAction(e -> handleCloseConnection(connectionId, card));

        buttonContainer.getChildren().addAll(progressIndicator, reconnectButton, closeButton);

        card.getChildren().addAll(info, buttonContainer);

        return card;
    }

    private HBox buildFooter(int count) {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 16, 12, 16));
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1px 0 0 0;");

        Label hint = new Label(count + " active connection" + (count != 1 ? "s" : ""));
        hint.getStyleClass().add(Styles.TEXT_MUTED);
        hint.setStyle("-fx-font-size: 11px;");

        footer.getChildren().add(hint);
        return footer;
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
                connectionsList.setAlignment(Pos.CENTER);
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
            CustomNotification notification = new CustomNotification(
                    "Failed to close connection!\n" + e.getMessage(),
                    new FontIcon(MaterialDesignC.CLOSE_CIRCLE)
            );
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
        }
    }

    public void refresh() {
        connectionsList.getChildren().clear();
        connectionItems.clear();
        loadActiveConnections();

        if (connectionsList.getChildren().isEmpty()) {
            connectionsList.setAlignment(Pos.CENTER);
            connectionsList.getChildren().add(emptyStateLabel);
        }
    }
}