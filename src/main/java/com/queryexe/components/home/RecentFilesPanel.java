package com.queryexe.components.home;

import atlantafx.base.theme.Styles;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.ConnectionPickerModal;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.queryexe.App;
import com.queryexe.service.ConnectionService;
import com.queryexe.service.QueryService;
import com.queryexe.service.RecentFilesManager;
import com.queryexe.utils.IconColorUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Left-hand column of the home page listing the SQL files opened or saved recently, each with the
 * connection it was last used on. Clicking one connects to that connection and opens the file; a file
 * with no (or a failed) remembered connection asks which connection to use.
 */
public class RecentFilesPanel extends VBox {

    public static final double WIDTH = 250;

    private final VBox items = new VBox(2);
    private final Map<String, ConnectionObject> connectionsById = new HashMap<>();

    public RecentFilesPanel() {
        this.setMinWidth(WIDTH);
        this.setPrefWidth(WIDTH);
        this.setMaxWidth(WIDTH);
        this.setPadding(new Insets(14));
        this.setSpacing(8);
        this.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: transparent -color-border-subtle transparent transparent;");

        Label title = new Label("RECENT FILES");
        title.getStyleClass().addAll(Styles.TEXT_MUTED);
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");

        Button clear = new Button("Clear");
        clear.getStyleClass().addAll(Styles.FLAT, Styles.SMALL, Styles.TEXT_MUTED);
        clear.setOnAction(e -> {
            RecentFilesManager.getInstance().clearRecentFiles();
            refresh();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(title, spacer, clear);
        header.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        this.getChildren().addAll(header, scroll);
        refresh();
    }

    public void refresh() {
        connectionsById.clear();
        for (ConnectionObject connection : ConnectionService.getInstance().loadConnections()) {
            connectionsById.put(connection.getId(), connection);
        }

        items.getChildren().clear();
        List<RecentFilesManager.Entry> entries = RecentFilesManager.getInstance().getRecentEntries();
        if (entries.isEmpty()) {
            Label empty = new Label("SQL files you open or save will show up here.");
            empty.setWrapText(true);
            empty.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            items.getChildren().add(empty);
            return;
        }
        for (RecentFilesManager.Entry entry : entries) {
            items.getChildren().add(createItem(entry));
        }
    }

    private HBox createItem(RecentFilesManager.Entry entry) {
        File file = new File(entry.getPath());
        ConnectionObject connection = entry.getConnectionId() == null ? null : connectionsById.get(entry.getConnectionId());

        FontIcon icon = new FontIcon(Feather.FILE_TEXT);
        IconColorUtil.apply(icon, "-color-accent-emphasis", 16);

        Label name = new Label(file.getName());
        name.setStyle("-fx-font-size: 13px;");
        name.setMinWidth(0);

        String folder = file.getParent() == null ? "" : file.getParent();
        Label location = new Label(folder);
        location.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        location.setStyle("-fx-font-size: 11px;");
        location.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        location.setMinWidth(0);

        VBox text = new VBox(1, name, location);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.getStyleClass().add("recent-file-item");
        Tooltip.install(row, new Tooltip(file.getAbsolutePath()));

        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                open(file, connection);
            }
        });

        MenuItem openItem = new MenuItem("Open", new FontIcon(Feather.PLAY));
        openItem.setOnAction(e -> open(file, connection));
        MenuItem openWithItem = new MenuItem("Open with…", new FontIcon(Feather.DATABASE));
        openWithItem.setOnAction(e -> pickConnectionAndOpen(file));
        MenuItem removeItem = new MenuItem("Remove from list", new FontIcon(Feather.X));
        removeItem.setOnAction(e -> {
            RecentFilesManager.getInstance().removeRecentFile(entry.getPath());
            refresh();
        });
        row.setOnContextMenuRequested(e -> {
            new ContextMenu(openItem, openWithItem, removeItem).show(row, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return row;
    }

    /** Uses the remembered connection when there is one; anything else falls back to the picker. */
    private void open(File file, ConnectionObject connection) {
        if (!file.exists()) {
            new CustomNotification("File Not Found", file.getName() + " no longer exists.",
                    new FontIcon(Feather.ALERT_CIRCLE)).showNotification();
            refresh();
            return;
        }
        if (connection == null) {
            pickConnectionAndOpen(file);
            return;
        }
        this.setDisable(true);
        QueryService.getInstance().connectAndOpen(
                file,
                connection,
                null,
                () -> this.setDisable(false),
                error -> Platform.runLater(() -> pickConnectionAndOpen(file))
        );
    }

    private void pickConnectionAndOpen(File file) {
        List<ConnectionObject> connections = ConnectionService.getInstance().loadConnections();
        ConnectionPickerModal picker = new ConnectionPickerModal(
                "Open with…",
                "Choose the connection to run " + file.getName() + " on.",
                connections,
                connection -> {
                    this.setDisable(true);
                    QueryService.getInstance().connectAndOpen(file, connection, null,
                            () -> this.setDisable(false), error -> { });
                });
        picker.setTranslateY(10);
        App.showModal(picker);
    }
}
