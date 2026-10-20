package com.queryexe.components.results;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.util.StringConverter;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.QueryData;
import com.queryexe.components.extra.CustomNotification;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;

@Slf4j
public class ResultTable extends TableView<TableRowData> {

    @Getter
    @Setter
    private String tableName;
    // Quoted, schema/catalog-qualified target taken from the result-set metadata, used in every generated statement.
    private String qualifiedTableName;
    // Upper-cased PK column names (for case-insensitive lookups) mapped to their real names, in KEY_SEQ order.
    @Getter
    private Set<String> primaryKeyColumns = new LinkedHashSet<>();
    private Map<String, String> primaryKeyActualNames = new LinkedHashMap<>();
    // Underlying (non-aliased) column names per grid column, used when generating SQL.
    private final List<String> columnNames = new ArrayList<>();
    // True when the result hit the row limit set on the statement, i.e. more rows exist than are shown.
    @Getter
    private boolean truncated;
    private com.queryexe.model.connections.ConnectionObject connectionObject;
    @Getter
    private long executionTime;
    @Getter
    private List<QueryData> updateQueries;
    // The rows as last loaded or applied, in order (references only). A row that is edited first saves a copy of
    // its values in loadedValues, so Revert needs no second copy of the whole result set.
    private List<TableRowData> loadedRows = new ArrayList<>();
    private final Map<TableRowData, LoadedValues> loadedValues = new IdentityHashMap<>();

    private static final class LoadedValues {
        final List<Object> original;
        final List<String> strings;

        LoadedValues(TableRowData row) {
            this.original = new ArrayList<>(row.getOriginalData());
            this.strings = new ArrayList<>(row.getStringData());
        }
    }
    // Grid columns that map to a real table column (expression columns have nothing to read back).
    private final List<Integer> tableColumnIndexes = new ArrayList<>();
    @Setter
    private Runnable onCellUpdate;
    private final int minColumnWidth = 150;
    private static final int MAX_CELL_LENGTH = 200;
    // Pending edits per existing row (identity-keyed: TableRowData's value equality changes as cells are edited).
    private final Map<TableRowData, RowEdit> rowEdits = new IdentityHashMap<>();

    private static final class RowEdit {
        // Primary key values as they are in the database, captured before the first edit of the row.
        final Map<String, Object> primaryKeyValues;
        final Map<Integer, Object> changes = new LinkedHashMap<>();
        QueryData query;

        RowEdit(Map<String, Object> primaryKeyValues) {
            this.primaryKeyValues = primaryKeyValues;
        }
    }
    private List<Integer> columnSqlTypes = new ArrayList<>();
    private final List<String> columnTypeNames = new ArrayList<>();
    private final javafx.event.EventHandler<MouseEvent> outsideEditClickFilter = event -> {
        TablePosition<TableRowData, ?> editingCell = this.getEditingCell();
        if (editingCell == null) {
            return;
        }

        Node target = (Node) event.getTarget();
        while (target != null) {
            if (target instanceof TableCell<?, ?> cell
                    && cell.getTableView() == this
                    && cell.getIndex() == editingCell.getRow()
                    && this.getColumns().indexOf(cell.getTableColumn()) == editingCell.getColumn()) {
                return;
            }
            target = target.getParent();
        }

        Platform.runLater(this::endEditCleanly);
    };

    // A click on another node in the same window is caught by outsideEditClickFilter
    // above, but a click on the taskbar, the desktop, or another application never
    // reaches our Scene at all — only the OS-level window focus flips. Catch that
    // case separately.
    private final ChangeListener<Boolean> windowFocusListener = (obs, wasFocused, isFocused) -> {
        if (!isFocused && this.getEditingCell() != null) {
            Platform.runLater(this::endEditCleanly);
        }
    };

    private void endEditCleanly() {
        if (this.getEditingCell() == null) {
            return;
        }
        this.requestFocus();
        if (this.getEditingCell() != null) {
            this.edit(-1, null);
        }
    }

    private final ChangeListener<Window> windowChangeListener = (obs, oldWindow, newWindow) -> {
        if (oldWindow != null) {
            oldWindow.focusedProperty().removeListener(windowFocusListener);
        }
        if (newWindow != null) {
            newWindow.focusedProperty().addListener(windowFocusListener);
        }
    };

    public ResultTable(Statement statement, long executionTime) throws SQLException {
        this(statement, executionTime, 0);
    }

    /**
     * @param maxRows the limit that was set on the statement (0 = none); reaching it marks the grid as truncated
     */
    public ResultTable(Statement statement, long executionTime, int maxRows) throws SQLException {
        this(statement, executionTime, maxRows, DatabaseConnection.getInstance().getConnectionObject());
    }

    /**
     * @param connectionObject the connection the statement ran on (it supplies the engine's quoting rules);
     *                         the statement's own JDBC connection is used for key lookups
     */
    public ResultTable(Statement statement, long executionTime, int maxRows,
                       com.queryexe.model.connections.ConnectionObject connectionObject) throws SQLException {
        this.connectionObject = connectionObject;
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

        // Focus-loss alone misses clicks on non-focusable areas (blank panes,
        // labels, ...), which is why the editor could get stuck open. Catch
        // every press scene-wide and close the edit unless it landed on the
        // cell currently being edited.
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideEditClickFilter);
                oldScene.windowProperty().removeListener(windowChangeListener);
                if (oldScene.getWindow() != null) {
                    oldScene.getWindow().focusedProperty().removeListener(windowFocusListener);
                }
            }
            if (newScene != null) {
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideEditClickFilter);
                newScene.windowProperty().addListener(windowChangeListener);
                if (newScene.getWindow() != null) {
                    newScene.getWindow().focusedProperty().addListener(windowFocusListener);
                }
            }
        });

        this.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        boolean hasPrimaryKey = false;

        ResultSet resultSet = statement.getResultSet();
        ResultSetMetaData metaData = resultSet.getMetaData();
        hasPrimaryKey = checkForPrimaryKey(statement.getConnection(), metaData);

        this.setEditable(hasPrimaryKey);

        int columnCount = metaData.getColumnCount();

        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            String columnName = metaData.getColumnName(columnIndex);
            columnNames.add(columnName);
            // the header shows the alias the query gave the column; SQL is generated from the real column name
            TableColumn<TableRowData, String> tableColumn = new TableColumn<>(metaData.getColumnLabel(columnIndex));

            tableColumn.setContextMenu(new ResultColumnContextMenu(tableColumn));
            tableColumn.setReorderable(false);
            tableColumn.setMinWidth(minColumnWidth);
            final int columnOffset = columnIndex - 1;

            int columnType = metaData.getColumnType(columnIndex);
            columnSqlTypes.add(metaData.getColumnType(columnIndex));
            columnTypeNames.add(Objects.toString(metaData.getColumnTypeName(columnIndex), ""));

            if (columnType == java.sql.Types.BLOB || columnType == java.sql.Types.LONGVARBINARY ||
                    columnType == java.sql.Types.VARBINARY || columnType == java.sql.Types.BINARY) {
                String columnClassName = metaData.getColumnClassName(columnIndex);
                String columnTypeName = metaData.getColumnTypeName(columnIndex);

                tableColumn.setEditable("java.util.UUID".equals(columnClassName) || columnTypeName.toUpperCase().contains("UUID"));
            } else {
                // expressions/literals have no column to write back to
                tableColumn.setEditable(!Objects.toString(metaData.getTableName(columnIndex), "").isEmpty());
            }
            if (!Objects.toString(metaData.getTableName(columnIndex), "").isEmpty()) {
                tableColumnIndexes.add(columnIndex - 1);
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

                    {
                        // Both JavaFX's built-in :focus-within and :editing pseudo-classes
                        // proved unreliable for driving the "is this cell being edited"
                        // border on a recycled TableCell whose editor gets swapped out
                        // programmatically. Own the style entirely instead: this listener
                        // is the single source of truth, in sync with editingProperty
                        // regardless of whether startEdit/cancelEdit/commitEdit is what
                        // changed it.
                        this.editingProperty().addListener((obs, wasEditing, editing) -> {
                            if (editing) {
                                if (!this.getStyleClass().contains("cell-editing")) {
                                    this.getStyleClass().add("cell-editing");
                                }
                            } else {
                                this.getStyleClass().remove("cell-editing");
                            }
                        });
                    }

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
                            setStyle("-fx-text-fill: -color-fg-muted; -fx-font-style: italic;");
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
                            setStyle("-fx-text-fill: -color-fg-muted; -fx-font-style: italic;");
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
                Object[] cell = readCell(resultSet, columnIndex, columnIndex - 1);
                originalRow.add(cell[0]);
                stringRow.add((String) cell[1]);
            }
            TableRowData row = new TableRowData(originalRow, stringRow);
            tableData.add(row);
            loadedRows.add(row);
        }

        this.setItems(tableData);
        this.truncated = maxRows > 0 && tableData.size() >= maxRows;
        resultSet.close();

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

    // Reads one cell: [0] the typed value kept for write-back, [1] the text shown in the grid.
    // columnIndex is the position in the result set, gridIndex the position among the grid columns.
    private Object[] readCell(ResultSet resultSet, int columnIndex, int gridIndex) throws SQLException {
        int columnType = columnSqlTypes.get(gridIndex);

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
                boolean boolIsNull = resultSet.wasNull();
                originalValue = boolIsNull ? null : boolValue;
                stringValue = boolIsNull ? null : String.valueOf(boolValue);
                break;
            // The drivers' own toString() of these is a debugging aid ("clob0: '...'", "[B@1f2e"), not the value.
            case java.sql.Types.CLOB:
            case java.sql.Types.NCLOB:
            case java.sql.Types.SQLXML:
                stringValue = resultSet.getString(columnIndex);
                originalValue = stringValue;
                break;
            case java.sql.Types.ARRAY:
                stringValue = formatArray(resultSet.getArray(columnIndex));
                originalValue = stringValue;
                break;
            default:
                String typeName = columnTypeNames.get(gridIndex).toLowerCase();
                if (typeName.contains("json") || typeName.contains("xml")) {
                    stringValue = resultSet.getString(columnIndex);
                    originalValue = stringValue;
                } else {
                    originalValue = resultSet.getObject(columnIndex);
                    stringValue = (originalValue != null) ? originalValue.toString() : null;
                }
        }
        return new Object[]{originalValue, stringValue};
    }

    private static String formatArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object elements = array.getArray();
        if (elements == null || !elements.getClass().isArray()) {
            return String.valueOf(elements);
        }
        // reflection so primitive arrays (int[]) are handled like object arrays
        StringJoiner joined = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < java.lang.reflect.Array.getLength(elements); i++) {
            Object element = java.lang.reflect.Array.get(elements, i);
            joined.add(element != null && element.getClass().isArray() && element instanceof Object[] nested
                    ? Arrays.deepToString(nested)
                    : String.valueOf(element));
        }
        return joined.toString();
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
        // Identify the target by catalog + schema + table: the bare table name is ambiguous when the same
        // name exists in several schemas/databases (edits would silently hit the default one).
        Set<List<String>> targets = new LinkedHashSet<>();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnTable = metaData.getTableName(i);
            if (columnTable != null && !columnTable.isEmpty()) {
                targets.add(List.of(
                        Objects.toString(metaData.getCatalogName(i), ""),
                        Objects.toString(metaData.getSchemaName(i), ""),
                        columnTable));
            }
        }

        if (targets.size() > 1) {
            log.debug("Multiple tables detected - likely a JOIN query");
            return false;
        }

        if (targets.isEmpty()) {
            log.debug("No table names found in metadata");
            return false;
        }

        List<String> target = targets.iterator().next();
        String catalog = target.get(0);
        String schema = target.get(1);
        tableName = target.get(2);

        if (connectionObject == null) {
            return false;
        }
        if (connectionObject.isTableReferenceAmbiguous(catalog, schema, tableName)) {
            log.debug("Table reference {} is ambiguous - results are read-only", tableName);
            return false;
        }
        qualifiedTableName = connectionObject.qualifyTable(catalog, schema, tableName);

        DatabaseMetaData dbMetaData = connection.getMetaData();
        primaryKeyColumns = new LinkedHashSet<>();
        primaryKeyActualNames = new LinkedHashMap<>();

        // Ordered by KEY_SEQ so composite keys keep their declared order.
        TreeMap<Short, String> orderedKeys = new TreeMap<>();
        try (ResultSet primaryKeys = dbMetaData.getPrimaryKeys(
                catalog.isEmpty() ? null : catalog, schema.isEmpty() ? null : schema, tableName)) {
            while (primaryKeys.next()) {
                orderedKeys.put(primaryKeys.getShort("KEY_SEQ"), primaryKeys.getString("COLUMN_NAME"));
            }
        }
        for (String pkName : orderedKeys.values()) {
            primaryKeyColumns.add(pkName.toUpperCase());
            primaryKeyActualNames.put(pkName.toUpperCase(), pkName);
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

    public void updateDatabaseRow(TableRowData rowData, int updatedColumnIndex, String oldValue, String newValue) {
        Object previousOriginal = rowData.getOriginalData().get(updatedColumnIndex);

        // A row inserted in this grid whose key the database generated but the driver didn't report back
        // can't be targeted by an UPDATE; refuse instead of silently matching nothing.
        if (!rowData.isNewRow() && !rowEdits.containsKey(rowData) && !hasKnownKey(rowData)) {
            try {
                new CustomNotification("Row Not Editable",
                        "This row's key is unknown. Re-run the query to edit it.",
                        new FontIcon(MaterialDesignA.ALERT_OUTLINE)).showNotification();
            } catch (Exception e) {
                log.warn("Row has no known key; edit ignored");
            }
            this.refresh();
            return;
        }

        try {
            Object typedValue = convertToOriginalType(updatedColumnIndex, newValue);

            RowEdit edit = null;
            boolean newEdit = false;
            if (!rowData.isNewRow()) {
                edit = rowEdits.get(rowData);
                if (edit == null) {
                    // Captured before the cell changes: when the edited cell is part of the key,
                    // the WHERE clause must still match the value stored in the database.
                    edit = new RowEdit(currentPrimaryKeyValues(rowData));
                    newEdit = true;
                }
            }

            if (!rowData.isNewRow()) {
                loadedValues.computeIfAbsent(rowData, LoadedValues::new);
            }
            rowData.getStringData().set(updatedColumnIndex, newValue);
            rowData.getOriginalData().set(updatedColumnIndex, typedValue);

            if (rowData.isNewRow()) {
                rebuildInsert(rowData);
            } else {
                edit.changes.put(updatedColumnIndex, typedValue);
                rebuildUpdate(edit);
                if (newEdit) {
                    rowEdits.put(rowData, edit);
                    updateQueries.add(edit.query);
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
            // Engines with a native UUID type (e.g. PostgreSQL) need a UUID object, but text columns that merely
            // hold UUID-looking strings must stay strings, so decide from the column type, not the value.
            if ("uuid".equalsIgnoreCase(columnTypeNames.get(columnIndex))) {
                return UUID.fromString(stringValue.trim());
            }
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

        QueryData queryInfo = new QueryData("", new ArrayList<>());
        newRow.setPendingInsert(queryInfo);
        rebuildInsert(newRow);
        updateQueries.add(queryInfo);

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
            if (primaryKeyColumns.contains(columnNames.get(i).toUpperCase())) {
                newOriginalData.set(i, null);
                newStringData.set(i, "");
            }
        }

        TableRowData newRow = new TableRowData(
                FXCollections.observableArrayList(newOriginalData),
                FXCollections.observableArrayList(newStringData)
        );
        newRow.setNewRow(true);

        QueryData queryInfo = new QueryData("", new ArrayList<>());
        newRow.setPendingInsert(queryInfo);
        rebuildInsert(newRow);
        updateQueries.add(queryInfo);

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

        // A row with pending edits is deleted by the key it has in the database, and its UPDATE is
        // dropped so it can't run before the DELETE (and fail or hit another row if the key was edited).
        RowEdit edit = rowEdits.remove(rowData);
        Map<String, Object> primaryKeyValues;
        if (edit != null) {
            updateQueries.removeIf(queryData -> queryData == edit.query);
            primaryKeyValues = edit.primaryKeyValues;
        } else {
            primaryKeyValues = currentPrimaryKeyValues(rowData);
        }

        List<Object> parameters = new ArrayList<>();
        String deleteQuery = "DELETE FROM " + qualifiedTableName + " WHERE " + buildWhereClause(primaryKeyValues, parameters);

        updateQueries.add(new QueryData(deleteQuery, parameters));
    }

    private Map<String, Object> currentPrimaryKeyValues(TableRowData rowData) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String primaryKeyName : primaryKeyActualNames.values()) {
            int index = columnIndexForName(primaryKeyName);
            if (index >= 0) {
                values.put(primaryKeyName, rowData.getOriginalData().get(index));
            }
        }
        return values;
    }

    private String buildWhereClause(Map<String, Object> primaryKeyValues, List<Object> parameters) {
        StringJoiner where = new StringJoiner(" AND ");
        for (Map.Entry<String, Object> entry : primaryKeyValues.entrySet()) {
            where.add(connectionObject.quoteIdentifier(entry.getKey()) + " = ?");
            parameters.add(entry.getValue());
        }
        return where.toString();
    }

    private int columnIndexForName(String name) {
        for (int i = 0; i < columnNames.size(); i++) {
            if (columnNames.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    // Regenerates a pending INSERT from the row's current values. Untouched (null) columns are left
    // out so identity/default columns are filled in by the database instead of being forced to NULL.
    private void rebuildInsert(TableRowData row) {
        StringJoiner columnsClause = new StringJoiner(", ");
        StringJoiner valuesClause = new StringJoiner(", ");
        List<Object> parameters = new ArrayList<>();

        for (int i = 0; i < columnNames.size(); i++) {
            Object value = row.getOriginalData().get(i);
            if (value != null) {
                columnsClause.add(connectionObject.quoteIdentifier(columnNames.get(i)));
                valuesClause.add("?");
                parameters.add(value);
            }
        }

        String insertQuery = parameters.isEmpty()
                ? connectionObject.generateDefaultsOnlyInsert(qualifiedTableName)
                : "INSERT INTO " + qualifiedTableName + " (" + columnsClause + ") VALUES (" + valuesClause + ")";

        QueryData insert = row.getPendingInsert();
        insert.setQuery(insertQuery);
        insert.setParameters(parameters);
    }

    private void rebuildUpdate(RowEdit edit) {
        StringJoiner setClause = new StringJoiner(", ");
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<Integer, Object> change : edit.changes.entrySet()) {
            setClause.add(connectionObject.quoteIdentifier(columnNames.get(change.getKey())) + " = ?");
            parameters.add(change.getValue());
        }
        String updateQuery = "UPDATE " + qualifiedTableName + " SET " + setClause
                + " WHERE " + buildWhereClause(edit.primaryKeyValues, parameters);

        if (edit.query == null) {
            edit.query = new QueryData(updateQuery, parameters);
        } else {
            edit.query.setQuery(updateQuery);
            edit.query.setParameters(parameters);
        }
    }

    public void restoreOriginalData() {
        Platform.runLater(() -> {
            for (Map.Entry<TableRowData, LoadedValues> saved : loadedValues.entrySet()) {
                saved.getKey().getOriginalData().setAll(saved.getValue().original);
                saved.getKey().getStringData().setAll(saved.getValue().strings);
            }
            loadedValues.clear();
            // rows added in the grid are not in loadedRows, deleted ones are back
            this.setItems(FXCollections.observableArrayList(loadedRows));

            this.updateQueries.clear();
            this.rowEdits.clear();
            this.refresh();
        });
    }

    /** Makes the current rows the state Revert returns to. FX thread. */
    public void updateBackupData() {
        loadedRows = new ArrayList<>(this.getItems());
        loadedValues.clear();
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

    private boolean hasKnownKey(TableRowData rowData) {
        Map<String, Object> values = currentPrimaryKeyValues(rowData);
        return values.size() == primaryKeyActualNames.size() && !values.containsValue(null);
    }

    /**
     * The pending INSERT statements, by identity, so the caller can ask the driver for generated keys.
     */
    public Set<QueryData> pendingInsertQueries() {
        Set<QueryData> inserts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TableRowData row : this.getItems()) {
            if (row.isNewRow() && row.getPendingInsert() != null) {
                inserts.add(row.getPendingInsert());
            }
        }
        return inserts;
    }

    /**
     * Called on the FX thread after all pending statements were committed: inserted rows become ordinary
     * rows (their generated key is filled in so later edits can address them), and tracking is reset.
     *
     * @param generatedKeys per INSERT (identity-keyed), the generated-key columns the driver returned
     */
    public List<InsertedRow> commitApplied(Map<QueryData, Map<String, Object>> generatedKeys) {
        List<InsertedRow> inserted = new ArrayList<>();
        for (TableRowData row : this.getItems()) {
            if (!row.isNewRow()) {
                continue;
            }
            Map<String, Object> keys = generatedKeys.get(row.getPendingInsert());
            if (keys != null) {
                fillGeneratedKeys(row, keys);
            }
            row.setNewRow(false);
            row.setPendingInsert(null);
            if (hasKnownKey(row)) {
                inserted.add(new InsertedRow(row, currentPrimaryKeyValues(row)));
            }
        }

        updateBackupData();
        clearUpdateTracking();
        if (onCellUpdate != null) {
            onCellUpdate.run();
        }
        this.refresh();
        return inserted;
    }

    /** A row that was just inserted, with the key to find it by. */
    public record InsertedRow(TableRowData row, Map<String, Object> key) {
    }

    /**
     * Reads back the columns the database filled in itself (defaults, timestamps, triggers) for rows that were just
     * inserted, so the grid shows what is stored. Blocking JDBC work: call it off the FX thread. Failures are
     * logged only; the insert itself already succeeded.
     */
    public void reloadInserted(Connection connection, List<InsertedRow> rows) {
        if (rows.isEmpty() || tableColumnIndexes.isEmpty() || connectionObject == null || qualifiedTableName == null) {
            return;
        }
        StringJoiner select = new StringJoiner(", ");
        for (int index : tableColumnIndexes) {
            select.add(connectionObject.quoteIdentifier(columnNames.get(index)));
        }

        Map<TableRowData, Object[][]> reloaded = new IdentityHashMap<>();
        for (InsertedRow inserted : rows) {
            List<Object> parameters = new ArrayList<>();
            String sql = "SELECT " + select + " FROM " + qualifiedTableName
                    + " WHERE " + buildWhereClause(inserted.key(), parameters);
            try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.size(); i++) {
                    statement.setObject(i + 1, parameters.get(i));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Object[][] cells = new Object[tableColumnIndexes.size()][];
                        for (int i = 0; i < cells.length; i++) {
                            cells[i] = readCell(resultSet, i + 1, tableColumnIndexes.get(i));
                        }
                        reloaded.put(inserted.row(), cells);
                    }
                }
            } catch (SQLException e) {
                log.warn("Could not read back an inserted row: {}", e.getMessage());
            }
        }

        Platform.runLater(() -> {
            reloaded.forEach((row, cells) -> {
                for (int i = 0; i < cells.length; i++) {
                    int gridIndex = tableColumnIndexes.get(i);
                    // only fill what the user left empty; what they typed is what was stored
                    if (row.getOriginalData().get(gridIndex) == null && cells[i][0] != null) {
                        row.getOriginalData().set(gridIndex, cells[i][0]);
                        row.getStringData().set(gridIndex, (String) cells[i][1]);
                    }
                }
            });
            this.refresh();
        });
    }

    private void fillGeneratedKeys(TableRowData row, Map<String, Object> keys) {
        List<Integer> missing = new ArrayList<>();
        for (String primaryKeyName : primaryKeyActualNames.values()) {
            int index = columnIndexForName(primaryKeyName);
            if (index >= 0 && row.getOriginalData().get(index) == null) {
                missing.add(index);
            }
        }

        for (int index : missing) {
            Object value = null;
            for (Map.Entry<String, Object> key : keys.entrySet()) {
                if (key.getKey().equalsIgnoreCase(columnNames.get(index))) {
                    value = key.getValue();
                }
            }
            // Drivers like MySQL/SQLite name the key column generically ("GENERATED_KEY", "last_insert_rowid()").
            if (value == null && keys.size() == 1 && missing.size() == 1) {
                value = keys.values().iterator().next();
            }
            if (value != null) {
                row.getOriginalData().set(index, value);
                row.getStringData().set(index, value.toString());
            }
        }
    }

    public void clearUpdateTracking() {
        this.updateQueries.clear();
        this.rowEdits.clear();
    }

    /** The real (un-aliased) column names, in grid order; this is what generated SQL must use. */
    public List<String> getColumnNames() {
        return Collections.unmodifiableList(columnNames);
    }

    /** {@code java.sql.Types} code of the grid column at this index, VARCHAR when unknown. */
    public int getColumnSqlType(int columnIndex) {
        return columnIndex >= 0 && columnIndex < columnSqlTypes.size() ? columnSqlTypes.get(columnIndex) : java.sql.Types.VARCHAR;
    }

    /** Whether the grid column at this index is (part of) the table's primary key, regardless of its alias. */
    public boolean isPrimaryKeyColumn(int columnIndex) {
        return columnIndex >= 0 && columnIndex < columnNames.size()
                && primaryKeyColumns.contains(columnNames.get(columnIndex).toUpperCase());
    }

    public boolean hasPendingChanges() {
        return !updateQueries.isEmpty();
    }

    public String getQualifiedTableName() {
        if (qualifiedTableName != null) {
            return qualifiedTableName;
        }
        return connectionObject != null && tableName != null ? connectionObject.quoteIdentifier(tableName) : tableName;
    }

}