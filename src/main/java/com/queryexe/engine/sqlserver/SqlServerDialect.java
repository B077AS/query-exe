package com.queryexe.engine.sqlserver;


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
import com.queryexe.query.RowTarget;
import com.queryexe.schema.DetailedColumnData;
import com.queryexe.schema.ColumnData;
import com.queryexe.schema.PartitionData;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.connection.driver.DriverInfo;
import com.queryexe.schema.ForeignKeyData;



import com.queryexe.engine.*;

@Slf4j
public class SqlServerDialect extends Dialect {
    public static String[] parseSchemaAndTable(String fullTableName) {
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


    private String[] dataTypes = new String[]{
            "INT", "TINYINT", "SMALLINT", "BIGINT", "DECIMAL(18, 0)", "FLOAT", "MONEY",
            "SMALLMONEY", "CHAR(10)", "VARCHAR(50)", "NCHAR(10)", "NVARCHAR(50)", "TEXT", "NTEXT",
            "DATE", "TIME(7)", "DATETIME", "DATETIME2(7)", "SMALLDATETIME", "DATETIMEOFFSET(7)",
            "BINARY(50)", "VARBINARY(50)", "IMAGE", "BIT", "GEOGRAPHY", "GEOMETRY", "HIERARCHYID",
            "NUMERIC(18, 0)", "NVARCHAR(MAX)", "REAL", "SQL_VARIANT",
            "TIMESTAMP", "UNIQUEIDENTIFIER", "VARBINARY(MAX)", "VARCHAR(MAX)", "XML"
    };

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    @Override
    public SqlSyntax getSqlSyntax() {
        return SqlSyntax.SQLSERVER;
    }

    @Override
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        String[] parts = parseSchemaAndTable(tableName);
        String schema = parts[0];
        String table = parts[1];
        return "SELECT TOP " + limit + " * FROM [" + schema + "].[" + table + "];";
    }

    @Override
    public String generateRowInsertScript(List<String> row, RowTarget table) {
        try {
            String tableName = table.tableName();

            String[] parts = parseSchemaAndTable(tableName);
            String schema = parts[0];
            String tableOnly = parts[1];

            StringBuilder insertScript = new StringBuilder();

            int columnCount = table.columnNames().size();

            List<String> columnNames = new ArrayList<>();
            List<Integer> columnTypes = new ArrayList<>();

            // the grid already knows each column's real name and SQL type; looking them up in the metadata by
            // table name alone would ignore the schema and treat '_' as a wildcard
            for (int i = 0; i < columnCount; i++) {
                columnNames.add(quoteIdentifier(table.columnNames().get(i)));
                columnTypes.add(table.columnSqlType(i));
            }

            insertScript.append("INSERT INTO [").append(schema).append("].[").append(tableOnly).append("]")
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
            log.error("generateRowInsertScript failed", e);
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
