package com.queryexe.model.connections;

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

public class SQLServerConnection extends ConnectionObject {

    private String[] KEYWORDS = new String[]{
            // Basic SQL Keywords
            "SELECT", "FROM", "WHERE", "AND", "OR", "INSERT", "INTO", "VALUES",
            "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "INDEX",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "AS", "USE",

            // SQL Server Specific
            "TOP", "OUTPUT", "MERGE", "IDENTITY", "TRIGGER", "PROCEDURE", "EXEC",
            "DECLARE", "CURSOR", "FETCH", "BROWSE", "HOLDLOCK", "NOLOCK",
            "READCOMMITTED", "READUNCOMMITTED", "REPEATABLEREAD", "SERIALIZABLE",

            // Control Flow
            "BEGIN", "END", "IF", "ELSE", "WHILE", "BREAK", "CONTINUE",
            "CASE", "WHEN", "THEN", "TRY", "CATCH", "THROW",

            // Joins and Set Operations
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "APPLY",
            "OUTER", "UNION", "EXCEPT", "INTERSECT", "ALL",

            // Functions
            "ISNULL", "COALESCE", "NULLIF", "CAST", "CONVERT",
            "COUNT", "SUM", "AVG", "MAX", "MIN",
            "CONCAT", "SUBSTRING", "LEN", "LOWER", "UPPER", "RTRIM", "LTRIM",
            "DATEADD", "DATEDIFF", "DATEFROMPARTS", "DATENAME", "DATEPART",

            // Window Functions
            "OVER", "PARTITION", "BY", "ORDER", "ASC", "DESC",
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE",

            // Temporal
            "GETDATE", "GETUTCDATE", "SYSDATETIME", "SYSUTCDATETIME",
            "CURRENT_TIMESTAMP", "DATETIME2", "DATETIMEOFFSET"
    };

    private String[] dataTypes = new String[]{
            "INT", "TINYINT", "SMALLINT", "BIGINT", "DECIMAL(18, 0)", "FLOAT", "MONEY",
            "SMALLMONEY", "CHAR(10)", "VARCHAR(50)", "NCHAR(10)", "NVARCHAR(50)", "TEXT", "NTEXT",
            "DATE", "TIME(7)", "DATETIME", "DATETIME2(7)", "SMALLDATETIME", "DATETIMEOFFSET(7)",
            "BINARY(50)", "VARBINARY(50)", "IMAGE", "BIT", "GEOGRAPHY", "GEOMETRY", "HIERARCHYID",
            "NUMERIC(18, 0)", "NVARCHAR(MAX)", "REAL", "SQL_VARIANT",
            "TIMESTAMP", "UNIQUEIDENTIFIER", "VARBINARY(MAX)", "VARCHAR(MAX)", "XML"
    };

    public SQLServerConnection(String id, String connectionName, String dbType, String baseUrl, String host, String port, String databaseName, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, baseUrl, host, port, databaseName, username, password, driverInfo);
    }

    public SQLServerConnection(String id, String connectionName, String dbType, String url, String username, String password, DriverInfo driverInfo) {
        super(id, connectionName, dbType, url, username, password, driverInfo);
    }

    private String[] parseSchemaAndTable(String fullTableName) {
        if (fullTableName == null || fullTableName.isEmpty()) {
            return new String[]{"dbo", ""};
        }
        
        String[] parts = fullTableName.split("\\.", 2);
        if (parts.length > 1) {
            return new String[]{parts[0], parts[1]};
        } else {
            return new String[]{"dbo", parts[0]};
        }
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
            e.printStackTrace();
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

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query)) {
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
        String[] parts = parseSchemaAndTable(tableName);
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

        PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
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
    public DetailedColumnData getDetailedColumnInfo(String databaseName, String tableName, String columnName) throws SQLException {
        DetailedColumnData info = new DetailedColumnData();

        // Parse schema and table from tableName (format: "schema.table")
        String[] parts = parseSchemaAndTable(tableName);
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

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(metadataQuery)) {
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

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(indexQuery)) {
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

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(fkQuery)) {
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

        try (PreparedStatement stmt = DatabaseConnection.getInstance().getConnection().prepareStatement(referencedByQuery)) {
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
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            statement.setString(1, name);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                databases.add(result.getString("name"));
            }

            statement.close();
            result.close();
            return databases;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return databases;
    }

    @Override
    public String generateCreateScript(String tableName, String dbName) {
        String[] parts = parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];

        try {
            StringBuilder script = new StringBuilder();
            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

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
            e.printStackTrace();
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public Map<String, ForeignKeyData> extractForeignKeys(String dbName, String tableName) throws SQLException {
        Map<String, ForeignKeyData> foreignKeys = new HashMap<>();
        DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

        // Parse schema and table from tableName
        String[] parts = parseSchemaAndTable(tableName);
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
            String[] parts = parseSchemaAndTable(tableName);
            String schema = parts[0];
            String table = parts[1];

            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();

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
                System.err.println("No columns found for table: " + tableName);
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
            e.printStackTrace();
            return "-- ERROR: " + e.getMessage();
        }
    }

    @Override
    public String generateRowInsertScript(ObservableList<String> row, TableCell<TableRowData, String> cell) {
        try {
            ResultTable table = (ResultTable) cell.getTableView();
            String tableName = table.getTableName();
            
            // Parse schema and table from tableName
            String[] parts = parseSchemaAndTable(tableName);
            String schema = parts[0];
            String tableOnly = parts[1];
            
            StringBuilder insertScript = new StringBuilder();

            DatabaseMetaData metaData = DatabaseConnection.getInstance().getConnection().getMetaData();
            ResultSet columns = metaData.getColumns(
                    DatabaseConnection.getInstance().getConnection().getCatalog(),
                    schema,
                    tableOnly,
                    null
            );

            List<String> columnNames = new ArrayList<>();
            List<Integer> columnTypes = new ArrayList<>();

            while (columns.next()) {
                columnNames.add("[" + columns.getString("COLUMN_NAME") + "]");
                columnTypes.add(columns.getInt("DATA_TYPE"));
            }

            insertScript.append("INSERT INTO [").append(schema).append("].[").append(tableOnly).append("]")
                    .append(" (")
                    .append(String.join(", ", columnNames))
                    .append(")\nVALUES (");

            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) {
                    insertScript.append(", ");
                }

                String value = row.get(i);

                if (value == null || value.equals("NULL")) {
                    insertScript.append("NULL");
                } else {
                    switch (columnTypes.get(i)) {
                        case Types.BOOLEAN -> {
                            insertScript.append(Boolean.parseBoolean(value) ? "1" : "0");
                        }
                        case Types.TIMESTAMP -> {
                            insertScript.append("CONVERT(DATETIME2, '").append(value).append("')");
                        }
                        case Types.DATE -> {
                            insertScript.append("CONVERT(DATE, '").append(value).append("')");
                        }
                        case Types.TIME -> {
                            insertScript.append("CONVERT(TIME, '").append(value).append("')");
                        }
                        case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT,
                             Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                            insertScript.append(value.isEmpty() ? "NULL" : value);
                        }
                        default -> {
                            insertScript.append("N'").append(value.replace("'", "''")).append("'");
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
    public String generateCreateTableSQL(String databaseName, String tableName, List<ColumnData> columns, List<ForeignKeyData> foreignKeys) {
        StringBuilder sql = new StringBuilder();

        // Parse schema and table from tableName if it contains a dot
        String[] parts = parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];

        // Build the fully qualified table name
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "[" + databaseName + "].[" + schema + "].[" + table + "]"
                : "[" + schema + "].[" + table + "]";

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        // Add columns
        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);

            sql.append("    [").append(col.getColumnName()).append("] ").append(col.getDataType());

            // Add IDENTITY if specified
            if (col.isAutoIncrement()) {
                sql.append(" IDENTITY(1,1)");
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
            sql.append("    CONSTRAINT [PK_").append(table).append("] PRIMARY KEY (");
            for (int i = 0; i < primaryKeys.size(); i++) {
                sql.append("[").append(primaryKeys.get(i)).append("]");
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

            // Parse reference table schema
            String[] refParts = parseSchemaAndTable(fk.getReferenceTable());
            String refSchema = refParts[0];
            String refTable = refParts[1];

            sql.append("    CONSTRAINT [").append(fk.getConstraintName()).append("] ")
                    .append("FOREIGN KEY ([").append(fk.getLocalColumn()).append("]) ")
                    .append("REFERENCES [").append(refSchema).append("].[").append(refTable).append("] ")
                    .append("([").append(fk.getReferenceColumn()).append("])");

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
    public ArrayList<String> getUsers() throws SQLException {
        ArrayList<String> users = new ArrayList<>();

        try {
            // Get all server logins and database users without name filtering
            String query = "SELECT DISTINCT name " +
                    "FROM sys.server_principals " +
                    "WHERE type IN ('S', 'U', 'G', 'R', 'C', 'K', 'E', 'X') " +
                    "UNION " +
                    "SELECT DISTINCT name " +
                    "FROM sys.database_principals " +
                    "WHERE type IN ('S', 'U', 'G', 'R', 'A', 'C', 'K', 'E', 'X') " +
                    "ORDER BY name";

            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String user = result.getString("name");
                users.add(user);
            }

            statement.close();
            result.close();

        } catch (SQLException e) {
            // If we don't have permissions, return the current user
            String fallbackQuery = "SELECT SYSTEM_USER as name " +
                    "UNION " +
                    "SELECT USER_NAME() as name";
            PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(fallbackQuery);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String user = result.getString("name");
                if (user != null && !user.isEmpty()) {
                    users.add(user);
                }
            }

            statement.close();
            result.close();
        }

        return users;
    }

    @Override
    public void deleteTable(String databaseName, String tableName) throws SQLException {
        // Parse schema and table from tableName
        String[] parts = parseSchemaAndTable(tableName);
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
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void createDatabase(String databaseName) throws SQLException {
        String sql = "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'" + databaseName + "') " +
                "CREATE DATABASE [" + databaseName + "]";

        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void deleteDatabase(String databaseName) throws SQLException {
        String sql = "DROP DATABASE IF EXISTS [" + databaseName + "]";

        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public void useDatabase(String databaseName) throws SQLException {
        String sql = "USE [" + databaseName + "]";
        PreparedStatement statement = DatabaseConnection.getInstance().getConnection().prepareStatement(sql);
        statement.execute();
        statement.close();
    }
    
    @Override
    public double getTableSize(String database, String tableName) throws SQLException {

        String[] parts = parseSchemaAndTable(tableName);
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

        PreparedStatement pstmt = DatabaseConnection.getInstance().getConnection().prepareStatement(query);
        pstmt.setString(1, table);
        pstmt.setString(2, schema);

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

    @Override
    public String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                        List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                        Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys,
                                        Map<String, String> columnRenames) throws SQLException {
        return null;
    }
}