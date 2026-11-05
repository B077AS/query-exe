package com.queryexe.engine;

import com.queryexe.engine.SqlSyntax;
import com.queryexe.schema.ColumnData;
import com.queryexe.schema.ForeignKeyData;
import com.queryexe.query.RowTarget;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Everything one engine says in SQL that needs no connection: how names are quoted, how a table is created or altered,
 * which data types exist. Stateless, so one instance per engine is shared. Reading a database's structure is the
 * {@link SchemaReader}'s job; a dialect never touches JDBC.
 */
public abstract class Dialect {

    /** The data types offered in the table editor. */
    public abstract String[] getDataTypes();

    /** How scripts for this engine are split into statements. */
    public abstract SqlSyntax getSqlSyntax();

    public abstract String generateSelectScript(String tableName, String databaseName, int limit);

    /** One row of a result as an INSERT statement. */
    public abstract String generateRowInsertScript(List<String> row, RowTarget table);

    public abstract String generateCreateTableSQL(String databaseName, String tableName, List<ColumnData> columns, List<ForeignKeyData> foreignKeys);

    public abstract String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                                 List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                                 Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys,
                                                 Map<String, String> columnRenames) throws SQLException;

    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public String qualifyTable(String catalog, String schema, String table) {
        if (schema != null && !schema.isEmpty()) {
            return quoteIdentifier(schema) + "." + quoteIdentifier(table);
        }
        return quoteIdentifier(table);
    }

    public String generateDefaultsOnlyInsert(String qualifiedTable) {
        return "INSERT INTO " + qualifiedTable + " DEFAULT VALUES";
    }
}
