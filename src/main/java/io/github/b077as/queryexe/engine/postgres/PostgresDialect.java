package io.github.b077as.queryexe.engine.postgres;


import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

import io.github.b077as.queryexe.query.RowTarget;
import io.github.b077as.queryexe.schema.DetailedColumnData;
import io.github.b077as.queryexe.schema.ColumnData;
import io.github.b077as.queryexe.schema.PartitionData;
import io.github.b077as.queryexe.connection.DatabaseConnection;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import io.github.b077as.queryexe.schema.ForeignKeyData;


import io.github.b077as.queryexe.engine.*;

@Slf4j
public class PostgresDialect extends Dialect {


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

    @Override
    public SqlSyntax getSqlSyntax() {
        return SqlSyntax.POSTGRES;
    }

    @Override
    public String generateSelectScript(String tableName, String databaseName, int limit) {
        String target = databaseName != null && !databaseName.isBlank()
                ? quoteIdentifier(databaseName) + "." + quoteIdentifier(tableName)
                : quoteIdentifier(tableName);
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
    public String[] getDataTypes() {
        return this.dataTypes;
    }
}
