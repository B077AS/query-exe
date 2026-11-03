package com.queryexe.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.connections.PostgresConnection;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.PartitionData;
import com.queryexe.utils.SqlInspector;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads a connection's structure (databases/schemas, tables, columns, other objects) into plain data for the page.
 *
 * <p>The engine classes read through "the current connection" of {@link DatabaseConnection}, so every call here
 * takes a lock and makes the right connection current first. Blocking JDBC: call off the UI thread.
 */
@Slf4j
public final class Catalog {

    private static final Catalog INSTANCE = new Catalog();
    private final ReentrantLock lock = new ReentrantLock();

    private Catalog() {
    }

    public static Catalog get() {
        return INSTANCE;
    }

    @Value
    public static class Column {
        String name;
        String type;
        boolean primaryKey;
        boolean notNull;
        boolean autoIncrement;
    }

    @Value
    public static class Partition {
        String name;
        /** The partition this is a sub-partition of; null for a partition of the table. */
        String parent;
        String method;
        String bound;
        Long rows;
        /** True when the partition is a table of its own (PostgreSQL), so the page can open it like one. */
        boolean table;
    }

    @Value
    public static class Table {
        String name;
        List<Column> columns;
        /** Empty for tables that are not partitioned. */
        List<Partition> partitions;

        public Table(String name, List<Column> columns) {
            this(name, columns, List.of());
        }

        public Table(String name, List<Column> columns, List<Partition> partitions) {
            this.name = name;
            this.columns = columns;
            this.partitions = partitions;
        }
    }

    @Value
    public static class Group {
        String title;
        List<String> items;
    }

    /** A database (MySQL, MariaDB, SQL Server, H2, SQLite) or a schema (PostgreSQL): what the engine calls "databaseName" in its methods. */
    @Value
    public static class Container {
        String name;
        /** "database" or "schema". */
        String kind;
        /** The owning database when a PostgreSQL connection shows several databases, else null. */
        String parent;
        List<Table> tables;
        List<Group> groups;
    }

    @Value
    public static class Tree {
        String connectionId;
        String name;
        String type;
        List<Container> containers;
        /** The container the connection is currently using, when known. */
        String current;
    }

    /** Runs {@code work} with this connection id as the current one, serialised with every other catalog call. */
    public <T> T with(String connectionId, Supplier<T> work) {
        lock.lock();
        try {
            DatabaseConnection.getInstance().setCurrentConnectionId(connectionId);
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    public interface SqlWork<T> {
        T run(ConnectionObject connection) throws SQLException;
    }

    /** Like {@link #with}, handing over the connection object and letting the work throw SQLException. */
    public <T> T call(String connectionId, SqlWork<T> work) throws SQLException {
        lock.lock();
        try {
            DatabaseConnection dc = DatabaseConnection.getInstance();
            ConnectionObject object = dc.getConnectionObject(connectionId);
            if (object == null) {
                throw new SQLException("This connection is not open.");
            }
            dc.setCurrentConnectionId(connectionId);
            return work.run(object);
        } finally {
            lock.unlock();
        }
    }

    public Tree load(String connectionId) throws SQLException {
        return call(connectionId, connection -> {
            List<Container> containers = new ArrayList<>();
            String current = null;
            boolean postgres = connection.getDbType().equals(ConnectionTypes.PostgreSQL.toString());
            String configured = connection.getDatabaseName();

            if (postgres && configured != null && !configured.isEmpty()) {
                PostgresConnection pg = (PostgresConnection) connection;
                for (String schema : pg.getSchemas()) {
                    containers.add(container(connection, schema, "schema", null));
                }
                current = defaultPostgresSchema(connection);
            } else if (postgres) {
                var hierarchy = ((PostgresConnection) connection).getCompleteHierarchy();
                for (var database : hierarchy.entrySet()) {
                    for (var schema : database.getValue().entrySet()) {
                        List<Table> tables = new ArrayList<>();
                        schema.getValue().forEach((name, columns) -> tables.add(new Table(name, columns(columns))));
                        containers.add(new Container(schema.getKey(), "schema", database.getKey(), tables, List.of()));
                    }
                }
            } else {
                for (String database : connection.getDatabases(configured)) {
                    containers.add(container(connection, database, "database", null));
                }
                if (configured != null && !configured.isEmpty()) {
                    try {
                        connection.useDatabase(configured);
                        current = configured;
                    } catch (SQLException e) {
                        log.warn("Could not select the configured database: {}", e.getMessage());
                    }
                }
            }
            return new Tree(connectionId, connection.getConnectionName(), connection.getDbType(), containers, current);
        });
    }

    /** Reloads one container (after DDL, or the Refresh action). */
    public Container reload(String connectionId, String name) throws SQLException {
        return call(connectionId, connection -> {
            boolean schema = connection.getDbType().equals(ConnectionTypes.PostgreSQL.toString());
            return container(connection, name, schema ? "schema" : "database", null);
        });
    }

    private Container container(ConnectionObject connection, String name, String kind, String parent) {
        Map<String, ArrayList<ColumnData>> map = connection.getAllTablesAndColumns(name);
        List<Table> tables = new ArrayList<>();
        if (map != null) {
            Map<String, List<PartitionData>> partitions = connection.getPartitions(name);
            boolean nested = connection.partitionsAreTables();
            Set<String> hidden = new HashSet<>();
            if (nested) {
                partitions.values().forEach(list -> list.forEach(p -> hidden.add(p.getName())));
            }
            map.forEach((table, columns) -> {
                if (!hidden.contains(table)) {
                    tables.add(new Table(table, columns(columns), partitionsOf(partitions.get(table), nested)));
                }
            });
        }
        List<Group> groups = new ArrayList<>();
        Map<String, List<String>> objects = connection.getSchemaObjects(name);
        if (objects != null) {
            objects.forEach((title, items) -> groups.add(new Group(title, items)));
        }
        return new Container(name, kind, parent, tables, groups);
    }

    private static List<Partition> partitionsOf(List<PartitionData> data, boolean table) {
        List<Partition> out = new ArrayList<>();
        if (data != null) {
            for (PartitionData p : data) {
                out.add(new Partition(p.getName(), p.getParent(), p.getMethod(), p.getBound(), p.getRows(), table));
            }
        }
        return out;
    }

    private static List<Column> columns(List<ColumnData> data) {
        List<Column> out = new ArrayList<>();
        if (data != null) {
            for (ColumnData column : data) {
                out.add(new Column(column.getColumnName(), column.getDataType(), column.isPrimaryKey(),
                        column.isNotNull(), column.isAutoIncrement()));
            }
        }
        return out;
    }

    private String defaultPostgresSchema(ConnectionObject connection) {
        try {
            java.sql.Connection jdbc = DatabaseConnection.getInstance().getConnection(connection.getId());
            try (var statement = jdbc.createStatement(); var rs = statement.executeQuery("SHOW search_path")) {
                if (rs.next()) {
                    return SqlInspector.firstSearchPathSchema(rs.getString(1)).orElse("public");
                }
            }
        } catch (SQLException e) {
            log.debug("Could not read search_path: {}", e.getMessage());
        }
        return "public";
    }

    /** What the connection is using now (catalog or schema), used after a script that may have switched it. */
    public String currentContainer(String connectionId) {
        try {
            return call(connectionId, connection -> {
                java.sql.Connection jdbc = DatabaseConnection.getInstance().getConnection(connectionId);
                if (connection.getDbType().equals(ConnectionTypes.PostgreSQL.toString())) {
                    return defaultPostgresSchema(connection);
                }
                String schema = null;
                try {
                    schema = jdbc.getSchema();
                } catch (SQLException | RuntimeException ignored) {
                    // not every driver has schemas
                }
                String catalog = jdbc.getCatalog();
                return connection.getDbType().equals(ConnectionTypes.H2.toString()) && schema != null ? schema : catalog;
            });
        } catch (SQLException e) {
            return null;
        }
    }

    /** Everything the page needs to describe the type lists of an engine (for the table editor and editor completion). */
    public Map<String, Object> vocabulary(String connectionId) throws SQLException {
        return call(connectionId, connection -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dataTypes", List.of(connection.getDataTypes()));
            return out;
        });
    }
}
