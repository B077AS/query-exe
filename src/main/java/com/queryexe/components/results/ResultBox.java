package com.queryexe.components.results;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.modals.ConfirmationModal;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.QueryData;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.queryexe.App;
import com.queryexe.service.Async;
import com.queryexe.service.ChangeApplier;
import com.queryexe.service.ScriptRunner;
import com.queryexe.utils.SqlInspector;
import com.queryexe.components.tree.CustomTree;
import com.queryexe.model.connections.ConnectionObject;
import java.sql.Statement;
import com.queryexe.service.ExportUtils;
import com.queryexe.utils.TabScrollChevrons;

@Slf4j
public class ResultBox extends VBox {

    private Button revertButton;
    private Button applyButton;
    private CustomTab<?> parentTab;

    public ResultBox(List<String> statements, CustomTab<?> parentTab) {
        this.parentTab = parentTab;
        ConnectionObject connectionObject = parentTab != null && parentTab.getConnectionObject() != null
                ? parentTab.getConnectionObject()
                : DatabaseConnection.getInstance().getCurrentConnectionObject();

        TabPane tabPane = new TabPane();
        tabPane.setTabDragPolicy(TabDragPolicy.REORDER);
        TabScrollChevrons.install(tabPane);

        revertButton = new Button("Revert");
        revertButton.setGraphic(new FontIcon(MaterialDesignR.RESTORE));
        revertButton.setDisable(true);
        revertButton.setOnAction(event -> {
            ConfirmationModal deleteModal = new ConfirmationModal(
                    "Revert Changes",
                    "Do you want to revert the changes?",
                    new FontIcon(MaterialDesignB.BACKUP_RESTORE),
                    () -> {
                        revertChanges(tabPane);
                    }
            );
            App.showModal(deleteModal);
        });

        applyButton = new Button("Apply");
        applyButton.setGraphic(new FontIcon(MaterialDesignC.CHECK));
        applyButton.setDisable(true);
        applyButton.setOnAction(event -> {
            ConfirmationModal applyModal = new ConfirmationModal(
                    "Apply Changes",
                    "Do you want to apply the changes?",
                    new FontIcon(MaterialDesignC.CHECK),
                    () -> {
                        applyChanges(tabPane);
                    }
            );
            App.showModal(applyModal);
        });

        ToolBar tableHeader = new ToolBar();
        tableHeader.setPadding(new Insets(5, 10, 5, 10));
        tableHeader.setStyle("-fx-spacing: 5;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rowsLabelBox = new HBox();
        rowsLabelBox.setAlignment(Pos.CENTER_RIGHT);
        rowsLabelBox.setSpacing(10);

        Label timeLabel = new Label("Time: 0ms");
        Label rowsLabel = new Label("Rows: 0");
        rowsLabelBox.getChildren().addAll(timeLabel, rowsLabel);

        Button addRow = new Button("Add Row");
        FontIcon addRowIcon = new FontIcon(MaterialDesignT.TABLE_ROW_PLUS_AFTER);
        addRow.setGraphic(addRowIcon);
        addRow.setOnAction(event -> {
            ResultTable currentTable = (ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent();
            currentTable.addDatabaseRow();
        });

        Button removeRow = new Button("Remove Row");
        FontIcon removeRowIcon = new FontIcon(MaterialDesignT.TABLE_ROW_REMOVE);
        removeRow.setGraphic(removeRowIcon);
        removeRow.setOnAction(event -> {
            ObservableList<Integer> rows = ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().getSelectedIndices();
            if (rows.isEmpty()) {
                CustomNotification errorRemoveNotification = new CustomNotification("No Row Selected", "Select a row to remove first.", new FontIcon(MaterialDesignT.TABLE_CANCEL));
                errorRemoveNotification.showNotification();
            } else {
                ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).deleteDatabaseRows(rows);
            }
        });

        MenuButton exportButton = new MenuButton("Export As");
        exportButton.setPopupSide(Side.RIGHT);
        MenuItem csvMenuItem = new MenuItem("CSV");
        csvMenuItem.setOnAction(event -> ExportUtils.exportToCSV((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()));
        MenuItem jsonMenuItem = new MenuItem("JSON");
        jsonMenuItem.setOnAction(event -> ExportUtils.exportToJSON((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()));
        MenuItem xmlMenuItem = new MenuItem("XML");
        xmlMenuItem.setOnAction(event -> ExportUtils.exportToXML((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()));
        exportButton.getItems().addAll(csvMenuItem, jsonMenuItem, xmlMenuItem);
        FontIcon exportIcon = new FontIcon(MaterialDesignT.TABLE_ARROW_RIGHT);
        exportButton.setGraphic(exportIcon);

        Button selectAllButton = new Button("Select All");
        FontIcon selectAllIcon = new FontIcon(MaterialDesignS.SELECT_ALL);
        selectAllButton.setGraphic(selectAllIcon);
        selectAllButton.setOnAction(event -> {
            if (((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().getSelectedItems().size() == ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getItems().size()) {
                ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).requestFocus();
                ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().clearSelection();
            } else {
                ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).requestFocus();
                ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().selectAll();
            }
        });

        tableHeader.getItems().addAll(
                addRow,
                new Separator(Orientation.VERTICAL),
                removeRow,
                new Separator(Orientation.VERTICAL),
                selectAllButton,
                new Separator(Orientation.VERTICAL),
                exportButton,
                spacer,
                applyButton,
                new Separator(Orientation.VERTICAL),
                revertButton,
                new Separator(Orientation.VERTICAL),
                rowsLabelBox
        );

        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null) {
                if (newTab.getContent() instanceof ResultTable) {
                    TableView<TableRowData> selectedTable = ((ResultTable) newTab.getContent());
                    if (selectedTable != null) {
                        rowsLabel.setText(rowsText((ResultTable) selectedTable));
                        timeLabel.setText("Time: " + ((ResultTable) selectedTable).getExecutionTime() + "ms");
                    }

                    if (((ResultTable) selectedTable).getUpdateQueries().isEmpty()) {
                        applyButton.setDisable(true);
                        revertButton.setDisable(true);
                    } else {
                        applyButton.setDisable(false);
                        revertButton.setDisable(false);
                    }

                    removeRow.setDisable(!((ResultTable) selectedTable).isEditable());
                    addRow.setDisable(!((ResultTable) selectedTable).isEditable());
                } else if (newTab.getContent() instanceof Label) {//TODO

                }

                if (((CustomTab<?>) newTab).isError()) {
                    tableHeader.setDisable(true);
                } else {
                    tableHeader.setDisable(false);
                }
            }
        });

        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            if (tabPane.getTabs().isEmpty()) {
                App.getCodeAndResultSplitPane().getItems().remove(1);
            }
        });

        tabPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.A && ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getEditingCell() == null) {

                if (((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().getSelectedItems().size() == ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getItems().size()) {
                    ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().clearSelection();
                } else {
                    ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getSelectionModel().selectAll();
                }

                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.C && ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getEditingCell() == null) {
                ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).copySelectedRows();
                event.consume();
            }
        });

        // Statements were already split for this connection's dialect by SqlSplitter. The connection is resolved
        // once, so switching tabs while a script runs can't send later statements to another database.
        boolean allSucceeded;
        try {
            Connection connection = openConnection(connectionObject);
            allSucceeded = ScriptRunner.run(connection, statements, MAX_ROWS, new ScriptRunner.Listener() {
                @Override
                public void onStatementStarted(String sql, Statement statement) {
                    if (parentTab != null) {
                        parentTab.setCurrentStatement(statement);
                    }
                }

                @Override
                public void onResultSet(String sql, Statement statement, long millis) throws SQLException {
                    ResultTable resultTable = new ResultTable(statement, millis, MAX_ROWS, connectionObject);
                    resultTable.setOnCellUpdate(() -> {
                        boolean hasChanges = resultTable.hasPendingChanges();
                        applyButton.setDisable(!hasChanges);
                        revertButton.setDisable(!hasChanges);
                        rowsLabel.setText(rowsText(resultTable));
                    });

                    CustomTab<ResultTable> tableTab = new CustomTab<>(resultTable, tabPane);
                    tableTab.getIcon().setOnMouseClicked(event -> {
                        if (applyButton.isDisabled()) {
                            tabPane.getTabs().remove(tableTab);
                        } else {
                            ConfirmationModal confirmationModal = new ConfirmationModal(
                                    "Unsaved Changes",
                                    "Do you want to apply the changes?",
                                    new FontIcon(MaterialDesignA.ALERT_OUTLINE),
                                    () -> {
                                        applyChanges(tabPane);
                                    }
                            );
                            App.showModal(confirmationModal);
                        }
                    });
                    tabPane.getTabs().add(tableTab);
                }

                @Override
                public void onUpdateCount(String sql, long count, long millis) {
                    SimpleResultBox simpleResultBox = new SimpleResultBox((int) Math.min(count, Integer.MAX_VALUE), sql);
                    tabPane.getTabs().add(new CustomTab<>(simpleResultBox, tabPane));
                }

                @Override
                public void onStatementSucceeded(String sql) {
                    rememberTreeEffects(sql, connectionObject);
                }

                @Override
                public void onError(String sql, SQLException error) {
                    addErrorTab(tabPane, sql, error);
                }
            });
        } catch (SQLException e) {
            // the connection itself could not be opened
            log.warn("Could not open connection: {}", e.getMessage());
            addErrorTab(tabPane, statements.isEmpty() ? "" : statements.get(0), e);
            allSucceeded = false;
        }

        if (!allSucceeded) {
            CustomNotification errorNotification = new CustomNotification("Execution Errors", "Some queries failed to execute.", new FontIcon(MaterialDesignC.CLOSE_CIRCLE));
            errorNotification.showNotification();
        }
        applyTreeEffects(connectionObject);

        this.getChildren().addAll(tableHeader, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
    }

    // Results beyond this many rows are not loaded; the row counter says so.
    private static final int MAX_ROWS = 10_000;

    // What the finished script means for the schema tree, applied once on the FX thread after the script.
    private String schemaToSelect;
    private boolean refreshTree;
    private boolean rebuildTree;

    private static String rowsText(ResultTable table) {
        return "Rows: " + table.getItems().size() + (table.isTruncated() ? "+ (limit reached)" : "");
    }

    private void addErrorTab(TabPane tabPane, String sql, SQLException error) {
        ResultErrorBox errorBox = new ResultErrorBox(sql, error);
        CustomTab<HBox> errorTab = new CustomTab<HBox>(errorBox, tabPane);
        errorTab.setErrorHeader();
        tabPane.getTabs().add(errorTab);
    }

    // The tab's own connection, opened on demand; never whichever one is "current".
    private Connection openConnection(ConnectionObject connectionObject) throws SQLException {
        if (connectionObject == null) {
            throw new SQLException("No connection is associated with this query tab.");
        }
        if (parentTab != null && parentTab.getConnectionObject() != null) {
            return parentTab.getSessionConnection();
        }
        DatabaseConnection databaseConnection = DatabaseConnection.getInstance();
        if (databaseConnection.getConnection(connectionObject.getId()) == null) {
            databaseConnection.initialize(connectionObject);
        }
        return databaseConnection.getConnection(connectionObject.getId());
    }

    private void rememberTreeEffects(String sql, ConnectionObject connectionObject) {
        SqlInspector.detectSchemaSwitch(sql, connectionObject.getDbType()).ifPresent(name -> schemaToSelect = name);
        if (SqlInspector.changesDatabaseList(sql)) {
            rebuildTree = true;
        } else if (SqlInspector.isDdl(sql)) {
            refreshTree = true;
        }
    }

    private void applyTreeEffects(ConnectionObject connectionObject) {
        if (schemaToSelect == null && !refreshTree && !rebuildTree) {
            return;
        }
        String select = schemaToSelect;
        boolean rebuild = rebuildTree;
        boolean refresh = refreshTree;

        Platform.runLater(() -> {
            ConnectionObject current = DatabaseConnection.getInstance().getCurrentConnectionObject();
            CustomTree tree = App.getCustomTree();
            // the tree shows the current connection only
            if (tree == null || current == null || !current.getId().equals(connectionObject.getId())) {
                return;
            }
            if (rebuild) {
                tree.initialize();
            } else if (refresh) {
                tree.refreshSelectedDatabase();
            }
            if (select != null) {
                tree.selectDatabase(select);
            }
        });
    }

    // Guards against a second Confirm click while the first apply is still running in the background.
    private boolean applying = false;

    private void applyChanges(TabPane tabPane) {
        if (applying) {
            return;
        }

        ResultTable table = (ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent();
        // Snapshot on the FX thread; the modal blocks editing until the apply finishes.
        List<QueryData> queries = new ArrayList<>(table.getUpdateQueries());
        Set<QueryData> inserts = table.pendingInsertQueries();

        applying = true;
        applyButton.setDisable(true);
        revertButton.setDisable(true);

        Async.run(() -> {
            try {
                // may open the tab's connection, so not on the FX thread
                Connection connection = resolveConnection();
                Map<QueryData, Map<String, Object>> generatedKeys = ChangeApplier.apply(connection, queries, inserts);

                Platform.runLater(() -> {
                    applying = false;
                    List<ResultTable.InsertedRow> inserted = table.commitApplied(generatedKeys);
                    applyButton.setDisable(true);
                    revertButton.setDisable(true);
                    if (!inserted.isEmpty()) {
                        // pick up the values the database defaulted (timestamps, defaults, triggers)
                        Async.run(() -> table.reloadInserted(connection, inserted));
                    }

                    App.closeModal();
                    new CustomNotification("Changes Saved", "Your edits were committed to the database.", new FontIcon(MaterialDesignD.DATABASE_CHECK_OUTLINE)).showNotification();
                });
            } catch (SQLException e) {
                log.error("applyChanges failed", e);

                Platform.runLater(() -> {
                    applying = false;
                    applyButton.setDisable(false);
                    revertButton.setDisable(false);
                    new CustomNotification("Save Failed", e.getMessage(), new FontIcon(MaterialDesignD.DATABASE_ALERT_OUTLINE)).showNotification();
                });
            }
        });
    }

    // The tab's own connection, not whichever one happens to be current when the user confirms.
    private Connection resolveConnection() {
        DatabaseConnection databaseConnection = DatabaseConnection.getInstance();
        if (parentTab != null && parentTab.getConnectionObject() != null) {
            try {
                return parentTab.getSessionConnection();
            } catch (SQLException e) {
                log.warn("Could not open the tab's connection: {}", e.getMessage());
            }
        }
        return databaseConnection.getConnection();
    }

    public void revertChanges(TabPane tabPane) {
        ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).restoreOriginalData();
        revertButton.setDisable(true);
        applyButton.setDisable(true);
        App.closeModal();

        CustomNotification customNotification = new CustomNotification("Changes Reverted", "Your unsaved edits were discarded.", new FontIcon(MaterialDesignC.CHECKBOX_MARKED_CIRCLE_OUTLINE));
        customNotification.showNotification();
    }
}