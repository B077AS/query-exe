package com.queryexe.engine.sqlite;

import com.queryexe.connection.ConnectionProfile;


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

import com.queryexe.engine.sqlite.SqliteCatalog;
import com.queryexe.engine.sqlite.SqliteCatalog.ColumnInfo;
import com.queryexe.engine.sqlite.SqliteCatalog.ForeignKeyInfo;
import com.queryexe.engine.sqlite.SqliteCatalog.IndexInfo;
import com.queryexe.query.RowTarget;
import com.queryexe.schema.DetailedColumnData;
import com.queryexe.schema.ColumnData;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.connection.driver.DriverInfo;
import com.queryexe.schema.ForeignKeyData;


import com.queryexe.engine.*;

@Slf4j
public class SqliteReader extends SchemaReader {

    public SqliteReader(java.sql.Connection connection, ConnectionProfile profile) {
        super(connection, profile);
    }


    private SqliteCatalog catalog() {
        return new SqliteCatalog(connection);
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
    public List<com.queryexe.schema.IndexData> getIndexes(String container, String table) throws SQLException {
        List<com.queryexe.schema.IndexData> out = new ArrayList<>();
        for (IndexInfo index : catalog().indexes(container, table)) {
            out.add(com.queryexe.schema.IndexData.builder().name(index.getName()).unique(index.isUnique())
                    .primary("pk".equals(index.getOrigin())).columns(index.getColumns()).build());
        }
        return out;
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
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        String fullTableName = databaseName != null && !databaseName.isEmpty()
                ? SqliteCatalog.qualified(databaseName, tableName)
                : SqliteCatalog.quote(tableName);

        try (PreparedStatement statement = connection
                .prepareStatement("DROP TABLE IF EXISTS " + fullTableName)) {
            statement.executeUpdate();
        }
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
    public double getTableSize(String database, String table) throws SQLException {
        return catalog().sizeMegabytes(database, table);
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        // SQLite has no current database: names resolve in main first, then in the attached ones
    }
}
