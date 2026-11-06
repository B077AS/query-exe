package io.github.b077as.queryexe.engine.sqlserver;

import io.github.b077as.queryexe.connection.ConnectionProfile;


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
import io.github.b077as.queryexe.query.RowTarget;
import io.github.b077as.queryexe.schema.DetailedColumnData;
import io.github.b077as.queryexe.schema.ColumnData;
import io.github.b077as.queryexe.schema.PartitionData;
import io.github.b077as.queryexe.connection.DatabaseConnection;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import io.github.b077as.queryexe.schema.ForeignKeyData;



import io.github.b077as.queryexe.engine.*;

@Slf4j
public class SqlServerReader extends SchemaReader {

    public SqlServerReader(java.sql.Connection connection, ConnectionProfile profile) {
        super(connection, profile);
    }


    @Override
    public LinkedHashMap<String, ArrayList<ColumnData>> getAllTablesAndColumns(String databaseName) {
        LinkedHashMap<String, ArrayList<ColumnData>> tablesMap = new LinkedHashMap<>();

        try {
            ArrayList<String> tableNames = getTablesForDatabase(databaseName);

            for (String fullTableName : tableNames) {
                ArrayList<ColumnData> columns = getColumnsForTable(databaseName, fullTableName);
                tablesMap.put(fullTableName, columns);
            }

            return tablesMap;
        } catch (SQLException e) {
            log.error("getAllTablesAndColumns failed", e);
            throw new RuntimeException("Failed to fetch database schema for database: " + databaseName, e);
        }
    }

    @Override
    public ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException {
        ArrayList<String> tableNames = new ArrayList<>();

        // Dynamic query construction for identifiers
        String query = String.format("""
            SELECT 
                T.TABLE_SCHEMA + '.' + T.TABLE_NAME AS FULL_TABLE_NAME
            FROM 
                %s.INFORMATION_SCHEMA.TABLES T
            WHERE 
                T.TABLE_CATALOG = ? 
                AND T.TABLE_TYPE = 'BASE TABLE'
            ORDER BY 
                T.TABLE_SCHEMA,
                T.TABLE_NAME;
        """, databaseName);

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, databaseName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String fullTableName = rs.getString("FULL_TABLE_NAME");
                    tableNames.add(fullTableName);
                }
            }
        }

        return tableNames;
    }

    @Override
    public ArrayList<ColumnData> getColumnsForTable(String schemaName, String tableName) throws SQLException {
        ArrayList<ColumnData> columns = new ArrayList<>();

        // For SQL Server, schemaName is actually the database name
        // and tableName is in format "schema.table"
        String databaseName = schemaName;

        // Parse schema and table from tableName
        String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
        String tableSchema = parts[0];
        String actualTableName = parts[1];

        String query = String.format("""
            SELECT
                C.COLUMN_NAME,
                C.DATA_TYPE,
                C.IS_NULLABLE,
                COLUMNPROPERTY(OBJECT_ID('%s.' + C.TABLE_SCHEMA + '.' + C.TABLE_NAME), C.COLUMN_NAME, 'IsIdentity') AS IS_IDENTITY,
                CASE
                    WHEN PK.CONSTRAINT_TYPE = 'PRIMARY KEY' THEN 1
                    ELSE 0
                END AS IS_PRIMARY_KEY,
                CASE
                    WHEN UQ.CONSTRAINT_TYPE = 'UNIQUE' THEN 1
                    ELSE 0
                END AS IS_UNIQUE,
                UQ.CONSTRAINT_NAME AS UNIQUE_INDEX_NAME
            FROM
                %s.INFORMATION_SCHEMA.COLUMNS C
            LEFT JOIN
                %s.INFORMATION_SCHEMA.KEY_COLUMN_USAGE KCU
                ON C.TABLE_NAME = KCU.TABLE_NAME
                AND C.COLUMN_NAME = KCU.COLUMN_NAME
                AND C.TABLE_SCHEMA = KCU.TABLE_SCHEMA
            LEFT JOIN
                %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS PK
                ON KCU.CONSTRAINT_NAME = PK.CONSTRAINT_NAME
                AND KCU.TABLE_SCHEMA = PK.TABLE_SCHEMA
                AND PK.CONSTRAINT_TYPE = 'PRIMARY KEY'
            LEFT JOIN
                %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS UQ
                ON KCU.CONSTRAINT_NAME = UQ.CONSTRAINT_NAME
                AND KCU.TABLE_SCHEMA = UQ.TABLE_SCHEMA
                AND UQ.CONSTRAINT_TYPE = 'UNIQUE'
            WHERE
                C.TABLE_SCHEMA = ?
                AND C.TABLE_NAME = ?
            ORDER BY
                C.ORDINAL_POSITION;
            """, databaseName, databaseName, databaseName, databaseName, databaseName);

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, tableSchema);
        stmt.setString(2, actualTableName);

        try (ResultSet results = stmt.executeQuery()) {
            while (results.next()) {
                String columnName = results.getString("COLUMN_NAME");
                String dataType = results.getString("DATA_TYPE").toUpperCase();
                boolean primaryKey = results.getInt("IS_PRIMARY_KEY") == 1;
                boolean notNull = results.getString("IS_NULLABLE").equals("NO");
                boolean unique = results.getInt("IS_UNIQUE") == 1;
                boolean autoIncrement = results.getInt("IS_IDENTITY") == 1;
                String uniqueIndexName = results.getString("UNIQUE_INDEX_NAME");

                ColumnData column = new ColumnData(
                    columnName,
                    dataType,
                    primaryKey,
                    notNull,
                    unique,
                    autoIncrement,
                    uniqueIndexName
                );
                
                columns.add(column);
            }
        }
        stmt.close();

        return columns;
    }

    @Override
    public Map<String, List<PartitionData>> getPartitions(String databaseName) {
        // tables whose clustered index (or heap) sits on a partition scheme
        String db = quoteIdentifier(databaseName);
        String sql = "SELECT t.name, p.partition_number, p.rows, CAST(rv.value AS nvarchar(200)), f.boundary_value_on_right"
                + " FROM " + db + ".sys.tables t"
                + " JOIN " + db + ".sys.indexes i ON i.object_id = t.object_id AND i.index_id IN (0, 1)"
                + " JOIN " + db + ".sys.partition_schemes ps ON ps.data_space_id = i.data_space_id"
                + " JOIN " + db + ".sys.partition_functions f ON f.function_id = ps.function_id"
                + " JOIN " + db + ".sys.partitions p ON p.object_id = t.object_id AND p.index_id = i.index_id"
                + " LEFT JOIN " + db + ".sys.partition_range_values rv ON rv.function_id = f.function_id AND rv.boundary_id = p.partition_number"
                + " ORDER BY t.name, p.partition_number";
        Map<String, List<PartitionData>> out = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String upper = rs.getString(4);
                String bound = upper == null ? "MAXVALUE" : (rs.getBoolean(5) ? "< " : "<= ") + upper;
                out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new PartitionData("Partition " + rs.getInt(2), null, "RANGE", bound, rs.getLong(3)));
            }
        } catch (SQLException e) {
            log.warn("Could not list the partitions of {}: {}", databaseName, e.getMessage());
        }
        return out;
    }

    @Override
    public DetailedColumnData getDetailedColumnInfo(String databaseName, String tableName, String columnName) throws SQLException {
        DetailedColumnData info = new DetailedColumnData();

        // Parse schema and table from tableName (format: "schema.table")
        String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];

        // 1. Get basic column metadata from INFORMATION_SCHEMA
        String metadataQuery = String.format("""
            SELECT 
                C.COLUMN_NAME,
                C.TABLE_NAME,
                C.TABLE_SCHEMA,
                C.ORDINAL_POSITION,
                C.DATA_TYPE,
                C.CHARACTER_MAXIMUM_LENGTH,
                C.CHARACTER_OCTET_LENGTH,
                C.NUMERIC_PRECISION,
                C.NUMERIC_SCALE,
                C.CHARACTER_SET_NAME,
                C.COLLATION_NAME,
                C.IS_NULLABLE,
                C.COLUMN_DEFAULT,
                COLUMNPROPERTY(OBJECT_ID('%s.' + C.TABLE_SCHEMA + '.' + C.TABLE_NAME), C.COLUMN_NAME, 'IsIdentity') AS IS_IDENTITY,
                CASE
                    WHEN PK.CONSTRAINT_TYPE = 'PRIMARY KEY' THEN 1
                    ELSE 0
                END AS IS_PRIMARY_KEY,
                CASE
                    WHEN UQ.CONSTRAINT_TYPE = 'UNIQUE' THEN 1
                    ELSE 0
                END AS IS_UNIQUE,
                UQ.CONSTRAINT_NAME AS UNIQUE_INDEX_NAME
            FROM
                %s.INFORMATION_SCHEMA.COLUMNS C
            LEFT JOIN
                %s.INFORMATION_SCHEMA.KEY_COLUMN_USAGE KCU
                ON C.TABLE_NAME = KCU.TABLE_NAME
                AND C.COLUMN_NAME = KCU.COLUMN_NAME
                AND C.TABLE_SCHEMA = KCU.TABLE_SCHEMA
            LEFT JOIN
                %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS PK
                ON KCU.CONSTRAINT_NAME = PK.CONSTRAINT_NAME
                AND KCU.TABLE_SCHEMA = PK.TABLE_SCHEMA
                AND PK.CONSTRAINT_TYPE = 'PRIMARY KEY'
            LEFT JOIN
                %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS UQ
                ON KCU.CONSTRAINT_NAME = UQ.CONSTRAINT_NAME
                AND KCU.TABLE_SCHEMA = UQ.TABLE_SCHEMA
                AND UQ.CONSTRAINT_TYPE = 'UNIQUE'
            WHERE
                C.TABLE_SCHEMA = ?
                AND C.TABLE_NAME = ?
                AND C.COLUMN_NAME = ?
            """, databaseName, databaseName, databaseName, databaseName, databaseName);

        try (PreparedStatement stmt = connection.prepareStatement(metadataQuery)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            stmt.setString(3, columnName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    info.setColumnName(rs.getString("COLUMN_NAME"));
                    info.setTableName(rs.getString("TABLE_NAME"));
                    info.setSchemaName(rs.getString("TABLE_SCHEMA"));
                    info.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                    info.setDataType(rs.getString("DATA_TYPE"));

                    // SQL Server doesn't have COLUMN_TYPE like MySQL, so we construct it
                    String dataType = rs.getString("DATA_TYPE").toUpperCase();
                    Long charMaxLength = rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ? rs.getLong("CHARACTER_MAXIMUM_LENGTH") : null;
                    Integer numericPrecision = rs.getObject("NUMERIC_PRECISION") != null ? rs.getInt("NUMERIC_PRECISION") : null;
                    Integer numericScale = rs.getObject("NUMERIC_SCALE") != null ? rs.getInt("NUMERIC_SCALE") : null;

                    String columnType = dataType;
                    if (charMaxLength != null && charMaxLength > 0) {
                        columnType += "(" + (charMaxLength == -1 ? "MAX" : charMaxLength.toString()) + ")";
                    } else if (numericPrecision != null && numericScale != null) {
                        columnType += "(" + numericPrecision + "," + numericScale + ")";
                    } else if (numericPrecision != null) {
                        columnType += "(" + numericPrecision + ")";
                    }
                    info.setColumnType(columnType);

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

                    // SQL Server doesn't have COLUMN_KEY, we'll set based on constraints
                    String columnKey = "";
                    if (rs.getInt("IS_PRIMARY_KEY") == 1) {
                        columnKey = "PRI";
                    } else if (rs.getInt("IS_UNIQUE") == 1) {
                        columnKey = "UNI";
                    }
                    info.setColumnKey(columnKey);

                    info.setNullable(rs.getString("IS_NULLABLE").equals("YES"));
                    info.setColumnDefault(rs.getString("COLUMN_DEFAULT"));

                    // SQL Server doesn't have COLUMN_COMMENT in INFORMATION_SCHEMA
                    // We could retrieve it from extended properties if needed
                    info.setColumnComment(null);

                    info.setPrimaryKey(rs.getInt("IS_PRIMARY_KEY") == 1);
                    info.setUnique(rs.getInt("IS_UNIQUE") == 1);
                    info.setAutoIncrement(rs.getInt("IS_IDENTITY") == 1);
                }
            }
        }

        // 2. Get indexes that include this column
        String indexQuery = String.format("""
            SELECT 
                i.name AS INDEX_NAME,
                i.type_desc AS INDEX_TYPE,
                CASE WHEN i.is_unique = 0 THEN 1 ELSE 0 END AS NON_UNIQUE,
                ic.index_column_id AS SEQ_IN_INDEX,
                s.row_count AS CARDINALITY
            FROM %s.sys.indexes i
            INNER JOIN %s.sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
            INNER JOIN %s.sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
            INNER JOIN %s.sys.tables t ON i.object_id = t.object_id
            INNER JOIN %s.sys.schemas sch ON t.schema_id = sch.schema_id
            LEFT JOIN %s.sys.dm_db_partition_stats s ON i.object_id = s.object_id AND i.index_id = s.index_id
            WHERE sch.name = ?
              AND t.name = ?
              AND c.name = ?
            ORDER BY i.name, ic.index_column_id
            """, databaseName, databaseName, databaseName, databaseName, databaseName, databaseName);

        try (PreparedStatement stmt = connection.prepareStatement(indexQuery)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            stmt.setString(3, columnName);

            List<Map<String, String>> indexes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> index = new HashMap<>();
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
        String fkQuery = String.format("""
            SELECT 
                fk.name AS CONSTRAINT_NAME,
                SCHEMA_NAME(ref_t.schema_id) AS REFERENCED_TABLE_SCHEMA,
                ref_t.name AS REFERENCED_TABLE_NAME,
                ref_c.name AS REFERENCED_COLUMN_NAME,
                CASE fk.update_referential_action
                    WHEN 0 THEN 'NO ACTION'
                    WHEN 1 THEN 'CASCADE'
                    WHEN 2 THEN 'SET NULL'
                    WHEN 3 THEN 'SET DEFAULT'
                END AS UPDATE_RULE,
                CASE fk.delete_referential_action
                    WHEN 0 THEN 'NO ACTION'
                    WHEN 1 THEN 'CASCADE'
                    WHEN 2 THEN 'SET NULL'
                    WHEN 3 THEN 'SET DEFAULT'
                END AS DELETE_RULE
            FROM %s.sys.foreign_keys fk
            INNER JOIN %s.sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
            INNER JOIN %s.sys.tables t ON fk.parent_object_id = t.object_id
            INNER JOIN %s.sys.schemas sch ON t.schema_id = sch.schema_id
            INNER JOIN %s.sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id
            INNER JOIN %s.sys.tables ref_t ON fk.referenced_object_id = ref_t.object_id
            INNER JOIN %s.sys.columns ref_c ON fkc.referenced_object_id = ref_c.object_id AND fkc.referenced_column_id = ref_c.column_id
            WHERE sch.name = ?
              AND t.name = ?
              AND c.name = ?
            """, databaseName, databaseName, databaseName, databaseName, databaseName, databaseName, databaseName);

        try (PreparedStatement stmt = connection.prepareStatement(fkQuery)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            stmt.setString(3, columnName);

            List<Map<String, String>> foreignKeys = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> fk = new HashMap<>();
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
        String referencedByQuery = String.format("""
            SELECT 
                SCHEMA_NAME(t.schema_id) AS TABLE_SCHEMA,
                t.name AS TABLE_NAME,
                c.name AS COLUMN_NAME,
                fk.name AS CONSTRAINT_NAME,
                CASE fk.update_referential_action
                    WHEN 0 THEN 'NO ACTION'
                    WHEN 1 THEN 'CASCADE'
                    WHEN 2 THEN 'SET NULL'
                    WHEN 3 THEN 'SET DEFAULT'
                END AS UPDATE_RULE,
                CASE fk.delete_referential_action
                    WHEN 0 THEN 'NO ACTION'
                    WHEN 1 THEN 'CASCADE'
                    WHEN 2 THEN 'SET NULL'
                    WHEN 3 THEN 'SET DEFAULT'
                END AS DELETE_RULE
            FROM %s.sys.foreign_keys fk
            INNER JOIN %s.sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
            INNER JOIN %s.sys.tables t ON fk.parent_object_id = t.object_id
            INNER JOIN %s.sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id
            INNER JOIN %s.sys.tables ref_t ON fk.referenced_object_id = ref_t.object_id
            INNER JOIN %s.sys.schemas ref_sch ON ref_t.schema_id = ref_sch.schema_id
            INNER JOIN %s.sys.columns ref_c ON fkc.referenced_object_id = ref_c.object_id AND fkc.referenced_column_id = ref_c.column_id
            WHERE ref_sch.name = ?
              AND ref_t.name = ?
              AND ref_c.name = ?
            """, databaseName, databaseName, databaseName, databaseName, databaseName, databaseName, databaseName);

        try (PreparedStatement stmt = connection.prepareStatement(referencedByQuery)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            stmt.setString(3, columnName);

            List<Map<String, String>> referencedBy = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> ref = new HashMap<>();
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
        ArrayList<String> databases = new ArrayList<String>();
        try {
            String query = "SELECT name\n"
                    + "FROM sys.databases\n"
                    + "WHERE name NOT IN ('tempdb', 'model', 'msdb')\n"
                    + "ORDER BY \n"
                    + "    CASE WHEN name = ? THEN 0 ELSE 1 END,\n"
                    + "    name;";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, name);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                databases.add(result.getString("name"));
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
        String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];

        try {
            StringBuilder script = new StringBuilder();
            DatabaseMetaData metaData = connection.getMetaData();

            // Get primary keys
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet pkRs = metaData.getPrimaryKeys(dbName, schema, table)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // Get foreign keys
            Map<String, ForeignKeyData> foreignKeys = extractForeignKeys(dbName, tableName);

            script.append("CREATE TABLE ").append(dbName).append(".").append(schema).append(".").append(table).append(" (\n");

            // Get columns
            try (ResultSet columns = metaData.getColumns(dbName, schema, table, null)) {
                boolean first = true;
                while (columns.next()) {
                    if (!first) script.append(",\n");
                    first = false;

                    String columnName = columns.getString("COLUMN_NAME");
                    String dataType = columns.getString("TYPE_NAME");
                    int columnSize = columns.getInt("COLUMN_SIZE");
                    int decimalDigits = columns.getInt("DECIMAL_DIGITS");
                    String nullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls ? " NOT NULL" : " NULL";
                    String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                    String columnDef = columns.getString("COLUMN_DEF");

                    script.append("\t").append(columnName).append(" ");

                    // Handle data types with proper sizing
                    switch (dataType.toUpperCase()) {
                        case "VARCHAR", "CHARACTER VARYING" -> {
                            if (columnSize == Integer.MAX_VALUE || columnSize == 2147483647 || columnSize == -1) {
                                script.append("varchar(MAX)");
                            } else {
                                script.append("varchar(").append(columnSize).append(")");
                            }
                        }
                        case "NVARCHAR" -> {
                            if (columnSize == Integer.MAX_VALUE || columnSize == 1073741823 || columnSize == -1) {
                                script.append("nvarchar(MAX)");
                            } else {
                                script.append("nvarchar(").append(columnSize).append(")");
                            }
                        }
                        case "CHAR" -> script.append("char(").append(columnSize).append(")");
                        case "NCHAR" -> script.append("nchar(").append(columnSize).append(")");
                        case "BINARY" -> script.append("binary(").append(columnSize).append(")");
                        case "VARBINARY" -> {
                            if (columnSize == Integer.MAX_VALUE || columnSize == 2147483647 || columnSize == -1) {
                                script.append("varbinary(MAX)");
                            } else {
                                script.append("varbinary(").append(columnSize).append(")");
                            }
                        }
                        case "DECIMAL", "NUMERIC" ->
                                script.append("decimal(").append(columnSize).append(",").append(decimalDigits).append(")");
                        case "DATETIME" -> script.append("datetime");
                        case "DATETIME2" -> script.append("datetime2");
                        case "TIMESTAMP" -> script.append("timestamp");
                        case "BIT", "BOOLEAN", "BOOL" -> script.append("bit");
                        case "INT", "INTEGER" -> {
                            script.append("int");
                            // Only add IDENTITY if it's actually auto-increment
                            if ("YES".equalsIgnoreCase(isAutoIncrement) && primaryKeys.contains(columnName)) {
                                script.append(" IDENTITY(1,1)");
                            }
                        }
                        case "BIGINT" -> {
                            script.append("bigint");
                            if ("YES".equalsIgnoreCase(isAutoIncrement) && primaryKeys.contains(columnName)) {
                                script.append(" IDENTITY(1,1)");
                            }
                        }
                        case "SMALLINT" -> script.append("smallint");
                        case "TINYINT" -> script.append("tinyint");
                        case "FLOAT" -> script.append("float");
                        case "REAL" -> script.append("real");
                        case "MONEY" -> script.append("money");
                        case "SMALLMONEY" -> script.append("smallmoney");
                        case "DATE" -> script.append("date");
                        case "TIME" -> script.append("time");
                        case "DATETIMEOFFSET" -> script.append("datetimeoffset");
                        case "SMALLDATETIME" -> script.append("smalldatetime");
                        case "UNIQUEIDENTIFIER" -> script.append("uniqueidentifier");
                        case "XML" -> script.append("xml");
                        case "GEOGRAPHY" -> script.append("geography");
                        case "GEOMETRY" -> script.append("geometry");
                        case "HIERARCHYID" -> script.append("hierarchyid");
                        case "SQL_VARIANT" -> script.append("sql_variant");
                        default -> script.append(dataType.toLowerCase());
                    }

                    // Add default value if exists
                    if (columnDef != null && !columnDef.trim().isEmpty()) {
                        script.append(" DEFAULT ").append(columnDef.trim());
                    }

                    script.append(nullable);
                }

                // Add primary key constraint
                if (!primaryKeys.isEmpty()) {
                    script.append(",\n\tCONSTRAINT ");

                    // Try to get the actual constraint name
                    String pkConstraintName = null;
                    try (ResultSet pkRs = metaData.getPrimaryKeys(dbName, schema, table)) {
                        if (pkRs.next()) {
                            pkConstraintName = pkRs.getString("PK_NAME");
                        }
                    }

                    if (pkConstraintName != null && !pkConstraintName.isEmpty()) {
                        script.append(pkConstraintName);
                    } else {
                        script.append("PK_").append(table);
                    }

                    script.append(" PRIMARY KEY (");
                    script.append(String.join(", ", primaryKeys));
                    script.append(")");
                }

                // Add foreign key constraints
                for (Map.Entry<String, ForeignKeyData> entry : foreignKeys.entrySet()) {
                    ForeignKeyData fkData = entry.getValue();
                    script.append(",\n\tCONSTRAINT ").append(fkData.getConstraintName())
                            .append(" FOREIGN KEY (").append(fkData.getLocalColumn()).append(")")
                            .append(" REFERENCES ").append(fkData.getReferenceTable())
                            .append("(").append(fkData.getReferenceColumn()).append(")");

                    // Add ON DELETE action
                    String deleteRule = fkData.getOnDelete();
                    if (deleteRule != null && !deleteRule.isEmpty() && !deleteRule.equals("NO ACTION")) {
                        script.append(" ON DELETE ").append(deleteRule);
                    }

                    // Add ON UPDATE action
                    String updateRule = fkData.getOnUpdate();
                    if (updateRule != null && !updateRule.isEmpty() && !updateRule.equals("NO ACTION")) {
                        script.append(" ON UPDATE ").append(updateRule);
                    }
                }

                script.append("\n);");
            }

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

        // Parse schema and table from tableName
        String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];

        try (ResultSet fkRs = metaData.getImportedKeys(dbName, schema, table)) {
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
            // Parse schema and table name from tableName (format: "schema.table")
            String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
            String schema = parts[0];
            String table = parts[1];

            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = connection.getMetaData();

            // Use dbName as catalog, schema, and table name correctly
            ResultSet columns = metaData.getColumns(
                    dbName,      // catalog (database name)
                    schema,      // schema name
                    table,       // table name
                    null         // column name pattern
            );

            List<String> columnNames = new ArrayList<>();
            List<String> columnValues = new ArrayList<>();

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME").toUpperCase();
                int sqlType = columns.getInt("DATA_TYPE");
                boolean isNullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");

                // Skip auto-increment columns
                if (isAutoIncrement != null && isAutoIncrement.equals("YES")) {
                    continue;
                }

                columnNames.add("[" + columnName + "]");  // Use square brackets for SQL Server

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
                        case Types.DATE -> defaultValue = "GETDATE()";
                        case Types.TIME -> defaultValue = "GETDATE()";
                        case Types.TIMESTAMP -> defaultValue = "GETDATE()";
                        case Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY, Types.BINARY -> defaultValue = "0x";
                        case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                             Types.CLOB, Types.NCLOB -> defaultValue = "N''";
                        default -> defaultValue = isNullable ? "NULL" : "N''";
                    }
                }

                columnValues.add(defaultValue);
            }

            columns.close();

            if (columnNames.isEmpty()) {
                log.error("No columns found for table: " + tableName);
                return "-- ERROR No columns found for table: " + tableName;
            }

            // Format the INSERT statement nicely with proper SQL Server syntax
            insertScript.append("INSERT INTO [").append(schema).append("].[").append(table).append("]\n");
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
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        // Parse schema and table from tableName
        String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];

        // Build the fully qualified table name
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "[" + databaseName + "].[" + schema + "].[" + table + "]"
                : "[" + schema + "].[" + table + "]";

        // For OBJECT_ID, we need the same format
        String objectIdName = (databaseName != null && !databaseName.isEmpty())
                ? databaseName + "." + schema + "." + table
                : schema + "." + table;

        String sql = "IF OBJECT_ID('" + objectIdName + "', 'U') IS NOT NULL DROP TABLE " + fullTableName;
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'" + databaseName.replace("'", "''") + "') " +
                "CREATE DATABASE " + quoteIdentifier(databaseName);

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
    public double getTableSize(String database, String tableName) throws SQLException {

        String[] parts = SqlServerDialect.parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];
        
        String query = """
        SELECT
            ROUND(
                SUM(a.total_pages) * 8.0 / 1024.0,
                2
            ) AS total_size_mb
        FROM
            sys.tables t
        INNER JOIN
            sys.indexes i ON t.object_id = i.object_id
        INNER JOIN
            sys.partitions p ON i.object_id = p.object_id AND i.index_id = p.index_id
        INNER JOIN
            sys.allocation_units a ON p.partition_id = a.container_id
        WHERE
            t.name = ?
        AND SCHEMA_NAME(t.schema_id) = ?
        GROUP BY
            t.name, SCHEMA_NAME(t.schema_id)
        """;

        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, table);
        pstmt.setString(2, schema);

        ResultSet rs = pstmt.executeQuery();
        double sizeMB = rs.next() ? rs.getDouble("total_size_mb") : -1;

        rs.close();
        pstmt.close();

        return sizeMB;
    }
}
