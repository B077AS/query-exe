package io.github.b077as.queryexe.engine;

import io.github.b077as.queryexe.connection.ConnectionProfile;
import io.github.b077as.queryexe.schema.ColumnData;
import io.github.b077as.queryexe.schema.DetailedColumnData;
import io.github.b077as.queryexe.schema.ForeignKeyData;
import io.github.b077as.queryexe.schema.IndexData;
import io.github.b077as.queryexe.schema.PartitionData;
import lombok.Getter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What one engine reads from, and does to, a database through one JDBC {@link Connection}: the structure for the tree,
 * the scripts that rebuild a table, and the few admin statements (drop table, create database). The connection is the
 * one handed to the constructor, never a global, so a reader is cheap to create and safe to use from any thread that
 * owns the connection. SQL that needs no connection lives in the engine's {@link Dialect}.
 * <p>
 * A "container" is what the engine calls {@code databaseName} in these methods: a database (MySQL, MariaDB, SQL Server,
 * H2, SQLite) or a schema (PostgreSQL).
 */
@Getter
public abstract class SchemaReader {

    protected Connection connection;
    protected ConnectionProfile profile;
    protected Dialect dialect;

    protected SchemaReader(Connection connection, ConnectionProfile profile) {
        this.connection = connection;
        this.profile = profile;
        this.dialect = profile.dialect();
    }

    public abstract Map<String, ArrayList<ColumnData>> getAllTablesAndColumns(String databaseName);

    public abstract ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException;

    public abstract ArrayList<ColumnData> getColumnsForTable(String databaseName, String tableName) throws SQLException;

    public abstract DetailedColumnData getDetailedColumnInfo(String schemaName, String tableName, String columnName) throws SQLException;

    public abstract Map<String, ForeignKeyData> extractForeignKeys(String databaseName, String tableName) throws SQLException;

    public abstract ArrayList<String> getDatabases(String databaseName);

    public abstract String generateCreateScript(String tableName, String databaseName);

    public abstract String generateInsertScript(String tableName, String databaseName);

    public abstract void deleteTable(String databaseName, String tableName) throws SQLException;

    public abstract void createDatabase(String databaseName) throws SQLException;

    public abstract void deleteDatabase(String databaseName) throws SQLException;

    public abstract void useDatabase(String databaseName) throws SQLException;

    public abstract double getTableSize(String database, String table) throws SQLException;

    /**
     * Other objects of a container that the tree lists next to its tables, by group title ("Views", "Sequences", "Indexes", ...),
     * in display order. Empty groups are left out; engines without support return nothing. A group titled "Views" or
     * "Materialized views" holds names that can be queried like a table (the page opens them with a SELECT); in SQL Server
     * they are {@code schema.name}, like its tables.
     */
    public Map<String, List<String>> getSchemaObjects(String databaseName) {
        return new LinkedHashMap<>();
    }

    /** The first column of a query as a list of names, with the given text parameters bound in order (for the lists of views and the like). */
    protected List<String> listNames(String sql, String... parameters) throws SQLException {
        List<String> names = new ArrayList<>();
        try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    /**
     * The foreign keys of one table from JDBC metadata, one entry per constraint with every column pair in key order
     * (a plain map keyed by constraint name that kept the last row would turn a composite key into its last column).
     * {@code qualifyReference} names the referenced table {@code schema.table} (SQL Server, whose tables are listed that way).
     */
    protected Map<String, ForeignKeyData> importedKeys(String catalog, String schema, String table, boolean qualifyReference) throws SQLException {
        Map<String, TreeMap<Integer, String[]>> rows = new LinkedHashMap<>(); // per key: KEY_SEQ -> {local column, referenced column}
        Map<String, ForeignKeyData> heads = new LinkedHashMap<>();
        try (ResultSet rs = connection.getMetaData().getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String local = rs.getString("FKCOLUMN_NAME");
                String name = rs.getString("FK_NAME");
                if (name == null || name.isEmpty()) {
                    name = "fk_" + table + "_" + local; // an unnamed key: its first column is as good as a name
                }
                rows.computeIfAbsent(name, k -> new TreeMap<>()).put(rs.getInt("KEY_SEQ"), new String[] { local, rs.getString("PKCOLUMN_NAME") });
                if (!heads.containsKey(name)) {
                    String referenceSchema = rs.getString("PKTABLE_SCHEM");
                    String referenceTable = rs.getString("PKTABLE_NAME");
                    if (qualifyReference && referenceSchema != null && !referenceSchema.isEmpty()) {
                        referenceTable = referenceSchema + "." + referenceTable;
                    }
                    ForeignKeyData fk = new ForeignKeyData(name, local, referenceTable, rs.getString("PKCOLUMN_NAME"),
                            rs.getInt("DELETE_RULE"), rs.getInt("UPDATE_RULE"));
                    fk.setReferenceCatalog(rs.getString("PKTABLE_CAT"));
                    heads.put(name, fk);
                }
            }
        }
        for (Map.Entry<String, ForeignKeyData> entry : heads.entrySet()) {
            ForeignKeyData fk = entry.getValue();
            List<String> locals = new ArrayList<>();
            List<String> references = new ArrayList<>();
            for (String[] pair : rows.get(entry.getKey()).values()) {
                locals.add(pair[0]);
                references.add(pair[1]);
            }
            fk.setLocalColumns(locals);
            fk.setReferenceColumns(references);
            fk.setLocalColumn(locals.get(0));
            fk.setReferenceColumn(references.get(0));
        }
        return heads;
    }

    /** Adds a group to the tree's object list when it has anything in it. */
    protected static void putIfAny(Map<String, List<String>> objects, String title, List<String> names) {
        if (names != null && !names.isEmpty()) {
            objects.put(title, names);
        }
    }

    /**
     * Partitions of the tables of a container, by table name (only partitioned tables appear). One catalog query per
     * container. Engines without partitions return nothing.
     */
    public Map<String, List<PartitionData>> getPartitions(String databaseName) {
        return new LinkedHashMap<>();
    }

    /** What the tree calls this engine's containers: "database" or "schema". */
    public String containerKind() {
        return "database";
    }

    /** The containers to list when the connection is opened. */
    public List<String> listContainers(String configuredDatabase) {
        return getDatabases(configuredDatabase);
    }

    /**
     * Selects the container the connection was configured with, so unqualified names mean something, and returns the one
     * now in use (null when none was configured or it could not be selected).
     */
    public String selectInitialContainer(String configuredDatabase) {
        if (configuredDatabase == null || configuredDatabase.isEmpty()) {
            return null;
        }
        try {
            useDatabase(configuredDatabase);
            return configuredDatabase;
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not select the configured database: {}", e.getMessage());
            return null;
        }
    }

    /** The container the connection is using now (catalog or schema); used after a script that may have switched it. */
    public String currentContainer() throws SQLException {
        return connection.getCatalog();
    }

    /**
     * Databases with their schemas, tables and columns, for engines that can list the whole server when no database is
     * configured (PostgreSQL). Everyone else, and a configured connection, returns null.
     */
    public Map<String, Map<String, Map<String, ArrayList<ColumnData>>>> getCompleteHierarchy(String configuredDatabase) {
        return null;
    }

    /** True when the engine's partitions are tables of their own (PostgreSQL), so the tree nests them instead of listing them twice. */
    public boolean partitionsAreTables() {
        return false;
    }

    /** True when a table name could mean tables of several attached databases (SQLite), so the result can't tell which one it came from. */
    public boolean isTableReferenceAmbiguous(String catalog, String schema, String table) {
        return false;
    }

    /**
     * Where the container lives in JDBC metadata: {catalog, schema}. Engines whose container is a database (MySQL,
     * MariaDB, SQL Server) use the catalog; the ones where it is a schema (PostgreSQL, H2) override this.
     */
    protected String[] metadataScope(String container) {
        return new String[] { container, null };
    }

    /** The indexes of one table from JDBC metadata, columns in key order. Engines with a better source override this. */
    public List<IndexData> getIndexes(String container, String table) throws SQLException {
        String[] scope = metadataScope(container);
        DatabaseMetaData meta = connection.getMetaData();
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(scope[0], scope[1], table)) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        }
        Map<String, TreeMap<Integer, String>> columns = new LinkedHashMap<>();
        Map<String, Boolean> unique = new java.util.HashMap<>();
        Map<String, String> types = new java.util.HashMap<>();
        try (ResultSet rs = meta.getIndexInfo(scope[0], scope[1], table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (name == null || column == null) {
                    continue; // table statistics row, or an expression column
                }
                columns.computeIfAbsent(name, k -> new TreeMap<>()).put(rs.getInt("ORDINAL_POSITION"), column);
                unique.put(name, !rs.getBoolean("NON_UNIQUE"));
                types.put(name, switch (rs.getShort("TYPE")) {
                    case DatabaseMetaData.tableIndexHashed -> "HASH";
                    case DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
                    default -> null;
                });
            }
        }
        List<IndexData> out = new ArrayList<>();
        columns.forEach((name, cols) -> {
            List<String> list = new ArrayList<>(cols.values());
            boolean primary = unique.get(name) && !pkColumns.isEmpty() && pkColumns.size() == list.size() && pkColumns.containsAll(list);
            out.add(IndexData.builder().name(name).unique(unique.get(name)).primary(primary).type(types.get(name)).columns(list).build());
        });
        return out;
    }

    // Reader code reads naturally with the dialect's quoting; these keep it from spelling out `dialect.` everywhere.
    protected String quoteIdentifier(String identifier) {
        return dialect.quoteIdentifier(identifier);
    }

    protected String qualifyTable(String catalog, String schema, String table) {
        return dialect.qualifyTable(catalog, schema, table);
    }
}
