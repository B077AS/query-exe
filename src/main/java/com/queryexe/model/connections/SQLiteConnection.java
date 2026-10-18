package com.queryexe.model.connections;

import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import com.queryexe.model.connections.sqlite.SqliteCatalog;
import com.queryexe.model.connections.sqlite.SqliteCatalog.ColumnInfo;
import com.queryexe.model.connections.sqlite.SqliteCatalog.ForeignKeyInfo;
import com.queryexe.model.connections.sqlite.SqliteCatalog.IndexInfo;
import com.queryexe.model.data.DetailedColumnData;
import com.queryexe.model.data.ColumnData;
import com.queryexe.components.results.ResultTable;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.drivers.DriverInfo;
import com.queryexe.model.data.ForeignKeyData;
import javafx.scene.control.TableColumn;

@Slf4j
public class SQLiteConnection extends ConnectionObject {

    private String[] dataTypes = new String[]{
            // SQLite Storage Classes (Affinity Types)
            "NULL", "INTEGER", "REAL", "TEXT", "BLOB",

            // Common SQL types that map to SQLite affinities
            "INT", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT", "UNSIGNED BIG INT",
            "INT2", "INT8", "BOOLEAN", "NUMERIC", "DECIMAL", "DOUBLE", "DOUBLE PRECISION",
            "FLOAT", "CHARACTER", "VARCHAR", "VARYING CHARACTER", "NCHAR",
            "NATIVE CHARACTER", "NVARCHAR", "CLOB", "DATE", "DATETIME", "TIMESTAMP"
    };

    public SQLiteConnection(String id, String connectionName, String dbType, String url, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, url, username, password, driverInfo);
    }

    private SqliteCatalog catalog() {
        return new SqliteCatalog(DatabaseConnection.getInstance().getConnection());
    }

    @Override
    public java.util.Map<String, java.util.List<String>> getSchemaObjects(String databaseName) {
        java.util.Map<String, java.util.List<String>> objects = new LinkedHashMap<>();
        try {
            SqliteCatalog catalog = catalog();
            List<String> indexes = catalog.indexNames(databaseName);
            List<String> triggers = catalog.triggerNames(databaseName);
            if (!indexes.isEmpty()) {
                objects.put("Indexes", indexes);
            }
            if (!triggers.isEmpty()) {
                objects.put("Triggers", triggers);
            }
        } catch (SQLException e) {
            log.warn("Could not list the indexes/triggers of {}: {}", databaseName, e.getMessage());
        }
        return objects;
    }

    // ------------------------------------------------------------------------------------ structure

    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String databaseName) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            String schema = SqliteCatalog.schemaOrMain(databaseName);
            for (String tableName : getTablesForDatabase(schema)) {
                tablesMap.put(tableName, getColumnsForTable(schema, tableName));
            }
            return tablesMap;
        } catch (SQLException e) {
            log.error("getAllTablesAndColumns failed", e);
        }
        return null;
    }

    @Override
    public ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException {
        return new ArrayList<>(catalog().tables(databaseName));
    }

    @Override
    public ArrayList<ColumnData> getColumnsForTable(String schemaName, String tableName) throws SQLException {
        SqliteCatalog catalog = catalog();

        // a column is unique by itself only through a single-column UNIQUE constraint or unique index
        Map<String, String> uniqueIndexByColumn = new HashMap<>();
        for (IndexInfo index : catalog.indexes(schemaName, tableName)) {
            if (index.isUnique() && !"pk".equals(index.getOrigin()) && index.getColumns().size() == 1) {
                uniqueIndexByColumn.putIfAbsent(index.getColumns().get(0), index.getName());
            }
        }
        boolean autoIncrement = catalog.isAutoincrement(schemaName, tableName);

        ArrayList<ColumnData> columns = new ArrayList<>();
        for (ColumnInfo column : catalog.columns(schemaName, tableName)) {
            boolean primaryKey = column.getPrimaryKeyPosition() > 0;
            String uniqueName = primaryKey ? null : uniqueIndexByColumn.get(column.getName());
            ColumnData data = new ColumnData(column.getName(), column.getType().toUpperCase(), primaryKey,
                    column.isNotNull(), uniqueName != null, primaryKey && autoIncrement, uniqueName);
            if (!column.isGenerated()) {
                data.setDefaultValue(column.getDefaultValue());
            }
            columns.add(data);
        }
        return columns;
    }

    @Override
    public DetailedColumnData getDetailedColumnInfo(String schemaName, String tableName, String columnName) throws SQLException {
        String schema = SqliteCatalog.schemaOrMain(schemaName);
        SqliteCatalog catalog = catalog();

        List<ColumnInfo> columns = catalog.columns(schema, tableName);
        int position = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equals(columnName)) {
                position = i;
            }
        }
        if (position < 0) {
            throw new SQLException("Column '" + columnName + "' not found in table '" + tableName + "'");
        }
        ColumnInfo column = columns.get(position);

        DetailedColumnData info = new DetailedColumnData();
        info.setColumnName(column.getName());
        info.setTableName(tableName);
        info.setSchemaName(schema);
        info.setOrdinalPosition(position + 1);

        String dataType = column.getType().toUpperCase();
        info.setDataType(dataType);
        info.setColumnType(dataType); // SQLite uses the same for both
        applyTypeSize(info, dataType);

        boolean primaryKey = column.getPrimaryKeyPosition() > 0;
        info.setPrimaryKey(primaryKey);
        info.setNullable(!column.isNotNull());
        info.setColumnKey(primaryKey ? "PRI" : "");
        info.setColumnDefault(column.getDefaultValue());
        info.setAutoIncrement(primaryKey && catalog.isAutoincrement(schema, tableName));

        // 2. indexes that include the column
        List<Map<String, String>> indexes = new ArrayList<>();
        boolean unique = false;
        for (IndexInfo index : catalog.indexes(schema, tableName)) {
            int seq = index.getColumns().indexOf(columnName);
            if (seq < 0) {
                continue;
            }
            Map<String, String> entry = new HashMap<>();
            entry.put("INDEX_NAME", index.getName());
            entry.put("INDEX_TYPE", "pk".equals(index.getOrigin()) ? "PRIMARY" : "BTREE");
            entry.put("NON_UNIQUE", index.isUnique() ? "0" : "1");
            entry.put("SEQ_IN_INDEX", String.valueOf(seq + 1));
            entry.put("CARDINALITY", null);
            indexes.add(entry);

            if (index.isUnique() && !"pk".equals(index.getOrigin()) && index.getColumns().size() == 1) {
                unique = true;
            }
        }
        info.setIndexes(indexes);
        info.setUnique(unique);

        // 3. foreign keys this column is part of
        List<Map<String, String>> foreignKeys = new ArrayList<>();
        for (ForeignKeyInfo fk : catalog.foreignKeys(schema, tableName)) {
            int index = fk.getColumns().indexOf(columnName);
            if (index < 0) {
                continue;
            }
            Map<String, String> entry = new HashMap<>();
            entry.put("CONSTRAINT_NAME", fk.getName());
            entry.put("REFERENCED_TABLE_SCHEMA", schema);
            entry.put("REFERENCED_TABLE_NAME", fk.getReferenceTable());
            entry.put("REFERENCED_COLUMN_NAME", fk.getReferenceColumns().get(index));
            entry.put("UPDATE_RULE", fk.getOnUpdate());
            entry.put("DELETE_RULE", fk.getOnDelete());
            foreignKeys.add(entry);
        }
        info.setForeignKeyReferences(foreignKeys);

        // 4. foreign keys of other tables that point at this column
        List<Map<String, String>> referencedBy = new ArrayList<>();
        for (ForeignKeyInfo fk : catalog.referencing(schema, tableName)) {
            int index = fk.getReferenceColumns().indexOf(columnName);
            if (index < 0) {
                continue;
            }
            Map<String, String> entry = new HashMap<>();
            entry.put("TABLE_SCHEMA", schema);
            entry.put("TABLE_NAME", fk.getTable());
            entry.put("COLUMN_NAME", fk.getColumns().get(index));
            entry.put("CONSTRAINT_NAME", fk.getName());
            entry.put("UPDATE_RULE", fk.getOnUpdate());
            entry.put("DELETE_RULE", fk.getOnDelete());
            referencedBy.add(entry);
        }
        info.setReferencedByForeignKeys(referencedBy);

        return info;
    }

    // SQLite keeps sizes only as part of the declared type text, e.g. VARCHAR(50) or DECIMAL(10,2).
    private static void applyTypeSize(DetailedColumnData info, String dataType) {
        int start = dataType.indexOf('(');
        int end = dataType.indexOf(')');
        if (start < 0 || end < start) {
            return;
        }
        try {
            String size = dataType.substring(start + 1, end);
            if (size.contains(",")) {
                String[] parts = size.split(",");
                info.setNumericPrecision(Integer.parseInt(parts[0].trim()));
                info.setNumericScale(Integer.parseInt(parts[1].trim()));
            } else if (dataType.startsWith("VARCHAR") || dataType.startsWith("CHAR") || dataType.startsWith("TEXT")) {
                info.setCharacterMaximumLength(Long.parseLong(size.trim()));
                info.setCharacterOctetLength(Long.parseLong(size.trim()));
            } else {
                info.setNumericPrecision(Integer.parseInt(size.trim()));
            }
        } catch (NumberFormatException e) {
            // a size we cannot read is left unset
        }
    }

    @Override
    public ArrayList<String> getDatabases(String name) {
        try {
            ArrayList<String> databases = new ArrayList<>(catalog().databases());
            if (!databases.isEmpty()) {
                return databases;
            }
        } catch (SQLException e) {
            log.error("getDatabases failed", e);
        }
        return new ArrayList<>(List.of(SqliteCatalog.MAIN));
    }

    @Override
    public Map<String, ForeignKeyData> extractForeignKeys(String dbName, String tableName) throws SQLException {
        Map<String, ForeignKeyData> foreignKeys = new LinkedHashMap<>();
        for (ForeignKeyInfo fk : catalog().foreignKeys(dbName, tableName)) {
            ForeignKeyData data = new ForeignKeyData(fk.getName(), fk.getColumns().get(0), fk.getReferenceTable(),
                    fk.getReferenceColumns().get(0), fk.getOnDelete(), fk.getOnUpdate());
            data.setLocalColumns(fk.getColumns());
            data.setReferenceColumns(fk.getReferenceColumns());
            foreignKeys.put(fk.getName(), data);
        }
        return foreignKeys;
    }

    // ------------------------------------------------------------------------------------ scripts

    @Override
    public String generateCreateScript(String tableName, String dbName) {
        try {
            SqliteCatalog catalog = catalog();
            String createSql = catalog.createSql(dbName, tableName).orElse(null);
            if (createSql == null) {
                return "-- ERROR: Table not found: " + tableName;
            }

            StringBuilder script = new StringBuilder("-- Original table structure\n").append(createSql).append(";\n");
            for (String related : catalog.relatedSql(dbName, tableName)) {
                script.append('\n').append(related).append(";\n");
            }
            return script.toString();
        } catch (SQLException e) {
            log.error("generateCreateScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public String generateInsertScript(String tableName, String dbName) {
        try {
            SqliteCatalog catalog = catalog();
            String schema = SqliteCatalog.schemaOrMain(dbName);
            List<ColumnInfo> all = catalog.columns(schema, tableName);
            List<String> primaryKey = catalog.primaryKeyColumns(schema, tableName);

            List<String> columnNames = new ArrayList<>();
            List<String> columnValues = new ArrayList<>();
            for (ColumnInfo column : all) {
                boolean rowidAlias = primaryKey.size() == 1 && primaryKey.get(0).equals(column.getName())
                        && column.getType().equalsIgnoreCase("INTEGER");
                // generated columns cannot be written, and an INTEGER PRIMARY KEY fills itself in
                if (column.isGenerated() || rowidAlias) {
                    continue;
                }
                columnNames.add(SqliteCatalog.quote(column.getName()));
                columnValues.add(column.isNotNull() ? placeholderFor(column.getType()) : "NULL");
            }

            if (columnNames.isEmpty()) {
                return "-- ERROR: No columns found for table: " + tableName;
            }

            StringBuilder insertScript = new StringBuilder("INSERT INTO ").append(SqliteCatalog.qualified(schema, tableName)).append("\n    (");
            insertScript.append(String.join(",\n     ", columnNames));
            insertScript.append(")\nVALUES\n    (");
            insertScript.append(String.join(",\n     ", columnValues));
            insertScript.append(");");
            return insertScript.toString();
        } catch (SQLException e) {
            log.error("generateInsertScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
    }

    // A value that satisfies NOT NULL for the column's affinity (SQLite's own type-name rules).
    private static String placeholderFor(String declaredType) {
        String type = declaredType.toUpperCase();
        if (type.contains("INT") || type.contains("BOOL")) {
            return "0";
        }
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) {
            return "''";
        }
        if (type.contains("BLOB")) {
            return "X''";
        }
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) {
            return "0.0";
        }
        if (type.contains("TIMESTAMP") || type.contains("DATETIME")) {
            return "CURRENT_TIMESTAMP";
        }
        if (type.contains("DATE")) {
            return "CURRENT_DATE";
        }
        if (type.contains("TIME")) {
            return "CURRENT_TIME";
        }
        return type.isEmpty() ? "''" : "0";
    }

    // The SQLite driver doesn't report which attached database a result column came from, so a table
    // name present in more than one attached database can't be targeted safely.
    @Override
    public boolean isTableReferenceAmbiguous(String catalog, String schema, String table) {
        int matches = 0;
        try {
            java.sql.Connection connection = DatabaseConnection.getInstance().getConnection();
            for (String database : new SqliteCatalog(connection).databases()) {
                String sql = "SELECT 1 FROM " + quoteIdentifier(database) + ".sqlite_master "
                        + "WHERE type IN ('table', 'view') AND name = ? COLLATE NOCASE";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, table);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            matches++;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("isTableReferenceAmbiguous failed", e);
            return true;
        }
        return matches > 1;
    }

    @Override
    public SqlSyntax getSqlSyntax() {
        return SqlSyntax.SQLITE;
    }

    @Override
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        String target = databaseName != null && !databaseName.isBlank()
                ? SqliteCatalog.qualified(databaseName, tableName)
                : SqliteCatalog.quote(tableName);
        return "SELECT * FROM " + target + " LIMIT " + limit + ";";
    }

    @Override
    public String generateRowInsertScript(ObservableList<String> row, TableCell<TableRowData, String> cell) {
        try {
            ResultTable table = (ResultTable) cell.getTableView();
            String tableName = table.getTableName();
            StringBuilder insertScript = new StringBuilder();

            ObservableList<TableColumn<TableRowData, ?>> tableColumns = table.getColumns();

            List<String> columnNames = new ArrayList<>();
            List<String> columnTypes = new ArrayList<>();

            // Get column info using PRAGMA
            String columnQuery = "PRAGMA table_info(" + tableName + ")";
            PreparedStatement columnStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
            ResultSet columns = columnStmt.executeQuery();

            Map<String, String> columnTypeMap = new HashMap<>();
            while (columns.next()) {
                columnTypeMap.put(columns.getString("name"), columns.getString("type"));
            }
            columns.close();
            columnStmt.close();

            for (TableColumn<TableRowData, ?> col : tableColumns) {
                String colName = col.getText();
                columnNames.add(quoteIdentifier(colName));
                columnTypes.add(columnTypeMap.getOrDefault(colName, "TEXT"));
            }

            insertScript.append("INSERT INTO ").append(table.getQualifiedTableName())
                    .append(" (")
                    .append(String.join(", ", columnNames))
                    .append(")\nVALUES (");

            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) {
                    insertScript.append(", ");
                }

                String value = (i < row.size()) ? row.get(i) : null;

                if (value == null || value.equals("NULL") || value.equals("null")) {
                    insertScript.append("NULL");
                } else {
                    String columnType = columnTypes.get(i).toUpperCase();

                    if (columnType.contains("INT") || columnType.contains("REAL") ||
                            columnType.contains("NUMERIC") || columnType.contains("DECIMAL")) {
                        insertScript.append(value.isEmpty() ? "NULL" : value);
                    } else {
                        insertScript.append("'").append(value.replace("'", "''")).append("'");
                    }
                }
            }

            insertScript.append(");");

            return insertScript.toString();
        } catch (Exception e) {
            log.error("generateRowInsertScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
    }


    @Override
    public String generateCreateTableSQL(String databaseName, String tableName, List<ColumnData> columns, List<ForeignKeyData> foreignKeys) {
        String target = databaseName != null && !databaseName.isEmpty()
                ? SqliteCatalog.qualified(databaseName, tableName)
                : SqliteCatalog.quote(tableName);

        List<String> primaryKeys = columns.stream().filter(ColumnData::isPrimaryKey).map(ColumnData::getColumnName).toList();
        // AUTOINCREMENT only exists as part of "INTEGER PRIMARY KEY AUTOINCREMENT" on a single column
        ColumnData autoIncrementKey = primaryKeys.size() == 1
                ? columns.stream().filter(c -> c.isPrimaryKey() && c.isAutoIncrement()).findFirst().orElse(null)
                : null;

        List<String> definitions = new ArrayList<>();
        for (ColumnData column : columns) {
            StringBuilder definition = new StringBuilder("    ").append(SqliteCatalog.quote(column.getColumnName()));
            if (column == autoIncrementKey) {
                definition.append(" INTEGER PRIMARY KEY AUTOINCREMENT");
            } else {
                if (column.getDataType() != null && !column.getDataType().isEmpty()) {
                    definition.append(' ').append(column.getDataType());
                }
                if (column.isNotNull() || column.isPrimaryKey()) {
                    definition.append(" NOT NULL");
                }
                if (column.isUnique() && !column.isPrimaryKey()) {
                    definition.append(" UNIQUE");
                }
            }
            definitions.add(definition.toString());
        }

        if (!primaryKeys.isEmpty() && autoIncrementKey == null) {
            definitions.add("    PRIMARY KEY (" + quoteAll(primaryKeys) + ")");
        }

        for (ForeignKeyData fk : foreignKeys) {
            StringBuilder definition = new StringBuilder("    CONSTRAINT ").append(SqliteCatalog.quote(fk.getConstraintName()))
                    .append(" FOREIGN KEY (").append(quoteAll(fk.allLocalColumns())).append(") REFERENCES ")
                    .append(SqliteCatalog.quote(fk.getReferenceTable())).append(" (").append(quoteAll(fk.allReferenceColumns())).append(")");
            if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                definition.append(" ON DELETE ").append(fk.getOnDelete());
            }
            if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                definition.append(" ON UPDATE ").append(fk.getOnUpdate());
            }
            definitions.add(definition.toString());
        }

        return "CREATE TABLE " + target + " (\n" + String.join(",\n", definitions) + "\n);";
    }

    private static String quoteAll(List<String> identifiers) {
        StringJoiner joined = new StringJoiner(", ");
        identifiers.forEach(identifier -> joined.add(SqliteCatalog.quote(identifier)));
        return joined.toString();
    }

    @Override
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        String fullTableName = databaseName != null && !databaseName.isEmpty()
                ? SqliteCatalog.qualified(databaseName, tableName)
                : SqliteCatalog.quote(tableName);

        try (PreparedStatement statement = DatabaseConnection.getInstance().getConnection()
                .prepareStatement("DROP TABLE IF EXISTS " + fullTableName)) {
            statement.executeUpdate();
        }
    }

    @Override
    public String[] getDataTypes() {
        return this.dataTypes;
    }

    // A SQLite "database" is a file; it can only be attached, which needs a path, not just a name.
    @Override
    public void createDatabase(String databaseName) throws SQLException {
        throw new SQLFeatureNotSupportedException("A SQLite database is a file. Use: ATTACH DATABASE '<file>' AS <name>");
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        throw new SQLFeatureNotSupportedException("A SQLite database is a file. Use: DETACH DATABASE <name>");
    }

    @Override
    public ArrayList<String> getUsers() throws SQLException {
        ArrayList<String> users = new ArrayList<>();
        users.add("(SQLite - No users)");
        return users;
    }

    @Override
    public double getTableSize(String database, String table) throws SQLException {
        return catalog().sizeMegabytes(database, table);
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        // SQLite has no current database: names resolve in main first, then in the attached ones
    }

    /**
     * SQLite can rename tables/columns and add or drop plain columns in place. Anything else (changing a
     * column's type, NOT NULL, key or UNIQUE setting, or the foreign keys) needs the whole table rebuilt, which
     * would silently lose defaults and CHECK constraints this editor does not know about, so it is refused.
     */
    @Override
    public String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                        List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                        Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys,
                                        Map<String, String> columnRenames) throws SQLException {
        if (!sameForeignKeys(oldForeignKeys, newForeignKeys)) {
            throw unsupported("changing foreign keys");
        }

        String table = SqliteCatalog.qualified(SqliteCatalog.schemaOrMain(databaseName), oldTableName);

        Map<String, String> newToOld = new HashMap<>();
        columnRenames.forEach((oldName, newName) -> newToOld.put(newName, oldName));

        List<String> renames = new ArrayList<>();
        List<String> drops = new ArrayList<>();
        List<String> adds = new ArrayList<>();
        Set<String> keptOldColumns = new HashSet<>();

        for (ColumnData newColumn : newColumns) {
            String oldName = newToOld.getOrDefault(newColumn.getColumnName(), newColumn.getColumnName());
            ColumnData oldColumn = oldColumns.stream().filter(c -> c.getColumnName().equals(oldName)).findFirst().orElse(null);

            if (oldColumn == null) {
                if (newColumn.isPrimaryKey() || newColumn.isUnique() || newColumn.isAutoIncrement()) {
                    throw unsupported("adding a primary key, unique or auto-increment column (" + newColumn.getColumnName() + ")");
                }
                if (newColumn.isNotNull()) {
                    throw unsupported("adding the NOT NULL column " + newColumn.getColumnName() + " (it would need a default value)");
                }
                adds.add("ALTER TABLE " + table + " ADD COLUMN " + SqliteCatalog.quote(newColumn.getColumnName())
                        + (newColumn.getDataType() == null || newColumn.getDataType().isEmpty() ? "" : " " + newColumn.getDataType()));
                continue;
            }

            keptOldColumns.add(oldName);
            boolean changed = !Objects.equals(oldColumn.getDataType() == null ? "" : oldColumn.getDataType().toUpperCase(),
                    newColumn.getDataType() == null ? "" : newColumn.getDataType().toUpperCase())
                    || oldColumn.isNotNull() != newColumn.isNotNull()
                    || oldColumn.isPrimaryKey() != newColumn.isPrimaryKey()
                    || oldColumn.isUnique() != newColumn.isUnique()
                    || oldColumn.isAutoIncrement() != newColumn.isAutoIncrement();
            if (changed) {
                throw unsupported("changing the type, NOT NULL, key or unique setting of column " + newColumn.getColumnName());
            }
            if (!oldName.equals(newColumn.getColumnName())) {
                renames.add("ALTER TABLE " + table + " RENAME COLUMN " + SqliteCatalog.quote(oldName)
                        + " TO " + SqliteCatalog.quote(newColumn.getColumnName()));
            }
        }

        for (ColumnData oldColumn : oldColumns) {
            if (!keptOldColumns.contains(oldColumn.getColumnName())) {
                drops.add("ALTER TABLE " + table + " DROP COLUMN " + SqliteCatalog.quote(oldColumn.getColumnName()));
            }
        }

        List<String> statements = new ArrayList<>();
        statements.addAll(renames);
        statements.addAll(drops);
        statements.addAll(adds);
        if (!oldTableName.equals(newTableName)) {
            statements.add("ALTER TABLE " + table + " RENAME TO " + SqliteCatalog.quote(newTableName));
        }
        return statements.isEmpty() ? "" : String.join(";\n", statements) + ";";
    }

    private static boolean sameForeignKeys(Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys) {
        if (oldForeignKeys.size() != newForeignKeys.size()) {
            return false;
        }
        for (ForeignKeyData newKey : newForeignKeys) {
            ForeignKeyData oldKey = oldForeignKeys.get(newKey.getConstraintName());
            if (oldKey == null
                    || !oldKey.allLocalColumns().equals(newKey.allLocalColumns())
                    || !oldKey.getReferenceTable().equals(newKey.getReferenceTable())
                    || !oldKey.allReferenceColumns().equals(newKey.allReferenceColumns())
                    || !Objects.equals(oldKey.getOnDelete(), newKey.getOnDelete())
                    || !Objects.equals(oldKey.getOnUpdate(), newKey.getOnUpdate())) {
                return false;
            }
        }
        return true;
    }

    private static SQLFeatureNotSupportedException unsupported(String what) {
        return new SQLFeatureNotSupportedException("SQLite cannot do this in place: " + what
                + ". It would need the table to be rebuilt; do that with an explicit script instead.");
    }
}
