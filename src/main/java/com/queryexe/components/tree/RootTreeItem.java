package com.queryexe.components.tree;

import java.util.concurrent.Executors;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.CreateDatabaseModal;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class RootTreeItem extends CustomTreeItem {

    public RootTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem createDatabaseMenuItem = new MenuItem("Create Database");
        createDatabaseMenuItem.setOnAction(event -> {
            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        Platform.runLater(() -> {
                            App.showModal(new CreateDatabaseModal(dbName -> {
                                createDatabase(dbName, true);
                            }, true));
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            CustomNotification customNotification = new CustomNotification("Creation Failed", "The database could not be created.", new FontIcon(MaterialDesignD.DATABASE_ALERT));
                            customNotification.showNotification();
                        });
                    } finally {
                        executor.shutdown();
                        executor = null;
                    }
                });
            }
        });

        MenuItem refreshDatabasesMenuItem = new MenuItem("Refresh");
        refreshDatabasesMenuItem.setOnAction(event -> {
            App.getDatabaseTree().initialize();
        });

        contextMenu.getItems().addAll(refreshDatabasesMenuItem);
        if (!DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLite.toString())) {
            contextMenu.getItems().add(0, createDatabaseMenuItem);
        }
    }

    public void createDatabase(String dbName, boolean isDatabase) {
        if (this.executor == null) {
            executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
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
                    executor.shutdown();
                    executor = null;
                    Platform.runLater(App::closeModal);
                }
            });
        }
    }
}