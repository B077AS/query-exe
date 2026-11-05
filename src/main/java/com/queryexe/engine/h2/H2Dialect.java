package com.queryexe.engine.h2;


import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import com.queryexe.query.RowTarget;
import com.queryexe.schema.DetailedColumnData;
import com.queryexe.schema.ColumnData;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.connection.driver.DriverInfo;
import com.queryexe.engine.h2.H2Catalog;
import com.queryexe.engine.h2.H2Catalog.ColumnInfo;
import com.queryexe.engine.h2.H2Catalog.ForeignKeyInfo;
import com.queryexe.engine.h2.H2Catalog.KeyInfo;
import com.queryexe.schema.ForeignKeyData;


import com.queryexe.engine.*;

@Slf4j
public class H2Dialect extends Dialect {


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

    /**
     * {@code FOREIGN KEY (...) REFERENCES schema.table (...) [ON DELETE ..] [ON UPDATE ..]}, over every column
     * of the key. A referenced table without a known schema is taken to live in {@code defaultSchema}.
     */
    private static String foreignKeyClause(ForeignKeyData fk, String defaultSchema) {
        String referenceSchema = fk.getReferenceSchema() != null ? fk.getReferenceSchema() : defaultSchema;
        String referenced = referenceSchema != null && !referenceSchema.isBlank()
                ? H2Catalog.qualified(referenceSchema, fk.getReferenceTable())
                : H2Catalog.quote(fk.getReferenceTable());

        StringBuilder clause = new StringBuilder("FOREIGN KEY (")
                .append(String.join(", ", fk.allLocalColumns().stream().map(H2Catalog::quote).toList()))
                .append(") REFERENCES ").append(referenced).append(" (")
                .append(String.join(", ", fk.allReferenceColumns().stream().map(H2Catalog::quote).toList()))
                .append(")");
        if (fk.getOnDelete() != null && !fk.getOnDelete().isEmpty()) {
            clause.append(" ON DELETE ").append(fk.getOnDelete());
        }
        if (fk.getOnUpdate() != null && !fk.getOnUpdate().isEmpty()) {
            clause.append(" ON UPDATE ").append(fk.getOnUpdate());
        }
        return clause.toString();
    }

    // The table editor has no field for a default value, so a column keeps the default it already has
    // (ALTER COLUMN with a full definition would otherwise reset it).
    private static String defaultClause(ColumnData oldColumn, ColumnData newColumn) {
        if (newColumn.isAutoIncrement()) {
            return "";
        }
        String defaultValue = newColumn.getDefaultValue() != null
                ? newColumn.getDefaultValue()
                : (oldColumn != null ? oldColumn.getDefaultValue() : null);
        return defaultValue == null ? "" : " DEFAULT " + defaultValue;
    }

    @Override
    public SqlSyntax getSqlSyntax() {
        return SqlSyntax.H2;
    }

    @Override
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        String target = databaseName != null && !databaseName.isBlank()
                ? H2Catalog.qualified(databaseName, tableName)
                : H2Catalog.quote(tableName);
        return "SELECT * FROM " + target + " LIMIT " + limit + ";";
    }

    @Override
    public String generateRowInsertScript(List<String> row, RowTarget table) {
        try {
            String tableName = table.tableName();
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
            log.error("generateRowInsertScript failed", e);
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

            if (!col.isAutoIncrement() && col.getDefaultValue() != null) {
                sql.append(" DEFAULT ").append(col.getDefaultValue());
            }

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

            sql.append("    CONSTRAINT ").append(H2Catalog.quote(fk.getConstraintName())).append(" ")
                    .append(foreignKeyClause(fk, schemaName));

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
        // Note: H2 only allows a single "DROP COLUMN" keyword per ALTER TABLE clause,
        // followed by a comma-separated list of column names (unlike MySQL/Postgres,
        // which repeat "DROP COLUMN" for each column).
        List<String> columnNamesToDrop = new ArrayList<>();
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
                columnNamesToDrop.add(oldColName);
            }
        }
        if (!columnNamesToDrop.isEmpty()) {
            StringBuilder dropClause = new StringBuilder("DROP COLUMN ");
            for (int i = 0; i < columnNamesToDrop.size(); i++) {
                if (i > 0) dropClause.append(", ");
                dropClause.append("\"").append(columnNamesToDrop.get(i)).append("\"");
            }
            columnsToDropNow.add(dropClause.toString());
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
                            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" %s%s;\n",
                                    databaseName, oldTableName, newColName, newCol.getDataType(), defaultClause(oldCol, newCol)));
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
                            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" %s%s%s%s;\n",
                                    databaseName, oldTableName, newColName,
                                    newCol.getDataType(),
                                    defaultClause(oldCol, newCol),
                                    newCol.isNotNull() ? " NOT NULL" : "",
                                    newCol.isAutoIncrement() ? " GENERATED BY DEFAULT AS IDENTITY" : ""));
                        }
                    } else {
                        // For non-renamed columns, use single ALTER statement
                        columnsToModify.add(String.format("ALTER COLUMN \"%s\" %s%s%s%s",
                                newColName,
                                newCol.getDataType(),
                                defaultClause(oldCol, newCol),
                                newCol.isNotNull() ? " NOT NULL" : " NULL",
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
            sql.append(String.format("ALTER TABLE \"%s\".\"%s\" ADD CONSTRAINT %s %s",
                    databaseName, oldTableName, H2Catalog.quote(fk.getConstraintName()), foreignKeyClause(fk, databaseName)));
            sql.append(";\n");
        }

        return sql.toString();
    }

    @Override
    public String[] getDataTypes() {
        return this.dataTypes;
    }
}
