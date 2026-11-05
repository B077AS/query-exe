package com.queryexe.engine.sqlite;


import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import com.queryexe.engine.sqlite.SqliteCatalog;
import com.queryexe.engine.sqlite.SqliteCatalog.ColumnInfo;
import com.queryexe.engine.sqlite.SqliteCatalog.ForeignKeyInfo;
import com.queryexe.engine.sqlite.SqliteCatalog.IndexInfo;
import com.queryexe.query.RowTarget;
import com.queryexe.schema.DetailedColumnData;
import com.queryexe.schema.ColumnData;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.connection.driver.DriverInfo;
import com.queryexe.schema.ForeignKeyData;


import com.queryexe.engine.*;

@Slf4j
public class SqliteDialect extends Dialect {


    private String[] dataTypes = new String[]{
            // SQLite Storage Classes (Affinity Types)
            "NULL", "INTEGER", "REAL", "TEXT", "BLOB",

            // Common SQL types that map to SQLite affinities
            "INT", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT", "UNSIGNED BIG INT",
            "INT2", "INT8", "BOOLEAN", "NUMERIC", "DECIMAL", "DOUBLE", "DOUBLE PRECISION",
            "FLOAT", "CHARACTER", "VARCHAR", "VARYING CHARACTER", "NCHAR",
            "NATIVE CHARACTER", "NVARCHAR", "CLOB", "DATE", "DATETIME", "TIMESTAMP"
    };

    @Override
    public SqlSyntax getSqlSyntax() {
        return SqlSyntax.SQLITE;
    }

    @Override
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        String target = databaseName != null && !databaseName.isBlank()
                ? SqliteCatalog.qualified(databaseName, tableName)
                : SqliteCatalog.quote(tableName);
        return "SELECT * FROM " + target + " LIMIT " + limit + ";";
    }

    @Override
    public String generateRowInsertScript(List<String> row, RowTarget table) {
        try {
            String tableName = table.tableName();
            StringBuilder insertScript = new StringBuilder();

            int columnCount = table.columnNames().size();

            List<String> columnNames = new ArrayList<>();
            List<String> columnTypes = new ArrayList<>();

            // the grid already knows each column's real name and type; SQLite's own type affinity names are derived from it
            for (int i = 0; i < columnCount; i++) {
                columnNames.add(quoteIdentifier(table.columnNames().get(i)));
                columnTypes.add(switch (table.columnSqlType(i)) {
                    case java.sql.Types.INTEGER, java.sql.Types.BIGINT, java.sql.Types.SMALLINT, java.sql.Types.TINYINT, java.sql.Types.BIT, java.sql.Types.BOOLEAN -> "INT";
                    case java.sql.Types.REAL, java.sql.Types.FLOAT, java.sql.Types.DOUBLE -> "REAL";
                    case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> "NUMERIC";
                    default -> "TEXT";
                });
            }

            insertScript.append("INSERT INTO ").append(table.qualifiedTableName())
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
        String target = databaseName != null && !databaseName.isEmpty()
                ? SqliteCatalog.qualified(databaseName, tableName)
                : SqliteCatalog.quote(tableName);

        List<String> primaryKeys = columns.stream().filter(ColumnData::isPrimaryKey).map(ColumnData::getColumnName).toList();
        // AUTOINCREMENT only exists as part of "INTEGER PRIMARY KEY AUTOINCREMENT" on a single column
        ColumnData autoIncrementKey = primaryKeys.size() == 1
                ? columns.stream().filter(c -> c.isPrimaryKey() && c.isAutoIncrement()).findFirst().orElse(null)
                : null;

        List<String> definitions = new ArrayList<>();
        for (ColumnData column : columns) {
            StringBuilder definition = new StringBuilder("    ").append(SqliteCatalog.quote(column.getColumnName()));
            if (column == autoIncrementKey) {
                definition.append(" INTEGER PRIMARY KEY AUTOINCREMENT");
            } else {
                if (column.getDataType() != null && !column.getDataType().isEmpty()) {
                    definition.append(' ').append(column.getDataType());
                }
                if (column.isNotNull() || column.isPrimaryKey()) {
                    definition.append(" NOT NULL");
                }
                if (column.isUnique() && !column.isPrimaryKey()) {
                    definition.append(" UNIQUE");
                }
            }
            definitions.add(definition.toString());
        }

        if (!primaryKeys.isEmpty() && autoIncrementKey == null) {
            definitions.add("    PRIMARY KEY (" + quoteAll(primaryKeys) + ")");
        }

        for (ForeignKeyData fk : foreignKeys) {
            StringBuilder definition = new StringBuilder("    CONSTRAINT ").append(SqliteCatalog.quote(fk.getConstraintName()))
                    .append(" FOREIGN KEY (").append(quoteAll(fk.allLocalColumns())).append(") REFERENCES ")
                    .append(SqliteCatalog.quote(fk.getReferenceTable())).append(" (").append(quoteAll(fk.allReferenceColumns())).append(")");
            if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
                definition.append(" ON DELETE ").append(fk.getOnDelete());
            }
            if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
                definition.append(" ON UPDATE ").append(fk.getOnUpdate());
            }
            definitions.add(definition.toString());
        }

        return "CREATE TABLE " + target + " (\n" + String.join(",\n", definitions) + "\n);";
    }

    private static String quoteAll(List<String> identifiers) {
        StringJoiner joined = new StringJoiner(", ");
        identifiers.forEach(identifier -> joined.add(SqliteCatalog.quote(identifier)));
        return joined.toString();
    }

    @Override
    public String[] getDataTypes() {
        return this.dataTypes;
    }

    /**
     * SQLite can rename tables/columns and add or drop plain columns in place. Anything else (changing a
     * column's type, NOT NULL, key or UNIQUE setting, or the foreign keys) needs the whole table rebuilt, which
     * would silently lose defaults and CHECK constraints this editor does not know about, so it is refused.
     */
    @Override
    public String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                        List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                        Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys,
                                        Map<String, String> columnRenames) throws SQLException {
        if (!sameForeignKeys(oldForeignKeys, newForeignKeys)) {
            throw unsupported("changing foreign keys");
        }

        String table = SqliteCatalog.qualified(SqliteCatalog.schemaOrMain(databaseName), oldTableName);

        Map<String, String> newToOld = new HashMap<>();
        columnRenames.forEach((oldName, newName) -> newToOld.put(newName, oldName));

        List<String> renames = new ArrayList<>();
        List<String> drops = new ArrayList<>();
        List<String> adds = new ArrayList<>();
        Set<String> keptOldColumns = new HashSet<>();

        for (ColumnData newColumn : newColumns) {
            String oldName = newToOld.getOrDefault(newColumn.getColumnName(), newColumn.getColumnName());
            ColumnData oldColumn = oldColumns.stream().filter(c -> c.getColumnName().equals(oldName)).findFirst().orElse(null);

            if (oldColumn == null) {
                if (newColumn.isPrimaryKey() || newColumn.isUnique() || newColumn.isAutoIncrement()) {
                    throw unsupported("adding a primary key, unique or auto-increment column (" + newColumn.getColumnName() + ")");
                }
                if (newColumn.isNotNull()) {
                    throw unsupported("adding the NOT NULL column " + newColumn.getColumnName() + " (it would need a default value)");
                }
                adds.add("ALTER TABLE " + table + " ADD COLUMN " + SqliteCatalog.quote(newColumn.getColumnName())
                        + (newColumn.getDataType() == null || newColumn.getDataType().isEmpty() ? "" : " " + newColumn.getDataType()));
                continue;
            }

            keptOldColumns.add(oldName);
            boolean changed = !Objects.equals(oldColumn.getDataType() == null ? "" : oldColumn.getDataType().toUpperCase(),
                    newColumn.getDataType() == null ? "" : newColumn.getDataType().toUpperCase())
                    || oldColumn.isNotNull() != newColumn.isNotNull()
                    || oldColumn.isPrimaryKey() != newColumn.isPrimaryKey()
                    || oldColumn.isUnique() != newColumn.isUnique()
                    || oldColumn.isAutoIncrement() != newColumn.isAutoIncrement();
            if (changed) {
                throw unsupported("changing the type, NOT NULL, key or unique setting of column " + newColumn.getColumnName());
            }
            if (!oldName.equals(newColumn.getColumnName())) {
                renames.add("ALTER TABLE " + table + " RENAME COLUMN " + SqliteCatalog.quote(oldName)
                        + " TO " + SqliteCatalog.quote(newColumn.getColumnName()));
            }
        }

        for (ColumnData oldColumn : oldColumns) {
            if (!keptOldColumns.contains(oldColumn.getColumnName())) {
                drops.add("ALTER TABLE " + table + " DROP COLUMN " + SqliteCatalog.quote(oldColumn.getColumnName()));
            }
        }

        List<String> statements = new ArrayList<>();
        statements.addAll(renames);
        statements.addAll(drops);
        statements.addAll(adds);
        if (!oldTableName.equals(newTableName)) {
            statements.add("ALTER TABLE " + table + " RENAME TO " + SqliteCatalog.quote(newTableName));
        }
        return statements.isEmpty() ? "" : String.join(";\n", statements) + ";";
    }

    private static boolean sameForeignKeys(Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys) {
        if (oldForeignKeys.size() != newForeignKeys.size()) {
            return false;
        }
        for (ForeignKeyData newKey : newForeignKeys) {
            ForeignKeyData oldKey = oldForeignKeys.get(newKey.getConstraintName());
            if (oldKey == null
                    || !oldKey.allLocalColumns().equals(newKey.allLocalColumns())
                    || !oldKey.getReferenceTable().equals(newKey.getReferenceTable())
                    || !oldKey.allReferenceColumns().equals(newKey.allReferenceColumns())
                    || !Objects.equals(oldKey.getOnDelete(), newKey.getOnDelete())
                    || !Objects.equals(oldKey.getOnUpdate(), newKey.getOnUpdate())) {
                return false;
            }
        }
        return true;
    }

    private static SQLFeatureNotSupportedException unsupported(String what) {
        return new SQLFeatureNotSupportedException("SQLite cannot do this in place: " + what
                + ". It would need the table to be rebuilt; do that with an explicit script instead.");
    }
}
