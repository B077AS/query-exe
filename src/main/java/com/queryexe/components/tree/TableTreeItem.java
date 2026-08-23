package com.queryexe.components.tree;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
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
import com.queryexe.service.Async;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.service.QueryService;
import com.queryexe.service.SQLFormatterUtils;
import com.queryexe.queryexe.App;

@Slf4j
public class TableTreeItem extends CustomTreeItem {

    public TableTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem copyTableName = new MenuItem("Copy Name", menuIcon(MaterialDesignC.CONTENT_COPY));
        copyTableName.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            clipboard.setContent(content);
        });

        MenuItem alterTableMenuItem = new MenuItem("Alter Table", menuIcon(MaterialDesignT.TABLE_EDIT));
        alterTableMenuItem.setOnAction(event -> {
            App.showModal(new CreateTableModal(this.databaseName, this.titleLabel.getText(), this::refreshTable));
        });

        MenuItem insertScriptMenuItem = new MenuItem("Insert Script", menuIcon(MaterialDesignS.SCRIPT_TEXT_PLAY_OUTLINE));
        insertScriptMenuItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();
            App.getTabPane().getTabs().add(queryTab);

            Async.run(() -> {
                try {
                    String script = SQLFormatterUtils.format(
                            DatabaseConnection.getInstance().getConnectionObject()
                                    .generateInsertScript(this.titleLabel.getText(), this.databaseName),
                            DatabaseConnection.getInstance().getConnectionObject());
                    Platform.runLater(() -> codeArea.replaceText(script));
                } catch (Exception e) {
                    Platform.runLater(() -> codeArea.replaceText("-- ERROR: " + e.getMessage()));
                } finally {
                    Platform.runLater(queryTab::stopLoading);
                }
            });
        });

        MenuItem quickSelectMenuItem = new MenuItem("Quick Select (100 rows)", menuIcon(MaterialDesignT.TABLE_SEARCH));
        quickSelectMenuItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();
            App.getTabPane().getTabs().add(queryTab);
            App.getTabPane().getSelectionModel().select(queryTab);

            Async.run(() -> {
                try {
                    String script = SQLFormatterUtils.format(
                            DatabaseConnection.getInstance().getConnectionObject()
                                    .generateSelectScript(this.titleLabel.getText(), this.databaseName, 100),
                            DatabaseConnection.getInstance().getConnectionObject());
                    Platform.runLater(() -> {
                        codeArea.replaceText(script);
                        QueryService.getInstance().runQuery();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        codeArea.replaceText("-- ERROR: " + e.getMessage());
                        queryTab.stopLoading();
                    });
                }
            });
        });

        MenuItem createScriptMenuItem = new MenuItem("Create Script", menuIcon(MaterialDesignS.SCRIPT_TEXT_OUTLINE));
        createScriptMenuItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();
            App.getTabPane().getTabs().add(queryTab);

            Async.run(() -> {
                try {
                    String script = SQLFormatterUtils.format(
                            DatabaseConnection.getInstance().getConnectionObject()
                                    .generateCreateScript(this.titleLabel.getText(), this.databaseName),
                            DatabaseConnection.getInstance().getConnectionObject());
                    Platform.runLater(() -> codeArea.replaceText(script));
                } catch (Exception e) {
                    Platform.runLater(() -> codeArea.replaceText("-- ERROR: " + e.getMessage()));
                } finally {
                    Platform.runLater(queryTab::stopLoading);
                }
            });
        });

        MenuItem deleteTableMenuItem = new MenuItem("Delete Table", menuIcon(MaterialDesignT.TABLE_REMOVE));
        deleteTableMenuItem.setOnAction(event -> {
            App.showModal(new ConfirmationModal("Delete Table", "Do you want to delete this table?",
                    new FontIcon(MaterialDesignT.TABLE_ALERT), this::deleteTable));
        });

        MenuItem refreshTableItem = new MenuItem("Refresh Table", menuIcon(MaterialDesignR.REFRESH));
        refreshTableItem.setOnAction(event -> refreshTable());

        MenuItem propertiesTableItem = new MenuItem("Properties", menuIcon(MaterialDesignI.INFORMATION_OUTLINE));
        propertiesTableItem.setOnAction(event -> {
            App.showModal(new TablePropertiesModal(this.databaseName, this.titleLabel.getText()));
        });

        contextMenu.getItems().addAll(
                copyTableName,
                new SeparatorMenuItem(),
                quickSelectMenuItem,
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
            log.error("refreshTable failed", e);
        }
    }

    public void deleteTable() {
        Async.run(() -> {
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
            }
        });
    }
}