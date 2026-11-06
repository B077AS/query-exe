package io.github.b077as.queryexe.engine.h2;

import io.github.b077as.queryexe.connection.ConnectionProfile;


import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import io.github.b077as.queryexe.query.RowTarget;
import io.github.b077as.queryexe.schema.DetailedColumnData;
import io.github.b077as.queryexe.schema.ColumnData;
import io.github.b077as.queryexe.connection.DatabaseConnection;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import io.github.b077as.queryexe.engine.h2.H2Catalog;
import io.github.b077as.queryexe.engine.h2.H2Catalog.ColumnInfo;
import io.github.b077as.queryexe.engine.h2.H2Catalog.ForeignKeyInfo;
import io.github.b077as.queryexe.engine.h2.H2Catalog.KeyInfo;
import io.github.b077as.queryexe.schema.ForeignKeyData;


import io.github.b077as.queryexe.engine.*;

@Slf4j
public class H2Reader extends SchemaReader {

    private static void putIfAny(java.util.Map<String, java.util.List<String>> objects, String group, java.util.List<String> names) {
        if (!names.isEmpty()) {
            objects.put(group, names);
        }
    }

    /** H2 has one catalog per database, so the schema is what matters. */
    @Override
    public String currentContainer() throws SQLException {
        String schema = connection.getSchema();
        return schema != null ? schema : connection.getCatalog();
    }

    public H2Reader(java.sql.Connection connection, ConnectionProfile profile) {
        super(connection, profile);
    }


    // Names read from the catalog are used exactly as stored (quoted identifiers are case-sensitive in H2);
    // no name at all means "the current schema".
    private String schemaOrCurrent(String schemaName) throws SQLException {
        return schemaName != null && !schemaName.isBlank() ? schemaName : catalog().currentSchema();
    }

    private H2Catalog catalog() {
        return new H2Catalog(connection);
    }

    @Override
    public java.util.Map<String, java.util.List<String>> getSchemaObjects(String schemaName) {
        java.util.Map<String, java.util.List<String>> objects = new LinkedHashMap<>();
        try {
            String schema = schemaOrCurrent(schemaName);
            H2Catalog catalog = catalog();
            // sequences are not listed for now (identity columns create one each, which is mostly noise)
            putIfAny(objects, "Synonyms", catalog.synonyms(schema));
        } catch (SQLException e) {
            log.warn("Could not list the sequences/synonyms of {}: {}", schemaName, e.getMessage());
        }
        return objects;
    }

    private static String escapePattern(String value, String escape) {
        if (escape == null || escape.isEmpty()) {
            return value;
        }
        return value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String schemaName) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            String schema = schemaOrCurrent(schemaName);
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
    public ArrayList<String> getTablesForDatabase(String schemaName) throws SQLException {
        ArrayList<String> tableNames = new ArrayList<>();

        String query = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "ORDER BY table_name";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schemaOrCurrent(schemaName));

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
        String schema = schemaOrCurrent(schemaName);
        H2Catalog catalog = catalog();

        Set<String> primaryKey = new HashSet<>();
        // only single-column UNIQUE constraints make a column unique by itself
        Map<String, String> uniqueConstraintByColumn = new HashMap<>();
        for (KeyInfo key : catalog.keys(schema, tableName)) {
            if (key.isPrimary()) {
                primaryKey.addAll(key.getColumns());
            } else if (key.getColumns().size() == 1) {
                uniqueConstraintByColumn.put(key.getColumns().get(0), key.getName());
            }
        }

        ArrayList<ColumnData> columns = new ArrayList<>();
        for (ColumnInfo column : catalog.columns(schema, tableName)) {
            boolean isPrimaryKey = primaryKey.contains(column.getName());
            String uniqueName = isPrimaryKey ? null : uniqueConstraintByColumn.get(column.getName());
            ColumnData data = new ColumnData(column.getName(), column.getTypeSql(), isPrimaryKey, !column.isNullable(),
                    uniqueName != null, column.isIdentity(), uniqueName);
            if (!column.isIdentity() && !column.isGenerated()) {
                data.setDefaultValue(column.getDefaultValue());
            }
            columns.add(data);
        }
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

        try (PreparedStatement stmt = connection.prepareStatement(keyQuery)) {
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
                    String indexType = rs.getString("INDEX_TYPE");
                    if (indexType != null && (indexType.contains("UNIQUE") || indexType.contains("PRIMARY"))) {
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
    public ArrayList<String> getDatabases(String name) {
        ArrayList<String> schemas = new ArrayList<>();
        String query = "SELECT schema_name FROM information_schema.schemata WHERE schema_name <> 'INFORMATION_SCHEMA' ";
        boolean filtered = name != null && !name.trim().isEmpty();
        if (filtered) {
            // the configured name is user text, so match it regardless of case
            query += "AND UPPER(schema_name) = UPPER(?) ";
        }
        query += "ORDER BY schema_name";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            if (filtered) {
                statement.setString(1, name);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    schemas.add(result.getString("schema_name"));
                }
            }
            if (filtered && schemas.isEmpty()) {
                log.warn("Schema '{}' not found.", name);
            }
        } catch (SQLException e) {
            log.error("getDatabases failed", e);
        }
        return schemas;
    }

    @Override
    public String generateCreateScript(String tableName, String dbName) {
        try {
            return catalog().createScript(schemaOrCurrent(dbName), tableName);
        } catch (SQLException e) {
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
        for (ForeignKeyInfo fk : catalog().foreignKeys(schemaOrCurrent(dbName), tableName)) {
            ForeignKeyData data = new ForeignKeyData(fk.getName(), fk.getColumns().get(0), fk.getReferenceTable(),
                    fk.getReferenceColumns().get(0), fk.getDeleteRule(), fk.getUpdateRule());
            data.setReferenceSchema(fk.getReferenceSchema());
            data.setLocalColumns(fk.getColumns());
            data.setReferenceColumns(fk.getReferenceColumns());
            foreignKeys.put(fk.getName(), data);
        }
        return foreignKeys;
    }

    @Override
    public String generateInsertScript(String tableName, String dbName) {
        try {
            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = connection.getMetaData();

            // exact schema + table: the name arguments of getColumns are LIKE patterns, so '_' and '%' must be escaped
            String schema = schemaOrCurrent(dbName);
            String escape = metaData.getSearchStringEscape();
            ResultSet columns = metaData.getColumns(null, escapePattern(schema, escape), escapePattern(tableName, escape), null);

            List<String> columnNames = new ArrayList<>();
            List<String> columnValues = new ArrayList<>();

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME").toUpperCase();
                int sqlType = columns.getInt("DATA_TYPE");
                boolean isNullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String autoIncrement = columns.getString("IS_AUTOINCREMENT");
                boolean isAutoIncrement = "YES".equalsIgnoreCase(autoIncrement);

                boolean isGenerated = "YES".equalsIgnoreCase(columns.getString("IS_GENERATEDCOLUMN"));
                if (isAutoIncrement || isGenerated || "IDENTITY".equals(dataType)) {
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

            insertScript.append("INSERT INTO ").append(H2Catalog.qualified(schema, tableName)).append("\n");
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
            log.error("generateInsertScript failed", e);
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public void deleteTable(String schemaName, String tableName) throws SQLException {
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

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP SCHEMA IF EXISTS " + quoteIdentifier(databaseName);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "SET SCHEMA " + quoteIdentifier(databaseName);
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.execute();
        statement.close();
    }

    @Override
    public double getTableSize(String schema, String table) throws SQLException {
        // DISK_SPACE_USED takes the table name as H2 would parse it, so quote and qualify it
        try (PreparedStatement stmt = connection
                .prepareStatement("SELECT ROUND(DISK_SPACE_USED(?) / 1024.0 / 1024.0, 2)")) {
            stmt.setString(1, H2Catalog.qualified(schemaOrCurrent(schema), table));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : -1;
            }
        }
    }
}
