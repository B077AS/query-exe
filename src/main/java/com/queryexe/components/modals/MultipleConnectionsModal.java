package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import com.google.gson.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import com.queryexe.components.extra.CustomProgressIndicator;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.ConnectionService;
import com.queryexe.service.QueryService;
import com.queryexe.queryexe.App;
import java.util.List;

public class MultipleConnectionsModal extends VBox {

    private Button closeButton;

    public MultipleConnectionsModal() {
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

        closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> {
            App.closeModal();
        });

        Button fakeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        fakeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        fakeButton.setVisible(false);

        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);
        HBox.setHgrow(headerSecondFillerRegion, Priority.ALWAYS);

        Label title = new Label("Open Additional Connection");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        title.setAlignment(Pos.CENTER);

        headerBox.getChildren().addAll(fakeButton, headerFillerRegion, title, headerSecondFillerRegion, closeButton);

        Label subtitle = new Label("Create a new query tab for any saved connection");
        subtitle.getStyleClass().addAll(Styles.TEXT_MUTED);
        subtitle.setStyle("-fx-font-size: 13px;");
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setPadding(new Insets(5, 20, 0, 20));

        titleSection.getChildren().addAll(headerBox, subtitle);

        VBox connectionsList = new VBox(8);
        connectionsList.setPadding(new Insets(0, 10, 0, 10));

        List<ConnectionObject> connections = ConnectionService.getInstance().loadConnections();

        for (ConnectionObject connection : connections) {
            connectionsList.getChildren().add(createConnectionListItem(connection));
        }

        ScrollPane scrollPane = new ScrollPane(connectionsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox scrollContainer = new VBox();
        scrollContainer.setPadding(new Insets(10));
        scrollContainer.getChildren().add(scrollPane);

        this.getChildren().addAll(titleSection, scrollContainer);
    }

    private HBox createConnectionListItem(ConnectionObject connection) {
        HBox listItem = new HBox();
        listItem.setAlignment(Pos.CENTER_LEFT);
        listItem.setPadding(new Insets(10, 15, 10, 15));
        listItem.setSpacing(10);
        listItem.getStyleClass().add("connection-list-item");
        listItem.setStyle("-fx-background-radius: 8px; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 8px;" +
                "-fx-cursor: hand");

        VBox infoSection = new VBox(4);
        infoSection.setAlignment(Pos.CENTER_LEFT);

        Label connectionName = new Label(connection.getConnectionName());
        connectionName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        connectionName.setGraphic(new FontIcon(MaterialDesignD.DATABASE));

        Label dbTypeBadge = new Label(connection.getDbType().toUpperCase());
        dbTypeBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        dbTypeBadge.setStyle("-fx-background-color: -color-border-muted; " +
                "-fx-padding: 2 6 2 6; " +
                "-fx-background-radius: 10; " +
                "-fx-font-size: 9px; " +
                "-fx-font-weight: bold;");

        Label details = new Label();
        if (connection.getHost() == null || connection.getHost().isBlank()) {
            details.setText(connection.getFullUrl());
        } else {
            details.setText(connection.getSimpleURL());
        }
        details.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        details.setStyle("-fx-font-size: 11px;");

        infoSection.getChildren().addAll(connectionName, dbTypeBadge, details);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        CustomProgressIndicator progressIndicator = new CustomProgressIndicator(20, 20);
        progressIndicator.setVisible(false);

        listItem.getChildren().addAll(infoSection, spacer, progressIndicator, new FontIcon(MaterialDesignC.CHEVRON_RIGHT));

        listItem.setOnMouseClicked(event -> {
            connectToDatabase(connection, progressIndicator);
        });

        return listItem;
    }

    private void connectToDatabase(ConnectionObject connection, CustomProgressIndicator progressIndicator) {

        ConnectionService.getInstance().connect(
                connection,
                () -> {
                    progressIndicator.setVisible(true);
                    closeButton.setDisable(true);
                    App.getModalPane().setPersistent(true);
                },
                () -> {
                    progressIndicator.setVisible(false);
                    closeButton.setDisable(false);
                    App.getModalPane().setPersistent(false);
                },
                () -> {
                    App.getDatabaseTreeForConnection(connection);
                    CustomTab<VirtualizedScrollPane<CodeArea>> queryTab =
                            QueryService.getInstance().newQueryForConnection(connection);
                    Platform.runLater(() -> {
                        App.getTabPane().getTabs().add(queryTab);
                        App.getTabPane().getSelectionModel().select(queryTab);
                        App.closeModal();
                    });
                },
                e -> {
                    App.removeDatabaseTreeForConnection(connection);
                }
        );
    }
}