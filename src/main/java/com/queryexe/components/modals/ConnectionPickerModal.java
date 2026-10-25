package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;
import com.queryexe.utils.IconColorUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;

import java.util.List;
import java.util.function.Consumer;

/** Lists the saved connections and hands back the one the user clicks. */
public class ConnectionPickerModal extends VBox {

    public ConnectionPickerModal(String title, String subtitle, List<ConnectionObject> connections,
                                 Consumer<ConnectionObject> onPick) {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(520, 460);
        this.setMinSize(520, 460);
        this.setPrefSize(520, 460);

        Region headerFiller = new Region();
        HBox.setHgrow(headerFiller, Priority.ALWAYS);
        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> App.closeModal());
        HBox headerBox = new HBox(headerFiller, closeButton);
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(5, 5, 0, 0));

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().addAll(Styles.TEXT_MUTED);
        subtitleLabel.setWrapText(true);
        VBox titleBox = new VBox(4, titleLabel, subtitleLabel);
        titleBox.setPadding(new Insets(0, 20, 10, 20));

        VBox rows = new VBox(8);
        rows.setPadding(new Insets(4, 20, 16, 20));
        if (connections.isEmpty()) {
            Label empty = new Label("No saved connections yet. Create one from the home page first.");
            empty.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            rows.getChildren().add(empty);
        }
        for (ConnectionObject connection : connections) {
            rows.getChildren().add(createRow(connection, onPick));
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, titleBox, scroll);
    }

    private HBox createRow(ConnectionObject connection, Consumer<ConnectionObject> onPick) {
        FontIcon icon = new FontIcon(MaterialDesignD.DATABASE);
        IconColorUtil.apply(icon, "-color-accent-emphasis", 18);
        StackPane tile = new StackPane(icon);
        tile.setMinSize(34, 34);
        tile.setMaxSize(34, 34);
        tile.setStyle("-fx-background-color: -color-accent-subtle; -fx-background-radius: 9px;");

        Label name = new Label(connection.getConnectionName());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        String location = (connection.getHost() == null || connection.getHost().isBlank())
                ? connection.getFullUrl() : connection.getSimpleURL();
        Label where = new Label(location);
        where.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        where.setMinWidth(0);
        VBox text = new VBox(2, name, where);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label badge = new Label(connection.getDbType().toUpperCase());
        badge.getStyleClass().add("db-type-badge");

        HBox row = new HBox(12, tile, text, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.getStyleClass().add("connection-lineitem-card");
        row.setOnMouseClicked(e -> {
            App.closeModal();
            onPick.accept(connection);
        });
        return row;
    }
}
