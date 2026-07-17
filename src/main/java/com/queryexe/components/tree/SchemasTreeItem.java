package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.CreateDatabaseModal;
import com.queryexe.service.Async;
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
            App.showModal(new CreateDatabaseModal(dbName -> {
                createSchema(dbName);
            }, false));
        });

        MenuItem refreshSchemasMenuItem = new MenuItem("Refresh");
        refreshSchemasMenuItem.setOnAction(event -> App.getDatabaseTree().initialize());

        contextMenu.getItems().addAll(createSchema, refreshSchemasMenuItem);
    }

    public void createSchema(String schemaName) {
        Async.run(() -> {
            try {
                DatabaseConnection.getInstance().getConnectionObject().createDatabase(schemaName);
                Platform.runLater(() -> {
                    App.getDatabaseTree().initialize();
                    CustomNotification customNotification = new CustomNotification(
                            "Schema Created",
                            "The schema was created successfully.",
                            new FontIcon(MaterialDesignD.DATABASE_CHECK)
                    );
                    customNotification.showNotification();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    CustomNotification customNotification = new CustomNotification(
                            "Schema Creation Failed",
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