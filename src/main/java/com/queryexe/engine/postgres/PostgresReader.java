package com.queryexe.engine.postgres;

import com.queryexe.connection.ConnectionProfile;


import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

import com.queryexe.query.RowTarget;
import com.queryexe.schema.DetailedColumnData;
import com.queryexe.schema.ColumnData;
import com.queryexe.schema.PartitionData;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.connection.driver.DriverInfo;
import com.queryexe.schema.ForeignKeyData;


import com.queryexe.engine.*;

@Slf4j
public class PostgresReader extends SchemaReader {

    public PostgresReader(java.sql.Connection connection, ConnectionProfile profile) {
        super(connection, profile);
    }



    @Override
    public String containerKind() {
        return "schema";
    }

    @Override
    public List<String> listContainers(String configuredDatabase) {
        return getSchemas();
    }

    @Override
    public String selectInitialContainer(String configuredDatabase) {
        try {
            return currentContainer();
        } catch (SQLException e) {
            return "public";
        }
    }

    /** The first schema of the session's search_path ("public" when it can't be read). */
    @Override
    public String currentContainer() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SHOW search_path")) {
            if (rs.next()) {
                return com.queryexe.query.SqlInspector.firstSearchPathSchema(rs.getString(1)).orElse("public");
            }
        } catch (SQLException e) {
            log.debug("Could not read search_path: {}", e.getMessage());
        }
        return "public";
    }
    /**
     * Schemas, tables and columns of every database on the server, for a connection that names no database. Each other
     * database is read through a short-lived connection of its own; this reader's connection is never switched.
     */
    @Override
    public Map<String, Map<String, Map<String, ArrayList<ColumnData>>>> getCompleteHierarchy(String configuredDatabase) {
        if (configuredDatabase != null && !configuredDatabase.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Map<String, ArrayList<ColumnData>>>> hierarchy = new LinkedHashMap<>();
        try {
            String currentDatabase = connection.getCatalog();
            String originalUrl = connection.getMetaData().getURL();
            for (String dbName : getDatabases(currentDatabase != null ? currentDatabase : "postgres")) {
                java.sql.Connection target = connection;
                boolean opened = false;
                try {
                    if (!dbName.equals(currentDatabase)) {
                        String newUrl = originalUrl.replaceFirst("/[^/]*\\?","/" + dbName + "?");
                        if (!originalUrl.contains("?")) {
                            newUrl = originalUrl.substring(0, originalUrl.lastIndexOf('/') + 1) + dbName;
                        }
                        target = java.sql.DriverManager.getConnection(newUrl, connection.getMetaData().getUserName(), profile.getPassword());
                        opened = true;
                    }
                    PostgresReader reader = new PostgresReader(target, profile);
                    Map<String, Map<String, ArrayList<ColumnData>>> schemaMap = new LinkedHashMap<>();
                    for (String schemaName : reader.getSchemas()) {
                        Map<String, ArrayList<ColumnData>> tables = reader.getAllTablesAndColumns(schemaName);
                        if (tables != null && !tables.isEmpty()) {
                            schemaMap.put(schemaName, tables);
                        }
                    }
                    if (!schemaMap.isEmpty()) {
                        hierarchy.put(dbName, schemaMap);
                    }
                } catch (SQLException e) {
                    log.error("Error accessing database '{}': {}", dbName, e.getMessage());
                } finally {
                    if (opened) {
                        try {
                            target.close();
                        } catch (SQLException e) {
                            log.warn("Could not close the connection to '{}': {}", dbName, e.getMessage());
                        }
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
    public Map<String, List<PartitionData>> getPartitions(String schemaName) {
        // declarative partitions are child tables, found through pg_inherits
        String sql = """
                SELECT p.relname, c.relname, pg_get_expr(c.relpartbound, c.oid), pt.partstrat, c.reltuples::bigint
                FROM pg_class c
                JOIN pg_inherits i ON i.inhrelid = c.oid
                JOIN pg_class p ON p.oid = i.inhparent
                JOIN pg_namespace n ON n.oid = p.relnamespace AND n.oid = c.relnamespace
                LEFT JOIN pg_partitioned_table pt ON pt.partrelid = p.oid
                WHERE c.relispartition AND c.relkind IN ('r', 'p') AND n.nspname = ?
                ORDER BY p.relname, c.relname
                """;
        Map<String, List<PartitionData>> out = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schemaName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long rows = rs.getLong(5);
                    Long count = rs.wasNull() || rows < 0 ? null : rows; // reltuples is -1 until the first ANALYZE
                    String method = switch (String.valueOf(rs.getString(4))) {
                        case "r" -> "RANGE";
                        case "l" -> "LIST";
                        case "h" -> "HASH";
                        default -> null;
                    };
                    String bound = rs.getString(3);
                    if (bound != null) {
                        // FOR VALUES FROM (1) TO (10) -> 1 .. 10; other forms only lose their FOR VALUES prefix
                        bound = bound.replaceFirst("^FOR VALUES ", "").replaceFirst("^FROM \\((.*)\\) TO \\((.*)\\)$", "$1 .. $2");
                    }
                    out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new PartitionData(rs.getString(2), null, method, bound, count));
                }
            }
        } catch (SQLException e) {
            log.warn("Could not list the partitions of {}: {}", schemaName, e.getMessage());
        }
        return out;
    }

    @Override
    public boolean partitionsAreTables() {
        return true;
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

        try (PreparedStatement stmt = connection.prepareStatement(metadataQuery)) {
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

        try (PreparedStatement stmt = connection.prepareStatement(indexQuery)) {
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

        try (PreparedStatement stmt = connection.prepareStatement(fkQuery)) {
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

        try (PreparedStatement stmt = connection.prepareStatement(referencedByQuery)) {
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
                "SELECT t.table_name " +
                        "FROM information_schema.tables t " +
                        "WHERE t.table_schema = ? " +
                        "AND t.table_type = 'BASE TABLE' " +
                        "ORDER BY t.table_name";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schemaName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
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
                "    CASE WHEN c.column_default LIKE 'nextval%' OR c.is_identity = 'YES' THEN true ELSE false END as is_auto_increment,\n" +
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
                "GROUP BY c.column_name, c.data_type, c.is_nullable, c.column_default, c.is_identity, c.ordinal_position, c.table_schema, c.table_name\n" +
                "ORDER BY c.ordinal_position";

        PreparedStatement columnStatement = connection.prepareStatement(columnQuery);

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
            PreparedStatement statement = connection.prepareStatement(query);
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
                    // every system schema: pg_catalog, pg_toast, pg_temp_N, pg_toast_temp_N, ...
                    + "WHERE schema_name <> 'information_schema' AND schema_name NOT LIKE 'pg\\_%'\n"
                    + "ORDER BY schema_name;\n";
            PreparedStatement statement = connection.prepareStatement(query);
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
            DatabaseMetaData metaData = connection.getMetaData();

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

            PreparedStatement columnStatement = connection.prepareStatement(columnQuery);
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
    protected String[] metadataScope(String container) {
        return new String[] { null, container };
    }

    @Override
    public Map<String, ForeignKeyData> extractForeignKeys(String dbName, String tableName) throws SQLException {
        Map<String, ForeignKeyData> foreignKeys = new LinkedHashMap<>();
        DatabaseMetaData metaData = connection.getMetaData();

        // In PostgreSQL the tree's "database" is a schema; the JDBC catalog is the real database, so pass it as the schema.
        // getImportedKeys is ordered by PK table / key name / KEY_SEQ, so the columns of a composite key arrive in order.
        try (ResultSet fkRs = metaData.getImportedKeys(null, dbName, tableName)) {
            while (fkRs.next()) {
                String constraintName = fkRs.getString("FK_NAME");
                ForeignKeyData existing = foreignKeys.get(constraintName);
                if (existing != null) {
                    existing.getLocalColumns().add(fkRs.getString("FKCOLUMN_NAME"));
                    existing.getReferenceColumns().add(fkRs.getString("PKCOLUMN_NAME"));
                    continue;
                }
                ForeignKeyData fk = new ForeignKeyData(
                        constraintName,
                        fkRs.getString("FKCOLUMN_NAME"),
                        fkRs.getString("PKTABLE_NAME"),
                        fkRs.getString("PKCOLUMN_NAME"),
                        fkRs.getInt("DELETE_RULE"),
                        fkRs.getInt("UPDATE_RULE"));
                fk.setReferenceSchema(fkRs.getString("PKTABLE_SCHEM"));
                fk.setLocalColumns(new ArrayList<>(List.of(fk.getLocalColumn())));
                fk.setReferenceColumns(new ArrayList<>(List.of(fk.getReferenceColumn())));
                foreignKeys.put(constraintName, fk);
            }
        }
        return foreignKeys;
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
                String catalog = connection.getCatalog();
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
    public void deleteTable(String schemaName, String tableName) throws SQLException {
        // Build the fully qualified table name
        String fullTableName = (schemaName != null && !schemaName.isEmpty())
                ? "\"" + schemaName + "\".\"" + tableName + "\""
                : "\"" + tableName + "\"";

        String sql = "DROP TABLE IF EXISTS " + fullTableName;
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(databaseName);

        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP SCHEMA IF EXISTS " + quoteIdentifier(databaseName) + " CASCADE";

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "SET search_path TO " + quoteIdentifier(databaseName);
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.execute();
        statement.close();
    }
    
    @Override
    public double getTableSize(String schema, String table) throws SQLException {
        // Get total size (table + indexes)
        String query = """
            SELECT ROUND(pg_total_relation_size(?::regclass) / 1024.0 / 1024.0, 2) AS total_size_mb
            """;

        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, schema + "." + table);

        ResultSet rs = pstmt.executeQuery();
        double sizeMB = rs.next() ? rs.getDouble("total_size_mb") : -1;

        rs.close();
        pstmt.close();

        return sizeMB;
    }
}
