package com.queryexe.components.tree;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.components.modals.*;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class CustomTreeItem extends TreeItem<String> {

    private Label titleLabel;
    private String databaseName;
    private FontIcon icon;
    private ContextMenu contextMenu;
    private ExecutorService executor;

    public CustomTreeItem(String name, FontIcon icon) {
        this.icon = icon;

        HBox graphicBox = new HBox();
        graphicBox.setAlignment(Pos.CENTER_LEFT);
        graphicBox.setSpacing(5);

        titleLabel = new Label(name);

        graphicBox.getChildren().addAll(icon, titleLabel);

        this.setValue("");
        this.setGraphic(graphicBox);
    }

    public void setupDatabaseContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem copyDatabaseName = new MenuItem("Copy Name");
        copyDatabaseName.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            clipboard.setContent(content);
        });

        MenuItem useMenuItem = new MenuItem("Use");
        useMenuItem.setOnAction(event -> {
            try {
                DatabaseConnection.getInstance().getConnectionObject().useDatabase(this.titleLabel.getText());
                App.getDatabaseTree().selectDatabase(this.titleLabel.getText());
            } catch (SQLException e) {
                CustomNotification customNotification = new CustomNotification("Database selection failed", new FontIcon(MaterialDesignD.DATABASE_ALERT));
                customNotification.showNotification();
            }
        });

        MenuItem deleteDatabaseMenuItem=null;
        if(DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.PostgreSQL.toString())){
            deleteDatabaseMenuItem = new MenuItem("Delete Schema");
            deleteDatabaseMenuItem.setOnAction(event -> {
                App.showModal(new ConfirmationModal("Drop Schema", "Do you want to delete this schema? ("+this.titleLabel.getText()+")", new FontIcon(MaterialDesignD.DATABASE_ALERT), () -> this.deleteDatabase(false)));
            });
        }else{
            deleteDatabaseMenuItem = new MenuItem("Delete Database");
            deleteDatabaseMenuItem.setOnAction(event -> {
                App.showModal(new ConfirmationModal("Drop Database", "Do you want to delete this database? ("+this.titleLabel.getText()+")", new FontIcon(MaterialDesignD.DATABASE_ALERT), () -> this.deleteDatabase(true)));
            });
        }

        MenuItem refreshMenuItem = new MenuItem("Refresh");
        refreshMenuItem.setOnAction(event -> {
            refreshDatabase();
        });

        MenuItem createTableMenuItem = new MenuItem("Create Table");
        createTableMenuItem.setOnAction(event -> {
            App.showModal(new CreateTableModal(this.databaseName, this::refreshDatabase));
        });

        contextMenu.getItems().addAll(copyDatabaseName,
                new SeparatorMenuItem(),
                useMenuItem,
                createTableMenuItem,
                new SeparatorMenuItem(),
                refreshMenuItem);

        if (!DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLite.toString())) {
            contextMenu.getItems().add(4, new SeparatorMenuItem());
            contextMenu.getItems().add(5, deleteDatabaseMenuItem);
        }
    }

    public void setupTablesContextMenu() {
        contextMenu = new ContextMenu();
        MenuItem createTableMenuItem = new MenuItem("Create Table");
        createTableMenuItem.setOnAction(event -> {
            CustomTreeItem parentItem = (CustomTreeItem) this.getParent();

            App.showModal(new CreateTableModal(this.databaseName,
                    parentItem::refreshDatabase));
        });

        MenuItem refreshTablesMenuItem = new MenuItem("Refresh Tables");
        refreshTablesMenuItem.setOnAction(event -> {
            CustomTreeItem parentItem = (CustomTreeItem) this.getParent();
            parentItem.refreshDatabase();
        });

        contextMenu.getItems().addAll(createTableMenuItem, refreshTablesMenuItem);
    }

    public void setupDatabasesContextMenu() {
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
                            CustomNotification customNotification = new CustomNotification("Database creation failed", new FontIcon(MaterialDesignD.DATABASE_ALERT));
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

    public void setupTableContextMenu() {

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
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<CodeArea>(codeArea);

            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();

            App.getTabPane().getTabs().add(queryTab);

            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        String script = DatabaseConnection.getInstance().getConnectionObject().generateInsertScript(this.titleLabel.getText(), this.databaseName);
                        Platform.runLater(() -> {
                            codeArea.replaceText(script);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            codeArea.replaceText("-- ERROR: " + e.getMessage());
                        });
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
            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<CodeArea>(codeArea);

            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");
            queryTab.setLoading();

            App.getTabPane().getTabs().add(queryTab);

            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        String script = DatabaseConnection.getInstance().getConnectionObject().generateCreateScript(this.titleLabel.getText(), this.databaseName);
                        Platform.runLater(() -> {
                            codeArea.replaceText(script);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            codeArea.replaceText("-- ERROR: " + e.getMessage());
                        });
                    } finally {
                        executor.shutdown();
                        executor = null;
                        Platform.runLater(queryTab::stopLoading);
                    }
                });
            }
        });

        MenuItem deleteScriptMenuItem = new MenuItem("Delete Table");
        deleteScriptMenuItem.setOnAction(event -> {
            ConfirmationModal confirmationModal = new ConfirmationModal("Delete Table", "Do you want to delete this table?", new FontIcon(MaterialDesignT.TABLE_ALERT), this::deleteTable);
            App.showModal(confirmationModal);
        });

        MenuItem refreshTableItem = new MenuItem("Refresh Table");
        refreshTableItem.setOnAction(event -> {
            refreshTable();
        });

        MenuItem propertiesTableItem = new MenuItem("Properties");
        propertiesTableItem.setOnAction(event -> {
            App.showModal(new TablePropertiesModal(this.databaseName, this.titleLabel.getText()));
        });


        contextMenu.getItems().addAll(copyTableName,
                new SeparatorMenuItem(),
                insertScriptMenuItem,
                createScriptMenuItem,
                new SeparatorMenuItem(),
                deleteScriptMenuItem,
                new SeparatorMenuItem(),
                propertiesTableItem,
                refreshTableItem);

        if (!DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLite.toString())
                && !DatabaseConnection.getInstance().getCurrentConnectionObject().getDbType().equals(ConnectionTypes.SQLServer.toString())) {
            contextMenu.getItems().add(1, alterTableMenuItem);
        }

    }

    public void setupColumnContextMenu(){
        contextMenu = new ContextMenu();

        MenuItem copyColumnName = new MenuItem("Copy Name");
        copyColumnName.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            clipboard.setContent(content);
        });

        MenuItem propertiesMenuItem = new MenuItem("Properties");
        propertiesMenuItem.setOnAction(event -> {
            CustomTreeItem parentItem = (CustomTreeItem) this.getParent();
            App.showModal(new ColumnPropertiesModal(this.databaseName, parentItem.getTitleLabel().getText(), this.getTitleLabel().getText()));
        });

        contextMenu.getItems().addAll(copyColumnName, propertiesMenuItem);
    }
    public void setupSchemasContextMenu(){
        contextMenu = new ContextMenu();

        MenuItem createSchema = new MenuItem("Create Schema");
        createSchema.setOnAction(event -> {

            if (this.executor == null) {
                executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    try {
                        Platform.runLater(() -> {
                            App.showModal(new CreateDatabaseModal(dbName -> {
                                createDatabase(dbName, false);
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
        refreshSchemasMenuItem.setOnAction(event -> {
            App.getDatabaseTree().initialize();
        });

        contextMenu.getItems().addAll(createSchema, refreshSchemasMenuItem);
    }

    public void refreshDatabase() {
        if (this.getChildren().size() > 0) {
            TreeItem<String> tablesNode = this.getChildren().get(0);
            tablesNode.getChildren().clear();

            Map<String, ArrayList<ColumnData>> tablesAndColumnsMap = DatabaseConnection.getInstance().getConnectionObject().getAllTablesAndColumns(this.databaseName);
            App.getDatabaseTree().addTablesAndColumns(tablesAndColumnsMap, tablesNode, this.databaseName);
        }
    }

    public void refreshTable() {
        this.getChildren().clear();

        try {
            ArrayList<ColumnData> columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, this.getTitleLabel().getText());
            for (ColumnData column : columns) {
                CustomTreeItem columnsTree;
                if (!column.isPrimaryKey()) {
                    columnsTree = new CustomTreeItem(column.getColumnName(), new FontIcon(MaterialDesignT.TABLE_COLUMN));
                } else {
                    columnsTree = new CustomTreeItem(column.getColumnName(), new FontIcon(MaterialDesignK.KEY));
                }
                this.getChildren().add(columnsTree);
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
                    DatabaseConnection.getInstance().getConnectionObject().deleteTable(this.databaseName, this.titleLabel.getText());
                    Platform.runLater(() -> {
                        CustomTreeItem databaseItem = (CustomTreeItem) this.getParent().getParent();
                        databaseItem.refreshDatabase();
                        CustomNotification customNotification = new CustomNotification("Table dropped successfully", new FontIcon(MaterialDesignT.TABLE_CHECK));
                        customNotification.showNotification();
                        App.closeModal();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        CustomNotification customNotification = new CustomNotification("Drop table failed!\n" + e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL));
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
                                entityType + " created successfully",
                                new FontIcon(MaterialDesignD.DATABASE_CHECK)
                        );
                        customNotification.showNotification();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        String entityType = isDatabase ? "Database" : "Schema";
                        CustomNotification customNotification = new CustomNotification(
                                entityType + " creation failed!\n" + e.getMessage(),
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

    public void deleteDatabase(boolean isDatabase) {
        if (this.executor == null) {
            executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    DatabaseConnection.getInstance().getConnectionObject().deleteDatabase(this.titleLabel.getText());
                    Platform.runLater(() -> {
                        App.getDatabaseTree().initialize();
                        String entityType = isDatabase ? "Database" : "Schema";
                        CustomNotification customNotification = new CustomNotification(
                                entityType + " dropped successfully",
                                new FontIcon(MaterialDesignD.DATABASE_CHECK)
                        );
                        customNotification.showNotification();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        String entityType = isDatabase ? "Database" : "Schema";
                        CustomNotification customNotification = new CustomNotification(
                                entityType + " drop failed!\n" + e.getMessage(),
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

    public void setSelected() {
        titleLabel.setStyle("-fx-font-weight: bold;");
    }

    public void setUnSelected() {
        titleLabel.setStyle("-fx-font-weight: normal;");
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public void setTitleLabel(Label titleLabel) {
        this.titleLabel = titleLabel;
    }

    public ContextMenu getContextMenu() {
        return contextMenu;
    }

    public void setContextMenu(ContextMenu contextMenu) {
        this.contextMenu = contextMenu;
    }

    public FontIcon getIcon() {
        return icon;
    }

    public void setIcon(FontIcon icon) {
        this.icon = icon;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }
}