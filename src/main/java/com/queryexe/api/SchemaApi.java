package com.queryexe.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.queryexe.Params;
import com.queryexe.Rpc;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.service.Catalog;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.service.SQLFormatterUtils;
import com.queryexe.service.ScriptRunner;
import com.queryexe.service.Workspace;
import com.queryexe.utils.SqlSplitter;

import lombok.extern.slf4j.Slf4j;

/** The schema tree, generated scripts, and the table editor (create / alter / drop). */
@Slf4j
final class SchemaApi {

    private static final int SELECT_LIMIT = 1_000;

    private final Catalog catalog = Catalog.get();

    void register(Rpc rpc) {
        rpc.on("schema.load", p -> catalog.load(Params.string(p, "connectionId")));
        rpc.on("schema.reload", p -> catalog.reload(Params.string(p, "connectionId"), Params.string(p, "container")));
        rpc.on("schema.current", p -> {
            Map<String, Object> out = new HashMap<>();
            out.put("container", catalog.currentContainer(Params.string(p, "connectionId")));
            return out;
        });
        rpc.on("schema.use", p -> {
            catalog.call(Params.string(p, "connectionId"), c -> {
                c.useDatabase(Params.string(p, "container"));
                return null;
            });
            return Map.of("ok", true);
        });
        rpc.on("schema.script", this::script);
        rpc.on("schema.tableInfo", this::tableInfo);
        rpc.on("schema.diagram", this::diagram);
        rpc.on("schema.columnInfo", p -> catalog.call(Params.string(p, "connectionId"), c ->
                c.getDetailedColumnInfo(Params.string(p, "container"), Params.string(p, "table"), Params.string(p, "column"))));
        rpc.on("schema.createContainer", p -> {
            catalog.call(Params.string(p, "connectionId"), c -> {
                c.createDatabase(Params.string(p, "name"));
                return null;
            });
            return Map.of("ok", true);
        });
        rpc.on("schema.dropContainer", p -> {
            catalog.call(Params.string(p, "connectionId"), c -> {
                c.deleteDatabase(Params.string(p, "name"));
                return null;
            });
            return Map.of("ok", true);
        });
        rpc.on("schema.dropTable", p -> {
            catalog.call(Params.string(p, "connectionId"), c -> {
                c.deleteTable(Params.string(p, "container"), Params.string(p, "table"));
                return null;
            });
            return Map.of("ok", true);
        });
        rpc.on("table.describe", this::describeTable);
        rpc.on("table.preview", this::preview);
        rpc.on("table.execute", this::execute);
        rpc.on("sql.format", p -> {
            ConnectionObject c = DatabaseConnection.getInstance().getConnectionObject(Params.string(p, "connectionId"));
            return Map.of("sql", SQLFormatterUtils.format(Params.string(p, "sql"), c));
        });
        rpc.on("sql.split", p -> {
            ConnectionObject c = DatabaseConnection.getInstance().getConnectionObject(Params.string(p, "connectionId"));
            if (c == null) {
                throw new IllegalStateException("This connection is not open.");
            }
            return Workspace.split(Params.string(p, "text"), c);
        });
    }

    private Object script(JsonObject p) throws SQLException {
        String kind = Params.string(p, "kind");
        String container = Params.string(p, "container");
        String table = Params.string(p, "table");
        return catalog.call(Params.string(p, "connectionId"), c -> {
            String sql = switch (kind) {
                case "select" -> c.generateSelectScript(table, container, SELECT_LIMIT);
                case "create" -> c.generateCreateScript(table, container);
                case "insert" -> c.generateInsertScript(table, container);
                default -> throw new IllegalArgumentException("Unknown script kind: " + kind);
            };
            return Map.of("sql", sql == null ? "" : sql);
        });
    }

    private Object tableInfo(JsonObject p) throws SQLException {
        String container = Params.string(p, "container");
        String table = Params.string(p, "table");
        return catalog.call(Params.string(p, "connectionId"), c -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("columns", c.getColumnsForTable(container, table));
            out.put("foreignKeys", new ArrayList<>(c.extractForeignKeys(container, table).values()));
            try {
                out.put("indexes", c.getIndexes(container, table));
            } catch (SQLException | RuntimeException e) {
                log.warn("No indexes for {}: {}", table, e.getMessage());
                out.put("indexes", List.of());
            }
            Double size = null;
            try {
                size = c.getTableSize(container, table);
            } catch (SQLException | RuntimeException e) {
                log.debug("No table size: {}", e.getMessage());
            }
            out.put("sizeMb", size);
            return out;
        });
    }

    /**
     * The entity-relationship data of one database/schema: every table with its columns and every foreign key
     * between tables of it. Keys pointing into another schema are left out (the table is not in the diagram).
     */
    private Object diagram(JsonObject p) throws SQLException {
        String container = Params.string(p, "container");
        return catalog.call(Params.string(p, "connectionId"), c -> {
            Map<String, ArrayList<ColumnData>> all = c.getAllTablesAndColumns(container);
            List<Map<String, Object>> tables = new ArrayList<>();
            List<Map<String, Object>> relations = new ArrayList<>();
            if (all == null) {
                return Map.of("tables", tables, "relations", relations);
            }
            for (Map.Entry<String, ArrayList<ColumnData>> table : all.entrySet()) {
                List<Map<String, Object>> columns = new ArrayList<>();
                for (ColumnData column : table.getValue()) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("name", column.getColumnName());
                    o.put("type", column.getDataType());
                    o.put("primaryKey", column.isPrimaryKey());
                    o.put("notNull", column.isNotNull());
                    columns.add(o);
                }
                tables.add(Map.of("name", table.getKey(), "columns", columns));
            }
            for (String table : all.keySet()) {
                Map<String, ForeignKeyData> keys;
                try {
                    keys = c.extractForeignKeys(container, table);
                } catch (SQLException | RuntimeException e) {
                    log.debug("No foreign keys for {}: {}", table, e.getMessage());
                    continue;
                }
                for (ForeignKeyData fk : keys.values()) {
                    String schema = fk.getReferenceSchema();
                    boolean sameContainer = schema == null || schema.isBlank() || schema.equals(container);
                    if (!sameContainer || !all.containsKey(fk.getReferenceTable())) {
                        continue;
                    }
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("name", fk.getConstraintName());
                    o.put("from", table);
                    o.put("fromColumns", fk.allLocalColumns());
                    o.put("to", fk.getReferenceTable());
                    o.put("toColumns", fk.allReferenceColumns());
                    o.put("onDelete", fk.getOnDelete());
                    o.put("onUpdate", fk.getOnUpdate());
                    relations.add(o);
                }
            }
            return Map.of("tables", tables, "relations", relations);
        });
    }

    // ------------------------------------------------------------------------------------ table editor

    /** Everything the table editor needs: the current columns and keys (empty for a new table), the other tables and the engine's types. */
    private Object describeTable(JsonObject p) throws SQLException {
        String container = Params.string(p, "container");
        String table = Params.optionalString(p, "table");
        return catalog.call(Params.string(p, "connectionId"), c -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dataTypes", List.of(c.getDataTypes()));
            out.put("tables", c.getTablesForDatabase(container));
            if (!table.isEmpty()) {
                out.put("columns", c.getColumnsForTable(container, table));
                out.put("foreignKeys", new ArrayList<>(c.extractForeignKeys(container, table).values()));
            try {
                out.put("indexes", c.getIndexes(container, table));
            } catch (SQLException | RuntimeException e) {
                log.warn("No indexes for {}: {}", table, e.getMessage());
                out.put("indexes", List.of());
            }
            } else {
                out.put("columns", List.of());
                out.put("foreignKeys", List.of());
            }
            return out;
        });
    }

    private static List<ColumnData> columns(JsonObject p) {
        List<ColumnData> out = new ArrayList<>();
        for (JsonElement element : p.getAsJsonArray("columns")) {
            JsonObject o = element.getAsJsonObject();
            String name = Params.optionalString(o, "name").trim();
            String type = Params.optionalString(o, "type").trim();
            if (name.isEmpty() || type.isEmpty()) {
                continue;
            }
            ColumnData column = new ColumnData(name, type, flag(o, "primaryKey"), flag(o, "notNull"), flag(o, "unique"), flag(o, "autoIncrement"));
            String def = Params.optionalString(o, "defaultValue");
            if (!def.isBlank()) {
                column.setDefaultValue(def);
            }
            out.add(column);
        }
        return out;
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    private static List<ForeignKeyData> foreignKeys(JsonObject p, String table) {
        List<ForeignKeyData> out = new ArrayList<>();
        if (!p.has("foreignKeys")) {
            return out;
        }
        for (JsonElement element : p.getAsJsonArray("foreignKeys")) {
            JsonObject o = element.getAsJsonObject();
            String local = Params.optionalString(o, "localColumn");
            String refTable = Params.optionalString(o, "referenceTable");
            String refColumn = Params.optionalString(o, "referenceColumn");
            if (local.isEmpty() || refTable.isEmpty() || refColumn.isEmpty()) {
                continue;
            }
            String name = Params.optionalString(o, "constraintName").trim();
            if (name.isEmpty()) {
                name = "fk_" + table + "_" + local;
            }
            String onDelete = Params.optionalString(o, "onDelete");
            String onUpdate = Params.optionalString(o, "onUpdate");
            ForeignKeyData fk = new ForeignKeyData(name, local, refTable, refColumn,
                    onDelete.isEmpty() ? "NO ACTION" : onDelete, onUpdate.isEmpty() ? "NO ACTION" : onUpdate);
            String refSchema = Params.optionalString(o, "referenceSchema");
            if (!refSchema.isEmpty()) {
                fk.setReferenceSchema(refSchema);
            }
            out.add(fk);
        }
        return out;
    }

    /** The CREATE script, or in edit mode the ALTER script against the table as it is in the database now. */
    private Object preview(JsonObject p) throws SQLException {
        String container = Params.string(p, "container");
        String table = Params.string(p, "table").trim();
        String original = Params.optionalString(p, "originalTable");
        if (table.isEmpty()) {
            throw new IllegalArgumentException("Name the table.");
        }
        List<ColumnData> columns = columns(p);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Add a column with a name and a type.");
        }
        List<ForeignKeyData> fks = foreignKeys(p, table);
        Map<String, String> renames = new LinkedHashMap<>();
        if (p.has("renames")) {
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("renames").entrySet()) {
                renames.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return catalog.call(Params.string(p, "connectionId"), c -> {
            String sql;
            if (original.isEmpty()) {
                sql = c.generateCreateTableSQL(container, table, columns, fks);
            } else {
                List<ColumnData> oldColumns = c.getColumnsForTable(container, original);
                Map<String, ForeignKeyData> oldFks = c.extractForeignKeys(container, original);
                sql = c.generateAlterTableSQL(container, original, table, oldColumns, columns, oldFks, fks, renames);
            }
            String shown = sql == null || sql.isBlank() ? "" : SQLFormatterUtils.format(sql, c);
            Map<String, Object> out = new HashMap<>();
            out.put("sql", shown);
            return out;
        });
    }

    /** Runs a generated script on a connection of its own, one statement at a time, stopping at the first error. */
    private Object execute(JsonObject p) throws SQLException {
        String connectionId = Params.string(p, "connectionId");
        ConnectionObject object = DatabaseConnection.getInstance().getConnectionObject(connectionId);
        if (object == null) {
            throw new IllegalStateException("This connection is not open.");
        }
        List<String> statements = SqlSplitter.texts(SqlSplitter.split(Params.string(p, "sql"), object.getSqlSyntax()));
        Connection session = DatabaseConnection.getInstance().openSession(object);
        String[] failure = {null};
        try {
            boolean ok = ScriptRunner.run(session, statements, 0, new ScriptRunner.Listener() {
                @Override
                public void onResultSet(String sql, java.sql.Statement statement, long millis) throws SQLException {
                    statement.getResultSet().close();
                }

                @Override
                public void onUpdateCount(String sql, long count, long millis) {
                }

                @Override
                public void onError(String sql, SQLException error) {
                    failure[0] = error.getMessage();
                }
            });
            if (!ok) {
                throw new SQLException(failure[0] == null ? "The script failed." : failure[0]);
            }
        } finally {
            DatabaseConnection.getInstance().closeSession(connectionId, session);
        }
        return Map.of("ok", true);
    }

}
