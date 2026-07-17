package com.queryexe.components.results;

import lombok.extern.slf4j.Slf4j;

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
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.QueryData;

@Slf4j
public class ResultTable extends TableView<TableRowData> {

    private String tableName;
    private Set<String> primaryKeyColumns;
    private long executionTime;
    private List<QueryData> updateQueries;
    private ObservableList<TableRowData> backupData = FXCollections.observableArrayList();
    private Runnable onCellUpdate;
    private final int minColumnWidth = 150;
    private static final int MAX_CELL_LENGTH = 200;
    private Map<String, QueryData> rowUpdateQueries = new HashMap<>();
    private List<Integer> columnSqlTypes = new ArrayList<>();

    public ResultTable(PreparedStatement preparedStatement, long executionTime) throws SQLException {
        this.updateQueries = new ArrayList<QueryData>();
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
            columnSqlTypes.add(metaData.getColumnType(columnIndex));

            if (columnType == java.sql.Types.BLOB || columnType == java.sql.Types.LONGVARBINARY ||
                    columnType == java.sql.Types.VARBINARY || columnType == java.sql.Types.BINARY) {
                String columnClassName = metaData.getColumnClassName(columnIndex);
                String columnTypeName = metaData.getColumnTypeName(columnIndex);

                tableColumn.setEditable("java.util.UUID".equals(columnClassName) || columnTypeName.toUpperCase().contains("UUID"));
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
                    private TextField editorField;
                    private boolean escapePressed;
                    // Captured at startEdit: when a cell is recycled while editing
                    // (scrolling), updateItem swaps in another row's data before
                    // cancelEdit runs, so reading them there would hit the wrong row.
                    private TableRowData editingRowData;
                    private String editingOldValue;

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
                                    ResultCellsContextMenu contextMenu = new ResultCellsContextMenu(item, this);
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
                                    ResultCellsContextMenu contextMenu = new ResultCellsContextMenu(item, this);
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

                        escapePressed = false;
                        if (isEditing()) {
                            editingRowData = getTableRow() != null ? getTableRow().getItem() : null;
                            editingOldValue = getItem();
                        }
                        if (isEditing() && getGraphic() instanceof TextField textField && textField != editorField) {
                            // TextFieldTableCell reuses one text field per cell, so the
                            // listeners are attached exactly once.
                            editorField = textField;
                            textField.addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
                                if (keyEvent.getCode() == KeyCode.ESCAPE) {
                                    escapePressed = true;
                                }
                            });
                            // Close the edit when the editor loses focus (e.g. a click
                            // outside the table). Routed through cancelEdit rather than
                            // commitEdit: cancelEdit reliably removes the text field, and
                            // our override below still logs the typed value.
                            textField.focusedProperty().addListener((obs, hadFocus, hasFocus) -> {
                                if (!hasFocus && isEditing()) {
                                    cancelEdit();
                                }
                            });
                        }
                    }

                    @Override
                    public void cancelEdit() {
                        // An edit interrupted by anything other than ESC (clicking another
                        // cell, scrolling, ...) still counts: log the typed value as a
                        // pending change instead of discarding it.
                        boolean logPendingEdit = !escapePressed && editorField != null && isEditing();
                        String editedValue = logPendingEdit ? editorField.getText() : null;
                        String previousValue = editingOldValue;
                        TableRowData rowData = editingRowData;
                        editingRowData = null;
                        editingOldValue = null;

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

                        if (logPendingEdit && rowData != null && !Objects.equals(previousValue, editedValue)
                                && !isUnchangedNull(previousValue, editedValue)) {
                            updateDatabaseRow(rowData, columnOffset, previousValue, editedValue);
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

    private static boolean isUnchangedNull(String previousValue, String editedValue) {
        return previousValue == null && (editedValue == null || editedValue.isEmpty());
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
            log.debug("Multiple tables detected - likely a JOIN query");
            return false;
        }

        if (tableNames.isEmpty()) {
            log.debug("No table names found in metadata");
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
            log.debug("No primary keys or unique indexes found for table: {}", tableName);
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
        Object previousOriginal = rowData.getOriginalData().get(updatedColumnIndex);
        try {
            Object typedValue = convertToOriginalType(updatedColumnIndex, newValue);

            rowData.getStringData().set(updatedColumnIndex, newValue);
            rowData.getOriginalData().set(updatedColumnIndex, typedValue);

            if (rowData.isNewRow()) {
                // typedValue is already null for null/empty input, which also avoids
                // the NPE the old newValue.isEmpty() check caused on "Set NULL".
                QueryData insertQuery = rowData.getPendingInsert();
                insertQuery.getParameters().set(updatedColumnIndex, typedValue);
            } else {
                String rowIdentifier = getRowIdentifier(rowData);
                QueryData existingQuery = rowUpdateQueries.get(rowIdentifier);

                if (existingQuery != null) {
                    String columnName = this.getColumns().get(updatedColumnIndex).getText();
                    String currentQuery = existingQuery.getQuery();
                    int setIndex = currentQuery.indexOf("SET ") + 4;
                    int whereIndex = currentQuery.indexOf(" WHERE ");
                    String setClause = currentQuery.substring(setIndex, whereIndex);
                    String whereClause = currentQuery.substring(whereIndex);

                    // Exact match against the SET columns: a contains() check would
                    // confuse columns whose names are suffixes of one another
                    // (e.g. "name" matches inside "username = ?").
                    String[] setColumns = setClause.split(",");
                    int existingParamIndex = -1;
                    for (int i = 0; i < setColumns.length; i++) {
                        String colName = setColumns[i].trim().split(" = ")[0];
                        if (colName.equals(columnName)) {
                            existingParamIndex = i;
                            break;
                        }
                    }

                    if (existingParamIndex >= 0) {
                        existingQuery.getParameters().set(existingParamIndex, typedValue);
                    } else {
                        setClause += ", " + columnName + " = ?";

                        String updatedQuery = String.format("UPDATE %s SET %s%s", tableName, setClause, whereClause);
                        existingQuery.setQuery(updatedQuery);

                        List<Object> params = existingQuery.getParameters();
                        int whereParamIndex = params.size() - primaryKeyColumns.size();
                        params.add(whereParamIndex, typedValue);
                    }
                } else {
                    Map<String, Object> primaryKeyValues = new HashMap<>();
                    for (String primaryKeyColumn : primaryKeyColumns) {
                        int pkIndex = getPrimaryKeyColumnIndex(rowData, primaryKeyColumn);
                        if (pkIndex == updatedColumnIndex) {
                            Object oldTypedValue = convertToOriginalType(pkIndex, oldValue);
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
                        rowUpdateQueries.put(rowIdentifier, queryInfo);
                    }
                }
            }

            if (onCellUpdate != null) {
                onCellUpdate.run();
            }
            this.refresh();
        } catch (Exception e) {
            log.error("updateDatabaseRow failed", e);
            rowData.getStringData().set(updatedColumnIndex, oldValue);
            rowData.getOriginalData().set(updatedColumnIndex, previousOriginal);
            this.refresh();
        }
    }

    private Object convertToOriginalType(int columnIndex, String stringValue) {
        if (stringValue == null || stringValue.isEmpty()) {
            return null;
        }

        if (columnIndex >= columnSqlTypes.size()) {
            return stringValue;
        }

        try {
            switch (columnSqlTypes.get(columnIndex)) {
                case java.sql.Types.BOOLEAN:
                case java.sql.Types.BIT: {
                    String lower = stringValue.trim().toLowerCase();
                    return lower.equals("true") || lower.equals("1") || lower.equals("yes");
                }
                case java.sql.Types.INTEGER:
                case java.sql.Types.SMALLINT:
                    return Integer.parseInt(stringValue);
                case java.sql.Types.BIGINT:
                    return Long.parseLong(stringValue);
                case java.sql.Types.FLOAT:
                case java.sql.Types.REAL:
                    return Float.parseFloat(stringValue);
                case java.sql.Types.DOUBLE:
                    return Double.parseDouble(stringValue);
                case java.sql.Types.NUMERIC:
                case java.sql.Types.DECIMAL:
                    return new java.math.BigDecimal(stringValue);
                case java.sql.Types.DATE:
                    return java.sql.Date.valueOf(stringValue);
                case java.sql.Types.TIME:
                    return java.sql.Time.valueOf(stringValue);
                case java.sql.Types.TIMESTAMP:
                    return java.sql.Timestamp.valueOf(stringValue);
                case java.sql.Types.BINARY:
                case java.sql.Types.VARBINARY:
                case java.sql.Types.BLOB:
                    return UUID.fromString(stringValue);
                default:
                    return stringValue;
            }
        } catch (Exception e) {
            log.warn("Type conversion failed for column {}: {}", columnIndex, e.getMessage());
            return stringValue;
        }
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
        newRow.setPendingInsert(queryInfo);

        this.getItems().add(newRow);

        this.scrollTo(newRow);
        this.getSelectionModel().clearSelection();
        this.getSelectionModel().select(newRow);

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    public void duplicateDatabaseRow(TableRowData sourceRow) {
        List<Object> newOriginalData = new ArrayList<>(sourceRow.getOriginalData());
        List<String> newStringData = new ArrayList<>(sourceRow.getStringData());

        for (int i = 0; i < this.getColumns().size(); i++) {
            String colName = this.getColumns().get(i).getText().toUpperCase();
            if (primaryKeyColumns.contains(colName)) {
                newOriginalData.set(i, null);
                newStringData.set(i, "");
            }
        }

        TableRowData newRow = new TableRowData(
                FXCollections.observableArrayList(newOriginalData),
                FXCollections.observableArrayList(newStringData)
        );
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
            parameters.add(newOriginalData.get(i));
        }

        String insertQuery = String.format(
                "INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                columnsClause.toString(),
                valuesClause.toString()
        );

        QueryData queryInfo = new QueryData(insertQuery, parameters);
        updateQueries.add(queryInfo);
        newRow.setPendingInsert(queryInfo);

        this.getItems().add(newRow);
        this.scrollTo(newRow);
        this.getSelectionModel().clearSelection();
        this.getSelectionModel().select(newRow);

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    public void deleteDatabaseRow(int rowIndex) {
        queueRowDeletion(this.getItems().get(rowIndex));
        this.getItems().remove(rowIndex);

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    public void deleteDatabaseRows(ObservableList<Integer> rows) {
        List<Integer> sortedRows = new ArrayList<>(rows);
        sortedRows.sort(Collections.reverseOrder());

        for (Integer row : sortedRows) {
            queueRowDeletion(this.getItems().get(row));
            this.getItems().remove(row.intValue());
        }

        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
    }

    private void queueRowDeletion(TableRowData rowData) {
        if (rowData.isNewRow()) {
            // The row never reached the database: dropping its pending INSERT is
            // enough. Identity comparison, because two duplicated rows can hold
            // equal-by-value insert queries.
            QueryData pendingInsert = rowData.getPendingInsert();
            updateQueries.removeIf(queryData -> queryData == pendingInsert);
            return;
        }

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
            this.rowUpdateQueries.clear();
            this.refresh();
        });
    }

    public void updateBackupData() {
        Platform.runLater(() -> {
            backupData.clear();

            for (TableRowData currentRow : this.getItems()) {
                ObservableList<Object> originalDataCopy = FXCollections.observableArrayList(currentRow.getOriginalData());
                ObservableList<String> stringDataCopy = FXCollections.observableArrayList(currentRow.getStringData());
                TableRowData backupRowCopy = new TableRowData(originalDataCopy, stringDataCopy);
                backupData.add(backupRowCopy);
            }
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
        this.rowUpdateQueries.clear();
    }

    public boolean hasPendingChanges() {
        return !updateQueries.isEmpty();
    }

    public void setOnCellUpdate(Runnable onCellUpdate) {
        this.onCellUpdate = onCellUpdate;
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