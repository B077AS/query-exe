package com.queryexe.components.home;

import javafx.scene.control.TextField;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.controls.Card;
import atlantafx.base.theme.Styles;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.extra.CustomProgressIndicator;
import com.queryexe.components.modals.AddConnectionModal;
import com.queryexe.components.modals.ConfirmationModal;
import com.queryexe.service.ConnectionService;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;

public class ConnectionCard extends Card {

    private ConnectionObject connection;
    private CustomProgressIndicator progressIndicator;

    public ConnectionCard(ConnectionObject connection) {
        this.connection = connection;
        this.progressIndicator = new CustomProgressIndicator(20, 20);
        this.progressIndicator.setVisible(false);
        this.progressIndicator.setPadding(new Insets(0, 5, 0, 0));
        this.setOnMouseClicked(this::handleCardClick);
        setupCard();
        createHeader();
        this.setBody(createDetailsSection());
        this.getStyleClass().add("connection-card");
        setupInteractions();
    }

    public ConnectionCard() {
        this.getStyleClass().add("connection-card");
        this.setStyle("-fx-cursor: pointer;");
        setupInteractions();

        VBox contentBox = new VBox(15);
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setPadding(new Insets(20));

        // Top Section - Title and Actions
        HBox topSection = new HBox(15);
        topSection.setAlignment(Pos.CENTER);

        Label mainLabel = new Label("Connection Manager");
        mainLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addButton = new Button("New");
        addButton.setGraphic(new FontIcon(Feather.PLUS));
        addButton.getStyleClass().addAll(Styles.BUTTON_OUTLINED);
        addButton.setOnAction(e -> {
            StackPane tempPane = new StackPane();
            tempPane.setOnMouseClicked(eventStack -> {
                App.closeModal();
            });

            AddConnectionModal modal = new AddConnectionModal();
            modal.addEventFilter(MouseEvent.MOUSE_CLICKED, eventModal -> {
                eventModal.consume();
            });

            tempPane.getChildren().add(modal);

            App.showModal(tempPane);
            modal.requestFocus();
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon(MaterialDesignR.REFRESH));
        refreshButton.getStyleClass().addAll(Styles.BUTTON_OUTLINED);
        refreshButton.setOnAction(e -> {
            ConnectionsPane connectionsPane = App.getConnectionsPane();
            if (connectionsPane != null) {
                connectionsPane.refresh();
            }
        });

        topSection.getChildren().addAll(mainLabel, spacer, addButton, refreshButton);

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(10, 15, 10, 15));
        searchBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6;");

        FontIcon searchIcon = new FontIcon(Feather.SEARCH);
        searchIcon.getStyleClass().add(Styles.TEXT_MUTED);

        TextField searchField = new TextField();
        searchField.setPromptText("Search connections by name...");
        searchField.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterConnections(newVal);
        });

        searchBox.getChildren().addAll(searchIcon, searchField);

        contentBox.getChildren().addAll(topSection, searchBox);
        this.setBody(contentBox);
    }

    private void filterConnections(String searchText) {
        if (this.getParent() == null) return;

        ConnectionsPane connectionsPane = findConnectionsPane(this.getParent());
        if (connectionsPane != null) {
            connectionsPane.filterCards(searchText);
        }
    }

    private ConnectionsPane findConnectionsPane(javafx.scene.Parent parent) {
        if (parent instanceof ConnectionsPane) {
            return (ConnectionsPane) parent;
        }
        if (parent.getParent() != null) {
            return findConnectionsPane(parent.getParent());
        }
        return null;
    }

    private void setupCard() {
        this.setMinWidth(500);
        this.setMaxWidth(500);
        this.setMinHeight(170);
        this.setMaxHeight(170);
    }

    private void handleCardClick(MouseEvent event) {
        if (event.getTarget() instanceof Button ||
                event.getTarget() instanceof FontIcon && ((FontIcon) event.getTarget()).getParent() instanceof Button) {
            return;
        }
        connect();
    }

    private void setupInteractions() {
        this.setOnMouseEntered(e -> {
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(150), this);
            scaleTransition.setToX(1.03);
            scaleTransition.setToY(1.03);
            scaleTransition.play();

            FadeTransition fadeTransition = new FadeTransition(Duration.millis(150), this);
            fadeTransition.setToValue(1.1);
            fadeTransition.play();
        });

        this.setOnMouseExited(e -> {
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(150), this);
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
            scaleTransition.play();

            FadeTransition fadeTransition = new FadeTransition(Duration.millis(150), this);
            fadeTransition.setToValue(1.0);
            fadeTransition.play();
        });
    }

    private void createHeader() {
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(5, 0, 5, 0));

        VBox titleSection = new VBox(3);
        Label connectionName = new Label(connection.getConnectionName());
        connectionName.setGraphic(new FontIcon(MaterialDesignD.DATABASE));
        connectionName.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label dbTypeBadge = new Label(connection.getDbType().toUpperCase());
        dbTypeBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        dbTypeBadge.setStyle("-fx-background-color: -color-border-muted; " + "-fx-padding: 2 8 2 8; "
                + "-fx-background-radius: 12; " + "-fx-font-size: 10px; " + "-fx-font-weight: bold;");

        titleSection.getChildren().addAll(connectionName, dbTypeBadge);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionButtons = createActionButtons();
        headerBox.getChildren().addAll(titleSection, spacer, actionButtons);
        this.setHeader(headerBox);
    }

    private HBox createActionButtons() {
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button editButton = new Button();
        editButton.setGraphic(new FontIcon(Feather.SETTINGS));
        editButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        editButton.setOnAction(event -> showEditModal());

        Button duplicateButton = new Button();
        duplicateButton.setGraphic(new FontIcon(MaterialDesignC.CONTENT_COPY));
        duplicateButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        duplicateButton.setOnAction(event -> cloneConnection());

        Button deleteButton = new Button();
        deleteButton.setGraphic(new FontIcon(Feather.TRASH_2));
        deleteButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.ACCENT);
        deleteButton.setOnAction(event -> showDeleteConfirmation());

        Separator separator = new Separator(Orientation.VERTICAL);
        separator.setMaxHeight(20);

        buttonBox.getChildren().addAll(progressIndicator, editButton, duplicateButton, separator, deleteButton);
        return buttonBox;
    }

    private VBox createDetailsSection() {
        VBox section = new VBox(8);
        HBox userBox = createDetailRow(new FontIcon(MaterialDesignA.ACCOUNT), "User:", connection.getUsername());
        HBox pathBox;
        if (connection.getHost() == null || connection.getHost().isBlank()) {
            pathBox = createDetailRow(new FontIcon(MaterialDesignL.LAN), "Path:", connection.getFullUrl());
        } else {
            pathBox = createDetailRow(new FontIcon(MaterialDesignL.LAN), "Path:", connection.getSimpleURL());
        }

        section.getChildren().addAll(userBox, pathBox);
        return section;
    }

    private HBox createDetailRow(FontIcon icon, String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label labelNode = new Label(label);
        labelNode.setMinWidth(Region.USE_PREF_SIZE);
        labelNode.setMinHeight(Region.USE_PREF_SIZE);

        Label valueNode = new Label(value);
        row.getChildren().addAll(icon, labelNode, valueNode);
        return row;
    }

    private void showDeleteConfirmation() {
        ConfirmationModal deleteModal = new ConfirmationModal("Delete Connection",
                "Do you want to delete this connection?", new FontIcon(MaterialDesignD.DELETE_ALERT_OUTLINE),
                this::deleteConnection);
        deleteModal.setTranslateY(10);
        App.showModal(deleteModal);
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

    public void connect() {
        ConnectionService.getInstance().connect(
                connection,
                () -> progressIndicator.setVisible(true),
                () -> progressIndicator.setVisible(false),
                () -> App.connect(),
                e -> {
                }
        );
    }

    public void cloneConnection() {
        String newId = ConnectionService.getInstance().cloneConnection(
                this.connection.getId(), this.connection.getConnectionName() + " (copy)");

        ConnectionsPane connectionsPane = App.getConnectionsPane();
        if (connectionsPane != null) {
            connectionsPane.addConnection(newId);
        }

        new CustomNotification("Connection duplicated successfully.", new FontIcon(MaterialDesignC.CONTENT_COPY))
                .showNotification();
    }

    public void deleteConnection() {
        if (ConnectionService.getInstance().deleteConnection(connection.getId())) {
            App.closeModal();

            ConnectionsPane connectionsPane = App.getConnectionsPane();
            if (connectionsPane != null) {
                connectionsPane.removeConnection(connection.getId());
            }

            new CustomNotification("Connection deleted successfully.", new FontIcon(MaterialDesignD.DELETE_EMPTY_OUTLINE))
                    .showNotification();
        }
    }

    public ConnectionObject getConnection() {
        return connection;
    }

    public void setConnection(ConnectionObject connection) {
        this.connection = connection;
    }
}