package com.queryexe.grid;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.data.QueryData;
import com.queryexe.model.data.RowTarget;
import com.queryexe.service.ChangeApplier;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * The rows of one result set and the edits made to them, without any UI. The page shows {@link Row#text}; every
 * edit, insert and delete goes through here and becomes a correctly targeted, quoted, parameterised statement that
 * {@link #apply} runs in one transaction.
 *
 * <p>All public methods are synchronized: the page calls them from RPC worker threads.
 */
@Slf4j
public final class GridModel implements RowTarget {

    /** Cells longer than this are cut when sent to the page; the full value is fetched when a cell is opened. */
    public static final int DISPLAY_LIMIT = 2_000;

    @Getter
    private final String id;
    private final ConnectionObject connectionObject;
    @Getter
    private final long executionTime;

    private final List<Column> columns = new ArrayList<>();
    private final List<String> columnNames = new ArrayList<>();
    private final List<Integer> columnSqlTypes = new ArrayList<>();
    private final List<String> columnTypeNames = new ArrayList<>();
    /** Grid columns that map to a real table column (expression columns have nothing to read back). */
    private final List<Integer> tableColumnIndexes = new ArrayList<>();

    private String tableName;
    /** Quoted, schema/catalog-qualified target taken from the result-set metadata, used in every generated statement. */
    private String qualifiedTableName;
    private final Set<String> primaryKeyColumns = new LinkedHashSet<>();
    private final Map<String, String> primaryKeyActualNames = new LinkedHashMap<>();
    private boolean editable;
    @Getter
    private boolean truncated;

    private int nextRowId = 1;
    private List<Row> rows = new ArrayList<>();
    /** The rows as last loaded or applied; Revert returns to them. */
    private List<Row> loadedRows = new ArrayList<>();
    private final Map<Row, LoadedValues> loadedValues = new IdentityHashMap<>();
    private final Map<Row, RowEdit> rowEdits = new IdentityHashMap<>();
    private final List<QueryData> updateQueries = new ArrayList<>();

    // ------------------------------------------------------------------------------------ data classes

    @Value
    public static class Column {
        String label;
        String name;
        String typeName;
        int sqlType;
        boolean primaryKey;
        boolean editable;
        boolean numeric;
    }

    /** A row of the grid. {@code original} keeps the typed values for write-back, {@code text} is what is shown. */
    public static final class Row {
        @Getter
        private final int id;
        private final Object[] original;
        private final String[] text;
        private boolean newRow;
        private QueryData pendingInsert;

        Row(int id, Object[] original, String[] text) {
            this.id = id;
            this.original = original;
            this.text = text;
        }
    }

    /** What the page gets for a row. {@code c} are the cell texts (null = SQL NULL), {@code m} the edited column indexes. */
    @Value
    public static class RowDto {
        int id;
        String[] c;
        boolean added;
        int[] m;
    }

    @Value
    public static class Pending {
        int updates;
        int inserts;
        int deletes;
    }

    @Value
    public static class Snapshot {
        String id;
        List<Column> columns;
        List<RowDto> rows;
        boolean editable;
        boolean truncated;
        String table;
        long millis;
        Pending pending;
    }

    /** The outcome of an edit: the rows that changed (or appeared), the ones that left the grid, and the new totals. */
    @Value
    public static class Patch {
        List<RowDto> rows;
        List<Integer> removed;
        Pending pending;
        /** Where a newly added row goes, as the id of the row it follows (0 = at the end); only used by the page for scrolling. */
        int focusRow;
    }

    private static final class LoadedValues {
        final Object[] original;
        final String[] text;

        LoadedValues(Row row) {
            this.original = row.original.clone();
            this.text = row.text.clone();
        }
    }

    private static final class RowEdit {
        /** Primary key values as they are in the database, captured before the first edit of the row. */
        final Map<String, Object> primaryKeyValues;
        final Map<Integer, Object> changes = new LinkedHashMap<>();
        QueryData query;

        RowEdit(Map<String, Object> primaryKeyValues) {
            this.primaryKeyValues = primaryKeyValues;
        }
    }

    // ------------------------------------------------------------------------------------ loading

    /**
     * Reads the current result set of {@code statement} completely (up to its row limit).
     *
     * @param maxRows the limit that was set on the statement (0 = none); reaching it marks the grid as truncated
     */
    public GridModel(String id, Statement statement, long executionTime, int maxRows, ConnectionObject connectionObject)
            throws SQLException {
        this.id = id;
        this.connectionObject = connectionObject;
        this.executionTime = executionTime;

        ResultSet resultSet = statement.getResultSet();
        ResultSetMetaData metaData = resultSet.getMetaData();
        boolean hasKey = checkForPrimaryKey(statement.getConnection(), metaData);
        this.editable = hasKey;

        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String name = metaData.getColumnName(i);
            int type = metaData.getColumnType(i);
            String typeName = Objects.toString(metaData.getColumnTypeName(i), "");
            columnNames.add(name);
            columnSqlTypes.add(type);
            columnTypeNames.add(typeName);

            boolean hasTable = !Objects.toString(metaData.getTableName(i), "").isEmpty();
            boolean cellEditable;
            if (type == Types.BLOB || type == Types.LONGVARBINARY || type == Types.VARBINARY || type == Types.BINARY) {
                String className = metaData.getColumnClassName(i);
                cellEditable = "java.util.UUID".equals(className) || typeName.toUpperCase().contains("UUID");
            } else {
                // expressions/literals have no column to write back to
                cellEditable = hasTable;
            }
            if (hasTable) {
                tableColumnIndexes.add(i - 1);
            }
            boolean numeric = switch (type) {
                case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT, Types.DECIMAL, Types.NUMERIC,
                     Types.FLOAT, Types.DOUBLE, Types.REAL -> true;
                default -> false;
            };
            boolean isKey = primaryKeyColumns.contains(name.toUpperCase());
            columns.add(new Column(metaData.getColumnLabel(i), name, typeName, type, isKey, hasKey && cellEditable, numeric));
        }

        while (resultSet.next()) {
            Object[] original = new Object[columnCount];
            String[] text = new String[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                Object[] cell = readCell(resultSet, i, i - 1);
                original[i - 1] = cell[0];
                text[i - 1] = (String) cell[1];
            }
            Row row = new Row(nextRowId++, original, text);
            rows.add(row);
            loadedRows.add(row);
        }
        resultSet.close();
        this.truncated = maxRows > 0 && rows.size() >= maxRows;
    }

    /** Reads one cell: [0] the typed value kept for write-back, [1] the text shown in the grid. */
    private Object[] readCell(ResultSet resultSet, int columnIndex, int gridIndex) throws SQLException {
        int columnType = columnSqlTypes.get(gridIndex);

        Object originalValue;
        String stringValue;
        switch (columnType) {
            case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> {
                byte[] binaryData = resultSet.getBytes(columnIndex);
                originalValue = binaryData;
                if (binaryData != null && (binaryData.length == 16 || binaryData.length == 36)) {
                    try {
                        if (binaryData.length == 16) {
                            UUID uuid = bytesToUuid(binaryData);
                            stringValue = uuid.toString();
                            originalValue = uuid;
                        } else {
                            String possible = new String(binaryData, java.nio.charset.StandardCharsets.UTF_8);
                            if (possible.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                stringValue = possible;
                                originalValue = UUID.fromString(possible);
                            } else {
                                stringValue = String.format("[Data: %d bytes]", binaryData.length);
                            }
                        }
                    } catch (RuntimeException e) {
                        stringValue = String.format("[Data: %d bytes]", binaryData.length);
                    }
                } else {
                    stringValue = binaryData != null ? String.format("[Data: %d bytes]", binaryData.length) : null;
                }
            }
            case Types.BOOLEAN -> {
                boolean value = resultSet.getBoolean(columnIndex);
                boolean isNull = resultSet.wasNull();
                originalValue = isNull ? null : value;
                stringValue = isNull ? null : String.valueOf(value);
            }
            // The drivers' own toString() of these is a debugging aid ("clob0: '...'"), not the value.
            case Types.CLOB, Types.NCLOB, Types.SQLXML -> {
                stringValue = resultSet.getString(columnIndex);
                originalValue = stringValue;
            }
            case Types.ARRAY -> {
                stringValue = formatArray(resultSet.getArray(columnIndex));
                originalValue = stringValue;
            }
            default -> {
                String typeName = columnTypeNames.get(gridIndex).toLowerCase();
                if (typeName.contains("json") || typeName.contains("xml")) {
                    stringValue = resultSet.getString(columnIndex);
                    originalValue = stringValue;
                } else {
                    originalValue = resultSet.getObject(columnIndex);
                    stringValue = originalValue != null ? originalValue.toString() : null;
                }
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
        StringJoiner joined = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < java.lang.reflect.Array.getLength(elements); i++) {
            Object element = java.lang.reflect.Array.get(elements, i);
            joined.add(element instanceof Object[] nested ? Arrays.deepToString(nested) : String.valueOf(element));
        }
        return joined.toString();
    }

    private static UUID bytesToUuid(byte[] bytes) {
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            least = (least << 8) | (bytes[i] & 0xff);
        }
        return new UUID(most, least);
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
        if (targets.size() != 1) {
            log.debug("Result is not from exactly one table ({} targets): read-only", targets.size());
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
            log.debug("No primary key for table: {}", tableName);
            return false;
        }

        Set<String> found = new HashSet<>();
        for (int i = 1; i <= columnCount; i++) {
            String name = metaData.getColumnName(i).toUpperCase();
            if (primaryKeyColumns.contains(name)) {
                found.add(name);
            }
        }
        return found.size() == primaryKeyColumns.size();
    }

    // ------------------------------------------------------------------------------------ reading state

    public synchronized Snapshot snapshot() {
        List<RowDto> dtos = new ArrayList<>(rows.size());
        for (Row row : rows) {
            dtos.add(dto(row));
        }
        return new Snapshot(id, Collections.unmodifiableList(columns), dtos, editable, truncated,
                tableName, executionTime, pending());
    }

    /** The full text of one cell (the page only gets {@link #DISPLAY_LIMIT} characters). */
    public synchronized String cellText(int rowId, int column) {
        Row row = find(rowId);
        return row == null || column < 0 || column >= row.text.length ? null : row.text[column];
    }

    /** The statements Apply would run, in order (the parameters are bound separately). */
    public synchronized List<String> pendingSql() {
        List<String> out = new ArrayList<>();
        for (QueryData query : updateQueries) {
            out.add(query.getQuery());
        }
        return out;
    }

    public synchronized boolean hasPendingChanges() {
        return !updateQueries.isEmpty();
    }

    public synchronized Pending pending() {
        int updates = 0;
        int inserts = 0;
        for (Row row : rows) {
            if (row.newRow) {
                inserts++;
            }
        }
        updates = rowEdits.size();
        int deletes = updateQueries.size() - updates - inserts;
        return new Pending(updates, inserts, Math.max(0, deletes));
    }

    private RowDto dto(Row row) {
        String[] shown = new String[row.text.length];
        for (int i = 0; i < shown.length; i++) {
            String value = row.text[i];
            shown[i] = value != null && value.length() > DISPLAY_LIMIT ? value.substring(0, DISPLAY_LIMIT) : value;
        }
        RowEdit edit = rowEdits.get(row);
        int[] changed = edit == null ? new int[0] : edit.changes.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        return new RowDto(row.id, shown, row.newRow, changed);
    }

    private Row find(int rowId) {
        for (Row row : rows) {
            if (row.id == rowId) {
                return row;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------ editing

    /** Sets a cell. A null {@code newValue} is SQL NULL; an empty string is NULL for non-text columns as before. */
    public synchronized Patch edit(int rowId, int column, String newValue) {
        Row row = find(rowId);
        if (row == null || column < 0 || column >= columns.size()) {
            throw new IllegalArgumentException("No such cell");
        }
        if (!editable || !columns.get(column).isEditable()) {
            throw new IllegalStateException("This cell cannot be edited.");
        }
        String oldValue = row.text[column];
        if (Objects.equals(oldValue, newValue) || (oldValue == null && (newValue == null || newValue.isEmpty()))) {
            return patchOf(List.of(row), List.of());
        }

        // A row inserted in this grid whose key the database generated but the driver didn't report back
        // can't be targeted by an UPDATE; refuse instead of silently matching nothing.
        if (!row.newRow && !rowEdits.containsKey(row) && !hasKnownKey(row)) {
            throw new IllegalStateException("This row's key is unknown. Re-run the query to edit it.");
        }

        Object previousOriginal = row.original[column];
        Object typed = convertToOriginalType(column, newValue);
        RowEdit edit = null;
        boolean first = false;
        if (!row.newRow) {
            edit = rowEdits.get(row);
            if (edit == null) {
                // Captured before the cell changes: when the edited cell is part of the key the WHERE clause
                // must still match the value stored in the database.
                edit = new RowEdit(currentPrimaryKeyValues(row));
                first = true;
            }
            loadedValues.computeIfAbsent(row, LoadedValues::new);
        }
        try {
            row.text[column] = newValue;
            row.original[column] = typed;
            if (row.newRow) {
                rebuildInsert(row);
            } else {
                edit.changes.put(column, typed);
                rebuildUpdate(edit);
                if (first) {
                    rowEdits.put(row, edit);
                    updateQueries.add(edit.query);
                }
            }
        } catch (RuntimeException e) {
            row.text[column] = oldValue;
            row.original[column] = previousOriginal;
            throw e;
        }
        return patchOf(List.of(row), List.of());
    }

    public synchronized Patch addRow() {
        Row row = new Row(nextRowId++, new Object[columns.size()], new String[columns.size()]);
        Arrays.fill(row.text, "");
        startInsert(row);
        return patchOf(List.of(row), List.of());
    }

    public synchronized Patch duplicateRow(int rowId) {
        Row source = find(rowId);
        if (source == null) {
            throw new IllegalArgumentException("No such row");
        }
        Row row = new Row(nextRowId++, source.original.clone(), source.text.clone());
        for (int i = 0; i < columns.size(); i++) {
            if (primaryKeyColumns.contains(columnNames.get(i).toUpperCase())) {
                row.original[i] = null;
                row.text[i] = "";
            }
        }
        startInsert(row);
        return patchOf(List.of(row), List.of());
    }

    private void startInsert(Row row) {
        if (!editable) {
            throw new IllegalStateException("This result cannot be edited.");
        }
        row.newRow = true;
        row.pendingInsert = new QueryData("", new ArrayList<>());
        rebuildInsert(row);
        updateQueries.add(row.pendingInsert);
        rows.add(row);
    }

    public synchronized Patch deleteRows(List<Integer> rowIds) {
        if (!editable) {
            throw new IllegalStateException("This result cannot be edited.");
        }
        List<Integer> removed = new ArrayList<>();
        for (int rowId : rowIds) {
            Row row = find(rowId);
            if (row != null) {
                queueRowDeletion(row);
                rows.remove(row);
                removed.add(rowId);
            }
        }
        return patchOf(List.of(), removed);
    }

    private void queueRowDeletion(Row row) {
        if (row.newRow) {
            // The row never reached the database: dropping its pending INSERT is enough (by identity,
            // because two duplicated rows can hold equal-by-value insert queries).
            QueryData pendingInsert = row.pendingInsert;
            updateQueries.removeIf(q -> q == pendingInsert);
            return;
        }
        // A row with pending edits is deleted by the key it has in the database, and its UPDATE is dropped so it
        // can't run before the DELETE (and fail or hit another row if the key was edited).
        RowEdit edit = rowEdits.remove(row);
        Map<String, Object> key;
        if (edit != null) {
            updateQueries.removeIf(q -> q == edit.query);
            key = edit.primaryKeyValues;
        } else {
            key = currentPrimaryKeyValues(row);
        }
        List<Object> parameters = new ArrayList<>();
        String sql = "DELETE FROM " + qualifiedTableName + " WHERE " + buildWhereClause(key, parameters);
        updateQueries.add(new QueryData(sql, parameters));
    }

    /** Throws away every pending edit and returns to the rows as last loaded or applied. */
    public synchronized Snapshot revert() {
        for (Map.Entry<Row, LoadedValues> saved : loadedValues.entrySet()) {
            System.arraycopy(saved.getValue().original, 0, saved.getKey().original, 0, saved.getValue().original.length);
            System.arraycopy(saved.getValue().text, 0, saved.getKey().text, 0, saved.getValue().text.length);
        }
        loadedValues.clear();
        // rows added in the grid are not in loadedRows, deleted ones are back
        rows = new ArrayList<>(loadedRows);
        updateQueries.clear();
        rowEdits.clear();
        return snapshot();
    }

    // ------------------------------------------------------------------------------------ applying

    /**
     * Runs every pending statement in one transaction on {@code connection}. Blocking JDBC: call off the UI thread.
     * On success the inserted rows become ordinary rows and Revert now returns to this state.
     */
    public Snapshot apply(Connection connection) throws SQLException {
        List<QueryData> queries;
        Set<QueryData> inserts = Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized (this) {
            queries = new ArrayList<>(updateQueries);
            for (Row row : rows) {
                if (row.newRow && row.pendingInsert != null) {
                    inserts.add(row.pendingInsert);
                }
            }
        }
        if (queries.isEmpty()) {
            return snapshot();
        }
        Map<QueryData, Map<String, Object>> generatedKeys = ChangeApplier.apply(connection, queries, inserts);

        List<InsertedRow> inserted;
        synchronized (this) {
            inserted = commitApplied(generatedKeys);
        }
        reloadInserted(connection, inserted);
        return snapshot();
    }

    private record InsertedRow(Row row, Map<String, Object> key) {
    }

    private List<InsertedRow> commitApplied(Map<QueryData, Map<String, Object>> generatedKeys) {
        List<InsertedRow> inserted = new ArrayList<>();
        for (Row row : rows) {
            if (!row.newRow) {
                continue;
            }
            Map<String, Object> keys = generatedKeys.get(row.pendingInsert);
            if (keys != null) {
                fillGeneratedKeys(row, keys);
            }
            row.newRow = false;
            row.pendingInsert = null;
            if (hasKnownKey(row)) {
                inserted.add(new InsertedRow(row, currentPrimaryKeyValues(row)));
            }
        }
        loadedRows = new ArrayList<>(rows);
        loadedValues.clear();
        updateQueries.clear();
        rowEdits.clear();
        return inserted;
    }

    /** Reads back the columns the database filled in itself (defaults, timestamps, triggers) for just-inserted rows. */
    private void reloadInserted(Connection connection, List<InsertedRow> insertedRows) {
        if (insertedRows.isEmpty() || tableColumnIndexes.isEmpty() || connectionObject == null || qualifiedTableName == null) {
            return;
        }
        StringJoiner select = new StringJoiner(", ");
        for (int index : tableColumnIndexes) {
            select.add(connectionObject.quoteIdentifier(columnNames.get(index)));
        }
        for (InsertedRow inserted : insertedRows) {
            List<Object> parameters = new ArrayList<>();
            String sql;
            synchronized (this) {
                sql = "SELECT " + select + " FROM " + qualifiedTableName + " WHERE " + buildWhereClause(inserted.key(), parameters);
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.size(); i++) {
                    statement.setObject(i + 1, parameters.get(i));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Object[][] cells = new Object[tableColumnIndexes.size()][];
                        for (int i = 0; i < cells.length; i++) {
                            cells[i] = readCell(resultSet, i + 1, tableColumnIndexes.get(i));
                        }
                        synchronized (this) {
                            for (int i = 0; i < cells.length; i++) {
                                int gridIndex = tableColumnIndexes.get(i);
                                // only fill what the user left empty; what they typed is what was stored
                                if (inserted.row().original[gridIndex] == null && cells[i][0] != null) {
                                    inserted.row().original[gridIndex] = cells[i][0];
                                    inserted.row().text[gridIndex] = (String) cells[i][1];
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                log.warn("Could not read back an inserted row: {}", e.getMessage());
            }
        }
    }

    private void fillGeneratedKeys(Row row, Map<String, Object> keys) {
        List<Integer> missing = new ArrayList<>();
        for (String name : primaryKeyActualNames.values()) {
            int index = columnIndexForName(name);
            if (index >= 0 && row.original[index] == null) {
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
                row.original[index] = value;
                row.text[index] = value.toString();
            }
        }
    }

    // ------------------------------------------------------------------------------------ statement building

    private boolean hasKnownKey(Row row) {
        Map<String, Object> values = currentPrimaryKeyValues(row);
        return values.size() == primaryKeyActualNames.size() && !values.containsValue(null);
    }

    private Map<String, Object> currentPrimaryKeyValues(Row row) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : primaryKeyActualNames.values()) {
            int index = columnIndexForName(name);
            if (index >= 0) {
                values.put(name, row.original[index]);
            }
        }
        return values;
    }

    private String buildWhereClause(Map<String, Object> key, List<Object> parameters) {
        StringJoiner where = new StringJoiner(" AND ");
        for (Map.Entry<String, Object> entry : key.entrySet()) {
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

    /** Regenerates a pending INSERT from the row's values. Untouched (null) columns are left out so identity/default columns are filled by the database. */
    private void rebuildInsert(Row row) {
        StringJoiner columnsClause = new StringJoiner(", ");
        StringJoiner valuesClause = new StringJoiner(", ");
        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            Object value = row.original[i];
            if (value != null) {
                columnsClause.add(connectionObject.quoteIdentifier(columnNames.get(i)));
                valuesClause.add("?");
                parameters.add(value);
            }
        }
        String sql = parameters.isEmpty()
                ? connectionObject.generateDefaultsOnlyInsert(qualifiedTableName)
                : "INSERT INTO " + qualifiedTableName + " (" + columnsClause + ") VALUES (" + valuesClause + ")";
        row.pendingInsert.setQuery(sql);
        row.pendingInsert.setParameters(parameters);
    }

    private void rebuildUpdate(RowEdit edit) {
        StringJoiner set = new StringJoiner(", ");
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<Integer, Object> change : edit.changes.entrySet()) {
            set.add(connectionObject.quoteIdentifier(columnNames.get(change.getKey())) + " = ?");
            parameters.add(change.getValue());
        }
        String sql = "UPDATE " + qualifiedTableName + " SET " + set + " WHERE " + buildWhereClause(edit.primaryKeyValues, parameters);
        if (edit.query == null) {
            edit.query = new QueryData(sql, parameters);
        } else {
            edit.query.setQuery(sql);
            edit.query.setParameters(parameters);
        }
    }

    private Object convertToOriginalType(int columnIndex, String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            // Engines with a native UUID type (e.g. PostgreSQL) need a UUID object, but text columns that merely hold
            // UUID-looking strings must stay strings, so decide from the column type, not the value.
            if ("uuid".equalsIgnoreCase(columnTypeNames.get(columnIndex))) {
                return UUID.fromString(text.trim());
            }
            return switch (columnSqlTypes.get(columnIndex)) {
                case Types.BOOLEAN, Types.BIT -> {
                    String lower = text.trim().toLowerCase();
                    yield lower.equals("true") || lower.equals("1") || lower.equals("yes");
                }
                case Types.INTEGER, Types.SMALLINT -> Integer.parseInt(text.trim());
                case Types.TINYINT -> Short.parseShort(text.trim());
                case Types.BIGINT -> Long.parseLong(text.trim());
                case Types.FLOAT, Types.REAL -> Float.parseFloat(text.trim());
                case Types.DOUBLE -> Double.parseDouble(text.trim());
                case Types.NUMERIC, Types.DECIMAL -> new java.math.BigDecimal(text.trim());
                case Types.DATE -> java.sql.Date.valueOf(text.trim());
                case Types.TIME -> java.sql.Time.valueOf(text.trim());
                case Types.TIMESTAMP -> java.sql.Timestamp.valueOf(text.trim());
                case Types.BINARY, Types.VARBINARY, Types.BLOB -> UUID.fromString(text.trim());
                default -> text;
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid " + columnTypeNames.get(columnIndex)
                    + " value for column " + columns.get(columnIndex).getLabel(), e);
        }
    }

    private Patch patchOf(List<Row> changed, List<Integer> removed) {
        List<RowDto> dtos = new ArrayList<>();
        for (Row row : changed) {
            dtos.add(dto(row));
        }
        return new Patch(dtos, removed, pending(), changed.isEmpty() ? 0 : changed.get(0).id);
    }

    // ------------------------------------------------------------------------------------ RowTarget

    @Override
    public synchronized String tableName() {
        return tableName;
    }

    @Override
    public synchronized String qualifiedTableName() {
        if (qualifiedTableName != null) {
            return qualifiedTableName;
        }
        return connectionObject != null && tableName != null ? connectionObject.quoteIdentifier(tableName) : tableName;
    }

    @Override
    public synchronized List<String> columnNames() {
        return Collections.unmodifiableList(columnNames);
    }

    @Override
    public synchronized int columnSqlType(int index) {
        return index >= 0 && index < columnSqlTypes.size() ? columnSqlTypes.get(index) : Types.VARCHAR;
    }

    /** The row as an INSERT statement for the table it came from, or a comment when the result has no single table. */
    public synchronized String rowInsertScript(int rowId) {
        Row row = find(rowId);
        if (row == null) {
            throw new IllegalArgumentException("No such row");
        }
        if (tableName == null || connectionObject == null) {
            return "-- This result does not come from a single table.";
        }
        return connectionObject.generateRowInsertScript(Arrays.asList(row.text), this);
    }

    /** The shown texts of the given rows (or all, when {@code rowIds} is null), for copy and export. */
    public synchronized List<String[]> texts(List<Integer> rowIds) {
        List<String[]> out = new ArrayList<>();
        if (rowIds == null) {
            for (Row row : rows) {
                out.add(row.text.clone());
            }
        } else {
            Set<Integer> wanted = new HashSet<>(rowIds);
            for (Row row : rows) {
                if (wanted.contains(row.id)) {
                    out.add(row.text.clone());
                }
            }
        }
        return out;
    }

    public synchronized List<String> columnLabels() {
        List<String> labels = new ArrayList<>();
        for (Column column : columns) {
            labels.add(column.getLabel());
        }
        return labels;
    }
}
