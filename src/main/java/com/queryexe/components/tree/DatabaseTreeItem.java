package com.queryexe.components.tree;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.ConfirmationModal;
import com.queryexe.components.modals.CreateTableModal;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.Async;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class DatabaseTreeItem extends CustomTreeItem {

    public DatabaseTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem copyDatabaseName = new MenuItem("Copy Name", menuIcon(MaterialDesignC.CONTENT_COPY));
        copyDatabaseName.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            clipboard.setContent(content);
        });

        MenuItem useMenuItem = new MenuItem("Use", menuIcon(MaterialDesignD.DATABASE_CHECK));
        useMenuItem.setOnAction(event -> {
            try {
                DatabaseConnection.getInstance().getConnectionObject().useDatabase(this.titleLabel.getText());
                App.getDatabaseTree().selectDatabase(this.titleLabel.getText());
            } catch (SQLException e) {
                CustomNotification customNotification = new CustomNotification("Selection Failed", "The database could not be selected.", new FontIcon(MaterialDesignD.DATABASE_ALERT));
                customNotification.showNotification();
            }
        });

        MenuItem deleteDatabaseMenuItem;
        if (DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.PostgreSQL.toString())) {
            deleteDatabaseMenuItem = new MenuItem("Delete Schema", menuIcon(MaterialDesignD.DATABASE_REMOVE));
            deleteDatabaseMenuItem.setOnAction(event -> {
                App.showModal(new ConfirmationModal("Drop Schema", "Do you want to delete this schema? (" + this.titleLabel.getText() + ")", new FontIcon(MaterialDesignD.DATABASE_ALERT), () -> this.deleteDatabase(false)));
            });
        } else {
            deleteDatabaseMenuItem = new MenuItem("Delete Database", menuIcon(MaterialDesignD.DATABASE_REMOVE));
            deleteDatabaseMenuItem.setOnAction(event -> {
                App.showModal(new ConfirmationModal("Drop Database", "Do you want to delete this database? (" + this.titleLabel.getText() + ")", new FontIcon(MaterialDesignD.DATABASE_ALERT), () -> this.deleteDatabase(true)));
            });
        }

        MenuItem refreshMenuItem = new MenuItem("Refresh", menuIcon(MaterialDesignR.REFRESH));
        refreshMenuItem.setOnAction(event -> refreshDatabase());

        MenuItem createTableMenuItem = new MenuItem("Create Table", menuIcon(MaterialDesignT.TABLE_PLUS));
        createTableMenuItem.setOnAction(event -> {
            App.showModal(new CreateTableModal(this.databaseName, this::refreshDatabase));
        });

        contextMenu.getItems().addAll(
                copyDatabaseName,
                new SeparatorMenuItem(),
                useMenuItem,
                createTableMenuItem,
                new SeparatorMenuItem(),
                refreshMenuItem
        );

        if (!DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLite.toString())) {
            contextMenu.getItems().add(4, new SeparatorMenuItem());
            contextMenu.getItems().add(5, deleteDatabaseMenuItem);
        }
    }

    public void refreshDatabase() {
        if (this.getChildren().size() > 0) {
            TreeItem<String> tablesNode = this.getChildren().get(0);
            tablesNode.getChildren().clear();

            Map<String, ArrayList<ColumnData>> tablesAndColumnsMap = DatabaseConnection.getInstance()
                    .getConnectionObject().getAllTablesAndColumns(this.databaseName);
            App.getDatabaseTree().addTablesAndColumns(tablesAndColumnsMap, tablesNode, this.databaseName);
        }
    }

    public void deleteDatabase(boolean isDatabase) {
        Async.run(() -> {
            try {
                DatabaseConnection.getInstance().getConnectionObject().deleteDatabase(this.titleLabel.getText());
                Platform.runLater(() -> {
                    App.getDatabaseTree().initialize();
                    String entityType = isDatabase ? "Database" : "Schema";
                    CustomNotification customNotification = new CustomNotification(
                            entityType + " Dropped",
                            "The " + entityType.toLowerCase() + " was dropped successfully.",
                            new FontIcon(MaterialDesignD.DATABASE_CHECK)
                    );
                    customNotification.showNotification();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String entityType = isDatabase ? "Database" : "Schema";
                    CustomNotification customNotification = new CustomNotification(
                            entityType + " Drop Failed",
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