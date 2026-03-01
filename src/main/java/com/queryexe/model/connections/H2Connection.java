package com.queryexe.model.connections;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
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

public class H2Connection extends ConnectionObject {

    private final String[] KEYWORDS = new String[]{
            // Basic SQL Keywords
            "SELECT", "FROM", "WHERE", "AND", "OR", "INSERT", "INTO", "VALUES",
            "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "INDEX",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "AS", "DISTINCT",
            "EXISTS", "IN", "BETWEEN", "LIKE", "IS", "UNIQUE", "CONSTRAINT", "DEFAULT",
            "CHECK", "COLLATE", "COMMENT", "TEMPORARY", "TEMP", "VIEW", "SEQUENCE",

            // H2 Specific Keywords
            "AUTO_INCREMENT", "IDENTITY", "GENERATED", "ALWAYS", "BY", "CACHED",
            "NOCACHE", "CYCLE", "NOCYCLE", "MINVALUE", "NOMAXVALUE", "MAXVALUE",
            "NOMINVALUE", "START", "WITH", "INCREMENT", "RESTART", "SCHEMA",
            "PUBLIC", "INFORMATION_SCHEMA", "SHOW", "EXPLAIN", "ANALYZE",

            // Control Flow
            "IF", "ELSE", "CASE", "WHEN", "THEN", "END", "IFNULL", "NULLIF",
            "COALESCE", "GREATEST", "LEAST", "DECODE", "NVL", "NVL2",

            // Joins and Set Operations
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "NATURAL",
            "UNION", "INTERSECT", "EXCEPT", "MINUS", "ALL",

            // Grouping and Ordering
            "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET",
            "TOP", "FETCH", "FIRST", "NEXT", "ONLY", "ROWS", "ROW",

            // Window Functions
            "OVER", "PARTITION", "RANGE", "PRECEDING", "FOLLOWING", "UNBOUNDED",
            "CURRENT", "EXCLUDE", "TIES", "NULLS", "LAST",

            // Functions
            "COUNT", "SUM", "AVG", "MAX", "MIN", "CONCAT", "SUBSTRING", "SUBSTR",
            "TRIM", "LTRIM", "RTRIM", "LENGTH", "CHAR_LENGTH", "LOWER", "UPPER",
            "REPLACE", "REGEXP_REPLACE", "REGEXP_LIKE", "INSTR", "POSITION",

            // Date/Time
            "NOW", "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "SYSDATE",
            "DATE", "DATETIME", "TIME", "TIMESTAMP", "YEAR", "MONTH", "DAY",
            "HOUR", "MINUTE", "SECOND", "DATEADD", "DATEDIFF", "EXTRACT",

            // Transaction Control
            "COMMIT", "ROLLBACK", "BEGIN", "TRANSACTION", "SAVEPOINT", "RELEASE",

            // Data Manipulation
            "MERGE", "UPSERT", "REPLACE", "TRUNCATE", "CASCADE", "RESTRICT",

            // Access Control
            "GRANT", "REVOKE", "ROLE", "USER", "PASSWORD", "ADMIN",

            // H2 Utility Functions
            "CSVREAD", "CSVWRITE", "RUNSCRIPT", "SCRIPT", "BACKUP", "COMPRESS",
            "ENCRYPT", "DECRYPT", "HASH", "SECURE_RAND", "FILE_READ", "FILE_WRITE"
    };

    private String[] dataTypes = new String[]{
            // Numeric Types
            "INT", "INTEGER", "TINYINT", "SMALLINT", "BIGINT", "DECIMAL", "NUMERIC",
            "DOUBLE", "FLOAT", "REAL", "BOOLEAN", "BIT",

            // Character Types
            "CHAR", "CHARACTER", "VARCHAR", "CHARACTER VARYING", "VARCHAR_IGNORECASE",
            "NCHAR", "NVARCHAR", "LONGVARCHAR", "TEXT", "NTEXT", "CLOB", "NCLOB",

            // Binary Types
            "BINARY", "VARBINARY", "LONGVARBINARY", "RAW", "BYTEA", "BLOB",

            // Date/Time Types
            "DATE", "TIME", "TIMESTAMP", "DATETIME", "SMALLDATETIME",
            "TIME WITH TIME ZONE", "TIMESTAMP WITH TIME ZONE",

            // Other Types
            "UUID", "ARRAY", "GEOMETRY", "JSON", "ENUM", "INTERVAL"
    };

    public H2Connection(String id, String connectionName, String dbType, String url, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, url, username, password, driverInfo);
    }

    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String schemaName) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            String normalizedSchemaName = schemaName != null ? schemaName.toUpperCase() : "PUBLIC";

            ArrayList<String> tableNames = getTablesForDatabase(normalizedSchemaName);

            for (String tableName : tableNames) {
                ArrayList<ColumnData> columns = getColumnsForTable(normalizedSchemaName, tableName);
                tablesMap.put(tableName, columns);
            }

            return tablesMap;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ArrayList<String> getTablesForDatabase(String schemaName) throws SQLException {
        ArrayList<String> tableNames = new ArrayList<>();

        String normalizedSchemaName = schemaName != null ? schemaName.toUpperCase() : "PUBLIC";

        String query = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "ORDER BY table_name";

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query)) {
            stmt.setString(1, normalizedSchemaName);

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

        String normalizedSchemaName = schemaName != null ? schemaName.toUpperCase() : "PUBLIC";

        String columnQuery =
                "SELECT " +
                        "    c.column_name, " +
                        "    c.data_type, " +
                        "    CASE WHEN c.is_nullable = 'NO' THEN 1 ELSE 0 END as is_not_null, " +
                        "    CASE WHEN pk.constraint_type = 'PRIMARY KEY' THEN 1 ELSE 0 END as is_primary_key, " +
                        "    CASE WHEN pk.constraint_type = 'UNIQUE' THEN 1 ELSE 0 END as is_unique, " +
                        "    CASE WHEN c.is_identity = 'YES' THEN 1 ELSE 0 END as is_auto_increment, " +
                        "    pk.constraint_name as unique_index_name " +
                        "FROM information_schema.columns c " +
                        "LEFT JOIN ( " +
                        "    SELECT ku.column_name, tc.constraint_type, tc.constraint_name " +
                        "    FROM information_schema.key_column_usage ku " +
                        "    JOIN information_schema.table_constraints tc " +
                        "        ON ku.constraint_name = tc.constraint_name " +
                        "        AND ku.table_schema = tc.table_schema " +
                        "        AND ku.table_name = tc.table_name " +
                        "    WHERE tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE') " +
                        "        AND ku.table_schema = ? " +
                        "        AND ku.table_name = ? " +
                        ") pk ON c.column_name = pk.column_name " +
                        "WHERE c.table_schema = ? " +
                        "    AND c.table_name = ? " +
                        "ORDER BY c.ordinal_position";

        PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);

        columnStatement.setString(1, normalizedSchemaName);
        columnStatement.setString(2, tableName);
        columnStatement.setString(3, normalizedSchemaName);
        columnStatement.setString(4, tableName);

        ResultSet resultSet = columnStatement.executeQuery();

        while (resultSet.next()) {
            String columnName = resultSet.getString("column_name");
            String dataType = resultSet.getString("data_type");
            boolean primaryKey = resultSet.getInt("is_primary_key") == 1;
            boolean notNull = resultSet.getInt("is_not_null") == 1;
            boolean unique = resultSet.getInt("is_unique") == 1;
            boolean autoIncrement = resultSet.getInt("is_auto_increment") == 1;
            String uniqueIndexName = resultSet.getString("unique_index_name");

            // Only set uniqueIndexName if the column is actually unique (not PK)
            if (!unique || primaryKey) {
                uniqueIndexName = null;
            }

            ColumnData columnData = new ColumnData(
                    columnName,
                    dataType.toUpperCase(),
                    primaryKey,
                    notNull,
                    unique,
                    autoIncrement,
                    uniqueIndexName
            );
            columns.add(columnData);
        }

        resultSet.close();
        columnStatement.close();

        return columns;
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
                    c.DATA_TYPE as COLUMN_TYPE,
                    c.CHARACTER_MAXIMUM_LENGTH,
                    c.CHARACTER_OCTET_LENGTH,
                    c.NUMERIC_PRECISION,
                    c.NUMERIC_SCALE,
                    c.CHARACTER_SET_NAME,
                    c.COLLATION_NAME,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.REMARKS as COLUMN_COMMENT,
                    CASE WHEN c.IS_IDENTITY = 'YES' THEN 1 ELSE 0 END as IS_AUTO_INCREMENT
                FROM INFORMATION_SCHEMA.COLUMNS c
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

                    info.setNullable(rs.getString("IS_NULLABLE").equals("YES"));
                    info.setColumnDefault(rs.getString("COLUMN_DEFAULT"));
                    info.setColumnComment(rs.getString("COLUMN_COMMENT"));
                    info.setAutoIncrement(rs.getInt("IS_AUTO_INCREMENT") == 1);
                }
            }
        }

        // 2. Check if column is primary key or unique (SAME AS getColumnsForTable)
        String keyQuery = """
                SELECT 
                    tc.CONSTRAINT_TYPE,
                    tc.CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    ON ku.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                    AND ku.TABLE_SCHEMA = tc.TABLE_SCHEMA
                    AND ku.TABLE_NAME = tc.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE IN ('PRIMARY KEY', 'UNIQUE')
                    AND ku.TABLE_SCHEMA = ?
                    AND ku.TABLE_NAME = ?
                    AND ku.COLUMN_NAME = ?
                """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(keyQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String constraintType = rs.getString("CONSTRAINT_TYPE");
                    if ("PRIMARY KEY".equals(constraintType)) {
                        info.setPrimaryKey(true);
                        info.setColumnKey("PRI");
                    } else if ("UNIQUE".equals(constraintType)) {
                        info.setUnique(true);
                        if (info.getColumnKey() == null) {
                            info.setColumnKey("UNI");
                        }
                    }
                }
            }
        }

        // 3. Get indexes that include this column
        String indexQuery = """
                SELECT 
                    ic.INDEX_NAME,
                    i.INDEX_TYPE_NAME as INDEX_TYPE,
                    ic.ORDINAL_POSITION as SEQ_IN_INDEX
                FROM INFORMATION_SCHEMA.INDEX_COLUMNS ic
                JOIN INFORMATION_SCHEMA.INDEXES i
                    ON ic.INDEX_SCHEMA = i.INDEX_SCHEMA
                    AND ic.INDEX_NAME = i.INDEX_NAME
                    AND ic.TABLE_SCHEMA = i.TABLE_SCHEMA
                    AND ic.TABLE_NAME = i.TABLE_NAME
                WHERE ic.TABLE_SCHEMA = ?
                  AND ic.TABLE_NAME = ?
                  AND ic.COLUMN_NAME = ?
                ORDER BY ic.INDEX_NAME, ic.ORDINAL_POSITION
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
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName.startsWith("PRIMARY_KEY") || indexName.contains("_UNIQUE")) {
                        index.put("NON_UNIQUE", "0");
                    } else {
                        index.put("NON_UNIQUE", "1");
                    }
                    index.put("SEQ_IN_INDEX", rs.getString("SEQ_IN_INDEX"));
                    index.put("CARDINALITY", null);
                    indexes.add(index);
                }
            }
            info.setIndexes(indexes);
        }

        // 4. Get foreign key references (where this column references another table)
        String fkQuery = """
                SELECT 
                    rc.CONSTRAINT_NAME,
                    rc.UNIQUE_CONSTRAINT_SCHEMA as REFERENCED_TABLE_SCHEMA,
                    kcu_pk.TABLE_NAME as REFERENCED_TABLE_NAME,
                    kcu_pk.COLUMN_NAME as REFERENCED_COLUMN_NAME,
                    rc.UPDATE_RULE,
                    rc.DELETE_RULE
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu_fk
                    ON rc.CONSTRAINT_NAME = kcu_fk.CONSTRAINT_NAME
                    AND rc.CONSTRAINT_SCHEMA = kcu_fk.CONSTRAINT_SCHEMA
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu_pk
                    ON rc.UNIQUE_CONSTRAINT_NAME = kcu_pk.CONSTRAINT_NAME
                    AND rc.UNIQUE_CONSTRAINT_SCHEMA = kcu_pk.CONSTRAINT_SCHEMA
                WHERE kcu_fk.TABLE_SCHEMA = ?
                  AND kcu_fk.TABLE_NAME = ?
                  AND kcu_fk.COLUMN_NAME = ?
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

        // 5. Get foreign keys that reference this column (incoming references)
        String referencedByQuery = """
                SELECT 
                    rc.CONSTRAINT_SCHEMA as TABLE_SCHEMA,
                    kcu_fk.TABLE_NAME as TABLE_NAME,
                    kcu_fk.COLUMN_NAME as COLUMN_NAME,
                    rc.CONSTRAINT_NAME,
                    rc.UPDATE_RULE,
                    rc.DELETE_RULE
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu_fk
                    ON rc.CONSTRAINT_NAME = kcu_fk.CONSTRAINT_NAME
                    AND rc.CONSTRAINT_SCHEMA = kcu_fk.CONSTRAINT_SCHEMA
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu_pk
                    ON rc.UNIQUE_CONSTRAINT_NAME = kcu_pk.CONSTRAINT_NAME
                    AND rc.UNIQUE_CONSTRAINT_SCHEMA = kcu_pk.CONSTRAINT_SCHEMA
                WHERE kcu_pk.TABLE_SCHEMA = ?
                  AND kcu_pk.TABLE_NAME = ?
                  AND kcu_pk.COLUMN_NAME = ?
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
    public ArrayList<String> getDatabases(String name) {
        ArrayList<String> schemas = new ArrayList<>();
        try {
            String query;
            PreparedStatement statement;

            if (name != null && !name.trim().isEmpty()) {
                query = "SELECT schema_name FROM information_schema.schemata " +
                        "WHERE schema_name = ? AND schema_name NOT IN ('INFORMATION_SCHEMA')";

                statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
                statement.setString(1, name.toUpperCase());
            } else {
                query = "SELECT schema_name FROM information_schema.schemata " +
                        "WHERE schema_name NOT IN ('INFORMATION_SCHEMA') " +
                        "ORDER BY schema_name";

                statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            }

            ResultSet result = statement.executeQuery();

            while (result.next()) {
                schemas.add(result.getString("schema_name"));
            }

            statement.close();
            result.close();

            if (name != null && !name.trim().isEmpty() && schemas.isEmpty()) {
                System.out.println("Schema '" + name + "' not found.");
            }

            return schemas;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return schemas;
    }

    @Override
    public String generateCreateScript(String tableName, String dbName) {
        try {
            StringBuilder script = new StringBuilder();
            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

            // Get primary keys
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // Get foreign keys
            Map<String, ForeignKeyData> foreignKeys = extractForeignKeys(dbName, tableName);

            // Get all indexes (including unique and regular)
            Map<String, List<String>> indexes = new LinkedHashMap<>();
            Map<String, Boolean> indexUniqueness = new HashMap<>();
            try (ResultSet indexInfo = metaData.getIndexInfo(null, dbName, tableName, false, false)) {
                while (indexInfo.next()) {
                    String indexName = indexInfo.getString("INDEX_NAME");
                    if (indexName == null || indexName.contains("PRIMARY_KEY")) {
                        continue;
                    }
                    String columnName = indexInfo.getString("COLUMN_NAME");
                    boolean nonUnique = indexInfo.getBoolean("NON_UNIQUE");

                    indexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                    indexUniqueness.put(indexName, !nonUnique);
                }
            }

            script.append("CREATE TABLE \"").append(tableName).append("\" (\n");

            // Use the SAME query structure as getColumnsForTable - it works!
            String normalizedSchemaName = dbName != null ? dbName.toUpperCase() : "PUBLIC";

            String columnQuery = """
                SELECT 
                    c.COLUMN_NAME,
                    c.DATA_TYPE,
                    c.CHARACTER_MAXIMUM_LENGTH,
                    c.NUMERIC_PRECISION,
                    c.NUMERIC_SCALE,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.IS_IDENTITY
                FROM INFORMATION_SCHEMA.COLUMNS c
                WHERE c.TABLE_SCHEMA = ? 
                  AND c.TABLE_NAME = ?
                ORDER BY c.ORDINAL_POSITION
                """;

            PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
            columnStatement.setString(1, normalizedSchemaName);
            columnStatement.setString(2, tableName);

            try (ResultSet rs = columnStatement.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) script.append(",\n");
                    first = false;

                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    String isNullable = rs.getString("IS_NULLABLE");
                    String columnDefault = rs.getString("COLUMN_DEFAULT");
                    String isIdentity = rs.getString("IS_IDENTITY");

                    // Build column type with precision/scale if applicable
                    String fullType = dataType;
                    if (dataType.equalsIgnoreCase("CHARACTER VARYING") ||
                            dataType.equalsIgnoreCase("VARCHAR")) {
                        Integer maxLength = rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ?
                                rs.getInt("CHARACTER_MAXIMUM_LENGTH") : null;
                        if (maxLength != null && maxLength > 0) {
                            fullType = "VARCHAR(" + maxLength + ")";
                        } else {
                            fullType = "VARCHAR";
                        }
                    } else if (dataType.equalsIgnoreCase("CHARACTER") ||
                            dataType.equalsIgnoreCase("CHAR")) {
                        Integer maxLength = rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ?
                                rs.getInt("CHARACTER_MAXIMUM_LENGTH") : null;
                        if (maxLength != null && maxLength > 0) {
                            fullType = "CHAR(" + maxLength + ")";
                        } else {
                            fullType = "CHAR";
                        }
                    } else if (dataType.equalsIgnoreCase("DECIMAL") ||
                            dataType.equalsIgnoreCase("NUMERIC")) {
                        Integer precision = rs.getObject("NUMERIC_PRECISION") != null ?
                                rs.getInt("NUMERIC_PRECISION") : null;
                        Integer scale = rs.getObject("NUMERIC_SCALE") != null ?
                                rs.getInt("NUMERIC_SCALE") : null;
                        if (precision != null && scale != null) {
                            fullType = "DECIMAL(" + precision + "," + scale + ")";
                        } else if (precision != null) {
                            fullType = "DECIMAL(" + precision + ")";
                        } else {
                            fullType = "DECIMAL";
                        }
                    }

                    script.append("  \"").append(columnName).append("\" ");
                    script.append(fullType);

                    // Add IDENTITY/AUTO_INCREMENT if present
                    if ("YES".equalsIgnoreCase(isIdentity)) {
                        script.append(" GENERATED BY DEFAULT AS IDENTITY");
                    }

                    // Add NOT NULL if applicable
                    if ("NO".equals(isNullable)) {
                        script.append(" NOT NULL");
                    }

                    // Add DEFAULT if present (and not identity)
                    if (columnDefault != null && !"YES".equalsIgnoreCase(isIdentity)) {
                        script.append(" DEFAULT ").append(columnDefault);
                    }
                }

                // Add PRIMARY KEY constraint
                if (!primaryKeys.isEmpty()) {
                    script.append(",\n  CONSTRAINT PK_").append(tableName).append(" PRIMARY KEY (");
                    script.append(primaryKeys.stream()
                            .map(pk -> "\"" + pk + "\"")
                            .collect(Collectors.joining(", ")));
                    script.append(")");
                }

                // Add unique indexes as constraints
                for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                    String indexName = entry.getKey();
                    List<String> indexColumns = entry.getValue();
                    boolean isUnique = indexUniqueness.get(indexName);

                    if (isUnique) {
                        script.append(",\n  ");
                        script.append("CONSTRAINT \"").append(indexName).append("\" UNIQUE");
                        script.append(" (");
                        script.append(indexColumns.stream()
                                .map(col -> "\"" + col + "\"")
                                .collect(Collectors.joining(", ")));
                        script.append(")");
                    }
                }

                // Add FOREIGN KEY constraints
                for (Map.Entry<String, ForeignKeyData> entry : foreignKeys.entrySet()) {
                    ForeignKeyData fk = entry.getValue();
                    script.append(",\n  CONSTRAINT \"").append(fk.getConstraintName()).append("\" ")
                            .append("FOREIGN KEY (\"").append(fk.getLocalColumn()).append("\") ")
                            .append("REFERENCES \"").append(fk.getReferenceTable()).append("\" ")
                            .append("(\"").append(fk.getReferenceColumn()).append("\")");

                    // Add ON DELETE action
                    if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                        script.append(" ON DELETE ").append(fk.getOnDelete());
                    }

                    // Add ON UPDATE action
                    if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                        script.append(" ON UPDATE ").append(fk.getOnUpdate());
                    }
                }

                script.append("\n);");

                // Create non-unique indexes separately (H2 requirement)
                for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                    String indexName = entry.getKey();
                    List<String> indexColumns = entry.getValue();
                    boolean isUnique = indexUniqueness.get(indexName);

                    if (!isUnique) {
                        script.append("\nCREATE INDEX \"").append(indexName).append("\" ON \"")
                                .append(tableName).append("\" (");
                        script.append(indexColumns.stream()
                                .map(col -> "\"" + col + "\"")
                                .collect(Collectors.joining(", ")));
                        script.append(");");
                    }
                }
            }

            columnStatement.close();
            return script.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public Map<String, ForeignKeyData> extractForeignKeys(String dbName, String tableName) throws SQLException {
        Map<String, ForeignKeyData> foreignKeys = new HashMap<>();
        DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

        ResultSet fkRs = null;
        try {
            fkRs = metaData.getImportedKeys(null, null, tableName);

            if (!fkRs.isBeforeFirst() && dbName != null) {
                fkRs.close();
                fkRs = metaData.getImportedKeys(dbName, null, tableName);
            }

            if (!fkRs.isBeforeFirst()) {
                fkRs.close();
                fkRs = metaData.getImportedKeys(null, "PUBLIC", tableName);
            }

            while (fkRs.next()) {
                String fkColumnName = fkRs.getString("FKCOLUMN_NAME");
                String constraintName = fkRs.getString("FK_NAME");

                foreignKeys.put(constraintName, new ForeignKeyData(
                        constraintName,
                        fkColumnName,
                        fkRs.getString("PKTABLE_NAME"),
                        fkRs.getString("PKCOLUMN_NAME"),
                        fkRs.getInt("DELETE_RULE"),
                        fkRs.getInt("UPDATE_RULE")
                ));
            }
        } finally {
            if (fkRs != null) {
                fkRs.close();
            }
        }

        return foreignKeys;
    }

    @Override
    public String generateInsertScript(String tableName, String dbName) {
        try {
            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

            ResultSet columns = null;

            try {
                String catalog = DatabaseConnection.getInstance().getConnection().getCatalog();
                columns = metaData.getColumns(catalog, null, tableName, null);
            } catch (Exception e) {
            }

            if (columns == null || !columns.next()) {
                try {
                    if (columns != null) columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(dbName, null, tableName, null);
            }

            if (columns == null || !columns.next()) {
                try {
                    if (columns != null) columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(null, "PUBLIC", tableName, null);
            }

            if (columns == null || !columns.next()) {
                try {
                    if (columns != null) columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(null, null, tableName, null);
            }

            if (columns != null) {
                columns.close();
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

                if (isAutoIncrement || "IDENTITY".equals(dataType)) {
                    continue;
                }

                columnNames.add("\"" + columnName + "\"");

                String defaultValue;
                if (isNullable) {
                    defaultValue = "NULL";
                } else {
                    switch (sqlType) {
                        case Types.BOOLEAN, Types.BIT -> defaultValue = "FALSE";
                        case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> defaultValue = "0";
                        case Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL ->
                                defaultValue = "0.00";
                        case Types.DATE -> defaultValue = "CURRENT_DATE";
                        case Types.TIME -> defaultValue = "CURRENT_TIME";
                        case Types.TIMESTAMP -> defaultValue = "CURRENT_TIMESTAMP";
                        case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> defaultValue = "''";
                        case Types.OTHER -> {
                            if ("UUID".equals(dataType)) {
                                defaultValue = "RANDOM_UUID()";
                            } else if ("JSON".equals(dataType)) {
                                defaultValue = "'{}'";
                            } else if ("GEOMETRY".equals(dataType)) {
                                defaultValue = "'POINT(0 0)'";
                            } else if (dataType.startsWith("INTERVAL")) {
                                defaultValue = "'0'";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        case Types.ARRAY -> defaultValue = "ARRAY[]";
                        case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                             Types.CLOB, Types.NCLOB -> {
                            if ("ENUM".equals(dataType)) {
                                defaultValue = "''";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        default -> {
                            if ("DATETIME".equals(dataType)) {
                                defaultValue = "CURRENT_TIMESTAMP";
                            } else if ("YEAR".equals(dataType)) {
                                defaultValue = "YEAR(CURRENT_DATE)";
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

            insertScript.append("INSERT INTO \"").append(tableName).append("\"\n");
            insertScript.append("    (");

            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) {
                    insertScript.append(",\n     ");
                }
                insertScript.append(columnNames.get(i));
            }

            insertScript.append(")\nVALUES\n    (");

            for (int i = 0; i < columnValues.size(); i++) {
                if (i > 0) {
                    insertScript.append(",\n     ");
                }
                insertScript.append(columnValues.get(i));
            }

            insertScript.append(");");

            return insertScript.toString();
        } catch (Exception e) {
            e.printStackTrace();
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
            List<Integer> columnTypes = new ArrayList<>();

            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

            for (TableColumn<TableRowData, ?> col : tableColumns) {
                String colName = col.getText();
                columnNames.add(colName);

                ResultSet columns = metaData.getColumns(null, null, tableName, colName);
                if (columns.next()) {
                    columnTypes.add(columns.getInt("DATA_TYPE"));
                } else {
                    columnTypes.add(Types.VARCHAR);
                }
                columns.close();
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
                    switch (columnTypes.get(i)) {
                        case Types.BOOLEAN, Types.BIT -> {
                            insertScript.append(Boolean.parseBoolean(value) ? "TRUE" : "FALSE");
                        }
                        case Types.TIMESTAMP -> {
                            insertScript.append("TIMESTAMP '").append(value).append("'");
                        }
                        case Types.DATE -> {
                            insertScript.append("DATE '").append(value).append("'");
                        }
                        case Types.TIME -> {
                            insertScript.append("TIME '").append(value).append("'");
                        }
                        case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT,
                             Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                            insertScript.append(value.isEmpty() ? "NULL" : value);
                        }
                        default -> {
                            insertScript.append("'").append(value.replace("'", "''")).append("'");
                        }
                    }
                }
            }

            insertScript.append(");");

            return insertScript.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public String generateCreateTableSQL(String schemaName, String tableName, List<ColumnData> columns, List<ForeignKeyData> foreignKeys) {
        StringBuilder sql = new StringBuilder();

        String fullTableName = (schemaName != null && !schemaName.isEmpty())
                ? "\"" + schemaName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);

            sql.append("    \"").append(col.getColumnName()).append("\" ").append(col.getDataType());

            if (col.isAutoIncrement()) {
                sql.append(" GENERATED BY DEFAULT AS IDENTITY");
            }

            if (col.isNotNull() || col.isPrimaryKey()) {
                sql.append(" NOT NULL");
            }

            if (col.isUnique() && !col.isPrimaryKey()) {
                sql.append(" UNIQUE");
            }

            if (col.isPrimaryKey()) {
                primaryKeys.add(col.getColumnName());
            }

            if (i < columns.size() - 1 || !primaryKeys.isEmpty() || !foreignKeys.isEmpty()) {
                sql.append(",");
            }
            sql.append("\n");
        }

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

        for (int i = 0; i < foreignKeys.size(); i++) {
            ForeignKeyData fk = foreignKeys.get(i);

            sql.append("    CONSTRAINT \"").append(fk.getConstraintName()).append("\" ")
                    .append("FOREIGN KEY (\"").append(fk.getLocalColumn()).append("\") ")
                    .append("REFERENCES \"").append(fk.getReferenceTable()).append("\" ")
                    .append("(\"").append(fk.getReferenceColumn()).append("\")");

            if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                sql.append(" ON DELETE ").append(fk.getOnDelete());
            }
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
    public String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                        List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                        Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys,
                                        Map<String, String> columnRenames) throws SQLException {

        StringBuilder sql = new StringBuilder();

        // 1. Rename table if needed (must be first)
        if (!oldTableName.equals(newTableName)) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" RENAME TO \"%s\";\n",
                    databaseName, oldTableName, newTableName));
            oldTableName = newTableName; // Update for subsequent operations
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
                // FK was removed
                fksToDrop.add(oldFkName);
            } else {
                // Check if FK column was renamed
                String oldFkColumn = oldFk.getLocalColumn();
                String newFkColumn = matchingNewFk.getLocalColumn();

                // If the column was renamed, adjust comparison
                if (columnRenames.containsKey(oldFkColumn)) {
                    oldFkColumn = columnRenames.get(oldFkColumn);
                }

                // Check if FK definition changed
                if (!oldFkColumn.equals(newFkColumn) ||
                        !oldFk.getReferenceTable().equals(matchingNewFk.getReferenceTable()) ||
                        !oldFk.getReferenceColumn().equals(matchingNewFk.getReferenceColumn()) ||
                        !Objects.equals(oldFk.getOnDelete(), matchingNewFk.getOnDelete()) ||
                        !Objects.equals(oldFk.getOnUpdate(), matchingNewFk.getOnUpdate())) {
                    // FK definition changed - drop and recreate
                    fksToDrop.add(oldFkName);
                    fksToRecreate.add(oldFkName);
                }
            }
        }

        // H2: Drop each FK in separate ALTER TABLE statement
        for (String fkName : fksToDrop) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" DROP CONSTRAINT \"%s\";\n",
                    databaseName, oldTableName, fkName));
        }

        // 2.5. Drop unique constraints that are removed or changed (accounting for renames)
        List<String> uniqueKeysToDrop = new ArrayList<>();
        for (ColumnData oldCol : oldColumns) {
            if (oldCol.isUnique() && !oldCol.isPrimaryKey()) {
                String oldColName = oldCol.getColumnName();
                String renamedColName = columnRenames.getOrDefault(oldColName, oldColName);

                boolean stillUnique = false;
                for (ColumnData newCol : newColumns) {
                    if (renamedColName.equals(newCol.getColumnName()) &&
                            newCol.isUnique() && !newCol.isPrimaryKey()) {
                        stillUnique = true;
                        break;
                    }
                }
                if (!stillUnique && oldCol.getUniqueIndexName() != null) {
                    uniqueKeysToDrop.add(oldCol.getUniqueIndexName());
                }
            }
        }

        for (String uniqueKey : uniqueKeysToDrop) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" DROP CONSTRAINT IF EXISTS \"%s\";\n",
                    databaseName, oldTableName, uniqueKey));
        }

        // 3. Determine primary key changes (accounting for renames)
        List<String> oldPKs = new ArrayList<>();
        List<String> newPKs = new ArrayList<>();
        for (ColumnData col : oldColumns) {
            if (col.isPrimaryKey()) oldPKs.add(col.getColumnName());
        }
        for (ColumnData col : newColumns) {
            if (col.isPrimaryKey()) newPKs.add(col.getColumnName());
        }

        // Check if any new PK columns are NEW columns (not renamed)
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

        // 4. Handle column renames FIRST (H2 uses ALTER TABLE ... ALTER COLUMN ... RENAME TO)
        for (Map.Entry<String, String> rename : columnRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" RENAME TO \"%s\";\n",
                    databaseName, oldTableName, oldName, newName));
        }

        // 5. Drop columns and add new columns
        List<String> columnsToDropNow = new ArrayList<>();
        List<String> columnsToAddNow = new ArrayList<>();

        // Drop columns (skip renamed columns)
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
                columnsToDropNow.add(String.format("DROP COLUMN \"%s\"", oldColName));
            }
        }

        // Add new columns (skip renamed columns)
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
                // For new PK columns, add without AUTO_INCREMENT initially
                if (newPKColumnsToAdd.contains(newCol.getColumnName())) {
                    columnsToAddNow.add(String.format("ADD COLUMN \"%s\" %s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : ""
                    ));
                } else {
                    // Regular new column with all attributes
                    columnsToAddNow.add(String.format("ADD COLUMN \"%s\" %s%s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " GENERATED BY DEFAULT AS IDENTITY" : "",
                            newCol.isUnique() && !newCol.isPrimaryKey() ? " UNIQUE" : ""
                    ));
                }
            }
        }

        // H2 allows combining DROP and ADD in single ALTER TABLE
        List<String> firstPhaseAlterations = new ArrayList<>();
        firstPhaseAlterations.addAll(columnsToDropNow);
        firstPhaseAlterations.addAll(columnsToAddNow);

        if (!firstPhaseAlterations.isEmpty()) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\"\n", databaseName, oldTableName));
            for (int i = 0; i < firstPhaseAlterations.size(); i++) {
                sql.append("  ").append(firstPhaseAlterations.get(i));
                if (i < firstPhaseAlterations.size() - 1) sql.append(",\n");
            }
            sql.append(";\n");
        }

        // 6. Handle PRIMARY KEY changes (accounting for renames)
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
                        // H2: Remove AUTO_INCREMENT using ALTER COLUMN SET
                        sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" %s NOT NULL;\n",
                                databaseName, oldTableName, adjustedPkCol, oldColWithAI.getDataType()));
                        columnsWithAIRemoved.add(adjustedPkCol);
                    }
                }
            }
        }

        // IMPORTANT: Ensure all new PK columns are NOT NULL BEFORE adding PK constraint
        if (pkIsChanging && !newPKs.isEmpty()) {
            for (String pkCol : newPKs) {
                // Check if this column exists and needs to be set to NOT NULL
                ColumnData pkColumn = null;
                String originalPkColName = pkCol;

                // Check if this PK column was renamed
                for (Map.Entry<String, String> entry : columnRenames.entrySet()) {
                    if (entry.getValue().equals(pkCol)) {
                        originalPkColName = entry.getKey();
                        break;
                    }
                }

                // Find the column in old or new columns
                for (ColumnData col : oldColumns) {
                    if (col.getColumnName().equals(originalPkColName)) {
                        pkColumn = col;
                        break;
                    }
                }

                // If not found in old columns, check new columns (newly added column)
                if (pkColumn == null) {
                    for (ColumnData col : newColumns) {
                        if (col.getColumnName().equals(pkCol)) {
                            pkColumn = col;
                            break;
                        }
                    }
                }

                // If column exists and is nullable, set it to NOT NULL before adding PK
                if (pkColumn != null && !pkColumn.isNotNull()) {
                    sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" SET NOT NULL;\n",
                            databaseName, oldTableName, pkCol));
                }
            }
        }

        if (pkIsChanging) {
            // Drop old PK
            if (!oldPKs.isEmpty()) {
                sql.append(String.format("ALTER TABLE \"%s\".\"%s\" DROP PRIMARY KEY;\n",
                        databaseName, oldTableName));
            }
            // Add new PK (all columns are now NOT NULL)
            if (!newPKs.isEmpty()) {
                String pkCols = newPKs.stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + ", " + b).orElse("");
                sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ADD PRIMARY KEY (%s);\n",
                        databaseName, oldTableName, pkCols));
            }
        }

        // 7. Modify existing columns that weren't renamed
        List<String> columnsToModify = new ArrayList<>();

        for (ColumnData newCol : newColumns) {
            String newColName = newCol.getColumnName();

            // Skip if this column was just renamed
            boolean wasRenamed = columnRenames.containsValue(newColName);

            ColumnData oldCol = null;
            String originalColName = null;

            // Find the old column
            if (wasRenamed) {
                // Find the original name
                for (Map.Entry<String, String> entry : columnRenames.entrySet()) {
                    if (entry.getValue().equals(newColName)) {
                        originalColName = entry.getKey();
                        break;
                    }
                }
                // Find old column by original name
                for (ColumnData col : oldColumns) {
                    if (col.getColumnName().equals(originalColName)) {
                        oldCol = col;
                        break;
                    }
                }
            } else {
                // Find old column by current name
                for (ColumnData col : oldColumns) {
                    if (col.getColumnName().equals(newColName)) {
                        oldCol = col;
                        break;
                    }
                }
            }

            if (oldCol != null) {
                // Skip if we already handled AI removal for old PK column
                if (columnsWithAIRemoved.contains(newColName)) {
                    continue;
                }

                // Existing column - check if modification needed
                boolean needsModify = false;

                // Check all attributes for changes (excluding UNIQUE - handled separately)
                if (!oldCol.getDataType().equalsIgnoreCase(newCol.getDataType()) ||
                        oldCol.isNotNull() != newCol.isNotNull() ||
                        oldCol.isAutoIncrement() != newCol.isAutoIncrement()) {
                    needsModify = true;
                }

                if (needsModify) {
                    // H2 uses ALTER COLUMN SET for modifications
                    // For renamed columns, modifications need to be applied separately if attributes changed
                    if (wasRenamed) {
                        // Apply modifications one by one for renamed columns
                        if (!oldCol.getDataType().equalsIgnoreCase(newCol.getDataType())) {
                            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" %s;\n",
                                    databaseName, oldTableName, newColName, newCol.getDataType()));
                        }
                        if (oldCol.isNotNull() != newCol.isNotNull()) {
                            if (newCol.isNotNull()) {
                                sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" SET NOT NULL;\n",
                                        databaseName, oldTableName, newColName));
                            } else {
                                sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" SET NULL;\n",
                                        databaseName, oldTableName, newColName));
                            }
                        }
                        if (oldCol.isAutoIncrement() != newCol.isAutoIncrement()) {
                            // Recreate column with/without AUTO_INCREMENT
                            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" %s%s%s;\n",
                                    databaseName, oldTableName, newColName,
                                    newCol.getDataType(),
                                    newCol.isNotNull() ? " NOT NULL" : "",
                                    newCol.isAutoIncrement() ? " GENERATED BY DEFAULT AS IDENTITY" : ""));
                        }
                    } else {
                        // For non-renamed columns, use single ALTER statement
                        columnsToModify.add(String.format("ALTER COLUMN \"%s\" %s%s%s",
                                newColName,
                                newCol.getDataType(),
                                newCol.isNotNull() ? " NOT NULL" : "",
                                newCol.isAutoIncrement() ? " GENERATED BY DEFAULT AS IDENTITY" : ""
                        ));
                    }
                }
            } else if (newPKColumnsToAdd.contains(newCol.getColumnName()) && newCol.isAutoIncrement()) {
                // New PK column - add AUTO_INCREMENT now that PK exists
                sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" %s NOT NULL GENERATED BY DEFAULT AS IDENTITY;\n",
                        databaseName, oldTableName, newCol.getColumnName(), newCol.getDataType()));
            }
        }

        if (!columnsToModify.isEmpty()) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\"\n", databaseName, oldTableName));
            for (int i = 0; i < columnsToModify.size(); i++) {
                sql.append("  ").append(columnsToModify.get(i));
                if (i < columnsToModify.size() - 1) sql.append(",\n");
            }
            sql.append(";\n");
        }

        // 8. Add UNIQUE constraints for columns that gained UNIQUE (including renamed columns)
        for (ColumnData newCol : newColumns) {
            if (newCol.isUnique() && !newCol.isPrimaryKey()) {
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

                // If this is an existing column that didn't have UNIQUE before, add constraint
                if (oldCol != null && !oldCol.isUnique()) {
                    sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ADD CONSTRAINT \"%s_UNIQUE\" UNIQUE (\"%s\");\n",
                            databaseName, oldTableName, newColName, newColName));
                }
            }
        }

        // 9. Add new foreign keys and recreate modified ones
        List<ForeignKeyData> fksToAdd = new ArrayList<>();
        for (ForeignKeyData newFk : newForeignKeys) {
            String fkName = newFk.getConstraintName();
            if (!oldForeignKeys.containsKey(fkName) || fksToRecreate.contains(fkName)) {
                fksToAdd.add(newFk);
            }
        }

        for (ForeignKeyData fk : fksToAdd) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ADD CONSTRAINT \"%s\" FOREIGN KEY (\"%s\") REFERENCES \"%s\"(\"%s\")",
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
            String query = "SELECT USER_NAME FROM INFORMATION_SCHEMA.USERS ORDER BY USER_NAME";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String user = result.getString("USER_NAME");
                users.add(user);
            }

            statement.close();
            result.close();

        } catch (SQLException e) {
            String fallbackQuery = "SELECT USER() as USER_NAME";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(fallbackQuery);
            ResultSet result = statement.executeQuery();

            if (result.next()) {
                String user = result.getString("USER_NAME");
                users.add(user);
            }

            statement.close();
            result.close();
        }

        return users;
    }

    @Override
    public void deleteTable(String schemaName, String tableName) throws SQLException {
        String fullTableName = (schemaName != null && !schemaName.isEmpty())
                ? "\"" + schemaName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        String sql = "DROP TABLE IF EXISTS " + fullTableName;
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "CREATE SCHEMA IF NOT EXISTS \"" + databaseName + "\"";

        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP SCHEMA IF EXISTS \"" + databaseName + "\"";

        try (PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "SET SCHEMA \"" + databaseName + "\"";
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.execute();
        statement.close();
    }

    @Override
    public double getTableSize(String schema, String table) throws SQLException {
        String query = """
                SELECT ROUND(DB_OBJECT_TOTAL_SIZE('TABLE', ?, ?) / 1024.0 / 1024.0, 2) AS total_size_mb
                """;

        PreparedStatement pstmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
        pstmt.setString(1, schema.toUpperCase());
        pstmt.setString(2, table.toUpperCase());

        ResultSet rs = pstmt.executeQuery();
        double sizeMB = rs.next() ? rs.getDouble("total_size_mb") : -1;

        rs.close();
        pstmt.close();

        return sizeMB;
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
}