package io.github.b077as.queryexe.engine.mysql;

import io.github.b077as.queryexe.connection.ConnectionProfile;


import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;

import io.github.b077as.queryexe.query.RowTarget;
import io.github.b077as.queryexe.schema.DetailedColumnData;
import io.github.b077as.queryexe.schema.ColumnData;
import io.github.b077as.queryexe.schema.PartitionData;
import io.github.b077as.queryexe.connection.DatabaseConnection;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import io.github.b077as.queryexe.schema.ForeignKeyData;


import io.github.b077as.queryexe.engine.*;

@Slf4j
public class MySqlReader extends SchemaReader {

    public MySqlReader(java.sql.Connection connection, ConnectionProfile profile) {
        super(connection, profile);
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
    public Map<String, List<PartitionData>> getPartitions(String name) {
        String sql = """
                SELECT TABLE_NAME, PARTITION_NAME, SUBPARTITION_NAME, PARTITION_METHOD, SUBPARTITION_METHOD,
                       PARTITION_DESCRIPTION, TABLE_ROWS
                FROM information_schema.PARTITIONS
                WHERE TABLE_SCHEMA = ? AND PARTITION_NAME IS NOT NULL
                ORDER BY TABLE_NAME, PARTITION_ORDINAL_POSITION, SUBPARTITION_ORDINAL_POSITION
                """;
        Map<String, List<PartitionData>> out = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                String lastTable = null;
                String lastPartition = null;
                while (rs.next()) {
                    String table = rs.getString(1);
                    String partition = rs.getString(2);
                    String sub = rs.getString(3);
                    String method = rs.getString(4);
                    String description = rs.getString(6);
                    long rows = rs.getLong(7);
                    Long count = rs.wasNull() ? null : rows;
                    List<PartitionData> list = out.computeIfAbsent(table, k -> new ArrayList<>());
                    // a sub-partitioned table has one row per sub-partition: emit the partition once
                    if (!table.equals(lastTable) || !partition.equals(lastPartition)) {
                        String bound = null;
                        if (method != null && description != null) {
                            String m = method.toUpperCase();
                            bound = m.startsWith("RANGE") ? "< " + description : m.startsWith("LIST") ? "IN (" + description + ")" : null;
                        }
                        list.add(new PartitionData(partition, null, method, bound, sub == null ? count : null));
                    }
                    if (sub != null) {
                        list.add(new PartitionData(sub, partition, rs.getString(5), null, count));
                    }
                    lastTable = table;
                    lastPartition = partition;
                }
            }
        } catch (SQLException e) {
            log.warn("Could not list the partitions of {}: {}", name, e.getMessage());
        }
        return out;
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

        try (PreparedStatement stmt = connection.prepareStatement(metadataQuery)) {
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

        try (PreparedStatement stmt = connection.prepareStatement(indexQuery)) {
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

        try (PreparedStatement stmt = connection.prepareStatement(fkQuery)) {
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

        try (PreparedStatement stmt = connection.prepareStatement(referencedByQuery)) {
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
        String query = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";

        try (PreparedStatement stmt = connection.prepareStatement(query);) {
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
        
        PreparedStatement columnStatement = connection.prepareStatement(columnQuery);
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
            PreparedStatement statement = connection.prepareStatement(query);
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
            DatabaseMetaData metaData = connection.getMetaData();
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

            PreparedStatement columnStatement = connection.prepareStatement(columnQuery);
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
        DatabaseMetaData metaData = connection.getMetaData();

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
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "`" + databaseName + "`.`" + tableName + "`"
                : "`" + tableName + "`";

        String sql = "DROP TABLE IF EXISTS " + fullTableName;
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "CREATE DATABASE " + quoteIdentifier(databaseName);

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName);

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "USE " + quoteIdentifier(databaseName);
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.execute();
        statement.close();
    }
    
	@Override
	public double getTableSize(String database, String table) throws SQLException {
		// Analyze table first
		Statement stmt = connection.createStatement();
		stmt.execute("ANALYZE TABLE " + database + "." + table);
		stmt.close();

		// Get size
		String query = """
				SELECT ROUND((data_length + index_length) / 1024 / 1024, 2) AS total_size_mb
				FROM information_schema.TABLES
				WHERE table_schema = ? AND table_name = ?
				""";

		PreparedStatement pstmt = connection.prepareStatement(query);
		pstmt.setString(1, database);
		pstmt.setString(2, table);

		ResultSet rs = pstmt.executeQuery();
		double sizeMB = rs.next() ? rs.getDouble("total_size_mb") : -1;

		rs.close();
		pstmt.close();

		return sizeMB;
	}


    @Override
    public String generateInsertScript(String tableName, String dbName) {
        try {
            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = connection.getMetaData();

            // Try different approaches to get columns
            ResultSet columns = null;

            // Method 1: Using catalog
            try {
                String catalog = connection.getCatalog();
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

                columns = metaData.getColumns(null, dbName, tableName, null);
            }

            // Method 4: Complete null
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
                // Get fresh ResultSet based on what worked
                String catalog = connection.getCatalog();
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
                        case Types.TIMESTAMP -> {
                            // Check if it's actually a DATETIME type
                            if ("DATETIME".equals(dataType)) {
                                defaultValue = "NOW()";
                            } else {
                                defaultValue = "NOW()";
                            }
                        }
                        case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> defaultValue = "''";
                        case Types.OTHER -> {
                            // Handle MariaDB specific types that map to Types.OTHER
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
                            // Check if it's an ENUM or SET type
                            if ("ENUM".equals(dataType)) {
                                defaultValue = "''";
                            } else if ("SET".equals(dataType)) {
                                defaultValue = "''";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        default -> {
                            // Additional check for DATETIME that might not be caught above
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
}
