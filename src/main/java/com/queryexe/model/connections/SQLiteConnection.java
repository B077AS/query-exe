package com.queryexe.model.connections;

import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
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

    private final String[] KEYWORDS = new String[]{
            // Basic SQL Keywords
            "SELECT", "FROM", "WHERE", "AND", "OR", "INSERT", "INTO", "VALUES",
            "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "INDEX",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "AS", "DISTINCT",
            "EXISTS", "IN", "BETWEEN", "LIKE", "IS", "UNIQUE", "CONSTRAINT", "DEFAULT",
            "CHECK", "COLLATE", "TEMPORARY", "TEMP", "VIEW", "TRIGGER",

            // SQLite Specific Keywords
            "AUTOINCREMENT", "WITHOUT", "ROWID", "STRICT", "GENERATED", "STORED",
            "VIRTUAL", "IF", "ABORT", "FAIL", "IGNORE", "REPLACE", "ROLLBACK",
            "ATTACH", "DETACH", "DATABASE", "PRAGMA", "VACUUM", "REINDEX",
            "ANALYZE", "EXPLAIN", "QUERY", "PLAN",

            // Control Flow
            "CASE", "WHEN", "THEN", "ELSE", "END", "IFNULL", "NULLIF", "COALESCE",
            "IIF", "INSTR", "SUBSTR", "SUBSTRING",

            // Joins and Set Operations
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "NATURAL",
            "UNION", "INTERSECT", "EXCEPT", "ALL",

            // Grouping and Ordering
            "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET",

            // Window Functions (SQLite 3.25+)
            "OVER", "PARTITION", "RANGE", "ROWS", "PRECEDING", "FOLLOWING",
            "UNBOUNDED", "CURRENT", "EXCLUDE", "TIES", "NULLS", "FIRST", "LAST",

            // Aggregate Functions
            "COUNT", "SUM", "AVG", "MAX", "MIN", "TOTAL", "GROUP_CONCAT",

            // String Functions
            "LENGTH", "LOWER", "UPPER", "TRIM", "LTRIM", "RTRIM", "REPLACE",
            "ROUND", "ABS", "RANDOM", "LIKE", "GLOB", "REGEXP", "MATCH",

            // Date/Time (limited in SQLite)
            "DATE", "TIME", "DATETIME", "JULIANDAY", "STRFTIME", "NOW",

            // Transaction Control
            "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE", "TRANSACTION",
            "DEFERRED", "IMMEDIATE", "EXCLUSIVE",

            // Data Manipulation
            "UPSERT", "ON", "CONFLICT", "DO", "NOTHING", "CASCADE", "RESTRICT",
            "SET", "NO", "ACTION",

            // SQLite Functions
            "TYPEOF", "CAST", "PRINTF", "QUOTE", "UNICODE", "ZEROBLOB", "HEX",
            "UNHEX", "RANDOMBLOB", "SOUNDEX", "LOAD_EXTENSION",

            // Common Table Expressions
            "WITH", "RECURSIVE",

            // JSON Functions (SQLite 3.45+)
            "JSON", "JSON_EXTRACT", "JSON_ARRAY", "JSON_OBJECT", "JSON_VALID",
            "JSON_TYPE", "JSON_ARRAY_LENGTH", "JSON_INSERT", "JSON_REPLACE",
            "JSON_SET", "JSON_REMOVE", "JSON_PATCH", "JSON_EACH", "JSON_TREE"
    };

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

    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String databaseName) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            // Get all tables from the specified database (or main if null/empty)
            String schema = (databaseName == null || databaseName.isEmpty()) ? "main" : databaseName;

            ArrayList<String> tableNames = getTablesForDatabase(schema);

            for (String tableName : tableNames) {
                ArrayList<ColumnData> columns = getColumnsForTable(schema, tableName);
                tablesMap.put(tableName, columns);
            }

            return tablesMap;
        } catch (SQLException e) {
            log.error("getAllTablesAndColumns failed", e);
        }
        return null;
    }

    @Override
    public ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException {
        ArrayList<String> tableNames = new ArrayList<>();

        // Get all tables from the specified database (or main if null/empty)
        String schema = (databaseName == null || databaseName.isEmpty()) ? "main" : databaseName;

        String query = "SELECT name FROM " + schema + ".sqlite_master " +
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "ORDER BY name";

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                tableNames.add(rs.getString("name"));
            }
        }

        return tableNames;
    }

    @Override
    public ArrayList<ColumnData> getColumnsForTable(String schemaName, String tableName) throws SQLException {
        ArrayList<ColumnData> columns = new ArrayList<>();
        
        // Use "main" as default schema if null/empty
        String schema = (schemaName == null || schemaName.isEmpty()) ? "main" : schemaName;

        // Get table info using PRAGMA table_info with schema prefix
        String columnQuery = "PRAGMA " + schema + ".table_info(" + tableName + ")";
        PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
        ResultSet resultSet = columnStatement.executeQuery();

        // Check table definition once for AUTOINCREMENT
        String createTableQuery = "SELECT sql FROM " + schema + ".sqlite_master WHERE type = 'table' AND name = ?";
        PreparedStatement createStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(createTableQuery);
        createStmt.setString(1, tableName);
        ResultSet createRs = createStmt.executeQuery();
        String createSql = "";
        if (createRs.next()) {
            createSql = createRs.getString("sql");
            if (createSql == null) createSql = "";
        }
        createStmt.close();
        createRs.close();

        while (resultSet.next()) {
            String columnName = resultSet.getString("name");
            String dataType = resultSet.getString("type").toUpperCase();
            boolean primaryKey = resultSet.getInt("pk") > 0;
            boolean notNull = resultSet.getInt("notnull") == 1;
            boolean unique = false; // Will be determined below
            boolean autoIncrement = false;

            // Check if it's autoincrement (only for INTEGER PRIMARY KEY)
            if (primaryKey && dataType.toUpperCase().contains("INTEGER")) {
                if (createSql.toUpperCase().contains("AUTOINCREMENT")) {
                    autoIncrement = true;
                }
            }

            // Check for UNIQUE constraint
            if (createSql.toUpperCase().contains("UNIQUE")) {
                // Simple check - could be enhanced with regex if needed
                String upperSql = createSql.toUpperCase();
                String upperColName = columnName.toUpperCase();
                if (upperSql.contains(upperColName) &&
                        (upperSql.contains(upperColName + " UNIQUE") ||
                                upperSql.contains("UNIQUE(" + upperColName + ")") ||
                                upperSql.contains("UNIQUE (" + upperColName + ")"))) {
                    unique = true;
                }
            }

            ColumnData columnData = new ColumnData(
                    columnName,
                    dataType,
                    primaryKey,
                    notNull,
                    unique,
                    autoIncrement
            );
            columns.add(columnData);
        }
        
        columnStatement.close();
        resultSet.close();

        return columns;
    }

    @Override
    public DetailedColumnData getDetailedColumnInfo(String schemaName, String tableName, String columnName) throws SQLException {
        DetailedColumnData info = new DetailedColumnData();

        // Use "main" as default schema if null/empty
        String schema = (schemaName == null || schemaName.isEmpty()) ? "main" : schemaName;

        // 1. Get basic column metadata using PRAGMA table_info
        String columnQuery = "PRAGMA " + schema + ".table_info(" + tableName + ")";
        PreparedStatement columnStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
        ResultSet rs = columnStmt.executeQuery();

        // Get CREATE TABLE statement for additional analysis
        String createTableQuery = "SELECT sql FROM " + schema + ".sqlite_master WHERE type = 'table' AND name = ?";
        PreparedStatement createStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(createTableQuery);
        createStmt.setString(1, tableName);
        ResultSet createRs = createStmt.executeQuery();
        String createSql = "";
        if (createRs.next()) {
            createSql = createRs.getString("sql");
            if (createSql == null) createSql = "";
        }
        createStmt.close();
        createRs.close();

        boolean found = false;
        int ordinalPosition = 0;

        while (rs.next()) {
            String colName = rs.getString("name");
            ordinalPosition++;

            if (colName.equals(columnName)) {
                found = true;

                info.setColumnName(colName);
                info.setTableName(tableName);
                info.setSchemaName(schema);
                info.setOrdinalPosition(ordinalPosition);

                String dataType = rs.getString("type").toUpperCase();
                info.setDataType(dataType);
                info.setColumnType(dataType); // SQLite uses same for both

                // SQLite doesn't track character/octet lengths or numeric precision/scale in PRAGMA
                // We could parse the type string (e.g., "VARCHAR(50)") if needed
                if (dataType.contains("(")) {
                    // Extract size/precision if present in type definition
                    try {
                        int start = dataType.indexOf("(");
                        int end = dataType.indexOf(")");
                        String sizeStr = dataType.substring(start + 1, end);
                        if (sizeStr.contains(",")) {
                            String[] parts = sizeStr.split(",");
                            info.setNumericPrecision(Integer.parseInt(parts[0].trim()));
                            info.setNumericScale(Integer.parseInt(parts[1].trim()));
                        } else {
                            long size = Long.parseLong(sizeStr.trim());
                            if (dataType.startsWith("VARCHAR") || dataType.startsWith("CHAR") ||
                                    dataType.startsWith("TEXT")) {
                                info.setCharacterMaximumLength(size);
                                info.setCharacterOctetLength(size);
                            } else {
                                info.setNumericPrecision((int) size);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                }

                // SQLite doesn't have character sets or collations in the same way
                info.setCharacterSetName(null);
                info.setCollationName(null);

                boolean isPrimaryKey = rs.getInt("pk") > 0;
                info.setPrimaryKey(isPrimaryKey);
                info.setNullable(rs.getInt("notnull") == 0);

                // Set column key
                if (isPrimaryKey) {
                    info.setColumnKey("PRI");
                } else {
                    info.setColumnKey("");
                }

                // Get default value
                String defaultValue = rs.getString("dflt_value");
                info.setColumnDefault(defaultValue);

                // SQLite doesn't have column comments
                info.setColumnComment(null);

                // Check for AUTOINCREMENT
                boolean autoIncrement = false;
                if (isPrimaryKey && dataType.contains("INTEGER")) {
                    if (createSql.toUpperCase().contains("AUTOINCREMENT")) {
                        autoIncrement = true;
                    }
                }
                info.setAutoIncrement(autoIncrement);

                // Check for UNIQUE constraint
                boolean isUnique = false;
                String upperSql = createSql.toUpperCase();
                String upperColName = columnName.toUpperCase();
                if (upperSql.contains("UNIQUE")) {
                    if (upperSql.contains(upperColName + " UNIQUE") ||
                            upperSql.contains("UNIQUE(" + upperColName + ")") ||
                            upperSql.contains("UNIQUE (" + upperColName + ")")) {
                        isUnique = true;
                    }
                }
                info.setUnique(isUnique);

                break;
            }
        }

        columnStmt.close();
        rs.close();

        if (!found) {
            throw new SQLException("Column '" + columnName + "' not found in table '" + tableName + "'");
        }

        // 2. Get indexes that include this column using PRAGMA index_list and index_info
        String indexListQuery = "PRAGMA " + schema + ".index_list(" + tableName + ")";
        PreparedStatement indexListStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(indexListQuery);
        ResultSet indexListRs = indexListStmt.executeQuery();

        List<Map<String, String>> indexes = new ArrayList<>();

        while (indexListRs.next()) {
            String indexName = indexListRs.getString("name");
            boolean isUnique = indexListRs.getInt("unique") == 1;
            String origin = indexListRs.getString("origin"); // "c" = CREATE INDEX, "u" = UNIQUE, "pk" = PRIMARY KEY

            // Get columns in this index
            String indexInfoQuery = "PRAGMA " + schema + ".index_info(" + indexName + ")";
            PreparedStatement indexInfoStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(indexInfoQuery);
            ResultSet indexInfoRs = indexInfoStmt.executeQuery();

            while (indexInfoRs.next()) {
                String idxColName = indexInfoRs.getString("name");
                if (idxColName != null && idxColName.equals(columnName)) {
                    Map<String, String> index = new HashMap<>();
                    index.put("INDEX_NAME", indexName);

                    // Map origin to index type
                    String indexType = "BTREE"; // SQLite primarily uses B-tree
                    if ("pk".equals(origin)) {
                        indexType = "PRIMARY";
                    }
                    index.put("INDEX_TYPE", indexType);

                    index.put("NON_UNIQUE", isUnique ? "0" : "1");
                    index.put("SEQ_IN_INDEX", String.valueOf(indexInfoRs.getInt("seqno") + 1));
                    index.put("CARDINALITY", null); // SQLite doesn't provide cardinality easily

                    indexes.add(index);
                    break;
                }
            }

            indexInfoStmt.close();
            indexInfoRs.close();
        }

        indexListStmt.close();
        indexListRs.close();
        info.setIndexes(indexes);

        // 3. Get foreign key references (where this column references another table)
        String fkListQuery = "PRAGMA " + schema + ".foreign_key_list(" + tableName + ")";
        PreparedStatement fkStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(fkListQuery);
        ResultSet fkRs = fkStmt.executeQuery();

        List<Map<String, String>> foreignKeys = new ArrayList<>();

        while (fkRs.next()) {
            String fkColumn = fkRs.getString("from");
            if (fkColumn.equals(columnName)) {
                Map<String, String> fk = new HashMap<>();

                // Generate constraint name (SQLite doesn't always name them)
                int fkId = fkRs.getInt("id");
                String constraintName = "fk_" + tableName + "_" + fkId;

                fk.put("CONSTRAINT_NAME", constraintName);
                fk.put("REFERENCED_TABLE_SCHEMA", schema);
                fk.put("REFERENCED_TABLE_NAME", fkRs.getString("table"));
                fk.put("REFERENCED_COLUMN_NAME", fkRs.getString("to"));

                // Map SQLite actions to standard SQL terms
                String onUpdate = fkRs.getString("on_update");
                String onDelete = fkRs.getString("on_delete");
                fk.put("UPDATE_RULE", onUpdate != null ? onUpdate.toUpperCase() : "NO ACTION");
                fk.put("DELETE_RULE", onDelete != null ? onDelete.toUpperCase() : "NO ACTION");

                foreignKeys.add(fk);
            }
        }

        fkStmt.close();
        fkRs.close();
        info.setForeignKeyReferences(foreignKeys);

        // 4. Get foreign keys that reference this column (incoming references)
        // This requires checking all tables in the database
        String allTablesQuery = "SELECT name FROM " + schema + ".sqlite_master " +
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%'";
        PreparedStatement allTablesStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(allTablesQuery);
        ResultSet allTablesRs = allTablesStmt.executeQuery();

        List<Map<String, String>> referencedBy = new ArrayList<>();

        while (allTablesRs.next()) {
            String otherTable = allTablesRs.getString("name");

            // Check foreign keys of this table
            String otherFkQuery = "PRAGMA " + schema + ".foreign_key_list(" + otherTable + ")";
            PreparedStatement otherFkStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(otherFkQuery);
            ResultSet otherFkRs = otherFkStmt.executeQuery();

            while (otherFkRs.next()) {
                String refTable = otherFkRs.getString("table");
                String refColumn = otherFkRs.getString("to");

                if (refTable.equals(tableName) && refColumn.equals(columnName)) {
                    Map<String, String> ref = new HashMap<>();

                    int fkId = otherFkRs.getInt("id");
                    String constraintName = "fk_" + otherTable + "_" + fkId;

                    ref.put("TABLE_SCHEMA", schema);
                    ref.put("TABLE_NAME", otherTable);
                    ref.put("COLUMN_NAME", otherFkRs.getString("from"));
                    ref.put("CONSTRAINT_NAME", constraintName);

                    String onUpdate = otherFkRs.getString("on_update");
                    String onDelete = otherFkRs.getString("on_delete");
                    ref.put("UPDATE_RULE", onUpdate != null ? onUpdate.toUpperCase() : "NO ACTION");
                    ref.put("DELETE_RULE", onDelete != null ? onDelete.toUpperCase() : "NO ACTION");

                    referencedBy.add(ref);
                }
            }

            otherFkStmt.close();
            otherFkRs.close();
        }

        allTablesStmt.close();
        allTablesRs.close();
        info.setReferencedByForeignKeys(referencedBy);

        return info;
    }

    @Override
    public ArrayList<String> getDatabases(String name) {
        ArrayList<String> databases = new ArrayList<>();
        try {
            // SQLite can have attached databases
            String query = "PRAGMA database_list";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String dbName = result.getString("name");
                databases.add(dbName);
            }

            statement.close();
            result.close();

            // If no databases found, add "main" as default
            if (databases.isEmpty()) {
                databases.add("main");
            }

            return databases;
        } catch (SQLException e) {
            log.error("getDatabases failed", e);
            // Fallback to main database
            databases.add("main");
        }
        return databases;
    }

    @Override
    public String generateCreateScript(String tableName, String dbName) {
        try {
            StringBuilder script = new StringBuilder();

            // Get the original CREATE TABLE statement
            String getCreateQuery = "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?";
            PreparedStatement getCreateStmt = DatabaseConnection.getInstance().getConnection().prepareStatement(getCreateQuery);
            getCreateStmt.setString(1, tableName);
            ResultSet createRs = getCreateStmt.executeQuery();

            if (createRs.next()) {
                String originalSql = createRs.getString("sql");
                if (originalSql != null) {
                    script.append("-- Original table structure\n");
                    script.append(originalSql).append(";\n\n");
                }
            } else {
                // Fallback: generate CREATE script from metadata
                DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

                // Get primary keys
                Set<String> primaryKeys = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
                    while (pkRs.next()) {
                        primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                    }
                }

                script.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

                // Get columns
                try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                    boolean first = true;
                    while (columns.next()) {
                        if (!first) script.append(",\n");
                        first = false;

                        String columnName = columns.getString("COLUMN_NAME");
                        String dataType = columns.getString("TYPE_NAME");
                        int columnSize = columns.getInt("COLUMN_SIZE");
                        String nullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls ? " NOT NULL" : "";

                        script.append("    ").append(columnName).append(" ");

                        // SQLite type mapping
                        switch (dataType.toUpperCase()) {
                            case "VARCHAR", "NVARCHAR" -> script.append("TEXT");
                            case "INTEGER", "INT" -> {
                                script.append("INTEGER");
                                if (primaryKeys.contains(columnName)) {
                                    script.append(" PRIMARY KEY");
                                }
                            }
                            case "REAL", "FLOAT", "DOUBLE" -> script.append("REAL");
                            case "BLOB", "BINARY" -> script.append("BLOB");
                            default -> script.append("TEXT");
                        }

                        script.append(nullable);

                        // Add default value if exists
                        String defaultValue = columns.getString("COLUMN_DEF");
                        if (defaultValue != null && !defaultValue.isEmpty()) {
                            script.append(" DEFAULT ").append(defaultValue);
                        }
                    }

                    script.append("\n);");
                }
            }

            getCreateStmt.close();
            createRs.close();

            return script.toString();
        } catch (Exception e) {
            log.error("generateCreateScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public Map<String, ForeignKeyData> extractForeignKeys(String dbName, String tableName) throws SQLException {
        Map<String, ForeignKeyData> foreignKeys = new HashMap<>();
        DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

        try (ResultSet fkRs = metaData.getImportedKeys(dbName, null, tableName)) {
            while (fkRs.next()) {
                String fkColumnName = fkRs.getString("FKCOLUMN_NAME");
                String constraintName = fkRs.getString("FK_NAME");  // Get constraint name

                foreignKeys.put(constraintName, new ForeignKeyData(
                        constraintName,                      // Constraint name
                        fkColumnName,                        // Local column
                        fkRs.getString("PKTABLE_NAME"),      // Reference table
                        fkRs.getString("PKCOLUMN_NAME"),     // Reference column
                        fkRs.getInt("DELETE_RULE"),          // Delete rule
                        fkRs.getInt("UPDATE_RULE")           // Update rule
                ));
            }
        }
        return foreignKeys;
    }

    @Override
    public String generateInsertScript(String tableName, String dbName) {
        try {
            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

            // Try different approaches to get columns
            ResultSet columns = null;

            // Method 1: Using catalog
            try {
                String catalog = DatabaseConnection.getInstance().getConnection().getCatalog();
                columns = metaData.getColumns(catalog, null, tableName, null);
            } catch (Exception e) {
                // Fall through to next method
            }

            // Method 2: Using database name as catalog
            if (columns == null || !columns.next()) {
                try {
                    if (columns != null) columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(dbName, null, tableName, null);
            }

            // Method 3: Using null catalog
            if (columns == null || !columns.next()) {
                try {
                    if (columns != null) columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(null, null, tableName, null);
            }

            // Reset to beginning if we used next() to check
            if (columns != null) {
                columns.close();
                // Get fresh ResultSet - SQLite typically works best with null catalog
                columns = metaData.getColumns(null, null, tableName, null);
            }

            List<String> columnNames = new ArrayList<>();
            List<String> columnValues = new ArrayList<>();

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME").toUpperCase();
                int sqlType = columns.getInt("DATA_TYPE");
                boolean isNullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String autoIncrement = columns.getString("IS_AUTOINCREMENT");
                boolean isAutoIncrement = "YES".equalsIgnoreCase(autoIncrement);

                // Skip auto-increment columns (INTEGER PRIMARY KEY in SQLite)
                if (isAutoIncrement) {
                    continue;
                }

                columnNames.add("\"" + columnName + "\"");

                // Generate appropriate default values based on data type
                String defaultValue;
                if (isNullable) {
                    defaultValue = "NULL";
                } else {
                    switch (sqlType) {
                        case Types.BOOLEAN, Types.BIT -> defaultValue = "0";
                        case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> defaultValue = "0";
                        case Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL ->
                                defaultValue = "0.00";
                        case Types.DATE -> defaultValue = "DATE('now')";
                        case Types.TIME -> defaultValue = "TIME('now')";
                        case Types.TIMESTAMP -> defaultValue = "DATETIME('now')";
                        case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> defaultValue = "''";
                        case Types.OTHER -> {
                            // Handle SQLite specific types that map to Types.OTHER
                            if ("DATETIME".equals(dataType)) {
                                defaultValue = "DATETIME('now')";
                            } else if ("YEAR".equals(dataType)) {
                                defaultValue = "strftime('%Y', 'now')";
                            } else if ("JSON".equals(dataType)) {
                                defaultValue = "'{}'";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                             Types.CLOB, Types.NCLOB -> {
                            // SQLite is very flexible with text types
                            if ("TEXT".equals(dataType)) {
                                defaultValue = "''";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        default -> {
                            // Additional checks for SQLite specific types
                            if ("DATETIME".equals(dataType)) {
                                defaultValue = "DATETIME('now')";
                            } else if ("NUMERIC".equals(dataType)) {
                                defaultValue = "0";
                            } else {
                                defaultValue = isNullable ? "NULL" : "''";
                            }
                        }
                    }
                }

                columnValues.add(defaultValue);
            }

            columns.close();

            if (columnNames.isEmpty()) {
                return "-- ERROR: No columns found for table: " + tableName;
            }

            // Format the INSERT statement nicely
            insertScript.append("INSERT INTO \"").append(tableName).append("\"\n");
            insertScript.append("    (");

            // Add column names with proper formatting
            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) {
                    insertScript.append(",\n     ");
                }
                insertScript.append(columnNames.get(i));
            }

            insertScript.append(")\nVALUES\n    (");

            // Add values with proper formatting
            for (int i = 0; i < columnValues.size(); i++) {
                if (i > 0) {
                    insertScript.append(",\n     ");
                }
                insertScript.append(columnValues.get(i));
            }
            insertScript.append(");");
            return insertScript.toString();
        } catch (Exception e) {
            log.error("generateInsertScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
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
                columnNames.add(colName);
                columnTypes.add(columnTypeMap.getOrDefault(colName, "TEXT"));
            }

            insertScript.append("INSERT INTO ").append(tableName)
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
        StringBuilder sql = new StringBuilder();

        // Build the fully qualified table name
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "\"" + databaseName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        // Add columns
        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);

            sql.append("    \"").append(col.getColumnName()).append("\" ").append(col.getDataType());

            // Add AUTOINCREMENT if specified
            if (col.isAutoIncrement()) {
                sql.append(" AUTOINCREMENT");
            }

            // Add NOT NULL if specified
            if (col.isNotNull() || col.isPrimaryKey()) {
                sql.append(" NOT NULL");
            }

            // Add UNIQUE if specified and not a primary key
            if (col.isUnique() && !col.isPrimaryKey()) {
                sql.append(" UNIQUE");
            }

            // Track primary keys
            if (col.isPrimaryKey()) {
                primaryKeys.add(col.getColumnName());
            }

            // Add comma if not last column or if we have constraints to add
            if (i < columns.size() - 1 || !primaryKeys.isEmpty() || !foreignKeys.isEmpty()) {
                sql.append(",");
            }
            sql.append("\n");
        }

        // Add PRIMARY KEY constraint
        if (!primaryKeys.isEmpty()) {
            sql.append("    PRIMARY KEY (");
            for (int i = 0; i < primaryKeys.size(); i++) {
                sql.append("\"").append(primaryKeys.get(i)).append("\"");
                if (i < primaryKeys.size() - 1) {
                    sql.append(", ");
                }
            }
            sql.append(")");

            if (!foreignKeys.isEmpty()) {
                sql.append(",");
            }
            sql.append("\n");
        }

        // Add FOREIGN KEY constraints
        for (int i = 0; i < foreignKeys.size(); i++) {
            ForeignKeyData fk = foreignKeys.get(i);

            sql.append("    CONSTRAINT \"").append(fk.getConstraintName()).append("\" ")
                    .append("FOREIGN KEY (\"").append(fk.getLocalColumn()).append("\") ")
                    .append("REFERENCES \"").append(fk.getReferenceTable()).append("\" ")
                    .append("(\"").append(fk.getReferenceColumn()).append("\")");

            // Add ON DELETE action
            if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                sql.append(" ON DELETE ").append(fk.getOnDelete());
            }

            // Add ON UPDATE action
            if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                sql.append(" ON UPDATE ").append(fk.getOnUpdate());
            }

            if (i < foreignKeys.size() - 1) {
                sql.append(",");
            }
            sql.append("\n");
        }

        sql.append(");");

        return sql.toString();
    }

    @Override
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        // Build the fully qualified table name
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "\"" + databaseName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        String sql = "DROP TABLE IF EXISTS " + fullTableName;
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public String[] getKEYWORDS() {
        return Stream.concat(Stream.of(this.KEYWORDS), Stream.of(this.dataTypes))
                .toArray(String[]::new);
    }

    @Override
    public String[] getDataTypes() {
        return this.dataTypes;
    }
    
    @Override
    public void createDatabase(String databaseName) throws SQLException {
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {

    }

    @Override
    public ArrayList<String> getUsers() throws SQLException {
        ArrayList<String> users = new ArrayList<>();
        users.add("(SQLite - No users)");
        return users;
    }
    
    @Override
    public double getTableSize(String database, String table) throws SQLException {
        
        String query = """
            SELECT 
                ROUND(
                    (SELECT page_count FROM pragma_page_count()) * 
                    (SELECT page_size FROM pragma_page_size()) / 1024.0 / 1024.0 * 
                    (SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?) / 
                    (SELECT COUNT(*) FROM sqlite_master WHERE type='table'),
                    2
                ) AS total_size_mb
            """;
        
        PreparedStatement pstmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
        pstmt.setString(1, table);
        
        ResultSet rs = pstmt.executeQuery();
        double sizeMB = rs.next() ? rs.getDouble("total_size_mb") : -1;
        
        rs.close();
        pstmt.close();
        
        return sizeMB;
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {

    }
    
    @Override
    public String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName, List<ColumnData> oldColumns, List<ColumnData> newColumns, Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys, Map<String, String> columnRenames) throws SQLException {
        return "";
    }
}