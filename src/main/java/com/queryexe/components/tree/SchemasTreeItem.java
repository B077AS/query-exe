package com.queryexe.components.tree;

import java.util.concurrent.Executors;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.CreateDatabaseModal;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class SchemasTreeItem extends CustomTreeItem {

    public SchemasTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem createSchema = new MenuItem("Create Schema");
        createSchema.setOnAction(event -> {
            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        Platform.runLater(() -> {
                            App.showModal(new CreateDatabaseModal(dbName -> {
                                createSchema(dbName);
                            }, false));
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            CustomNotification customNotification = new CustomNotification("Schema creation failed", new FontIcon(MaterialDesignD.DATABASE_ALERT));
                            customNotification.showNotification();
                        });
                    } finally {
                        executor.shutdown();
                        executor = null;
                    }
                });
            }
        });

        MenuItem refreshSchemasMenuItem = new MenuItem("Refresh");
        refreshSchemasMenuItem.setOnAction(event -> App.getDatabaseTree().initialize());

        contextMenu.getItems().addAll(createSchema, refreshSchemasMenuItem);
    }

    public void createSchema(String schemaName) {
        if (this.executor == null) {
            executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    DatabaseConnection.getInstance().getConnectionObject().createDatabase(schemaName);
                    Platform.runLater(() -> {
                        App.getDatabaseTree().initialize();
                        CustomNotification customNotification = new CustomNotification(
                                "Schema created successfully",
                                new FontIcon(MaterialDesignD.DATABASE_CHECK)
                        );
                        customNotification.showNotification();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        CustomNotification customNotification = new CustomNotification(
                                "Schema creation failed!\n" + e.getMessage(),
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