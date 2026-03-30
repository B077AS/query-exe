package com.queryexe.components.results;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.queryexe.components.modals.QueriesSummaryModal;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.QueryData;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.queryexe.App;
import com.queryexe.service.ExportUtils;

public class ResultBox extends VBox {

    private Button revertButton;
    private Button applyButton;
    private CustomTab<?> parentTab;

    public ResultBox(String query, CustomTab<?> parentTab) {

        if (DatabaseConnection.getInstance().getConnectionObject() == null) {
            try {
                DatabaseConnection.getInstance().initialize(parentTab.getConnectionObject());
            } catch (SQLException e) {
            }
        }

        this.parentTab = parentTab;

        TabPane tabPane = new TabPane();
        tabPane.setTabDragPolicy(TabDragPolicy.REORDER);

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
            QueriesSummaryModal queriesSummaryModal = new QueriesSummaryModal(
                    ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getUpdateQueries(),
                    () -> {
                        applyChanges(tabPane);
                    }
            );
            App.showModal(queriesSummaryModal);
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
                CustomNotification errorRemoveNotification = new CustomNotification("Error: no row selected", new FontIcon(MaterialDesignT.TABLE_CANCEL));
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
                        rowsLabel.setText("Rows: " + selectedTable.getItems().size());
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

        String[] queries = query.split(";");
        boolean hasErrors = false;

        for (String singleQuery : queries) {
            if (!singleQuery.trim().isEmpty()) {

                if (!DatabaseConnection.getInstance().getConnectionObject().getDbType().equals(ConnectionTypes.PostgreSQL.toString())) {
                    Pattern usePattern = Pattern.compile("^\\s*USE\\s+([^;\\s]+)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
                    Matcher useMatcher = usePattern.matcher(singleQuery.trim());
                    if (useMatcher.find()) {
                        String newDatabase = useMatcher.group(1);
                        newDatabase = newDatabase.replaceAll("[`'\"]", "");
                        App.getDatabaseTree().selectDatabase(newDatabase);
                    }

                    if (DatabaseConnection.getInstance().getConnectionObject().getDbType().equals(ConnectionTypes.H2.toString())) {
                        Pattern h2Pattern = Pattern.compile("^\\s*SET\\s+SCHEMA\\s+\"([^\"]+)\"\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
                        Matcher h2Matcher = h2Pattern.matcher(singleQuery.trim());
                        if (h2Matcher.find()) {
                            String newDatabase = h2Matcher.group(1);
                            App.getDatabaseTree().selectDatabase(newDatabase);
                        }
                    }
                } else {
                    Pattern pattern = Pattern.compile("^\\s*SET\\s+search_path\\s+TO\\s+(['\"]?\\$?\\w+['\"]?\\s*(,\\s*['\"]?\\w+['\"]?)*\\s*)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(singleQuery.trim());
                    if (matcher.find()) {
                        String newDatabase = matcher.group(1);
                        newDatabase = newDatabase.replaceAll("[`'\"]", "");

                        if (newDatabase.split(",").length > 1) {
                            newDatabase = newDatabase.split(",")[1].trim();
                        }
                        App.getDatabaseTree().selectDatabase(newDatabase);
                    }
                }

                PreparedStatement preparedStatement = null;
                ResultTable customTableView = null;

                try {
                    Connection connection = DatabaseConnection.getInstance().getConnection();
                    long startTime = System.currentTimeMillis();
                    preparedStatement = connection.prepareStatement(singleQuery);

                    if (parentTab != null) {
                        this.parentTab.setCurrentStatement(preparedStatement);
                    }

                    boolean hasResultSet = preparedStatement.execute();
                    long endTime = System.currentTimeMillis();
                    long executionTime = endTime - startTime;

                    if (hasResultSet) {
                        customTableView = new ResultTable(preparedStatement, executionTime, () -> {
                            applyButton.setDisable(false);
                            revertButton.setDisable(false);
                        });

                        if (customTableView != null) {
                            CustomTab<ResultTable> tableTab = new CustomTab<>(customTableView, tabPane);

                            tableTab.getIcon().setOnMouseClicked(event -> {
                                if (applyButton.isDisabled()) {
                                    tabPane.getTabs().remove(tableTab);
                                } else {
                                    ConfirmationModal confirmationModal = new ConfirmationModal(
                                            "Unsaved Changes",
                                            "Do you want to apply the changes?",
                                            new FontIcon(MaterialDesignA.ALERT_OUTLINE),
                                            () -> {
                                                QueriesSummaryModal queriesSummaryModal = new QueriesSummaryModal(
                                                        ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getUpdateQueries(),
                                                        () -> {
                                                            applyChanges(tabPane);
                                                        }
                                                );
                                                App.showModal(queriesSummaryModal);
                                            }
                                    );
                                    App.showModal(confirmationModal);
                                }
                            });
                            tabPane.getTabs().add(tableTab);
                        }
                    } else {
                        int updateCount = preparedStatement.getUpdateCount();
                        SimpleResultBox simpleResultBox = new SimpleResultBox(updateCount, singleQuery);

                        CustomTab<SimpleResultBox> simpleQueryTab = new CustomTab<>(simpleResultBox, tabPane);
                        tabPane.getTabs().add(simpleQueryTab);
                        preparedStatement.close();
                    }
                } catch (SQLException e) {
                    //e.printStackTrace();
                    hasErrors = true;

                    try {
                        if (preparedStatement != null && !preparedStatement.isClosed()) {
                            preparedStatement.close();
                        }
                    } catch (SQLException closeException) {
                        closeException.printStackTrace();
                    }

                    ResultErrorBox errorBox = new ResultErrorBox(singleQuery, e);

                    CustomTab<HBox> errorTab = new CustomTab<HBox>(errorBox, tabPane);
                    errorTab.setErrorHeader();

                    tabPane.getTabs().add(errorTab);
                    break;
                }
            }
        }
        if (hasErrors) {
            CustomNotification errorNotification = new CustomNotification("Some queries failed to execute.", new FontIcon(MaterialDesignC.CLOSE_CIRCLE));
            errorNotification.showNotification();
        }

        this.getChildren().addAll(tableHeader, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
    }

    private void applyChanges(TabPane tabPane) {
        List<QueryData> updateQueries = ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).getUpdateQueries();

        Connection connection = null;
        try {
            connection = DatabaseConnection.getInstance().getConnection();
            connection.setAutoCommit(false);

            for (QueryData queryInfo : updateQueries) {
                try (PreparedStatement stmt = connection.prepareStatement(queryInfo.getQuery())) {
                    for (int i = 0; i < queryInfo.getParameters().size(); i++) {
                        Object param = queryInfo.getParameters().get(i);
                        if (param == null) {
                            stmt.setNull(i + 1, Types.NULL);
                        } else if (isUuidParameter(param)) {
                            if (param instanceof String) {
                                stmt.setObject(i + 1, java.util.UUID.fromString((String) param));
                            } else {
                                stmt.setObject(i + 1, param);
                            }
                        } else if (param instanceof Boolean) {
                            stmt.setBoolean(i + 1, (Boolean) param);
                        } else {
                            stmt.setObject(i + 1, param);
                        }
                    }
                    stmt.executeUpdate();
                }
            }

            connection.commit();
            connection.setAutoCommit(true);

            ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).updateBackupData();

            applyButton.setDisable(true);
            revertButton.setDisable(true);

            ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).clearUpdateTracking();

            App.closeModal();
            CustomNotification customNotification = new CustomNotification("Changes saved", new FontIcon(MaterialDesignD.DATABASE_CHECK_OUTLINE));
            customNotification.showNotification();

        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();

            CustomNotification customNotification = new CustomNotification("Error Saving Changes " + e.getMessage(), new FontIcon(MaterialDesignD.DATABASE_ALERT_OUTLINE));
            customNotification.showNotification();
        }
    }

    private boolean isUuidParameter(Object param) {
        if (param == null) {
            return false;
        }

        if (param instanceof java.util.UUID) {
            return true;
        }

        if (param instanceof String) {
            String str = (String) param;
            return str.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        }

        return false;
    }

    public void revertChanges(TabPane tabPane) {
        ((ResultTable) tabPane.getSelectionModel().getSelectedItem().getContent()).restoreOriginalData();
        revertButton.setDisable(true);
        applyButton.setDisable(true);
        App.closeModal();

        CustomNotification customNotification = new CustomNotification("Changes reverted successfully.", new FontIcon(MaterialDesignC.CHECKBOX_MARKED_CIRCLE_OUTLINE));
        customNotification.showNotification();
    }
}