package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import com.queryexe.components.extra.CustomProgressIndicator;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.ConnectionService;
import com.queryexe.service.QueryService;
import com.queryexe.queryexe.App;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;

import java.util.List;

public class MultipleConnectionsModal extends VBox {

    private Button closeButton;
    private VBox connectionsList;

    public MultipleConnectionsModal() {
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

        List<ConnectionObject> connections = ConnectionService.getInstance().loadConnections();
        for (ConnectionObject conn : connections) {
            connectionsList.getChildren().add(createConnectionCard(conn));
        }

        ScrollPane scrollPane = new ScrollPane(connectionsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox footer = buildFooter(connections.size());

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

        Region spacerLeft  = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft,  Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        Label title = new Label("Open Additional Connection");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(e -> App.closeModal());

        topRow.getChildren().addAll(phantom, spacerLeft, title, spacerRight, closeButton);

        Label subtitle = new Label("Select a saved connection to open a new query tab");
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

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterConnections(newVal.trim().toLowerCase());
        });

        searchBox.getChildren().addAll(searchIcon, searchField);
        row.getChildren().add(searchBox);
        return row;
    }

    private void filterConnections(String query) {
        connectionsList.getChildren().forEach(node -> {
            if (node instanceof HBox card) {
                Object tag = card.getUserData();
                if (tag instanceof ConnectionObject conn) {
                    boolean matches = query.isEmpty()
                            || conn.getConnectionName().toLowerCase().contains(query)
                            || conn.getDbType().toLowerCase().contains(query)
                            || getDisplayUrl(conn).toLowerCase().contains(query);
                    card.setVisible(matches);
                    card.setManaged(matches);
                }
            }
        });
    }

    private HBox createConnectionCard(ConnectionObject connection) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setUserData(connection);
        card.getStyleClass().add("connection-lineitem-card");

        VBox info = new VBox(3);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(connection.getConnectionName());
        nameLabel.setGraphic(new FontIcon(MaterialDesignD.DATABASE));
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

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

        Label urlLabel = new Label(getDisplayUrl(connection));
        urlLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        urlLabel.setStyle("-fx-font-size: 11px;");
        urlLabel.setMaxWidth(440);

        metaRow.getChildren().addAll(dbBadge, urlLabel);
        info.getChildren().addAll(nameLabel, metaRow);

        CustomProgressIndicator progress = new CustomProgressIndicator(18, 18);
        progress.setVisible(false);

        FontIcon chevron = new FontIcon(MaterialDesignC.CHEVRON_RIGHT);
        chevron.getStyleClass().add(Styles.TEXT_MUTED);

        card.getChildren().addAll(info, progress, chevron);
        card.setOnMouseClicked(e -> connectToDatabase(connection, progress));

        return card;
    }

    private HBox buildFooter(int count) {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 16, 12, 16));
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1px 0 0 0;");

        Label hint = new Label(count + " saved connection" + (count != 1 ? "s" : ""));
        hint.getStyleClass().add(Styles.TEXT_MUTED);
        hint.setStyle("-fx-font-size: 11px;");

        footer.getChildren().add(hint);
        return footer;
    }

    private void connectToDatabase(ConnectionObject connection, CustomProgressIndicator progress) {
        ConnectionService.getInstance().connect(
                connection,
                () -> {
                    progress.setVisible(true);
                    closeButton.setDisable(true);
                    App.getModalPane().setPersistent(true);
                },
                () -> {
                    progress.setVisible(false);
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
                e -> App.removeDatabaseTreeForConnection(connection)
        );
    }

    private String getDisplayUrl(ConnectionObject connection) {
        if (connection.getHost() == null || connection.getHost().isBlank()) {
            return connection.getFullUrl();
        }
        return connection.getSimpleURL();
    }
}