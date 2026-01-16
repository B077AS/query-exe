package com.queryexe.components.results;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.QueryData;

public class ResultTable extends TableView<TableRowData> {

    private String tableName;
    private Set<String> primaryKeyColumns;
    private long executionTime;
    private List<QueryData> updateQueries;
    private ObservableList<TableRowData> backupData = FXCollections.observableArrayList();
    private Runnable onCellUpdate;
    private final int minColumnWidth = 150;
    private static final int MAX_CELL_LENGTH = 200;
    private Map<String, Integer> rowUpdateQueryIndex = new HashMap<>();

    public ResultTable(PreparedStatement preparedStatement, long executionTime, Runnable onCellUpdate) throws SQLException {
        this.updateQueries = new ArrayList<QueryData>();
        this.onCellUpdate = onCellUpdate;
        this.executionTime = executionTime;

        Styles.toggleStyleClass(this, Styles.BORDERED);
        this.getStyleClass().addAll("custom-table-view-dense", Tweaks.EDGE_TO_EDGE);
        this.setFocusTraversable(true);
        this.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        this.setFixedCellSize(25);
        this.setCache(true);
        this.setCacheHint(javafx.scene.CacheHint.SPEED);

        this.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                this.getParent().getParent().requestFocus();
            }
        });

        this.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        boolean hasPrimaryKey = false;

        ResultSet resultSet = preparedStatement.getResultSet();
        ResultSetMetaData metaData = resultSet.getMetaData();
        hasPrimaryKey = checkForPrimaryKey(DatabaseConnection.getInstance().getConnection(), metaData);

        this.setEditable(hasPrimaryKey);

        int columnCount = metaData.getColumnCount();

        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            String columnName = metaData.getColumnName(columnIndex);
            TableColumn<TableRowData, String> tableColumn = new TableColumn<>(columnName);

            tableColumn.setContextMenu(new ResultColumnContextMenu(tableColumn));
            tableColumn.setReorderable(false);
            tableColumn.setMinWidth(minColumnWidth);
            final int columnOffset = columnIndex - 1;

            int columnType = metaData.getColumnType(columnIndex);

            if (columnType == java.sql.Types.BLOB || columnType == java.sql.Types.LONGVARBINARY ||
                    columnType == java.sql.Types.VARBINARY || columnType == java.sql.Types.BINARY) {
                tableColumn.setEditable(false);
            } else {
                tableColumn.setEditable(true);
            }

            tableColumn.setCellFactory(col -> {
                TextFieldTableCell<TableRowData, String> cell = new TextFieldTableCell<>(
                        new StringConverter<String>() {
                            @Override
                            public String toString(String object) {
                                return object;
                            }

                            @Override
                            public String fromString(String string) {
                                return string;
                            }
                        }) {

                    private String fullValue;

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText(null);
                            setContextMenu(null);
                            setStyle("");
                            fullValue = null;
                        } else if (item == null) {
                            setText("[NULL]");
                            setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                            fullValue = null;
                            if (getContextMenu() == null && getTableView() != null && getIndex() >= 0) {
                                try {
                                    ResultCellsContextMenu contextMenu = new ResultCellsContextMenu(
                                            item,
                                            getTableView().getItems().get(getIndex()),
                                            this
                                    );
                                    setContextMenu(contextMenu);
                                } catch (Exception e) {
                                }
                            }
                        } else {
                            fullValue = item;
                            String displayText = item.length() > MAX_CELL_LENGTH
                                    ? item.substring(0, MAX_CELL_LENGTH) + "..."
                                    : item;
                            setText(displayText);
                            setStyle("");

                            if (getContextMenu() == null && getTableView() != null && getIndex() >= 0) {
                                try {
                                    ResultCellsContextMenu contextMenu = new ResultCellsContextMenu(
                                            item,
                                            getTableView().getItems().get(getIndex()),
                                            this
                                    );
                                    setContextMenu(contextMenu);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                    @Override
                    public void startEdit() {
                        if (!isEditable() || !getTableColumn().isEditable() || !getTableView().isEditable()) {
                            return;
                        }
                        super.startEdit();
                        setText(null);
                        setStyle("");
                    }

                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        String item = getItem();
                        if (item == null) {
                            setText("[NULL]");
                            setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                        } else {
                            String displayText = item.length() > MAX_CELL_LENGTH
                                    ? item.substring(0, MAX_CELL_LENGTH) + "..."
                                    : item;
                            setText(displayText);
                            setStyle("");
                        }
                    }
                };

                cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        if (cell.isEditing()) {
                            return;
                        }

                        boolean wasSelected = cell.getTableRow().isSelected();
                        int selectedCount = this.getSelectionModel().getSelectedItems().size();
                        int clickedRowIndex = cell.getTableRow().getIndex();

                        if (event.getClickCount() == 2) {
                            event.consume();
                            Platform.runLater(() -> {
                                if (this.getEditingCell() != null) {
                                    this.edit(-1, null);
                                }
                                cell.startEdit();
                                this.getSelectionModel().clearSelection();
                                this.getSelectionModel().select(clickedRowIndex);
                            });
                        } else if (event.getClickCount() == 1 && !event.isControlDown()) {
                            if (wasSelected && selectedCount == 1) {
                                event.consume();
                                Platform.runLater(() -> {
                                    if (this.getEditingCell() != null) {
                                        this.edit(-1, null);
                                    }
                                    this.getSelectionModel().clearSelection();
                                });
                            } else if (selectedCount > 1) {
                                event.consume();
                                Platform.runLater(() -> {
                                    if (this.getEditingCell() != null) {
                                        this.edit(-1, null);
                                    }
                                    this.getSelectionModel().clearSelection();
                                    this.getSelectionModel().select(clickedRowIndex);
                                });
                            }
                        }
                    }
                    this.requestFocus();
                });

                return cell;
            });

            tableColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getStringData().get(columnOffset)));
            tableColumn.setOnEditCommit(event -> {
                int row = event.getTablePosition().getRow();
                TableRowData rowData = event.getTableView().getItems().get(row);
                int column = event.getTablePosition().getColumn();

                String oldValue = event.getOldValue();
                String newValue = event.getNewValue();

                if (!Objects.equals(oldValue, newValue)) {
                    updateDatabaseRow(rowData, column, oldValue, newValue);
                }
            });

            this.getColumns().add(tableColumn);
        }

        ObservableList<TableRowData> tableData = FXCollections.observableArrayList();
        while (resultSet.next()) {
            ObservableList<Object> originalRow = FXCollections.observableArrayList();
            ObservableList<String> stringRow = FXCollections.observableArrayList();
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                int columnType = metaData.getColumnType(columnIndex);

                Object originalValue;
                String stringValue;
                switch (columnType) {
                    case java.sql.Types.BLOB:
                    case java.sql.Types.LONGVARBINARY:
                    case java.sql.Types.VARBINARY:
                    case java.sql.Types.BINARY:
                        byte[] binaryData = resultSet.getBytes(columnIndex);
                        originalValue = binaryData;

                        // Try to convert to UUID string if possible
                        if (binaryData != null && (binaryData.length == 16 || binaryData.length == 36)) {
                            try {

                                if (binaryData.length == 16) {
                                    UUID uuid = bytesToUUID(binaryData);
                                    stringValue = uuid.toString();
                                    originalValue = uuid;
                                }

                                else {
                                    String possibleUUID = new String(binaryData, java.nio.charset.StandardCharsets.UTF_8);
                                    if (possibleUUID.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                        stringValue = possibleUUID;
                                        originalValue = UUID.fromString(possibleUUID);
                                    } else {
                                        stringValue = String.format("[Data: %d bytes]", binaryData.length);
                                    }
                                }
                            } catch (Exception e) {
                                stringValue = String.format("[Data: %d bytes]", binaryData.length);
                            }
                        } else {
                            stringValue = (binaryData != null) ? String.format("[Data: %d bytes]", binaryData.length) : null;
                        }
                        break;
                    case java.sql.Types.BOOLEAN:
                        boolean boolValue = resultSet.getBoolean(columnIndex);
                        originalValue = boolValue;
                        stringValue = (resultSet.wasNull()) ? null : boolValue ? "1" : "0";
                        break;
                    default:
                        originalValue = resultSet.getObject(columnIndex);
                        stringValue = (originalValue != null) ? originalValue.toString() : null;
                }
                originalRow.add(originalValue);
                stringRow.add(stringValue);
            }
            tableData.add(new TableRowData(originalRow, stringRow));
            backupData.add(new TableRowData(FXCollections.observableArrayList(originalRow), FXCollections.observableArrayList(stringRow)));
        }

        this.setItems(tableData);
        resultSet.close();
        preparedStatement.close();

        this.setVisible(false);
        this.skinProperty().addListener((observable, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> {
                    ScrollBar horizontalScrollBar = findHorizontalScrollBar(this);
                    if (horizontalScrollBar != null) {
                        if (!horizontalScrollBar.isVisible()) {
                            this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
                        }
                        horizontalScrollBar.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
                            if (!isVisible) {
                                this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
                            }
                        });
                    }
                    this.setVisible(true);
                });
            }
        });

        addTableResizeListener();
    }

    private UUID bytesToUUID(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID must be 16 bytes");
        }

        long mostSigBits = 0;
        long leastSigBits = 0;

        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (bytes[i] & 0xff);
        }

        return new UUID(mostSigBits, leastSigBits);
    }


    private void addTableResizeListener() {
        this.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (newWidth.intValue() < this.getColumns().size() * minColumnWidth) {
                this.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            }
        });
    }

    private ScrollBar findHorizontalScrollBar(TableView<?> tableView) {
        return tableView.lookupAll(".scroll-bar").stream()
                .filter(node -> node instanceof ScrollBar)
                .map(node -> (ScrollBar) node)
                .filter(scrollBar -> scrollBar.getOrientation() == Orientation.HORIZONTAL)
                .findFirst()
                .orElse(null);
    }

    private boolean checkForPrimaryKey(Connection connection, ResultSetMetaData metaData) throws SQLException {
        Set<String> tableNames = new HashSet<>();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String tableName = metaData.getTableName(i);
            if (tableName != null && !tableName.isEmpty()) {
                tableNames.add(tableName);
            }
        }

        if (tableNames.size() > 1) {
            System.out.println("Multiple tables detected - likely a JOIN query");
            return false;
        }

        if (tableNames.isEmpty()) {
            System.out.println("No table names found in metadata");
            return false;
        }

        tableName = tableNames.iterator().next();

        DatabaseMetaData dbMetaData = connection.getMetaData();
        primaryKeyColumns = new HashSet<>();

        try (ResultSet primaryKeys = dbMetaData.getPrimaryKeys(connection.getCatalog(), null, tableName)) {
            while (primaryKeys.next()) {
                primaryKeyColumns.add(primaryKeys.getString("COLUMN_NAME").toUpperCase());
            }
        }

        if (primaryKeyColumns.isEmpty()) {
            System.out.println("No primary keys or unique indexes found for table: " + tableName);
            return false;
        }

        Set<String> foundColumns = new HashSet<>();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i).toUpperCase();
            if (primaryKeyColumns.contains(columnName)) {
                foundColumns.add(columnName);
            }
        }

        return foundColumns.size() == primaryKeyColumns.size();
    }

    private String getRowIdentifier(TableRowData rowData) {
        StringBuilder identifier = new StringBuilder();
        for (String primaryKeyColumn : primaryKeyColumns) {
            int pkIndex = getPrimaryKeyColumnIndex(rowData, primaryKeyColumn);
            if (pkIndex >= 0) {
                Object value = rowData.getOriginalData().get(pkIndex);
                identifier.append(primaryKeyColumn).append(":").append(value).append("|");
            }
        }
        return identifier.toString();
    }

    public void updateDatabaseRow(TableRowData rowData, int updatedColumnIndex, String oldValue, String newValue) {
        try {
            Object typedValue = convertToOriginalType(rowData, updatedColumnIndex, newValue);

            rowData.getStringData().set(updatedColumnIndex, newValue);
            rowData.getOriginalData().set(updatedColumnIndex, typedValue);

            if (rowData.isNewRow()) {
                int queryIndex = rowData.getQueryIndex();
                QueryData insertQuery = updateQueries.get(queryIndex);
                insertQuery.getParameters().set(updatedColumnIndex, newValue.isEmpty() ? null : typedValue);
            } else {
                String rowIdentifier = getRowIdentifier(rowData);

                if (rowUpdateQueryIndex.containsKey(rowIdentifier)) {
                    int queryIndex = rowUpdateQueryIndex.get(rowIdentifier);
                    QueryData existingQuery = updateQueries.get(queryIndex);

                    String columnName = this.getColumns().get(updatedColumnIndex).getText();
                    String currentQuery = existingQuery.getQuery();
                    int setIndex = currentQuery.indexOf("SET ") + 4;
                    int whereIndex = currentQuery.indexOf(" WHERE ");
                    String setClause = currentQuery.substring(setIndex, whereIndex);
                    String whereClause = currentQuery.substring(whereIndex);

                    if (!setClause.contains(columnName + " = ?")) {
                        setClause += ", " + columnName + " = ?";

                        String updatedQuery = String.format("UPDATE %s SET %s%s", tableName, setClause, whereClause);
                        existingQuery.setQuery(updatedQuery);

                        List<Object> params = existingQuery.getParameters();
                        int whereParamIndex = params.size() - primaryKeyColumns.size();
                        params.add(whereParamIndex, typedValue);
                    } else {
                        String[] setColumns = setClause.split(",");
                        int paramIndex = 0;
                        for (String setColumn : setColumns) {
                            String colName = setColumn.trim().split(" = ")[0];
                            if (colName.equals(columnName)) {
                                existingQuery.getParameters().set(paramIndex, typedValue);
                                break;
                            }
                            paramIndex++;
                        }
                    }
                } else {
                    Map<String, Object> primaryKeyValues = new HashMap<>();
                    for (String primaryKeyColumn : primaryKeyColumns) {
                        int pkIndex = getPrimaryKeyColumnIndex(rowData, primaryKeyColumn);
                        if (pkIndex == updatedColumnIndex) {
                            Object oldTypedValue = convertToOriginalType(rowData, pkIndex, oldValue);
                            primaryKeyValues.put(primaryKeyColumn, oldTypedValue);
                        } else {
                            primaryKeyValues.put(primaryKeyColumn, rowData.getOriginalData().get(pkIndex));
                        }
                    }

                    if (!primaryKeyColumns.isEmpty()) {
                        String columnName = this.getColumns().get(updatedColumnIndex).getText();

                        StringBuilder whereClause = new StringBuilder();
                        List<Object> parameters = new ArrayList<>();

                        parameters.add(typedValue);

                        boolean isFirst = true;
                        for (String primaryKeyColumn : primaryKeyColumns) {
                            if (!isFirst) {
                                whereClause.append(" AND ");
                            }
                            whereClause.append(primaryKeyColumn).append(" = ?");
                            parameters.add(primaryKeyValues.get(primaryKeyColumn));
                            isFirst = false;
                        }

                        String updateQuery = String.format("UPDATE %s SET %s = ? WHERE %s",
                                tableName,
                                columnName,
                                whereClause.toString());

                        QueryData queryInfo = new QueryData(updateQuery, parameters);
                        updateQueries.add(queryInfo);
                        rowUpdateQueryIndex.put(rowIdentifier, updateQueries.size() - 1);
                    }
                }
            }

            if (onCellUpdate != null) {
                onCellUpdate.run();
            }
            this.refresh();
        } catch (Exception e) {
            e.printStackTrace();
            rowData.getStringData().set(updatedColumnIndex, oldValue);
            this.refresh();
        }
    }

    private Object convertToOriginalType(TableRowData rowData, int columnIndex, String stringValue) {
        if (stringValue == null || stringValue.isEmpty()) {
            return null;
        }

        if (columnIndex < backupData.get(0).getOriginalData().size()) {
            Object originalValue = null;

            int rowIndex = this.getItems().indexOf(rowData);
            if (rowIndex >= 0 && rowIndex < backupData.size()) {
                originalValue = backupData.get(rowIndex).getOriginalData().get(columnIndex);
            }

            if (originalValue == null && rowData.getOriginalData().get(columnIndex) != null) {
                originalValue = rowData.getOriginalData().get(columnIndex);
            }

            if (originalValue != null) {
                try {
                    if (originalValue instanceof UUID) {
                        return UUID.fromString(stringValue);
                    } else if (originalValue instanceof Integer) {
                        return Integer.parseInt(stringValue);
                    } else if (originalValue instanceof Long) {
                        return Long.parseLong(stringValue);
                    } else if (originalValue instanceof Double) {
                        return Double.parseDouble(stringValue);
                    } else if (originalValue instanceof Float) {
                        return Float.parseFloat(stringValue);
                    } else if (originalValue instanceof Short) {
                        return Short.parseShort(stringValue);
                    } else if (originalValue instanceof Byte) {
                        return Byte.parseByte(stringValue);
                    } else if (originalValue instanceof Boolean) {
                        return Boolean.parseBoolean(stringValue) || stringValue.equals("1");
                    } else if (originalValue instanceof java.sql.Date) {
                        return java.sql.Date.valueOf(stringValue);
                    } else if (originalValue instanceof java.sql.Time) {
                        return java.sql.Time.valueOf(stringValue);
                    } else if (originalValue instanceof java.sql.Timestamp) {
                        return java.sql.Timestamp.valueOf(stringValue);
                    } else if (originalValue instanceof java.math.BigDecimal) {
                        return new java.math.BigDecimal(stringValue);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to convert value to original type: " + e.getMessage());
                }
            }
        }
        return stringValue;
    }

    public void addDatabaseRow() {
        List<Object> newOriginalData = new ArrayList<>();
        List<String> newStringData = new ArrayList<>();

        for (int i = 0; i < this.getColumns().size(); i++) {
            newOriginalData.add(null);
            newStringData.add("");
        }

        TableRowData newRow = new TableRowData(FXCollections.observableArrayList(newOriginalData), FXCollections.observableArrayList(newStringData));
        newRow.setNewRow(true);

        StringBuilder columnsClause = new StringBuilder();
        StringBuilder valuesClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        for (int i = 0; i < this.getColumns().size(); i++) {
            if (i > 0) {
                columnsClause.append(", ");
                valuesClause.append(", ");
            }
            columnsClause.append(this.getColumns().get(i).getText());
            valuesClause.append("?");
            parameters.add(null);
        }

        String insertQuery = String.format(
                "INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                columnsClause.toString(),
                valuesClause.toString()
        );

        QueryData queryInfo = new QueryData(insertQuery, parameters);
        updateQueries.add(queryInfo);

        newRow.setQueryIndex(updateQueries.size() - 1);

        this.getItems().add(newRow);

        this.scrollTo(newRow);
        this.getSelectionModel().clearSelection();
        this.getSelectionModel().select(newRow);

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    public void deleteDatabaseRow(int rowIndex) {
        TableRowData rowData = this.getItems().get(rowIndex);
        Map<String, Object> primaryKeyValues = new HashMap<>();
        for (String primaryKeyColumn : primaryKeyColumns) {
            int pkIndex = getPrimaryKeyColumnIndex(rowData, primaryKeyColumn);
            if (pkIndex >= 0) {
                primaryKeyValues.put(primaryKeyColumn, rowData.getOriginalData().get(pkIndex));
            }
        }

        StringBuilder whereClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        boolean isFirst = true;
        for (Map.Entry<String, Object> entry : primaryKeyValues.entrySet()) {
            if (!isFirst) {
                whereClause.append(" AND ");
            }
            whereClause.append(entry.getKey()).append(" = ?");
            parameters.add(entry.getValue());
            isFirst = false;
        }

        String deleteQuery = String.format("DELETE FROM %s WHERE %s", tableName, whereClause.toString());

        QueryData queryInfo = new QueryData(deleteQuery, parameters);
        updateQueries.add(queryInfo);

        this.getItems().remove(rowIndex);

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    public void deleteDatabaseRows(ObservableList<Integer> rows) {
        List<Integer> sortedRows = new ArrayList<>(rows);
        sortedRows.sort(Collections.reverseOrder());

        for (Integer row : sortedRows) {
            TableRowData rowData = this.getItems().get(row);
            Map<String, Object> primaryKeyValues = new HashMap<>();
            for (String primaryKeyColumn : primaryKeyColumns) {
                int pkIndex = getPrimaryKeyColumnIndex(rowData, primaryKeyColumn);
                if (pkIndex >= 0) {
                    primaryKeyValues.put(primaryKeyColumn, rowData.getOriginalData().get(pkIndex));
                }
            }

            StringBuilder whereClause = new StringBuilder();
            List<Object> parameters = new ArrayList<>();

            boolean isFirst = true;
            for (Map.Entry<String, Object> entry : primaryKeyValues.entrySet()) {
                if (!isFirst) {
                    whereClause.append(" AND ");
                }
                whereClause.append(entry.getKey()).append(" = ?");
                parameters.add(entry.getValue());
                isFirst = false;
            }

            String deleteQuery = String.format("DELETE FROM %s WHERE %s", tableName, whereClause.toString());

            QueryData queryInfo = new QueryData(deleteQuery, parameters);
            updateQueries.add(queryInfo);

            this.getItems().remove(row.intValue());
        }

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    private int getPrimaryKeyColumnIndex(TableRowData rowData, String primaryKeyColumnName) {
        for (TableColumn<TableRowData, ?> column : this.getColumns()) {
            if (column.getText().equalsIgnoreCase(primaryKeyColumnName)) {
                return this.getColumns().indexOf(column);
            }
        }
        return -1;
    }

    public void restoreOriginalData() {
        Platform.runLater(() -> {
            ObservableList<TableRowData> restoredData = FXCollections.observableArrayList();

            for (TableRowData backupRow : backupData) {
                ObservableList<Object> originalDataCopy = FXCollections.observableArrayList(backupRow.getOriginalData());
                ObservableList<String> stringDataCopy = FXCollections.observableArrayList(backupRow.getStringData());
                TableRowData rowCopy = new TableRowData(originalDataCopy, stringDataCopy);
                restoredData.add(rowCopy);
            }
            this.setItems(restoredData);

            this.updateQueries.clear();
            this.rowUpdateQueryIndex.clear();
            this.refresh();
        });
    }

    public void copySelectedRows() {
        StringBuilder formattedData = new StringBuilder();

        this.getSelectionModel().getSelectedItems().forEach(rowData -> {
            this.getColumns().forEach(column -> {
                Object cellData = column.getCellData(rowData);

                String cellValue = cellData != null ? cellData.toString() : "";

                if (cellValue.contains(";") || cellValue.contains("\"") || cellValue.contains("\n")) {
                    cellValue = "\"" + cellValue.replace("\"", "\"\"") + "\"";
                }

                formattedData.append(cellValue).append(";");
            });
            if (!this.getColumns().isEmpty()) {
                formattedData.setLength(formattedData.length() - 1);
            }
            formattedData.append("\n");
        });

        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(formattedData.toString());
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    public void clearUpdateTracking() {
        this.updateQueries.clear();
        this.rowUpdateQueryIndex.clear();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Set<String> getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public List<QueryData> getUpdateQueries() {
        return updateQueries;
    }
}