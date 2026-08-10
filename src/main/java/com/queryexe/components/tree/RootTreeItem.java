package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.CreateDatabaseModal;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.service.Async;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class RootTreeItem extends CustomTreeItem {

    public RootTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem createDatabaseMenuItem = new MenuItem("Create Database", menuIcon(MaterialDesignD.DATABASE_PLUS));
        createDatabaseMenuItem.setOnAction(event -> {
            App.showModal(new CreateDatabaseModal(dbName -> {
                createDatabase(dbName, true);
            }, true));
        });

        MenuItem refreshDatabasesMenuItem = new MenuItem("Refresh", menuIcon(MaterialDesignR.REFRESH));
        refreshDatabasesMenuItem.setOnAction(event -> {
            App.getDatabaseTree().initialize();
        });

        contextMenu.getItems().addAll(refreshDatabasesMenuItem);
        if (!DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLite.toString())) {
            contextMenu.getItems().add(0, createDatabaseMenuItem);
        }
    }

    public void createDatabase(String dbName, boolean isDatabase) {
        Async.run(() -> {
            try {
                DatabaseConnection.getInstance().getConnectionObject().createDatabase(dbName);
                Platform.runLater(() -> {
                    App.getDatabaseTree().initialize();
                    String entityType = isDatabase ? "Database" : "Schema";
                    CustomNotification customNotification = new CustomNotification(
                            entityType + " Created",
                            "The " + entityType.toLowerCase() + " was created successfully.",
                            new FontIcon(MaterialDesignD.DATABASE_CHECK)
                    );
                    customNotification.showNotification();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String entityType = isDatabase ? "Database" : "Schema";
                    CustomNotification customNotification = new CustomNotification(
                            entityType + " Creation Failed",
                            e.getMessage(),
                            new FontIcon(MaterialDesignD.DATABASE_REMOVE)
                    );
                    customNotification.showNotification();
                });
            } finally {
                Platform.runLater(App::closeModal);
            }
        });
    }
}