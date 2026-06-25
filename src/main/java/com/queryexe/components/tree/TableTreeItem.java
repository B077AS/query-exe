package com.queryexe.components.tree;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.components.modals.ConfirmationModal;
import com.queryexe.components.modals.CreateTableModal;
import com.queryexe.components.modals.TablePropertiesModal;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class TableTreeItem extends CustomTreeItem {

    public TableTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem copyTableName = new MenuItem("Copy Name");
        copyTableName.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            clipboard.setContent(content);
        });

        MenuItem alterTableMenuItem = new MenuItem("Alter Table");
        alterTableMenuItem.setOnAction(event -> {
            App.showModal(new CreateTableModal(this.databaseName, this.titleLabel.getText(), this::refreshTable));
        });

        MenuItem insertScriptMenuItem = new MenuItem("Insert Script");
        insertScriptMenuItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();
            App.getTabPane().getTabs().add(queryTab);

            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        String script = DatabaseConnection.getInstance().getConnectionObject()
                                .generateInsertScript(this.titleLabel.getText(), this.databaseName);
                        Platform.runLater(() -> codeArea.replaceText(script));
                    } catch (Exception e) {
                        Platform.runLater(() -> codeArea.replaceText("-- ERROR: " + e.getMessage()));
                    } finally {
                        executor.shutdown();
                        executor = null;
                        Platform.runLater(queryTab::stopLoading);
                    }
                });
            }
        });

        MenuItem createScriptMenuItem = new MenuItem("Create Script");
        createScriptMenuItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();
            App.getTabPane().getTabs().add(queryTab);

            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        String script = DatabaseConnection.getInstance().getConnectionObject()
                                .generateCreateScript(this.titleLabel.getText(), this.databaseName);
                        Platform.runLater(() -> codeArea.replaceText(script));
                    } catch (Exception e) {
                        Platform.runLater(() -> codeArea.replaceText("-- ERROR: " + e.getMessage()));
                    } finally {
                        executor.shutdown();
                        executor = null;
                        Platform.runLater(queryTab::stopLoading);
                    }
                });
            }
        });

        MenuItem deleteTableMenuItem = new MenuItem("Delete Table");
        deleteTableMenuItem.setOnAction(event -> {
            App.showModal(new ConfirmationModal("Delete Table", "Do you want to delete this table?",
                    new FontIcon(MaterialDesignT.TABLE_ALERT), this::deleteTable));
        });

        MenuItem refreshTableItem = new MenuItem("Refresh Table");
        refreshTableItem.setOnAction(event -> refreshTable());

        MenuItem propertiesTableItem = new MenuItem("Properties");
        propertiesTableItem.setOnAction(event -> {
            App.showModal(new TablePropertiesModal(this.databaseName, this.titleLabel.getText()));
        });

        contextMenu.getItems().addAll(
                copyTableName,
                new SeparatorMenuItem(),
                insertScriptMenuItem,
                createScriptMenuItem,
                new SeparatorMenuItem(),
                deleteTableMenuItem,
                new SeparatorMenuItem(),
                propertiesTableItem,
                refreshTableItem
        );

        if (!DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLite.toString())
                && !DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLServer.toString())) {
            contextMenu.getItems().add(1, alterTableMenuItem);
        }
    }

    public void refreshTable() {
        this.getChildren().clear();
        try {
            ArrayList<ColumnData> columns = DatabaseConnection.getInstance().getConnectionObject()
                    .getColumnsForTable(databaseName, this.getTitleLabel().getText());
            for (ColumnData column : columns) {
                ColumnTreeItem columnItem = new ColumnTreeItem(column.getColumnName(),
                        new FontIcon(column.isPrimaryKey() ? MaterialDesignK.KEY : MaterialDesignT.TABLE_COLUMN));
                columnItem.setDatabaseName(databaseName);
                this.getChildren().add(columnItem);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteTable() {
        if (this.executor == null) {
            executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    DatabaseConnection.getInstance().getConnectionObject()
                            .deleteTable(this.databaseName, this.titleLabel.getText());
                    Platform.runLater(() -> {
                        DatabaseTreeItem databaseItem = (DatabaseTreeItem) this.getParent().getParent();
                        databaseItem.refreshDatabase();
                        CustomNotification customNotification = new CustomNotification("Table Dropped", "The table was dropped successfully.", new FontIcon(MaterialDesignT.TABLE_CHECK));
                        customNotification.showNotification();
                        App.closeModal();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        CustomNotification customNotification = new CustomNotification("Drop Failed", e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL));
                        customNotification.showNotification();
                        App.closeModal();
                    });
                } finally {
                    executor.shutdown();
                    executor = null;
                }
            });
        }
    }
}