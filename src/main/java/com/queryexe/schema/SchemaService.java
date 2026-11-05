package com.queryexe.schema;

import com.queryexe.schema.tree.*;

import com.queryexe.connection.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.queryexe.connection.ConnectionProfile;
import com.queryexe.engine.SchemaReader;
import com.queryexe.schema.ColumnData;
import com.queryexe.schema.PartitionData;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads a connection's structure (databases/schemas, tables, columns, other objects) into plain data for the page.
 *
 * <p>Every call here reads through the engine's {@link SchemaReader} on the shared connection of {@link DatabaseConnection}, one at a time.
 * Blocking JDBC: call off the UI thread.
 */
@Slf4j
public class SchemaService {

    private static final SchemaService INSTANCE = new SchemaService();
    private ReentrantLock lock = new ReentrantLock();

    private SchemaService() {
    }

    public static SchemaService get() {
        return INSTANCE;
    }

    /** Runs {@code work} with this connection id as the current one, serialised with every other catalog call. */
    public interface SqlWork<T> {
        T run(SchemaReader reader) throws SQLException;
    }

    /**
     * Runs {@code work} with a reader on the connection's shared JDBC connection. One at a time, because the tree and
     * the dialogs share that connection. Blocking JDBC: call off the UI thread.
     */
    public <T> T read(String connectionId, SqlWork<T> work) throws SQLException {
        lock.lock();
        try {
            DatabaseConnection dc = DatabaseConnection.getInstance();
            ConnectionProfile profile = dc.getProfile(connectionId);
            Connection jdbc = dc.sharedConnection(connectionId);
            if (profile == null || jdbc == null) {
                throw new SQLException("This connection is not open.");
            }
            return work.run(profile.type().reader(jdbc, profile));
        } finally {
            lock.unlock();
        }
    }

    public Tree load(String connectionId) throws SQLException {
        return read(connectionId, reader -> {
            ConnectionProfile profile = reader.getProfile();
            String configured = profile.getDatabaseName();
            List<Container> containers = new ArrayList<>();
            String current = null;

            var hierarchy = reader.getCompleteHierarchy(configured);
            if (hierarchy != null) {
                for (var database : hierarchy.entrySet()) {
                    for (var schema : database.getValue().entrySet()) {
                        List<Table> tables = new ArrayList<>();
                        schema.getValue().forEach((name, columns) -> tables.add(new Table(name, columns(columns))));
                        containers.add(new Container(schema.getKey(), "schema", database.getKey(), tables, List.of()));
                    }
                }
            } else {
                for (String name : reader.listContainers(configured)) {
                    containers.add(container(reader, name, null));
                }
                current = reader.selectInitialContainer(configured);
            }
            return new Tree(connectionId, profile.getConnectionName(), profile.getDbType(), containers, current);
        });
    }

    /** Reloads one container (after DDL, or the Refresh action). */
    public Container reload(String connectionId, String name) throws SQLException {
        return read(connectionId, reader -> container(reader, name, null));
    }

    private Container container(SchemaReader reader, String name, String parent) {
        Map<String, ArrayList<ColumnData>> map = reader.getAllTablesAndColumns(name);
        List<Table> tables = new ArrayList<>();
        if (map != null) {
            Map<String, List<PartitionData>> partitions = reader.getPartitions(name);
            boolean nested = reader.partitionsAreTables();
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
        Map<String, List<String>> objects = reader.getSchemaObjects(name);
        if (objects != null) {
            objects.forEach((title, items) -> groups.add(new Group(title, items)));
        }
        return new Container(name, reader.containerKind(), parent, tables, groups);
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

    /** What the connection is using now (catalog or schema), used after a script that may have switched it. */
    public String currentContainer(String connectionId) {
        try {
            return read(connectionId, SchemaReader::currentContainer);
        } catch (SQLException e) {
            return null;
        }
    }
}
