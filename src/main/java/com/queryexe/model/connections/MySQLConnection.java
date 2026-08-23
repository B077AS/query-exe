package com.queryexe.model.connections;

import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;

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
public class MySQLConnection extends ConnectionObject {

    private String[] dataTypes = new String[]{
            "INT", "VARCHAR(255)", "DECIMAL", "BINARY(8)", "BLOB", "LONGBLOB",
            "MEDIUMBLOB", "TINYBLOB", "VARBINARY(255)", "DATE", "DATETIME", "TIME",
            "TIMESTAMP", "YEAR", "GEOMETRY", "GEOMETRYCOLLECTION", "LINESTRING",
            "MULTILINESTRING", "MULTIPOINT", "MULTIPOLYGON", "POINT", "POLYGON",
            "BIGINT", "DOUBLE", "FLOAT", "MEDIUMINT", "REAL",
            "SMALLINT", "TINYINT", "CHAR", "JSON", "NCHAR", "NVARCHAR(255)",
            "VARCHAR", "LONGTEXT", "MEDIUMTEXT", "TEXT", "TINYTEXT", "BIT",
            "BOOLEAN", "ENUM()", "SET()"
    };

    public MySQLConnection(String id, String connectionName, String dbType, String baseUrl, String host, String port, String databaseName, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, baseUrl, host, port, databaseName, username, password, driverInfo);
    }

    public MySQLConnection(String id, String connectionName, String dbType, String url, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, url, username, password, driverInfo);
    }

    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String name) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            ArrayList<String> tableNames = getTablesForDatabase(name);

            for (String tableName : tableNames) {
                ArrayList<ColumnData> columns = getColumnsForTable(name, tableName);

                tablesMap.put(tableName, columns);
            }

            return tablesMap;
        } catch (SQLException e) {
            log.error("getAllTablesAndColumns failed", e);
        }
        return null;
    }

    @Override
    public DetailedColumnData getDetailedColumnInfo(String schemaName, String tableName, String columnName) throws SQLException {
        DetailedColumnData info = new DetailedColumnData();

        // 1. Get basic column metadata from INFORMATION_SCHEMA
        String metadataQuery = """
                SELECT 
                    c.COLUMN_NAME,
                    c.TABLE_NAME,
                    c.TABLE_SCHEMA,
                    c.ORDINAL_POSITION,
                    c.DATA_TYPE,
                    c.COLUMN_TYPE,
                    c.CHARACTER_MAXIMUM_LENGTH,
                    c.CHARACTER_OCTET_LENGTH,
                    c.NUMERIC_PRECISION,
                    c.NUMERIC_SCALE,
                    c.CHARACTER_SET_NAME,
                    c.COLLATION_NAME,
                    c.COLUMN_KEY,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.EXTRA,
                    c.COLUMN_COMMENT,
                    c.COLUMN_KEY = 'PRI' as IS_PRIMARY_KEY,
                    (c.COLUMN_KEY = 'UNI' OR EXISTS (
                        SELECT 1 FROM information_schema.STATISTICS s 
                        WHERE s.TABLE_SCHEMA = c.TABLE_SCHEMA 
                          AND s.TABLE_NAME = c.TABLE_NAME 
                          AND s.COLUMN_NAME = c.COLUMN_NAME 
                          AND s.NON_UNIQUE = 0
                          AND s.INDEX_NAME != 'PRIMARY'
                    )) as IS_UNIQUE,
                    c.EXTRA LIKE '%auto_increment%' as IS_AUTO_INCREMENT
                FROM information_schema.COLUMNS c
                WHERE c.TABLE_SCHEMA = ? 
                  AND c.TABLE_NAME = ? 
                  AND c.COLUMN_NAME = ?
                """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(metadataQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    info.setColumnName(rs.getString("COLUMN_NAME"));
                    info.setTableName(rs.getString("TABLE_NAME"));
                    info.setSchemaName(rs.getString("TABLE_SCHEMA"));
                    info.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                    info.setDataType(rs.getString("DATA_TYPE"));
                    info.setColumnType(rs.getString("COLUMN_TYPE"));

                    // Handle nullable fields
                    if (rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null) {
                        info.setCharacterMaximumLength(rs.getLong("CHARACTER_MAXIMUM_LENGTH"));
                    }
                    if (rs.getObject("CHARACTER_OCTET_LENGTH") != null) {
                        info.setCharacterOctetLength(rs.getLong("CHARACTER_OCTET_LENGTH"));
                    }
                    if (rs.getObject("NUMERIC_PRECISION") != null) {
                        info.setNumericPrecision(rs.getInt("NUMERIC_PRECISION"));
                    }
                    if (rs.getObject("NUMERIC_SCALE") != null) {
                        info.setNumericScale(rs.getInt("NUMERIC_SCALE"));
                    }

                    info.setCharacterSetName(rs.getString("CHARACTER_SET_NAME"));
                    info.setCollationName(rs.getString("COLLATION_NAME"));
                    info.setColumnKey(rs.getString("COLUMN_KEY"));
                    info.setNullable(rs.getString("IS_NULLABLE").equals("YES"));
                    info.setColumnDefault(rs.getString("COLUMN_DEFAULT"));
                    info.setColumnComment(rs.getString("COLUMN_COMMENT"));
                    info.setPrimaryKey(rs.getInt("IS_PRIMARY_KEY") == 1);
                    info.setUnique(rs.getInt("IS_UNIQUE") == 1);
                    info.setAutoIncrement(rs.getInt("IS_AUTO_INCREMENT") == 1);
                }
            }
        }

        // 2. Get indexes that include this column
        String indexQuery = """
                SELECT 
                    INDEX_NAME,
                    INDEX_TYPE,
                    NON_UNIQUE,
                    SEQ_IN_INDEX,
                    CARDINALITY
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(indexQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            List<Map<String, String>> indexes = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> index = new java.util.HashMap<>();
                    index.put("INDEX_NAME", rs.getString("INDEX_NAME"));
                    index.put("INDEX_TYPE", rs.getString("INDEX_TYPE"));
                    index.put("NON_UNIQUE", rs.getString("NON_UNIQUE"));
                    index.put("SEQ_IN_INDEX", rs.getString("SEQ_IN_INDEX"));
                    index.put("CARDINALITY", rs.getString("CARDINALITY"));
                    indexes.add(index);
                }
            }
            info.setIndexes(indexes);
        }

        // 3. Get foreign key references (where this column references another table)
        String fkQuery = """
                SELECT 
                    kcu.CONSTRAINT_NAME,
                    kcu.REFERENCED_TABLE_SCHEMA,
                    kcu.REFERENCED_TABLE_NAME,
                    kcu.REFERENCED_COLUMN_NAME,
                    rc.UPDATE_RULE,
                    rc.DELETE_RULE
                FROM information_schema.KEY_COLUMN_USAGE kcu
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                    ON kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                    AND kcu.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA
                WHERE kcu.TABLE_SCHEMA = ?
                  AND kcu.TABLE_NAME = ?
                  AND kcu.COLUMN_NAME = ?
                  AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
                """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(fkQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            List<Map<String, String>> foreignKeys = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> fk = new java.util.HashMap<>();
                    fk.put("CONSTRAINT_NAME", rs.getString("CONSTRAINT_NAME"));
                    fk.put("REFERENCED_TABLE_SCHEMA", rs.getString("REFERENCED_TABLE_SCHEMA"));
                    fk.put("REFERENCED_TABLE_NAME", rs.getString("REFERENCED_TABLE_NAME"));
                    fk.put("REFERENCED_COLUMN_NAME", rs.getString("REFERENCED_COLUMN_NAME"));
                    fk.put("UPDATE_RULE", rs.getString("UPDATE_RULE"));
                    fk.put("DELETE_RULE", rs.getString("DELETE_RULE"));
                    foreignKeys.add(fk);
                }
            }
            info.setForeignKeyReferences(foreignKeys);
        }

        // 4. Get foreign keys that reference this column (incoming references)
        String referencedByQuery = """
                SELECT 
                    kcu.TABLE_SCHEMA,
                    kcu.TABLE_NAME,
                    kcu.COLUMN_NAME,
                    kcu.CONSTRAINT_NAME,
                    rc.UPDATE_RULE,
                    rc.DELETE_RULE
                FROM information_schema.KEY_COLUMN_USAGE kcu
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                    ON kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                    AND kcu.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA
                WHERE kcu.REFERENCED_TABLE_SCHEMA = ?
                  AND kcu.REFERENCED_TABLE_NAME = ?
                  AND kcu.REFERENCED_COLUMN_NAME = ?
                """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(referencedByQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            List<Map<String, String>> referencedBy = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> ref = new java.util.HashMap<>();
                    ref.put("TABLE_SCHEMA", rs.getString("TABLE_SCHEMA"));
                    ref.put("TABLE_NAME", rs.getString("TABLE_NAME"));
                    ref.put("COLUMN_NAME", rs.getString("COLUMN_NAME"));
                    ref.put("CONSTRAINT_NAME", rs.getString("CONSTRAINT_NAME"));
                    ref.put("UPDATE_RULE", rs.getString("UPDATE_RULE"));
                    ref.put("DELETE_RULE", rs.getString("DELETE_RULE"));
                    referencedBy.add(ref);
                }
            }
            info.setReferencedByForeignKeys(referencedBy);
        }

        return info;
    }

    @Override
    public ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException {
        ArrayList<String> tableNames = new ArrayList<>();
        String query = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?";

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);) {
            stmt.setString(1, databaseName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tableNames.add(rs.getString("table_name"));
                }
            }
        }

        return tableNames;
    }

    @Override
    public ArrayList<ColumnData> getColumnsForTable(String schemaName, String tableName) throws SQLException {
        ArrayList<ColumnData> columns = new ArrayList<ColumnData>();

        String columnQuery = "SELECT \n" +
                " c.COLUMN_NAME,\n" +
                " c.COLUMN_TYPE,\n" +
                " c.COLUMN_KEY = 'PRI' as IS_PRIMARY_KEY,\n" +
                " c.IS_NULLABLE = 'NO' as IS_NOT_NULL,\n" +
                " (c.COLUMN_KEY = 'UNI' OR EXISTS (\n" +
                "   SELECT 1 FROM information_schema.STATISTICS s \n" +
                "   WHERE s.TABLE_SCHEMA = c.TABLE_SCHEMA \n" +
                "     AND s.TABLE_NAME = c.TABLE_NAME \n" +
                "     AND s.COLUMN_NAME = c.COLUMN_NAME \n" +
                "     AND s.NON_UNIQUE = 0\n" +
                "     AND s.INDEX_NAME != 'PRIMARY'\n" +
                " )) as IS_UNIQUE,\n" +
                " c.EXTRA LIKE '%auto_increment%' as IS_AUTO_INCREMENT,\n" +
                " (SELECT s.INDEX_NAME FROM information_schema.STATISTICS s \n" +
                "  WHERE s.TABLE_SCHEMA = c.TABLE_SCHEMA \n" +
                "    AND s.TABLE_NAME = c.TABLE_NAME \n" +
                "    AND s.COLUMN_NAME = c.COLUMN_NAME \n" +
                "    AND s.NON_UNIQUE = 0\n" +
                "    AND s.INDEX_NAME != 'PRIMARY'\n" +
                "  LIMIT 1) as UNIQUE_INDEX_NAME\n" +
                "FROM information_schema.COLUMNS c\n" +
                "WHERE c.TABLE_SCHEMA = ? \n" +
                " AND c.TABLE_NAME = ? \n" +
                "ORDER BY c.ORDINAL_POSITION";
        
        PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
        columnStatement.setString(1, schemaName);
        columnStatement.setString(2, tableName);
        
        ResultSet resultSet = columnStatement.executeQuery();
        
        while (resultSet.next()) {
            String columnName = resultSet.getString("COLUMN_NAME");
            String dataType = resultSet.getString("COLUMN_TYPE").toUpperCase();
            boolean primaryKey = resultSet.getInt("IS_PRIMARY_KEY") == 1;
            boolean notNull = resultSet.getInt("IS_NOT_NULL") == 1;
            boolean unique = resultSet.getInt("IS_UNIQUE") == 1;
            boolean autoIncrement = resultSet.getInt("IS_AUTO_INCREMENT") == 1;
            String uniqueIndexName = resultSet.getString("UNIQUE_INDEX_NAME");

            ColumnData columnData = new ColumnData(
                    columnName,
                    dataType,
                    primaryKey,
                    notNull,
                    unique,
                    autoIncrement,
                    uniqueIndexName  // Add this parameter
            );
            columns.add(columnData);
        }
        
        resultSet.close();
        columnStatement.close();
        
        return columns;
    }

    @Override
    public ArrayList<String> getDatabases(String name) {
        ArrayList<String> databases = new ArrayList<String>();
        try {
            String query = "SELECT SCHEMA_NAME \n"
                    + "FROM information_schema.SCHEMATA\n"
                    + "WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema')\n"
                    + "ORDER BY \n"
                    + "    CASE WHEN SCHEMA_NAME = ? THEN 0 ELSE 1 END,\n"
                    + "    SCHEMA_NAME;";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            statement.setString(1, name);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                databases.add(result.getString("SCHEMA_NAME"));
            }

            statement.close();
            result.close();
            return databases;
        } catch (SQLException e) {
            log.error("getDatabases failed", e);
        }
        return databases;
    }

    @Override
    public String generateCreateScript(String tableName, String dbName) {
        try {
            StringBuilder script = new StringBuilder();

            Set<String> primaryKeys = new HashSet<>();
            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();
            try (ResultSet pkRs = metaData.getPrimaryKeys(dbName, null, tableName)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            Map<String, ForeignKeyData> foreignKeys = extractForeignKeys(dbName, tableName);

            Map<String, List<String>> indexes = new LinkedHashMap<>();
            Map<String, Boolean> indexUniqueness = new HashMap<>();
            try (ResultSet indexInfo = metaData.getIndexInfo(dbName, null, tableName, false, false)) {
                while (indexInfo.next()) {
                    String indexName = indexInfo.getString("INDEX_NAME");
                    if (indexName == null || indexName.equals("PRIMARY")) {
                        continue;
                    }
                    String columnName = indexInfo.getString("COLUMN_NAME");
                    boolean nonUnique = indexInfo.getBoolean("NON_UNIQUE");

                    indexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                    indexUniqueness.put(indexName, !nonUnique);
                }
            }

            script.append("CREATE TABLE `").append(tableName).append("` (\n");

            String columnQuery = """
                SELECT 
                    c.COLUMN_NAME,
                    c.COLUMN_TYPE,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.EXTRA
                FROM information_schema.COLUMNS c
                WHERE c.TABLE_SCHEMA = ? 
                  AND c.TABLE_NAME = ?
                ORDER BY c.ORDINAL_POSITION
                """;

            PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
            columnStatement.setString(1, dbName);
            columnStatement.setString(2, tableName);

            try (ResultSet rs = columnStatement.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) script.append(",\n");
                    first = false;

                    String columnName = rs.getString("COLUMN_NAME");
                    String columnType = rs.getString("COLUMN_TYPE");
                    String isNullable = rs.getString("IS_NULLABLE");
                    String columnDefault = rs.getString("COLUMN_DEFAULT");
                    String extra = rs.getString("EXTRA");

                    script.append("  `").append(columnName).append("` ");
                    script.append(columnType);

                    if ("NO".equals(isNullable)) {
                        script.append(" NOT NULL");
                    }

                    if (extra != null && extra.toLowerCase().contains("auto_increment")) {
                        script.append(" AUTO_INCREMENT");
                    }

                    if (columnDefault != null) {
                        script.append(" DEFAULT ").append(columnDefault);
                    }
                }

                if (!primaryKeys.isEmpty()) {
                    script.append(",\n  PRIMARY KEY (");
                    script.append(primaryKeys.stream()
                            .map(pk -> "`" + pk + "`")
                            .collect(java.util.stream.Collectors.joining(", ")));
                    script.append(")");
                }

                for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                    String indexName = entry.getKey();
                    List<String> indexColumns = entry.getValue();
                    boolean isUnique = indexUniqueness.get(indexName);

                    script.append(",\n  ");
                    if (isUnique) {
                        script.append("UNIQUE ");
                    }
                    script.append("KEY `").append(indexName).append("` (");
                    script.append(indexColumns.stream()
                            .map(col -> "`" + col + "`")
                            .collect(java.util.stream.Collectors.joining(", ")));
                    script.append(")");
                }

                for (Map.Entry<String, ForeignKeyData> entry : foreignKeys.entrySet()) {
                    ForeignKeyData fk = entry.getValue();
                    script.append(",\n  CONSTRAINT `").append(fk.getConstraintName()).append("` ")
                            .append("FOREIGN KEY (`").append(fk.getLocalColumn()).append("`) ")
                            .append("REFERENCES `").append(fk.getReferenceTable()).append("` ")
                            .append("(`").append(fk.getReferenceColumn()).append("`)");

                    if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                        script.append(" ON DELETE ").append(fk.getOnDelete());
                    }

                    if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                        script.append(" ON UPDATE ").append(fk.getOnUpdate());
                    }
                }

                script.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            }

            columnStatement.close();
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
            }

            // Method 2: Using database name as catalog
            if (columns == null || !columns.next()) {
                try {
                    columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(dbName, null, tableName, null);
            }

            // Method 3: Using null catalog
            if (columns == null || !columns.next()) {
                try {
                    columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(null, dbName, tableName, null);
            }

            // Method 4: Complete null
            if (columns == null || !columns.next()) {
                try {
                    columns.close();
                } catch (Exception ignored) {
                }
                columns = metaData.getColumns(null, null, tableName, null);
            }

            // Reset to beginning if we used next() to check
            if (columns != null) {
                columns.close();
                // Get fresh ResultSet based on what worked
                String catalog = DatabaseConnection.getInstance().getConnection().getCatalog();
                if (catalog != null && !catalog.isEmpty()) {
                    columns = metaData.getColumns(catalog, null, tableName, null);
                } else {
                    columns = metaData.getColumns(null, null, tableName, null);
                }
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

                // Skip auto-increment columns
                if (isAutoIncrement) {
                    continue;
                }

                columnNames.add("`" + columnName + "`");

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
                        case Types.DATE -> defaultValue = "CURDATE()";
                        case Types.TIME -> defaultValue = "CURTIME()";
                        case Types.TIMESTAMP -> defaultValue = "NOW()";
                        case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> defaultValue = "''";
                        case Types.OTHER -> {
                            // Handle MySQL specific types that map to Types.OTHER
                            if ("JSON".equals(dataType)) {
                                defaultValue = "'{}'";
                            } else if ("YEAR".equals(dataType)) {
                                defaultValue = "YEAR(NOW())";
                            } else if ("DATETIME".equals(dataType)) {
                                defaultValue = "NOW()";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                             Types.CLOB, Types.NCLOB -> {
                            if ("ENUM".equals(dataType)) {
                                defaultValue = "''";
                            } else if ("SET".equals(dataType)) {
                                defaultValue = "''";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        default -> {
                            if ("DATETIME".equals(dataType)) {
                                defaultValue = "NOW()";
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
                //insertScript("-- ERROR: No columns found for table: " + tableName);
                return "-- ERROR: No columns found for table: " + tableName;
            }

            // Format the INSERT statement nicely
            insertScript.append("INSERT INTO `").append(tableName).append("`\n");
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
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        return "SELECT * FROM `" + tableName + "` LIMIT " + limit + ";";
    }

    @Override
    public String generateRowInsertScript(ObservableList<String> row, TableCell<TableRowData, String> cell) {
        try {
            ResultTable table = (ResultTable) cell.getTableView();
            String tableName = table.getTableName();
            StringBuilder insertScript = new StringBuilder();

            ObservableList<TableColumn<TableRowData, ?>> tableColumns = table.getColumns();

            List<String> columnNames = new ArrayList<>();
            List<Integer> columnTypes = new ArrayList<>();

            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();
            String catalog = DatabaseConnection.getInstance().getConnection().getCatalog();

            for (TableColumn<TableRowData, ?> col : tableColumns) {
                String colName = col.getText();
                columnNames.add("`" + colName + "`");

                ResultSet columns = metaData.getColumns(catalog, null, tableName, colName);
                if (columns.next()) {
                    columnTypes.add(columns.getInt("DATA_TYPE"));
                } else {
                    columnTypes.add(Types.VARCHAR);
                }
                columns.close();
            }

            insertScript.append("INSERT INTO `").append(tableName).append("`")
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
                    switch (columnTypes.get(i)) {
                        case Types.BOOLEAN, Types.BIT -> {
                            insertScript.append(Boolean.parseBoolean(value) ? "1" : "0");
                        }
                        case Types.TIMESTAMP -> {
                            insertScript.append("STR_TO_DATE('").append(value).append("', '%Y-%m-%d %H:%i:%s')");
                        }
                        case Types.DATE -> {
                            insertScript.append("STR_TO_DATE('").append(value).append("', '%Y-%m-%d')");
                        }
                        case Types.TIME -> {
                            insertScript.append("STR_TO_DATE('").append(value).append("', '%H:%i:%s')");
                        }
                        case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT,
                             Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                            insertScript.append(value.isEmpty() ? "NULL" : value);
                        }
                        default -> {
                            insertScript.append("'").append(value.replace("'", "\\'")).append("'");
                        }
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
                ? "`" + databaseName + "`.`" + tableName + "`"
                : "`" + tableName + "`";

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        // Add columns
        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);

            sql.append("    `").append(col.getColumnName()).append("` ").append(col.getDataType());

            // Add AUTO_INCREMENT if specified
            if (col.isAutoIncrement()) {
                sql.append(" AUTO_INCREMENT");
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
                sql.append("`").append(primaryKeys.get(i)).append("`");
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

            sql.append("    CONSTRAINT `").append(fk.getConstraintName()).append("` ")
                    .append("FOREIGN KEY (`").append(fk.getLocalColumn()).append("`) ")
                    .append("REFERENCES `").append(fk.getReferenceTable()).append("` ")
                    .append("(`").append(fk.getReferenceColumn()).append("`)");

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

        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        return sql.toString();
    }

    @Override
    public String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                        List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                        Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys,
                                        Map<String, String> columnRenames) throws SQLException {

        StringBuilder sql = new StringBuilder();

        // 1. Rename table if needed (must be first)
        if (!oldTableName.equals(newTableName)) {
            sql.append(String.format("RENAME TABLE `%s`.`%s` TO `%s`.`%s`;\n",
                    databaseName, oldTableName, databaseName, newTableName));
            oldTableName = newTableName;
        }

        // 2. Drop foreign keys that are removed or modified
        List<String> fksToDrop = new ArrayList<>();
        Set<String> fksToRecreate = new HashSet<>();

        for (String oldFkName : oldForeignKeys.keySet()) {
            ForeignKeyData oldFk = oldForeignKeys.get(oldFkName);
            ForeignKeyData matchingNewFk = null;

            for (ForeignKeyData newFk : newForeignKeys) {
                if (oldFkName.equals(newFk.getConstraintName())) {
                    matchingNewFk = newFk;
                    break;
                }
            }

            if (matchingNewFk == null) {
                fksToDrop.add(oldFkName);
            } else {
                // Check if FK column was renamed
                String oldFkColumn = oldFk.getLocalColumn();
                String newFkColumn = matchingNewFk.getLocalColumn();

                // If the column was renamed, adjust comparison
                if (columnRenames.containsKey(oldFkColumn)) {
                    oldFkColumn = columnRenames.get(oldFkColumn);
                }

                if (!oldFkColumn.equals(newFkColumn) ||
                        !oldFk.getReferenceTable().equals(matchingNewFk.getReferenceTable()) ||
                        !oldFk.getReferenceColumn().equals(matchingNewFk.getReferenceColumn()) ||
                        !Objects.equals(oldFk.getOnDelete(), matchingNewFk.getOnDelete()) ||
                        !Objects.equals(oldFk.getOnUpdate(), matchingNewFk.getOnUpdate())) {
                    fksToDrop.add(oldFkName);
                    fksToRecreate.add(oldFkName);
                }
            }
        }

        if (!fksToDrop.isEmpty()) {
            StringBuilder alterFk = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < fksToDrop.size(); i++) {
                alterFk.append(String.format("  DROP FOREIGN KEY `%s`", fksToDrop.get(i)));
                if (i < fksToDrop.size() - 1) alterFk.append(",\n");
            }
            alterFk.append(";\n");
            sql.append(alterFk);
        }

        // 3. Analyze PK changes (accounting for renames)
        List<String> oldPKs = new ArrayList<>();
        List<String> newPKs = new ArrayList<>();
        for (ColumnData col : oldColumns) {
            if (col.isPrimaryKey()) oldPKs.add(col.getColumnName());
        }
        for (ColumnData col : newColumns) {
            if (col.isPrimaryKey()) newPKs.add(col.getColumnName());
        }

        Set<String> newPKColumnsToAdd = new HashSet<>();
        for (String pkCol : newPKs) {
            boolean existsInOld = false;
            for (ColumnData oldCol : oldColumns) {
                String oldColName = oldCol.getColumnName();
                // Check if this column was renamed
                if (columnRenames.containsKey(oldColName)) {
                    oldColName = columnRenames.get(oldColName);
                }
                if (oldColName.equals(pkCol)) {
                    existsInOld = true;
                    break;
                }
            }
            if (!existsInOld) {
                newPKColumnsToAdd.add(pkCol);
            }
        }

        // 4. Drop UNIQUE indexes that are being removed
        List<String> uniqueKeysToDrop = new ArrayList<>();
        for (ColumnData oldCol : oldColumns) {
            if (oldCol.isUnique()) {
                ColumnData matchingNewCol = null;
                String oldColName = oldCol.getColumnName();
                String renamedColName = columnRenames.getOrDefault(oldColName, oldColName);

                for (ColumnData newCol : newColumns) {
                    if (renamedColName.equals(newCol.getColumnName())) {
                        matchingNewCol = newCol;
                        break;
                    }
                }

                if (matchingNewCol == null) {
                    // Column is being dropped entirely (not renamed), unique will be dropped with it
                    continue;
                }

                // Drop unique index if the new column doesn't have isUnique flag
                if (!matchingNewCol.isUnique()) {
                    if (oldCol.getUniqueIndexName() != null) {
                        uniqueKeysToDrop.add(oldCol.getUniqueIndexName());
                    }
                }
            }
        }

        if (!uniqueKeysToDrop.isEmpty()) {
            StringBuilder alterUnique = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < uniqueKeysToDrop.size(); i++) {
                alterUnique.append(String.format("  DROP INDEX `%s`", uniqueKeysToDrop.get(i)));
                if (i < uniqueKeysToDrop.size() - 1) alterUnique.append(",\n");
            }
            alterUnique.append(";\n");
            sql.append(alterUnique);
        }

        // 5. Handle column renames FIRST, then drops and adds
        List<String> columnsToRename = new ArrayList<>();

        for (Map.Entry<String, String> rename : columnRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            // Find the old column definition
            ColumnData oldCol = null;
            for (ColumnData col : oldColumns) {
                if (col.getColumnName().equals(oldName)) {
                    oldCol = col;
                    break;
                }
            }

            if (oldCol != null) {
                // Find the new column definition to get updated attributes
                ColumnData newCol = null;
                for (ColumnData col : newColumns) {
                    if (col.getColumnName().equals(newName)) {
                        newCol = col;
                        break;
                    }
                }

                if (newCol != null) {
                    // RENAME with updated definition (but NOT including UNIQUE inline)
                    columnsToRename.add(String.format("CHANGE COLUMN `%s` `%s` %s%s%s",
                            oldName,
                            newName,
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " AUTO_INCREMENT" : ""
                    ));
                }
            }
        }

        if (!columnsToRename.isEmpty()) {
            StringBuilder alterRename = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < columnsToRename.size(); i++) {
                alterRename.append("  ").append(columnsToRename.get(i));
                if (i < columnsToRename.size() - 1) alterRename.append(",\n");
            }
            alterRename.append(";\n");
            sql.append(alterRename);
        }

        // 6. Drop columns and add new columns (Phase 1)
        List<String> columnsToDropNow = new ArrayList<>();
        List<String> columnsToAddNow = new ArrayList<>();

        for (ColumnData oldCol : oldColumns) {
            String oldColName = oldCol.getColumnName();

            // Skip if this column was renamed (already handled)
            if (columnRenames.containsKey(oldColName)) {
                continue;
            }

            boolean found = false;
            for (ColumnData newCol : newColumns) {
                if (oldColName.equals(newCol.getColumnName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                columnsToDropNow.add(String.format("DROP COLUMN `%s`", oldColName));
            }
        }

        for (ColumnData newCol : newColumns) {
            boolean isNewColumn = true;
            String newColName = newCol.getColumnName();

            // Check if this is a renamed column
            for (String renamedCol : columnRenames.values()) {
                if (renamedCol.equals(newColName)) {
                    isNewColumn = false;
                    break;
                }
            }

            if (isNewColumn) {
                for (ColumnData oldCol : oldColumns) {
                    if (oldCol.getColumnName().equals(newColName)) {
                        isNewColumn = false;
                        break;
                    }
                }
            }

            if (isNewColumn) {
                // Add new column WITHOUT AUTO_INCREMENT if it will be PK (add AI after PK is set)
                // For NEW columns, we CAN use inline UNIQUE
                if (newPKColumnsToAdd.contains(newCol.getColumnName())) {
                    columnsToAddNow.add(String.format("ADD COLUMN `%s` %s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isUnique() ? " UNIQUE" : ""
                    ));
                } else {
                    // Regular new column with all attributes
                    columnsToAddNow.add(String.format("ADD COLUMN `%s` %s%s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " AUTO_INCREMENT" : "",
                            newCol.isUnique() ? " UNIQUE" : ""
                    ));
                }
            }
        }

        List<String> firstPhaseAlterations = new ArrayList<>();
        firstPhaseAlterations.addAll(columnsToDropNow);
        firstPhaseAlterations.addAll(columnsToAddNow);

        if (!firstPhaseAlterations.isEmpty()) {
            StringBuilder alterCols = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < firstPhaseAlterations.size(); i++) {
                alterCols.append("  ").append(firstPhaseAlterations.get(i));
                if (i < firstPhaseAlterations.size() - 1) alterCols.append(",\n");
            }
            alterCols.append(";\n");
            sql.append(alterCols);
        }

        // 7. Handle PRIMARY KEY changes (accounting for renames)
        List<String> adjustedOldPKs = new ArrayList<>();
        for (String oldPk : oldPKs) {
            adjustedOldPKs.add(columnRenames.getOrDefault(oldPk, oldPk));
        }

        List<String> sortedOldPKs = new ArrayList<>(adjustedOldPKs);
        List<String> sortedNewPKs = new ArrayList<>(newPKs);
        sortedOldPKs.sort(String::compareTo);
        sortedNewPKs.sort(String::compareTo);

        boolean pkIsChanging = !sortedOldPKs.equals(sortedNewPKs);
        Set<String> columnsWithAIRemoved = new HashSet<>();

        // Remove AUTO_INCREMENT from old PK columns that are losing PK status
        if (pkIsChanging && !oldPKs.isEmpty()) {
            for (String oldPkCol : oldPKs) {
                String adjustedPkCol = columnRenames.getOrDefault(oldPkCol, oldPkCol);

                // Check if this PK column is NOT in the new PK list
                if (!newPKs.contains(adjustedPkCol)) {
                    // Find the old column definition
                    ColumnData oldColWithAI = null;
                    for (ColumnData col : oldColumns) {
                        if (col.getColumnName().equals(oldPkCol) && col.isAutoIncrement()) {
                            oldColWithAI = col;
                            break;
                        }
                    }

                    if (oldColWithAI != null) {
                        // Remove AUTO_INCREMENT before dropping PK (use renamed column name)
                        sql.append(String.format("ALTER TABLE `%s`.`%s` MODIFY COLUMN `%s` %s NOT NULL;\n",
                                databaseName, oldTableName, adjustedPkCol, oldColWithAI.getDataType()));
                        columnsWithAIRemoved.add(adjustedPkCol);
                    }
                }
            }
        }

        if (pkIsChanging) {
            if (!oldPKs.isEmpty()) {
                sql.append(String.format("ALTER TABLE `%s`.`%s` DROP PRIMARY KEY;\n", databaseName, oldTableName));
            }
            if (!newPKs.isEmpty()) {
                String pkCols = newPKs.stream().map(c -> "`" + c + "`").reduce((a, b) -> a + ", " + b).orElse("");
                sql.append(String.format("ALTER TABLE `%s`.`%s` ADD PRIMARY KEY (%s);\n", databaseName, oldTableName, pkCols));
            }
        }

        // 8. Modify existing columns that weren't renamed (without UNIQUE - that's handled separately)
        List<String> columnsToModifyLater = new ArrayList<>();

        for (ColumnData newCol : newColumns) {
            String newColName = newCol.getColumnName();

            // Skip if this column was just renamed (already handled in CHANGE COLUMN)
            if (columnRenames.containsValue(newColName)) {
                continue;
            }

            ColumnData oldCol = null;
            for (ColumnData col : oldColumns) {
                if (col.getColumnName().equals(newColName)) {
                    oldCol = col;
                    break;
                }
            }

            if (oldCol != null) {
                // Skip if we already handled AI removal for old PK column
                if (columnsWithAIRemoved.contains(oldCol.getColumnName())) {
                    continue;
                }

                // Existing column - check if modification needed (excluding UNIQUE check)
                boolean needsModify = false;

                // Check all attributes for changes (but NOT isUnique - handled separately)
                if (!oldCol.getDataType().equalsIgnoreCase(newCol.getDataType()) ||
                        oldCol.isNotNull() != newCol.isNotNull() ||
                        oldCol.isAutoIncrement() != newCol.isAutoIncrement()) {
                    needsModify = true;
                }

                if (needsModify) {
                    // IMPORTANT: Do NOT include UNIQUE in MODIFY COLUMN for existing columns
                    columnsToModifyLater.add(String.format("MODIFY COLUMN `%s` %s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " AUTO_INCREMENT" : ""
                    ));
                }
            } else if (newPKColumnsToAdd.contains(newCol.getColumnName()) && newCol.isAutoIncrement()) {
                // New PK column - add AUTO_INCREMENT now that PK exists
                columnsToModifyLater.add(String.format("MODIFY COLUMN `%s` %s%s%s",
                        newCol.getColumnName(),
                        newCol.getDataType(),
                        newCol.isNotNull() ? " NOT NULL" : "",
                        " AUTO_INCREMENT"
                ));
            }
        }

        if (!columnsToModifyLater.isEmpty()) {
            StringBuilder alterCols = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < columnsToModifyLater.size(); i++) {
                alterCols.append("  ").append(columnsToModifyLater.get(i));
                if (i < columnsToModifyLater.size() - 1) alterCols.append(",\n");
            }
            alterCols.append(";\n");
            sql.append(alterCols);
        }

        // 9. Add UNIQUE indexes for columns that gained UNIQUE (including renamed columns)
        List<String> uniqueIndexesToAdd = new ArrayList<>();

        for (ColumnData newCol : newColumns) {
            if (newCol.isUnique()) {
                String newColName = newCol.getColumnName();
                ColumnData oldCol = null;

                // Check if this is a renamed column
                String originalName = null;
                for (Map.Entry<String, String> entry : columnRenames.entrySet()) {
                    if (entry.getValue().equals(newColName)) {
                        originalName = entry.getKey();
                        break;
                    }
                }

                // Find the old column (by original name if renamed, or by current name)
                for (ColumnData col : oldColumns) {
                    if (originalName != null && col.getColumnName().equals(originalName)) {
                        oldCol = col;
                        break;
                    } else if (originalName == null && col.getColumnName().equals(newColName)) {
                        oldCol = col;
                        break;
                    }
                }

                // If this is an existing column that didn't have UNIQUE before, add index
                if (oldCol != null && !oldCol.isUnique()) {
                    uniqueIndexesToAdd.add(newColName);
                }
            }
        }

        if (!uniqueIndexesToAdd.isEmpty()) {
            for (String colName : uniqueIndexesToAdd) {
                sql.append(String.format("ALTER TABLE `%s`.`%s` ADD UNIQUE INDEX `%s` (`%s`);\n",
                        databaseName, oldTableName, colName+"_UNIQUE", colName));
            }
        }

        // 10. Add new foreign keys and recreate modified ones
        List<ForeignKeyData> fksToAdd = new ArrayList<>();
        for (ForeignKeyData newFk : newForeignKeys) {
            String fkName = newFk.getConstraintName();
            if (!oldForeignKeys.containsKey(fkName) || fksToRecreate.contains(fkName)) {
                fksToAdd.add(newFk);
            }
        }

        for (ForeignKeyData fk : fksToAdd) {
            sql.append(String.format("ALTER TABLE `%s`.`%s` ADD CONSTRAINT `%s` FOREIGN KEY (`%s`) REFERENCES `%s`(`%s`)",
                    databaseName, oldTableName, fk.getConstraintName(), fk.getLocalColumn(),
                    fk.getReferenceTable(), fk.getReferenceColumn()));

            if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                sql.append(" ON DELETE ").append(fk.getOnDelete());
            }
            if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                sql.append(" ON UPDATE ").append(fk.getOnUpdate());
            }
            sql.append(";\n");
        }

        return sql.toString();
    }

    @Override
    public ArrayList<String> getUsers() throws SQLException {
        ArrayList<String> users = new ArrayList<>();

        try {
            // Try to get all users (requires elevated privileges)
            String query = "SELECT User, Host FROM mysql.user ORDER BY User";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String user = result.getString("User");
                String host = result.getString("Host");
                users.add("'" + user + "'@'" + host + "'");
            }

            statement.close();
            result.close();

        } catch (SQLException e) {
            // Get current user and actual connecting IP
            String query = "SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1) as user, " +
                    "SUBSTRING_INDEX(CURRENT_USER(), '@', -1) as host_pattern, " +
                    "SUBSTRING_INDEX(p.HOST, ':', 1) as actual_ip " +
                    "FROM information_schema.PROCESSLIST p " +
                    "WHERE p.ID = CONNECTION_ID()";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            if (result.next()) {
                String user = result.getString("user");
                String actualIp = result.getString("actual_ip");
                log.debug("Current user: '{}'@'{}'", user, actualIp);
                users.add("'" + user + "'@'" + actualIp + "'");
            }

            statement.close();
            result.close();
        }

        return users;
    }

    @Override
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "`" + databaseName + "`.`" + tableName + "`"
                : "`" + tableName + "`";

        String sql = "DROP TABLE IF EXISTS " + fullTableName;
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "CREATE DATABASE `" + databaseName + "` ";

        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP DATABASE IF EXISTS `" + databaseName + "`";

        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "USE `" + databaseName + "`";
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.execute();
        statement.close();
    }
    
	@Override
	public double getTableSize(String database, String table) throws SQLException {
		// Analyze table first
		Statement stmt = DatabaseConnection.getInstance().getConnection().createStatement();
		stmt.execute("ANALYZE TABLE " + database + "." + table);
		stmt.close();

		// Get size
		String query = """
				SELECT ROUND((data_length + index_length) / 1024 / 1024, 2) AS total_size_mb
				FROM information_schema.TABLES
				WHERE table_schema = ? AND table_name = ?
				""";

		PreparedStatement pstmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
		pstmt.setString(1, database);
		pstmt.setString(2, table);

		ResultSet rs = pstmt.executeQuery();
		double sizeMB = rs.next() ? rs.getDouble("total_size_mb") : -1;

		rs.close();
		pstmt.close();

		return sizeMB;
	}

    @Override
    public String[] getDataTypes() {
        return this.dataTypes;
    }
}