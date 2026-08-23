package com.queryexe.model.connections;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
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
public class PostgresConnection extends ConnectionObject {

    private String[] KEYWORDS = new String[]{
            // Basic SQL Keywords
            "SELECT", "FROM", "WHERE", "AND", "OR", "INSERT", "INTO", "VALUES",
            "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "INDEX",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "AS", "SHOW",
            "RESET",

            // PostgreSQL Specific
            "RETURNING", "USING", "VACUUM", "ANALYZE", "CLUSTER", "EXPLAIN",
            "LISTEN", "NOTIFY", "UNLISTEN", "REINDEX", "REFRESH", "MATERIALIZED",
            "VIEW", "EXTENSION", "RECURSIVE", "LATERAL", "ORDINALITY",

            // Control Flow and Operators
            "CASE", "WHEN", "THEN", "ELSE", "END", "COALESCE", "NULLIF",
            "GREATEST", "LEAST", "FILTER", "WITHIN", "GROUP", "ILIKE",

            // Joins and Set Operations
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "NATURAL",
            "UNION", "INTERSECT", "EXCEPT", "ALL",

            // Window Functions
            "OVER", "PARTITION", "BY", "ORDER", "ASC", "DESC",
            "RANGE", "ROWS", "GROUPS", "PRECEDING", "FOLLOWING",

            // Aggregate Functions
            "COUNT", "SUM", "AVG", "MAX", "MIN", "ARRAY_AGG", "STRING_AGG",
            "JSONB_AGG", "JSON_AGG", "XMLAGG",

            // String Functions
            "CONCAT", "SUBSTRING", "TRIM", "LTRIM", "RTRIM", "LENGTH", "LOWER", "UPPER",

            // Date/Time
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW",
            "DATE_TRUNC", "DATE_PART", "EXTRACT", "INTERVAL",

            "DISTINCT", "EXISTS", "IN", "BETWEEN", "LIKE", "IS", "UNIQUE", "CONSTRAINT",
            "DEFAULT", "CHECK", "COLLATE", "COMMENT", "TEMPORARY", "TEMP", "SEQUENCE",
            "PROCEDURE", "FUNCTION", "TRIGGER", "GRANT", "REVOKE", "COMMIT", "ROLLBACK",
            "BEGIN", "TRANSACTION", "LOCK", "UNLOCK", "TRUNCATE", "CASCADE", "RESTRICT",
            "COPY", "PERFORM", "RAISE", "NOTICE", "EXCEPTION", "LOOP", "WHILE", "FOR",
            "FOREACH", "IF", "ELSIF", "CONTINUE", "EXIT", "RETURN", "DECLARE", "DOMAIN",

            "TABLESPACE", "OWNER", "INHERITS", "WITH", "WITHOUT", "OIDS", "STORAGE", "PLAIN",
            "EXTERNAL", "EXTENDED", "MAIN", "STATISTICS", "DEFERRABLE", "INITIALLY", "DEFERRED",
            "IMMEDIATE"
    };

    private String[] dataTypes = new String[]{
            "BIGINT", "BIGSERIAL", "BIT(1)", "BIT VARYING(64)", "BOOLEAN",
            "BOX", "BYTEA", "CHARACTER(50)", "CHARACTER VARYING(255)", "CIDR",
            "CIRCLE", "DATE", "DOUBLE PRECISION", "INET", "INTEGER", "JSON",
            "JSONB", "LINE", "LSEG", "MACADDR", "MACADDR8", "MONEY", "NUMERIC(10,2)",
            "PATH", "PG_LSN", "PG_SNAPSHOT", "POINT", "POLYGON", "REAL", "SMALLINT",
            "SMALLSERIAL", "SERIAL", "TEXT", "TIME(6) WITHOUT TIME ZONE",
            "TIME(6) WITH TIME ZONE", "TIMESTAMP(6) WITHOUT TIME ZONE", "TIMESTAMP(6) WITH TIME ZONE",
            "TSQUERY", "TSVECTOR", "TXID_SNAPSHOT", "UUID", "XML"
    };

    public PostgresConnection(String id, String connectionName, String dbType, String baseUrl, String host, String port, String databaseName, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, baseUrl, host, port, databaseName, username, password, driverInfo);
    }

    public PostgresConnection(String id, String connectionName, String dbType, String url, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, url, username, password, driverInfo);
    }

    public LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>>> getCompleteHierarchy() {
        LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>>> hierarchy = new LinkedHashMap<>();

        try {
            // Get current database name (might be null if not specified)
            String currentDatabase = DatabaseConnection.getInstance().getConnection().getCatalog();

            // Step 1: Get all databases
            ArrayList<String> databases = getDatabases(currentDatabase != null ? currentDatabase : "postgres");

            // Store the original connection
            java.sql.Connection originalConnection = DatabaseConnection.getInstance().getConnection();
            String originalUrl = originalConnection.getMetaData().getURL();

            // Step 2: For each database, get schemas, tables, and columns
            for (String dbName : databases) {
                LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>> schemaMap = new LinkedHashMap<>();

                try {
                    // Switch to this database if not already connected
                    if (!dbName.equals(currentDatabase)) {
                        // Create a new connection to this specific database
                        String newUrl = originalUrl.replaceFirst("/[^/]*\\?", "/" + dbName + "?");
                        if (!originalUrl.contains("?")) {
                            int lastSlash = originalUrl.lastIndexOf('/');
                            newUrl = originalUrl.substring(0, lastSlash + 1) + dbName;
                        }

                        java.sql.Connection tempConnection = java.sql.DriverManager.getConnection(
                                newUrl,
                                originalConnection.getMetaData().getUserName(),
                                DatabaseConnection.getInstance().getConnectionObject().getPassword()
                        );

                        // Temporarily set this as the active connection
                        DatabaseConnection.getInstance().setConnection(tempConnection);
                    }

                    // Step 3: Get all schemas for this database
                    ArrayList<String> schemas = getSchemas();

                    // Step 4: For each schema, get all tables and columns
                    for (String schemaName : schemas) {
                        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = getAllTablesAndColumns(schemaName);

                        if (tablesMap != null && !tablesMap.isEmpty()) {
                            schemaMap.put(schemaName, tablesMap);
                        }
                    }

                    // Add this database's schemas to the hierarchy
                    if (!schemaMap.isEmpty()) {
                        hierarchy.put(dbName, schemaMap);
                    }

                } catch (SQLException e) {
                    log.error("Error accessing database '" + dbName + "': " + e.getMessage());
                } finally {
                    if (!dbName.equals(currentDatabase)) {
                        try {
                            java.sql.Connection currentConn = DatabaseConnection.getInstance().getConnection();
                            if (currentConn != originalConnection) {
                                currentConn.close();
                            }
                        } catch (SQLException e) {
                            log.error("getCompleteHierarchy failed", e);
                        }
                        DatabaseConnection.getInstance().setConnection(originalConnection);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("getCompleteHierarchy failed", e);
        }

        return hierarchy;
    }

    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String schemaName) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            ArrayList<String> tableNames = getTablesForDatabase(schemaName);

            for (String tableName : tableNames) {
                ArrayList<ColumnData> columns = getColumnsForTable(schemaName, tableName);
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
                c.column_name,
                c.table_name,
                c.table_schema,
                c.ordinal_position,
                c.data_type,
                c.udt_name,
                c.character_maximum_length,
                c.character_octet_length,
                c.numeric_precision,
                c.numeric_scale,
                c.character_set_name,
                c.collation_name,
                c.is_nullable,
                c.column_default,
                pgd.description as column_comment,
                CASE WHEN pk.constraint_type = 'PRIMARY KEY' THEN true ELSE false END as is_primary_key,
                CASE WHEN uq.constraint_type = 'UNIQUE' THEN true ELSE false END as is_unique,
                CASE WHEN c.column_default LIKE 'nextval(%' THEN true ELSE false END as is_auto_increment
            FROM information_schema.columns c
            LEFT JOIN pg_catalog.pg_statio_all_tables st ON c.table_schema = st.schemaname 
                AND c.table_name = st.relname
            LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid 
                AND pgd.objsubid = c.ordinal_position
            LEFT JOIN (
                SELECT tc.table_schema, tc.table_name, kcu.column_name, tc.constraint_type
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu 
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
            ) pk ON c.table_schema = pk.table_schema 
                AND c.table_name = pk.table_name 
                AND c.column_name = pk.column_name
            LEFT JOIN (
                SELECT tc.table_schema, tc.table_name, kcu.column_name, tc.constraint_type
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu 
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'UNIQUE'
            ) uq ON c.table_schema = uq.table_schema 
                AND c.table_name = uq.table_name 
                AND c.column_name = uq.column_name
            WHERE c.table_schema = ? 
              AND c.table_name = ? 
              AND c.column_name = ?
            """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(metadataQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    info.setColumnName(rs.getString("column_name"));
                    info.setTableName(rs.getString("table_name"));
                    info.setSchemaName(rs.getString("table_schema"));
                    info.setOrdinalPosition(rs.getInt("ordinal_position"));
                    info.setDataType(rs.getString("data_type"));
                    info.setColumnType(rs.getString("udt_name")); // PostgreSQL equivalent

                    // Handle nullable fields
                    if (rs.getObject("character_maximum_length") != null) {
                        info.setCharacterMaximumLength(rs.getLong("character_maximum_length"));
                    }
                    if (rs.getObject("character_octet_length") != null) {
                        info.setCharacterOctetLength(rs.getLong("character_octet_length"));
                    }
                    if (rs.getObject("numeric_precision") != null) {
                        info.setNumericPrecision(rs.getInt("numeric_precision"));
                    }
                    if (rs.getObject("numeric_scale") != null) {
                        info.setNumericScale(rs.getInt("numeric_scale"));
                    }

                    info.setCharacterSetName(rs.getString("character_set_name"));
                    info.setCollationName(rs.getString("collation_name"));
                    info.setNullable(rs.getString("is_nullable").equals("YES"));
                    info.setColumnDefault(rs.getString("column_default"));
                    info.setColumnComment(rs.getString("column_comment"));
                    info.setPrimaryKey(rs.getBoolean("is_primary_key"));
                    info.setUnique(rs.getBoolean("is_unique"));
                    info.setAutoIncrement(rs.getBoolean("is_auto_increment"));

                    // PostgreSQL doesn't have COLUMN_KEY like MySQL, so we set it based on constraints
                    String columnKey = "";
                    if (rs.getBoolean("is_primary_key")) {
                        columnKey = "PRI";
                    } else if (rs.getBoolean("is_unique")) {
                        columnKey = "UNI";
                    }
                    info.setColumnKey(columnKey);
                }
            }
        }

        // 2. Get indexes that include this column
        String indexQuery = """
            SELECT 
                i.relname as index_name,
                am.amname as index_type,
                CASE WHEN ix.indisunique THEN 0 ELSE 1 END as non_unique,
                a.attnum as seq_in_index,
                s.n_distinct as cardinality
            FROM pg_class t
            JOIN pg_index ix ON t.oid = ix.indrelid
            JOIN pg_class i ON i.oid = ix.indexrelid
            JOIN pg_am am ON i.relam = am.oid
            JOIN pg_attribute a ON a.attrelid = t.oid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            LEFT JOIN pg_stats s ON s.schemaname = n.nspname 
                AND s.tablename = t.relname 
                AND s.attname = a.attname
            WHERE n.nspname = ?
              AND t.relname = ?
              AND a.attname = ?
              AND a.attnum = ANY(ix.indkey)
            ORDER BY i.relname, a.attnum
            """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(indexQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            List<Map<String, String>> indexes = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> index = new java.util.HashMap<>();
                    index.put("INDEX_NAME", rs.getString("index_name"));
                    index.put("INDEX_TYPE", rs.getString("index_type"));
                    index.put("NON_UNIQUE", rs.getString("non_unique"));
                    index.put("SEQ_IN_INDEX", rs.getString("seq_in_index"));
                    Object cardinality = rs.getObject("cardinality");
                    index.put("CARDINALITY", cardinality != null ? cardinality.toString() : null);
                    indexes.add(index);
                }
            }
            info.setIndexes(indexes);
        }

        // 3. Get foreign key references (where this column references another table)
        String fkQuery = """
            SELECT 
                tc.constraint_name,
                ccu.table_schema as referenced_table_schema,
                ccu.table_name as referenced_table_name,
                ccu.column_name as referenced_column_name,
                rc.update_rule,
                rc.delete_rule
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu 
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu 
                ON ccu.constraint_name = tc.constraint_name
                AND ccu.table_schema = tc.table_schema
            JOIN information_schema.referential_constraints rc 
                ON tc.constraint_name = rc.constraint_name
                AND tc.table_schema = rc.constraint_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND kcu.table_schema = ?
              AND kcu.table_name = ?
              AND kcu.column_name = ?
            """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(fkQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            List<Map<String, String>> foreignKeys = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> fk = new java.util.HashMap<>();
                    fk.put("CONSTRAINT_NAME", rs.getString("constraint_name"));
                    fk.put("REFERENCED_TABLE_SCHEMA", rs.getString("referenced_table_schema"));
                    fk.put("REFERENCED_TABLE_NAME", rs.getString("referenced_table_name"));
                    fk.put("REFERENCED_COLUMN_NAME", rs.getString("referenced_column_name"));
                    fk.put("UPDATE_RULE", rs.getString("update_rule"));
                    fk.put("DELETE_RULE", rs.getString("delete_rule"));
                    foreignKeys.add(fk);
                }
            }
            info.setForeignKeyReferences(foreignKeys);
        }

        // 4. Get foreign keys that reference this column (incoming references)
        String referencedByQuery = """
            SELECT 
                kcu.table_schema,
                kcu.table_name,
                kcu.column_name,
                tc.constraint_name,
                rc.update_rule,
                rc.delete_rule
            FROM information_schema.constraint_column_usage ccu
            JOIN information_schema.key_column_usage kcu 
                ON ccu.constraint_name = kcu.constraint_name
                AND ccu.table_schema = kcu.table_schema
            JOIN information_schema.table_constraints tc 
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            JOIN information_schema.referential_constraints rc 
                ON tc.constraint_name = rc.constraint_name
                AND tc.table_schema = rc.constraint_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND ccu.table_schema = ?
              AND ccu.table_name = ?
              AND ccu.column_name = ?
            """;

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(referencedByQuery)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);

            List<Map<String, String>> referencedBy = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> ref = new java.util.HashMap<>();
                    ref.put("TABLE_SCHEMA", rs.getString("table_schema"));
                    ref.put("TABLE_NAME", rs.getString("table_name"));
                    ref.put("COLUMN_NAME", rs.getString("column_name"));
                    ref.put("CONSTRAINT_NAME", rs.getString("constraint_name"));
                    ref.put("UPDATE_RULE", rs.getString("update_rule"));
                    ref.put("DELETE_RULE", rs.getString("delete_rule"));
                    referencedBy.add(ref);
                }
            }
            info.setReferencedByForeignKeys(referencedBy);
        }

        return info;
    }

    @Override
    public ArrayList<String> getTablesForDatabase(String schemaName) throws SQLException {
        ArrayList<String> tableNames = new ArrayList<>();

        String query =
                "SELECT t.table_name, " +
                        "       pg_total_relation_size(quote_ident(t.table_schema) || '.' || quote_ident(t.table_name)) AS table_size_bytes " +
                        "FROM information_schema.tables t " +
                        "WHERE t.table_schema = ? " +
                        "AND t.table_type = 'BASE TABLE'";

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query)) {
            stmt.setString(1, schemaName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    //double tableSizeBytes = rs.getDouble("table_size_bytes"); //TODO
                    tableNames.add(tableName);
                }
            }
        }

        return tableNames;
    }

    @Override
    public ArrayList<ColumnData> getColumnsForTable(String schemaName, String tableName) throws SQLException {
        ArrayList<ColumnData> columns = new ArrayList<>();

        String columnQuery = "SELECT \n" +
                "    c.column_name,\n" +
                "    c.data_type,\n" +
                "    CASE WHEN bool_or(pk.constraint_type = 'PRIMARY KEY') THEN true ELSE false END as is_primary_key,\n" +
                "    CASE WHEN c.is_nullable = 'NO' THEN true ELSE false END as is_not_null,\n" +
                "    CASE WHEN bool_or(pk.constraint_type = 'UNIQUE') THEN true ELSE false END as is_unique,\n" +
                "    CASE WHEN c.column_default LIKE 'nextval%' THEN true ELSE false END as is_auto_increment,\n" +
                "    (SELECT tc.constraint_name \n" +
                "     FROM information_schema.key_column_usage ku\n" +
                "     JOIN information_schema.table_constraints tc\n" +
                "         ON ku.constraint_name = tc.constraint_name\n" +
                "         AND ku.table_schema = tc.table_schema\n" +
                "         AND ku.table_name = tc.table_name\n" +
                "     WHERE tc.constraint_type = 'UNIQUE'\n" +
                "         AND ku.table_schema = c.table_schema\n" +
                "         AND ku.table_name = c.table_name\n" +
                "         AND ku.column_name = c.column_name\n" +
                "     LIMIT 1) as unique_index_name\n" +
                "FROM information_schema.columns c\n" +
                "LEFT JOIN (\n" +
                "    SELECT ku.column_name, tc.constraint_type\n" +
                "    FROM information_schema.key_column_usage ku\n" +
                "    JOIN information_schema.table_constraints tc\n" +
                "        ON ku.constraint_name = tc.constraint_name\n" +
                "        AND ku.table_schema = tc.table_schema\n" +
                "        AND ku.table_name = tc.table_name\n" +
                "    WHERE tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')\n" +
                "        AND ku.table_schema = ?\n" +
                "        AND ku.table_name = ?\n" +
                ") pk ON c.column_name = pk.column_name\n" +
                "WHERE c.table_schema = ?\n" +
                "    AND c.table_name = ?\n" +
                "GROUP BY c.column_name, c.data_type, c.is_nullable, c.column_default, c.ordinal_position, c.table_schema, c.table_name\n" +
                "ORDER BY c.ordinal_position";

        PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);

        columnStatement.setString(1, schemaName);
        columnStatement.setString(2, tableName);
        columnStatement.setString(3, schemaName);
        columnStatement.setString(4, tableName);

        ResultSet resultSet = columnStatement.executeQuery();

        while (resultSet.next()) {
            String columnName = resultSet.getString("column_name");
            String dataType = resultSet.getString("data_type").toUpperCase();
            boolean primaryKey = resultSet.getBoolean("is_primary_key");
            boolean notNull = resultSet.getBoolean("is_not_null");
            boolean unique = resultSet.getBoolean("is_unique");
            boolean autoIncrement = resultSet.getBoolean("is_auto_increment");
            String uniqueIndexName = resultSet.getString("unique_index_name");

            ColumnData columnData = new ColumnData(
                    columnName,
                    dataType,
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
    public ArrayList<String> getDatabases(String name) {
        ArrayList<String> databases = new ArrayList<String>();
        try {
            String query = "SELECT datname \n"
                    + "FROM pg_database\n"
                    + "WHERE datistemplate = false\n"
                    + "ORDER BY \n"
                    + "    CASE WHEN datname = ? THEN 0 ELSE 1 END,\n"
                    + "    datname;";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            statement.setString(1, name);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                databases.add(result.getString("datname"));
            }

            statement.close();
            result.close();
            return databases;
        } catch (SQLException e) {
            log.error("getDatabases failed", e);
        }
        return databases;
    }

    public ArrayList<String> getSchemas() {
        ArrayList<String> schemas = new ArrayList<String>();
        try {
            String query = "SELECT schema_name\n"
                    + "FROM information_schema.schemata\n"
                    + "WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast', 'pg_temp_1', 'pg_toast_temp_1')\n"
                    + "   OR schema_name = 'public';\n"
                    + "";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                schemas.add(result.getString("schema_name"));
            }
        } catch (SQLException e) {
            log.error("getSchemas failed", e);
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
            try (ResultSet pkRs = metaData.getPrimaryKeys(null, dbName, tableName)) {
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
                    if (indexName == null || indexName.endsWith("_pkey")) {
                        continue;
                    }
                    String columnName = indexInfo.getString("COLUMN_NAME");
                    boolean nonUnique = indexInfo.getBoolean("NON_UNIQUE");

                    indexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                    indexUniqueness.put(indexName, !nonUnique);
                }
            }

            script.append("-- Table: ").append(dbName).append(".").append(tableName).append("\n");
            script.append("-- DROP TABLE IF EXISTS ").append(dbName).append(".").append(tableName).append(";\n\n");
            script.append("CREATE TABLE IF NOT EXISTS ").append(dbName).append(".").append(tableName).append("\n(\n");

            // Query information_schema to get complete column type info (similar to getDetailedColumnInfo)
            String columnQuery = """
                SELECT 
                    column_name,
                    data_type,
                    character_maximum_length,
                    numeric_precision,
                    numeric_scale,
                    is_nullable,
                    column_default,
                    udt_name
                FROM information_schema.columns
                WHERE table_schema = ? 
                  AND table_name = ?
                ORDER BY ordinal_position
                """;

            PreparedStatement columnStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(columnQuery);
            columnStatement.setString(1, dbName);
            columnStatement.setString(2, tableName);

            try (ResultSet rs = columnStatement.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) script.append(",\n");
                    first = false;

                    String columnName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    String udtName = rs.getString("udt_name");
                    String isNullable = rs.getString("is_nullable");
                    String columnDefault = rs.getString("column_default");

                    // Build full column type with sizes
                    String fullType;
                    switch (dataType.toLowerCase()) {
                        case "character varying" -> {
                            Integer maxLength = rs.getObject("character_maximum_length") != null ?
                                    rs.getInt("character_maximum_length") : null;
                            fullType = maxLength != null ?
                                    "character varying(" + maxLength + ") COLLATE pg_catalog.\"default\"" :
                                    "character varying COLLATE pg_catalog.\"default\"";
                        }
                        case "character" -> {
                            Integer maxLength = rs.getObject("character_maximum_length") != null ?
                                    rs.getInt("character_maximum_length") : null;
                            fullType = maxLength != null ?
                                    "character(" + maxLength + ") COLLATE pg_catalog.\"default\"" :
                                    "character COLLATE pg_catalog.\"default\"";
                        }
                        case "numeric" -> {
                            Integer precision = rs.getObject("numeric_precision") != null ?
                                    rs.getInt("numeric_precision") : null;
                            Integer scale = rs.getObject("numeric_scale") != null ?
                                    rs.getInt("numeric_scale") : null;
                            if (precision != null && scale != null) {
                                fullType = "numeric(" + precision + "," + scale + ")";
                            } else if (precision != null) {
                                fullType = "numeric(" + precision + ")";
                            } else {
                                fullType = "numeric";
                            }
                        }
                        case "text" -> fullType = "text COLLATE pg_catalog.\"default\"";
                        case "USER-DEFINED" -> fullType = udtName; // For enums, custom types
                        case "ARRAY" -> fullType = udtName; // For array types
                        default -> fullType = dataType;
                    }

                    script.append("    ").append(columnName).append(" ").append(fullType);

                    // Add NOT NULL if applicable
                    if ("NO".equals(isNullable)) {
                        script.append(" NOT NULL");
                    }

                    // Add DEFAULT if present
                    if (columnDefault != null) {
                        script.append(" DEFAULT ").append(columnDefault);
                    }
                }

                // Add PRIMARY KEY constraint
                if (!primaryKeys.isEmpty()) {
                    script.append(",\n    CONSTRAINT ").append(tableName).append("_pkey PRIMARY KEY (");
                    script.append(String.join(", ", primaryKeys));
                    script.append(")");
                }

                // Add unique indexes as constraints
                for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                    String indexName = entry.getKey();
                    List<String> indexColumns = entry.getValue();
                    boolean isUnique = indexUniqueness.get(indexName);

                    if (isUnique) {
                        script.append(",\n    CONSTRAINT ").append(indexName).append(" UNIQUE (");
                        script.append(String.join(", ", indexColumns));
                        script.append(")");
                    }
                }

                // Add FOREIGN KEY constraints
                for (Map.Entry<String, ForeignKeyData> entry : foreignKeys.entrySet()) {
                    ForeignKeyData fk = entry.getValue();
                    script.append(",\n    CONSTRAINT ").append(fk.getConstraintName())
                            .append(" FOREIGN KEY (").append(fk.getLocalColumn()).append(")")
                            .append(" REFERENCES ").append(dbName).append(".")
                            .append(fk.getReferenceTable()).append(" (")
                            .append(fk.getReferenceColumn()).append(")")
                            .append(" MATCH SIMPLE");

                    // Add ON UPDATE action
                    if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                        script.append(" ON UPDATE ").append(fk.getOnUpdate());
                    }

                    // Add ON DELETE action
                    if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                        script.append(" ON DELETE ").append(fk.getOnDelete());
                    }
                }

                script.append("\n)\nTABLESPACE pg_default;\n\n");
                script.append("ALTER TABLE IF EXISTS ").append(dbName).append(".")
                        .append(tableName).append("\n    OWNER to postgres;");

                // Create non-unique indexes separately
                for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                    String indexName = entry.getKey();
                    List<String> indexColumns = entry.getValue();
                    boolean isUnique = indexUniqueness.get(indexName);

                    if (!isUnique) {
                        script.append("\n\nCREATE INDEX ").append(indexName)
                                .append(" ON ").append(dbName).append(".").append(tableName)
                                .append(" (").append(String.join(", ", indexColumns)).append(");");
                    }
                }
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

            // Method 3: Using null catalog with schema
            if (columns == null || !columns.next()) {
                try {
                    if (columns != null) columns.close();
                } catch (Exception ignored) {
                }

                columns = metaData.getColumns(null, "public", tableName, null);
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
                String catalog = DatabaseConnection.getInstance().getConnection().getCatalog();
                if (catalog != null && !catalog.isEmpty()) {
                    columns = metaData.getColumns(catalog, null, tableName, null);
                } else {
                    columns = metaData.getColumns(null, "public", tableName, null);
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

                // Skip auto-increment columns (SERIAL, BIGSERIAL)
                if (isAutoIncrement || "SERIAL".equals(dataType) || "BIGSERIAL".equals(dataType)) {
                    continue;
                }

                columnNames.add("\"" + columnName + "\"");

                // Generate appropriate default values based on data type
                String defaultValue;
                if (isNullable) {
                    defaultValue = "NULL";
                } else {
                    switch (sqlType) {
                        case Types.BOOLEAN, Types.BIT -> defaultValue = "false";
                        case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> defaultValue = "0";
                        case Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL ->
                                defaultValue = "0.00";
                        case Types.DATE -> defaultValue = "CURRENT_DATE";
                        case Types.TIME -> defaultValue = "CURRENT_TIME";
                        case Types.TIMESTAMP -> defaultValue = "CURRENT_TIMESTAMP";
                        case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> defaultValue = "''";
                        case Types.OTHER -> {
                            // Handle PostgreSQL specific types that map to Types.OTHER
                            if ("UUID".equals(dataType)) {
                                defaultValue = "gen_random_uuid()";
                            } else if ("JSON".equals(dataType) || "JSONB".equals(dataType)) {
                                defaultValue = "'{}'";
                            } else if ("INET".equals(dataType)) {
                                defaultValue = "'0.0.0.0'";
                            } else if ("TIMESTAMPTZ".equals(dataType)) {
                                defaultValue = "CURRENT_TIMESTAMP";
                            } else if ("TIMETZ".equals(dataType)) {
                                defaultValue = "CURRENT_TIME";
                            } else if (dataType.startsWith("INTERVAL")) {
                                defaultValue = "'0'";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        case Types.ARRAY -> defaultValue = "'{}'";
                        case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                             Types.CLOB, Types.NCLOB -> {
                            // PostgreSQL doesn't have ENUM/SET in the same way as MySQL
                            if ("TEXT".equals(dataType)) {
                                defaultValue = "''";
                            } else {
                                defaultValue = "''";
                            }
                        }
                        default -> {
                            // Additional checks for PostgreSQL specific types
                            if ("TIMESTAMPTZ".equals(dataType)) {
                                defaultValue = "CURRENT_TIMESTAMP";
                            } else if ("TIMETZ".equals(dataType)) {
                                defaultValue = "CURRENT_TIME";
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
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        return "SELECT * FROM \"" + tableName + "\" LIMIT " + limit + ";";
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
                columnNames.add(colName);

                ResultSet columns = metaData.getColumns(catalog, null, tableName, colName);
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
                        case Types.BOOLEAN -> {
                            insertScript.append(Boolean.parseBoolean(value) ? "TRUE" : "FALSE");
                        }
                        case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                            insertScript.append("TIMESTAMP '").append(value).append("'");
                        }
                        case Types.DATE -> {
                            insertScript.append("DATE '").append(value).append("'");
                        }
                        case Types.TIME, Types.TIME_WITH_TIMEZONE -> {
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
            log.error("generateRowInsertScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public String generateCreateTableSQL(String schemaName, String tableName, List<ColumnData> columns, List<ForeignKeyData> foreignKeys) {
        StringBuilder sql = new StringBuilder();

        // Build the fully qualified table name
        String fullTableName = (schemaName != null && !schemaName.isEmpty())
                ? "\"" + schemaName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        // Add columns
        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);

            sql.append("    \"").append(col.getColumnName()).append("\" ");

            // Handle auto-increment using SERIAL types
            if (col.isAutoIncrement()) {
                String dataType = col.getDataType().toUpperCase();
                if (dataType.contains("BIGINT")) {
                    sql.append("BIGSERIAL");
                } else if (dataType.contains("SMALLINT")) {
                    sql.append("SMALLSERIAL");
                } else {
                    sql.append("SERIAL");
                }
            } else {
                sql.append(col.getDataType());
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

        // PostgreSQL: Drop each FK in separate ALTER TABLE statement
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
                if (!stillUnique) {
                    uniqueKeysToDrop.add(oldColName + "_key"); // PostgreSQL naming convention
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

        // 4. Handle column renames FIRST
        List<String> columnsToRename = new ArrayList<>();

        for (Map.Entry<String, String> rename : columnRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            columnsToRename.add(String.format("RENAME COLUMN \"%s\" TO \"%s\"", oldName, newName));
        }

        if (!columnsToRename.isEmpty()) {
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\"\n", databaseName, oldTableName));
            for (int i = 0; i < columnsToRename.size(); i++) {
                sql.append("  ").append(columnsToRename.get(i));
                if (i < columnsToRename.size() - 1) sql.append(",\n");
            }
            sql.append(";\n");
        }

        // 5. Drop columns and add new columns
        List<String> firstPhaseAlterations = new ArrayList<>();

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
                firstPhaseAlterations.add(String.format("DROP COLUMN \"%s\"", oldColName));
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
                if (newPKColumnsToAdd.contains(newCol.getColumnName())) {
                    // Add without SERIAL for now (will be added after PK is set)
                    String dataType = newCol.getDataType();
                    // Convert SERIAL types to INT/BIGINT temporarily
                    if (dataType.equalsIgnoreCase("SERIAL")) dataType = "INTEGER";
                    if (dataType.equalsIgnoreCase("BIGSERIAL")) dataType = "BIGINT";

                    firstPhaseAlterations.add(String.format("ADD COLUMN \"%s\" %s%s",
                            newCol.getColumnName(),
                            dataType,
                            newCol.isNotNull() ? " NOT NULL" : ""
                    ));
                } else {
                    // Regular new column with all attributes
                    firstPhaseAlterations.add(String.format("ADD COLUMN \"%s\" %s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isUnique() && !newCol.isPrimaryKey() ? " UNIQUE" : ""
                    ));
                }
            }
        }

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
        Set<String> columnsWithSequenceRemoved = new HashSet<>();

        // Remove sequences from old PK columns that are losing PK status
        if (pkIsChanging && !oldPKs.isEmpty()) {
            for (String oldPkCol : oldPKs) {
                String adjustedPkCol = columnRenames.getOrDefault(oldPkCol, oldPkCol);

                // Check if this PK column is NOT in the new PK list
                if (!newPKs.contains(adjustedPkCol)) {
                    // Find the old column definition
                    ColumnData oldColWithSeq = null;
                    for (ColumnData col : oldColumns) {
                        if (col.getColumnName().equals(oldPkCol) && col.isAutoIncrement()) {
                            oldColWithSeq = col;
                            break;
                        }
                    }

                    if (oldColWithSeq != null) {
                        // Remove sequence before dropping PK
                        sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" DROP DEFAULT;\n",
                                databaseName, oldTableName, adjustedPkCol));
                        String seqName = oldTableName + "_" + oldPkCol + "_seq";
                        sql.append(String.format("DROP SEQUENCE IF EXISTS \"%s\".\"%s\";\n", databaseName, seqName));
                        columnsWithSequenceRemoved.add(adjustedPkCol);
                    }
                }
            }
        }

        if (pkIsChanging) {
            // Drop old PK constraint (PostgreSQL requires constraint name)
            if (!oldPKs.isEmpty()) {
                sql.append(String.format("ALTER TABLE \"%s\".\"%s\" DROP CONSTRAINT \"%s_pkey\";\n",
                        databaseName, oldTableName, oldTableName));
            }
            // Add new PK
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

            // Skip if this column was just renamed (modifications applied during rename phase if needed)
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
                // Skip if we already handled sequence removal for old PK column
                if (columnsWithSequenceRemoved.contains(newColName)) {
                    continue;
                }

                // Existing column - check if it needs modification
                boolean needsModification = false;
                List<String> modifications = new ArrayList<>();

                // Check if AUTO_INCREMENT is being removed
                if (oldCol.isAutoIncrement() && !newCol.isAutoIncrement()) {
                    // Remove default (drop sequence reference)
                    modifications.add(String.format("ALTER COLUMN \"%s\" DROP DEFAULT", newColName));
                    needsModification = true;

                    // Drop the sequence if it exists
                    String seqName = oldTableName + "_" + newColName + "_seq";
                    sql.append(String.format("DROP SEQUENCE IF EXISTS \"%s\".\"%s\";\n", databaseName, seqName));
                }

                // Check data type change
                if (!oldCol.getDataType().equalsIgnoreCase(newCol.getDataType())) {
                    String dataType = newCol.getDataType();
                    if (dataType.equalsIgnoreCase("SERIAL")) dataType = "INTEGER";
                    if (dataType.equalsIgnoreCase("BIGSERIAL")) dataType = "BIGINT";
                    modifications.add(String.format("ALTER COLUMN \"%s\" TYPE %s", newColName, dataType));
                    needsModification = true;
                }

                // Check NOT NULL change
                if (oldCol.isNotNull() != newCol.isNotNull()) {
                    if (newCol.isNotNull()) {
                        modifications.add(String.format("ALTER COLUMN \"%s\" SET NOT NULL", newColName));
                    } else {
                        modifications.add(String.format("ALTER COLUMN \"%s\" DROP NOT NULL", newColName));
                    }
                    needsModification = true;
                }

                // Check if AUTO_INCREMENT is being added to existing column
                if (!oldCol.isAutoIncrement() && newCol.isAutoIncrement()) {
                    String seqName = oldTableName + "_" + newColName + "_seq";
                    sql.append(String.format("CREATE SEQUENCE \"%s\".\"%s\";\n", databaseName, seqName));
                    modifications.add(String.format("ALTER COLUMN \"%s\" SET DEFAULT nextval('\"%s\".\"%s\"')",
                            newColName, databaseName, seqName));
                    sql.append(String.format("ALTER SEQUENCE \"%s\".\"%s\" OWNED BY \"%s\".\"%s\".\"%s\";\n",
                            databaseName, seqName, databaseName, oldTableName, newColName));
                    needsModification = true;
                }

                // Check UNIQUE constraint (handled separately in PostgreSQL)
                if (oldCol.isUnique() != newCol.isUnique() && !newCol.isPrimaryKey()) {
                    if (newCol.isUnique()) {
                        sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ADD UNIQUE (\"%s\");\n",
                                databaseName, oldTableName, newColName));
                    }
                }

                if (needsModification) {
                    columnsToModify.addAll(modifications);
                }

            } else if (newPKColumnsToAdd.contains(newCol.getColumnName())) {
                // New column that was added as PK - now set up sequence if AUTO_INCREMENT
                if (newCol.isAutoIncrement() || newCol.getDataType().equalsIgnoreCase("SERIAL") ||
                        newCol.getDataType().equalsIgnoreCase("BIGSERIAL")) {
                    // Create sequence and set default
                    String seqName = oldTableName + "_" + newCol.getColumnName() + "_seq";
                    sql.append(String.format("CREATE SEQUENCE \"%s\".\"%s\";\n", databaseName, seqName));
                    sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" SET DEFAULT nextval('\"%s\".\"%s\"');\n",
                            databaseName, oldTableName, newCol.getColumnName(), databaseName, seqName));
                    sql.append(String.format("ALTER SEQUENCE \"%s\".\"%s\" OWNED BY \"%s\".\"%s\".\"%s\";\n",
                            databaseName, seqName, databaseName, oldTableName, newCol.getColumnName()));
                }
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
                    sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ADD UNIQUE (\"%s\");\n",
                            databaseName, oldTableName, newColName));
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
    public void deleteTable(String schemaName, String tableName) throws SQLException {
        // Build the fully qualified table name
        String fullTableName = (schemaName != null && !schemaName.isEmpty())
                ? "\"" + schemaName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        String sql = "DROP TABLE IF EXISTS " + fullTableName;
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public ArrayList<String> getUsers() throws SQLException {
        ArrayList<String> users = new ArrayList<>();

        try {
            String query = "SELECT usename FROM pg_catalog.pg_user ORDER BY usename";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String user = result.getString("usename");
                users.add(user);
            }

            statement.close();
            result.close();

        } catch (SQLException e) {
            // If we don't have permissions, return the current user
            String fallbackQuery = "SELECT CURRENT_USER as usename";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(fallbackQuery);
            ResultSet result = statement.executeQuery();

            if (result.next()) {
                String user = result.getString("usename");
                users.add(user);
            }

            statement.close();
            result.close();
        }

        return users;
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "CREATE SCHEMA IF NOT EXISTS \"" + databaseName + "\"";

        Statement statement = DatabaseConnection.getInstance().getConnection().createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP SCHEMA IF EXISTS \"" + databaseName + "\" CASCADE";

        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "SET search_path TO \"" + databaseName + "\"";
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.execute();
        statement.close();
    }
    
    @Override
    public double getTableSize(String schema, String table) throws SQLException {
        // Get total size (table + indexes)
        String query = """
            SELECT ROUND(pg_total_relation_size(?::regclass) / 1024.0 / 1024.0, 2) AS total_size_mb
            """;

        PreparedStatement pstmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
        pstmt.setString(1, schema + "." + table);

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
