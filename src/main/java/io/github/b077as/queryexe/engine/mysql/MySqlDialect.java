package io.github.b077as.queryexe.engine.mysql;


import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
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
public class MySqlDialect extends Dialect {


    private String[] dataTypes = new String[]{
            "INT", "VARCHAR(255)", "DECIMAL", "BINARY(8)", "BLOB", "LONGBLOB",
            "MEDIUMBLOB", "TINYBLOB", "VARBINARY(255)", "DATE", "DATETIME", "TIME",
            "TIMESTAMP", "YEAR", "GEOMETRY", "GEOMETRYCOLLECTION", "LINESTRING",
            "MULTILINESTRING", "MULTIPOINT", "MULTIPOLYGON", "POINT", "POLYGON",
            "BIGINT", "DOUBLE", "FLOAT", "MEDIUMINT", "REAL",
            "SMALLINT", "TINYINT", "CHAR", "JSON", "NCHAR", "NVARCHAR(255)",
            "VARCHAR", "LONGTEXT", "MEDIUMTEXT", "TEXT", "TINYTEXT", "BIT",
            "BOOLEAN", "ENUM()", "SET()"
    };

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    // MySQL/MariaDB "databases" are catalogs; ResultSetMetaData reports them as the catalog name.
    @Override
    public String qualifyTable(String catalog, String schema, String table) {
        if (catalog != null && !catalog.isEmpty()) {
            return quoteIdentifier(catalog) + "." + quoteIdentifier(table);
        }
        return quoteIdentifier(table);
    }

    @Override
    public String generateDefaultsOnlyInsert(String qualifiedTable) {
        return "INSERT INTO " + qualifiedTable + " () VALUES ()";
    }

    @Override
    public SqlSyntax getSqlSyntax() {
        return SqlSyntax.MYSQL;
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
                        case Types.BOOLEAN, Types.BIT -> {
                            insertScript.append(Boolean.parseBoolean(value) ? "1" : "0");
                        }
                        case Types.TIMESTAMP -> {
                            insertScript.append("STR_TO_DATE('").append(value).append("', '%Y-%m-%d %H:%i:%s')");
                        }
                        case Types.DATE -> {
                            insertScript.append("STR_TO_DATE('").append(value).append("', '%Y-%m-%d')");
                        }
                        case Types.TIME -> {
                            insertScript.append("STR_TO_DATE('").append(value).append("', '%H:%i:%s')");
                        }
                        case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT,
                             Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                            insertScript.append(value.isEmpty() ? "NULL" : value);
                        }
                        default -> {
                            insertScript.append("'").append(value.replace("'", "\\'")).append("'");
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

        // Build the fully qualified table name
        String fullTableName = (databaseName != null && !databaseName.isEmpty())
                ? "`" + databaseName + "`.`" + tableName + "`"
                : "`" + tableName + "`";

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        // Add columns
        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);

            sql.append("    `").append(col.getColumnName()).append("` ").append(col.getDataType());

            // Add AUTO_INCREMENT if specified
            if (col.isAutoIncrement()) {
                sql.append(" AUTO_INCREMENT");
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
                sql.append("`").append(primaryKeys.get(i)).append("`");
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

            sql.append("    CONSTRAINT `").append(fk.getConstraintName()).append("` ")
                    .append("FOREIGN KEY (`").append(fk.getLocalColumn()).append("`) ")
                    .append("REFERENCES `").append(fk.getReferenceTable()).append("` ")
                    .append("(`").append(fk.getReferenceColumn()).append("`)");

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

        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

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
            sql.append(String.format("RENAME TABLE `%s`.`%s` TO `%s`.`%s`;\n",
                    databaseName, oldTableName, databaseName, newTableName));
            oldTableName = newTableName;
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
                fksToDrop.add(oldFkName);
            } else {
                // Check if FK column was renamed
                String oldFkColumn = oldFk.getLocalColumn();
                String newFkColumn = matchingNewFk.getLocalColumn();

                // If the column was renamed, adjust comparison
                if (columnRenames.containsKey(oldFkColumn)) {
                    oldFkColumn = columnRenames.get(oldFkColumn);
                }

                if (!oldFkColumn.equals(newFkColumn) ||
                        !oldFk.getReferenceTable().equals(matchingNewFk.getReferenceTable()) ||
                        !oldFk.getReferenceColumn().equals(matchingNewFk.getReferenceColumn()) ||
                        !Objects.equals(oldFk.getOnDelete(), matchingNewFk.getOnDelete()) ||
                        !Objects.equals(oldFk.getOnUpdate(), matchingNewFk.getOnUpdate())) {
                    fksToDrop.add(oldFkName);
                    fksToRecreate.add(oldFkName);
                }
            }
        }

        if (!fksToDrop.isEmpty()) {
            StringBuilder alterFk = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < fksToDrop.size(); i++) {
                alterFk.append(String.format("  DROP FOREIGN KEY `%s`", fksToDrop.get(i)));
                if (i < fksToDrop.size() - 1) alterFk.append(",\n");
            }
            alterFk.append(";\n");
            sql.append(alterFk);
        }

        // 3. Analyze PK changes (accounting for renames)
        List<String> oldPKs = new ArrayList<>();
        List<String> newPKs = new ArrayList<>();
        for (ColumnData col : oldColumns) {
            if (col.isPrimaryKey()) oldPKs.add(col.getColumnName());
        }
        for (ColumnData col : newColumns) {
            if (col.isPrimaryKey()) newPKs.add(col.getColumnName());
        }

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

        // 4. Drop UNIQUE indexes that are being removed
        List<String> uniqueKeysToDrop = new ArrayList<>();
        for (ColumnData oldCol : oldColumns) {
            if (oldCol.isUnique()) {
                ColumnData matchingNewCol = null;
                String oldColName = oldCol.getColumnName();
                String renamedColName = columnRenames.getOrDefault(oldColName, oldColName);

                for (ColumnData newCol : newColumns) {
                    if (renamedColName.equals(newCol.getColumnName())) {
                        matchingNewCol = newCol;
                        break;
                    }
                }

                if (matchingNewCol == null) {
                    // Column is being dropped entirely (not renamed), unique will be dropped with it
                    continue;
                }

                // Drop unique index if the new column doesn't have isUnique flag
                if (!matchingNewCol.isUnique()) {
                    if (oldCol.getUniqueIndexName() != null) {
                        uniqueKeysToDrop.add(oldCol.getUniqueIndexName());
                    }
                }
            }
        }

        if (!uniqueKeysToDrop.isEmpty()) {
            StringBuilder alterUnique = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < uniqueKeysToDrop.size(); i++) {
                alterUnique.append(String.format("  DROP INDEX `%s`", uniqueKeysToDrop.get(i)));
                if (i < uniqueKeysToDrop.size() - 1) alterUnique.append(",\n");
            }
            alterUnique.append(";\n");
            sql.append(alterUnique);
        }

        // 5. Handle column renames FIRST, then drops and adds
        List<String> columnsToRename = new ArrayList<>();

        for (Map.Entry<String, String> rename : columnRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            // Find the old column definition
            ColumnData oldCol = null;
            for (ColumnData col : oldColumns) {
                if (col.getColumnName().equals(oldName)) {
                    oldCol = col;
                    break;
                }
            }

            if (oldCol != null) {
                // Find the new column definition to get updated attributes
                ColumnData newCol = null;
                for (ColumnData col : newColumns) {
                    if (col.getColumnName().equals(newName)) {
                        newCol = col;
                        break;
                    }
                }

                if (newCol != null) {
                    // RENAME with updated definition (but NOT including UNIQUE inline)
                    columnsToRename.add(String.format("CHANGE COLUMN `%s` `%s` %s%s%s",
                            oldName,
                            newName,
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " AUTO_INCREMENT" : ""
                    ));
                }
            }
        }

        if (!columnsToRename.isEmpty()) {
            StringBuilder alterRename = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < columnsToRename.size(); i++) {
                alterRename.append("  ").append(columnsToRename.get(i));
                if (i < columnsToRename.size() - 1) alterRename.append(",\n");
            }
            alterRename.append(";\n");
            sql.append(alterRename);
        }

        // 6. Drop columns and add new columns (Phase 1)
        List<String> columnsToDropNow = new ArrayList<>();
        List<String> columnsToAddNow = new ArrayList<>();

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
                columnsToDropNow.add(String.format("DROP COLUMN `%s`", oldColName));
            }
        }

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
                // Add new column WITHOUT AUTO_INCREMENT if it will be PK (add AI after PK is set)
                // For NEW columns, we CAN use inline UNIQUE
                if (newPKColumnsToAdd.contains(newCol.getColumnName())) {
                    columnsToAddNow.add(String.format("ADD COLUMN `%s` %s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isUnique() ? " UNIQUE" : ""
                    ));
                } else {
                    // Regular new column with all attributes
                    columnsToAddNow.add(String.format("ADD COLUMN `%s` %s%s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " AUTO_INCREMENT" : "",
                            newCol.isUnique() ? " UNIQUE" : ""
                    ));
                }
            }
        }

        List<String> firstPhaseAlterations = new ArrayList<>();
        firstPhaseAlterations.addAll(columnsToDropNow);
        firstPhaseAlterations.addAll(columnsToAddNow);

        if (!firstPhaseAlterations.isEmpty()) {
            StringBuilder alterCols = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < firstPhaseAlterations.size(); i++) {
                alterCols.append("  ").append(firstPhaseAlterations.get(i));
                if (i < firstPhaseAlterations.size() - 1) alterCols.append(",\n");
            }
            alterCols.append(";\n");
            sql.append(alterCols);
        }

        // 7. Handle PRIMARY KEY changes (accounting for renames)
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
                        // Remove AUTO_INCREMENT before dropping PK (use renamed column name)
                        sql.append(String.format("ALTER TABLE `%s`.`%s` MODIFY COLUMN `%s` %s NOT NULL;\n",
                                databaseName, oldTableName, adjustedPkCol, oldColWithAI.getDataType()));
                        columnsWithAIRemoved.add(adjustedPkCol);
                    }
                }
            }
        }

        if (pkIsChanging) {
            if (!oldPKs.isEmpty()) {
                sql.append(String.format("ALTER TABLE `%s`.`%s` DROP PRIMARY KEY;\n", databaseName, oldTableName));
            }
            if (!newPKs.isEmpty()) {
                String pkCols = newPKs.stream().map(c -> "`" + c + "`").reduce((a, b) -> a + ", " + b).orElse("");
                sql.append(String.format("ALTER TABLE `%s`.`%s` ADD PRIMARY KEY (%s);\n", databaseName, oldTableName, pkCols));
            }
        }

        // 8. Modify existing columns that weren't renamed (without UNIQUE - that's handled separately)
        List<String> columnsToModifyLater = new ArrayList<>();

        for (ColumnData newCol : newColumns) {
            String newColName = newCol.getColumnName();

            // Skip if this column was just renamed (already handled in CHANGE COLUMN)
            if (columnRenames.containsValue(newColName)) {
                continue;
            }

            ColumnData oldCol = null;
            for (ColumnData col : oldColumns) {
                if (col.getColumnName().equals(newColName)) {
                    oldCol = col;
                    break;
                }
            }

            if (oldCol != null) {
                // Skip if we already handled AI removal for old PK column
                if (columnsWithAIRemoved.contains(oldCol.getColumnName())) {
                    continue;
                }

                // Existing column - check if modification needed (excluding UNIQUE check)
                boolean needsModify = false;

                // Check all attributes for changes (but NOT isUnique - handled separately)
                if (!oldCol.getDataType().equalsIgnoreCase(newCol.getDataType()) ||
                        oldCol.isNotNull() != newCol.isNotNull() ||
                        oldCol.isAutoIncrement() != newCol.isAutoIncrement()) {
                    needsModify = true;
                }

                if (needsModify) {
                    // IMPORTANT: Do NOT include UNIQUE in MODIFY COLUMN for existing columns
                    columnsToModifyLater.add(String.format("MODIFY COLUMN `%s` %s%s%s",
                            newCol.getColumnName(),
                            newCol.getDataType(),
                            newCol.isNotNull() ? " NOT NULL" : "",
                            newCol.isAutoIncrement() ? " AUTO_INCREMENT" : ""
                    ));
                }
            } else if (newPKColumnsToAdd.contains(newCol.getColumnName()) && newCol.isAutoIncrement()) {
                // New PK column - add AUTO_INCREMENT now that PK exists
                columnsToModifyLater.add(String.format("MODIFY COLUMN `%s` %s%s%s",
                        newCol.getColumnName(),
                        newCol.getDataType(),
                        newCol.isNotNull() ? " NOT NULL" : "",
                        " AUTO_INCREMENT"
                ));
            }
        }

        if (!columnsToModifyLater.isEmpty()) {
            StringBuilder alterCols = new StringBuilder(String.format("ALTER TABLE `%s`.`%s`\n", databaseName, oldTableName));
            for (int i = 0; i < columnsToModifyLater.size(); i++) {
                alterCols.append("  ").append(columnsToModifyLater.get(i));
                if (i < columnsToModifyLater.size() - 1) alterCols.append(",\n");
            }
            alterCols.append(";\n");
            sql.append(alterCols);
        }

        // 9. Add UNIQUE indexes for columns that gained UNIQUE (including renamed columns)
        List<String> uniqueIndexesToAdd = new ArrayList<>();

        for (ColumnData newCol : newColumns) {
            if (newCol.isUnique()) {
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

                // If this is an existing column that didn't have UNIQUE before, add index
                if (oldCol != null && !oldCol.isUnique()) {
                    uniqueIndexesToAdd.add(newColName);
                }
            }
        }

        if (!uniqueIndexesToAdd.isEmpty()) {
            for (String colName : uniqueIndexesToAdd) {
                sql.append(String.format("ALTER TABLE `%s`.`%s` ADD UNIQUE INDEX `%s` (`%s`);\n",
                        databaseName, oldTableName, colName+"_UNIQUE", colName));
            }
        }

        // 10. Add new foreign keys and recreate modified ones
        List<ForeignKeyData> fksToAdd = new ArrayList<>();
        for (ForeignKeyData newFk : newForeignKeys) {
            String fkName = newFk.getConstraintName();
            if (!oldForeignKeys.containsKey(fkName) || fksToRecreate.contains(fkName)) {
                fksToAdd.add(newFk);
            }
        }

        for (ForeignKeyData fk : fksToAdd) {
            sql.append(String.format("ALTER TABLE `%s`.`%s` ADD CONSTRAINT `%s` FOREIGN KEY (`%s`) REFERENCES `%s`(`%s`)",
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
