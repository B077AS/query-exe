package com.queryexe.model.connections.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Reads a SQLite database's structure through the {@code pragma_*} table-valued functions (names are bound
 * as parameters, so tables called {@code "my table"} or {@code "order"} are fine) and the
 * {@code sqlite_master} of the right attached database. Works on any plain {@link Connection}.
 */
@RequiredArgsConstructor
public class SqliteCatalog {

    public static final String MAIN = "main";

    private static final Pattern AUTOINCREMENT = Pattern.compile("\\bAUTOINCREMENT\\b", Pattern.CASE_INSENSITIVE);

    private final Connection connection;

    // ------------------------------------------------------------------------------------ model

    @Value
    @Builder
    public static class ColumnInfo {
        String name;
        /** The type exactly as declared, e.g. {@code VARCHAR(50)}; empty for typeless columns. */
        String type;
        boolean notNull;
        String defaultValue;
        /** 1-based position in the primary key, 0 when the column is not part of it. */
        int primaryKeyPosition;
        /** A generated (virtual or stored) column: it cannot be written to. */
        boolean generated;
    }

    @Value
    @Builder
    public static class IndexInfo {
        String name;
        boolean unique;
        /** {@code c} = CREATE INDEX, {@code u} = UNIQUE constraint, {@code pk} = PRIMARY KEY. */
        String origin;
        List<String> columns;
    }

    @Value
    @Builder
    public static class ForeignKeyInfo {
        /** SQLite does not store constraint names in a readable form, so one is derived from the table and key id. */
        String name;
        String table;
        List<String> columns;
        String referenceTable;
        List<String> referenceColumns;
        String onDelete;
        String onUpdate;
    }

    // ------------------------------------------------------------------------------------ naming

    public static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(String schema, String name) {
        return quote(schema) + "." + quote(name);
    }

    public static String schemaOrMain(String schema) {
        return schema == null || schema.isBlank() ? MAIN : schema;
    }

    // ------------------------------------------------------------------------------------ reading

    public List<String> databases() throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("PRAGMA database_list");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    /** Ordinary tables, without SQLite's internal {@code sqlite_*} ones. */
    public List<String> tables(String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM " + quote(schemaOrMain(schema)) + ".sqlite_master "
                + "WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' ORDER BY name";
        try (PreparedStatement stmt = connection.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    public List<ColumnInfo> columns(String schema, String table) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        // table_xinfo (unlike table_info) also lists generated columns; hidden=1 are virtual-table internals
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM pragma_table_xinfo(?, ?)")) {
            stmt.setString(1, table);
            stmt.setString(2, schemaOrMain(schema));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int hidden = rs.getInt("hidden");
                    if (hidden == 1) {
                        continue;
                    }
                    String type = rs.getString("type");
                    columns.add(ColumnInfo.builder()
                            .name(rs.getString("name"))
                            .type(type == null ? "" : type)
                            .notNull(rs.getInt("notnull") == 1)
                            .defaultValue(rs.getString("dflt_value"))
                            .primaryKeyPosition(rs.getInt("pk"))
                            .generated(hidden == 2 || hidden == 3)
                            .build());
                }
            }
        }
        return columns;
    }

    public List<IndexInfo> indexes(String schema, String table) throws SQLException {
        record Raw(String name, boolean unique, String origin) {
        }
        List<Raw> raw = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM pragma_index_list(?, ?)")) {
            stmt.setString(1, table);
            stmt.setString(2, schemaOrMain(schema));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    raw.add(new Raw(rs.getString("name"), rs.getInt("unique") == 1, rs.getString("origin")));
                }
            }
        }

        List<IndexInfo> indexes = new ArrayList<>();
        for (Raw index : raw) {
            List<String> columns = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM pragma_index_info(?, ?) ORDER BY seqno")) {
                stmt.setString(1, index.name());
                stmt.setString(2, schemaOrMain(schema));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        columns.add(rs.getString("name"));
                    }
                }
            }
            indexes.add(IndexInfo.builder().name(index.name()).unique(index.unique()).origin(index.origin()).columns(columns).build());
        }
        return indexes;
    }

    /** Foreign keys defined on {@code table}, one entry per key with all its column pairs. */
    public List<ForeignKeyInfo> foreignKeys(String schema, String table) throws SQLException {
        Map<Integer, ForeignKeyInfo.ForeignKeyInfoBuilder> builders = new LinkedHashMap<>();
        Map<Integer, List<String>> from = new LinkedHashMap<>();
        Map<Integer, List<String>> to = new LinkedHashMap<>();
        Map<Integer, String> referenceTables = new LinkedHashMap<>();

        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM pragma_foreign_key_list(?, ?) ORDER BY id, seq")) {
            stmt.setString(1, table);
            stmt.setString(2, schemaOrMain(schema));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    referenceTables.putIfAbsent(id, rs.getString("table"));
                    if (!builders.containsKey(id)) {
                        builders.put(id, ForeignKeyInfo.builder()
                                .table(table)
                                .referenceTable(rs.getString("table"))
                                .onDelete(rule(rs.getString("on_delete")))
                                .onUpdate(rule(rs.getString("on_update"))));
                    }
                    from.computeIfAbsent(id, k -> new ArrayList<>()).add(rs.getString("from"));
                    to.computeIfAbsent(id, k -> new ArrayList<>()).add(rs.getString("to"));
                }
            }
        }

        List<ForeignKeyInfo> keys = new ArrayList<>();
        for (Map.Entry<Integer, ForeignKeyInfo.ForeignKeyInfoBuilder> entry : builders.entrySet()) {
            int id = entry.getKey();
            ForeignKeyInfo.ForeignKeyInfoBuilder builder = entry.getValue();
            // "REFERENCES t" without a column list points at t's primary key
            List<String> referenceColumns = to.get(id);
            if (referenceColumns.contains(null)) {
                referenceColumns = primaryKeyColumns(schema, referenceTables.get(id));
            }
            keys.add(builder.name("fk_" + table + "_" + id).columns(from.get(id)).referenceColumns(referenceColumns).build());
        }
        return keys;
    }

    /** Foreign keys of other tables that reference {@code table}. */
    public List<ForeignKeyInfo> referencing(String schema, String table) throws SQLException {
        List<ForeignKeyInfo> incoming = new ArrayList<>();
        for (String other : tables(schema)) {
            for (ForeignKeyInfo fk : foreignKeys(schema, other)) {
                if (fk.getReferenceTable().equalsIgnoreCase(table)) {
                    incoming.add(fk);
                }
            }
        }
        return incoming;
    }

    public List<String> primaryKeyColumns(String schema, String table) throws SQLException {
        return columns(schema, table).stream()
                .filter(c -> c.getPrimaryKeyPosition() > 0)
                .sorted((a, b) -> Integer.compare(a.getPrimaryKeyPosition(), b.getPrimaryKeyPosition()))
                .map(ColumnInfo::getName)
                .toList();
    }

    /** The statement the table was created with, as SQLite stores it. */
    public Optional<String> createSql(String schema, String table) throws SQLException {
        String sql = "SELECT sql FROM " + quote(schemaOrMain(schema)) + ".sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /** CREATE INDEX / CREATE TRIGGER statements that belong to the table (not the indexes SQLite makes for constraints). */
    public List<String> relatedSql(String schema, String table) throws SQLException {
        List<String> statements = new ArrayList<>();
        String sql = "SELECT sql FROM " + quote(schemaOrMain(schema)) + ".sqlite_master "
                + "WHERE tbl_name = ? AND type IN ('index', 'trigger') AND sql IS NOT NULL ORDER BY type DESC, name";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    statements.add(rs.getString(1));
                }
            }
        }
        return statements;
    }

    /**
     * A single INTEGER primary key column is the rowid alias and gets values automatically; with the
     * AUTOINCREMENT keyword it also never reuses them. The keyword is only meaningful on that column.
     */
    public boolean isAutoincrement(String schema, String table) throws SQLException {
        List<ColumnInfo> pk = columns(schema, table).stream().filter(c -> c.getPrimaryKeyPosition() > 0).toList();
        if (pk.size() != 1 || !pk.get(0).getType().equalsIgnoreCase("INTEGER")) {
            return false;
        }
        return createSql(schema, table).map(sql -> AUTOINCREMENT.matcher(sql).find()).orElse(false);
    }

    /** Size of the table and its indexes in MB, from the {@code dbstat} virtual table; -1 when it is unavailable. */
    public double sizeMegabytes(String schema, String table) throws SQLException {
        List<String> names = new ArrayList<>(List.of(table));
        for (IndexInfo index : indexes(schema, table)) {
            names.add(index.getName());
        }
        String placeholders = String.join(", ", names.stream().map(n -> "?").toList());
        String sql = "SELECT ROUND(COALESCE(SUM(pgsize), 0) / 1048576.0, 2) FROM dbstat WHERE schema = ? AND name IN (" + placeholders + ")";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schemaOrMain(schema));
            for (int i = 0; i < names.size(); i++) {
                stmt.setString(i + 2, names.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : -1;
            }
        } catch (SQLException e) {
            return -1; // SQLite built without SQLITE_ENABLE_DBSTAT_VTAB
        }
    }

    private static String rule(String value) {
        return value == null ? "NO ACTION" : value.toUpperCase();
    }
}
