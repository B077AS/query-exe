package com.queryexe.model.connections;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;
import com.queryexe.model.data.RowTarget;
import com.queryexe.model.data.DetailedColumnData;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.drivers.DriverInfo;
import com.queryexe.model.data.ForeignKeyData;

@Data
public abstract class ConnectionObject {

    private String id;
    private String connectionName;
    private String dbType;
    private String baseUrl;
    private String host;
    private String port;
    private String databaseName;
    private String username;
    private String password;
    private String fullUrl;
    private boolean customJdbc;
    private DriverInfo driverInfo;
    /** Home page folder this connection is filed under, or null when ungrouped. */
    private String folder;
    private boolean pinned;


    public ConnectionObject(String id, String connectionName, String dbType, String baseUrl, String host, String port,
                            String databaseName, String username, String password, DriverInfo driverInfo) {
        super();
        this.id = id;
        this.connectionName = connectionName;
        this.dbType = dbType;
        this.baseUrl = baseUrl;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.fullUrl = String.format(baseUrl, host, port, databaseName);
        this.driverInfo = driverInfo;
        this.customJdbc = false;
    }

    public ConnectionObject(String id, String connectionName, String dbType, String url, String username, String password, DriverInfo driverInfo) {
        super();
        this.id = id;
        this.connectionName = connectionName;
        this.dbType = dbType;
        this.username = username;
        this.password = password;
        this.fullUrl = url;
        this.driverInfo = driverInfo;
        this.customJdbc = true;
    }

    public abstract String[] getDataTypes();

    public abstract Map<String, ArrayList<ColumnData>> getAllTablesAndColumns(String databaseName);

    public abstract ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException;

    public abstract ArrayList<ColumnData> getColumnsForTable(String databaseName, String tableName) throws SQLException;

    public abstract DetailedColumnData getDetailedColumnInfo(String schemaName, String tableName, String columnName) throws SQLException;

    public abstract Map<String, ForeignKeyData> extractForeignKeys(String databaseName, String tableName) throws SQLException;

    public abstract ArrayList<String> getDatabases(String databaseName);

    public abstract String generateCreateScript(String tableName, String databaseName);

    public abstract String generateInsertScript(String tableName, String databaseName);

    public abstract String generateSelectScript(String tableName, String databaseName, int limit);

    public abstract String generateRowInsertScript(List<String> row, RowTarget table);

    public abstract String generateCreateTableSQL(String databaseName, String tableName, List<ColumnData> columns, List<ForeignKeyData> foreignKeys);

    public abstract String generateAlterTableSQL(String databaseName, String oldTableName, String newTableName,
                                                 List<ColumnData> oldColumns, List<ColumnData> newColumns,
                                                 Map<String, ForeignKeyData> oldForeignKeys, List<ForeignKeyData> newForeignKeys, Map<String, String> columnRenames) throws SQLException;

    public abstract void deleteTable(String databaseName, String tableName) throws SQLException;

    public abstract void createDatabase(String databaseName) throws SQLException;

    public abstract void deleteDatabase(String databaseName) throws SQLException;

    public abstract ArrayList<String> getUsers() throws SQLException;

    public abstract void useDatabase(String databaseName) throws SQLException;

    public abstract double getTableSize(String database, String table) throws SQLException;

    public abstract SqlSyntax getSqlSyntax();

    /** What the server monitor reads for this engine, or null when there is nothing to monitor (SQLite is a file, not a server). */
    public com.queryexe.model.monitor.ServerMonitor createMonitor() {
        return null;
    }


    /**
     * Where the tree's "container" lives in JDBC metadata: {catalog, schema}. Engines whose container is a database
     * (MySQL, MariaDB, SQL Server) use the catalog; the ones where it is a schema (PostgreSQL, H2) override this.
     */
    protected String[] metadataScope(String container) {
        return new String[] { container, null };
    }

    /**
     * The indexes of one table, from JDBC metadata, columns in key order. Engines with a better source override this.
     */
    public java.util.List<com.queryexe.model.data.IndexData> getIndexes(String container, String table) throws java.sql.SQLException {
        String[] scope = metadataScope(container);
        java.sql.DatabaseMetaData meta = com.queryexe.service.DatabaseConnection.getInstance().getConnection().getMetaData();
        java.util.Set<String> pkColumns = new java.util.HashSet<>();
        try (java.sql.ResultSet rs = meta.getPrimaryKeys(scope[0], scope[1], table)) {
            while (rs.next()) pkColumns.add(rs.getString("COLUMN_NAME"));
        }
        java.util.Map<String, java.util.TreeMap<Integer, String>> columns = new java.util.LinkedHashMap<>();
        java.util.Map<String, Boolean> unique = new java.util.HashMap<>();
        java.util.Map<String, String> types = new java.util.HashMap<>();
        try (java.sql.ResultSet rs = meta.getIndexInfo(scope[0], scope[1], table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (name == null || column == null) continue; // table statistics row, or an expression column
                columns.computeIfAbsent(name, k -> new java.util.TreeMap<>()).put(rs.getInt("ORDINAL_POSITION"), column);
                unique.put(name, !rs.getBoolean("NON_UNIQUE"));
                types.put(name, switch (rs.getShort("TYPE")) {
                    case java.sql.DatabaseMetaData.tableIndexHashed -> "HASH";
                    case java.sql.DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
                    default -> null;
                });
            }
        }
        java.util.List<com.queryexe.model.data.IndexData> out = new java.util.ArrayList<>();
        columns.forEach((name, cols) -> {
            java.util.List<String> list = new java.util.ArrayList<>(cols.values());
            boolean primary = unique.get(name) && !pkColumns.isEmpty() && pkColumns.size() == list.size() && pkColumns.containsAll(list);
            out.add(com.queryexe.model.data.IndexData.builder().name(name).unique(unique.get(name)).primary(primary)
                    .type(types.get(name)).columns(list).build());
        });
        return out;
    }
    /**
     * Other objects of a database/schema that the tree lists next to its tables, by group title ("Sequences",
     * "Indexes", ...), in display order. Empty groups are left out; engines without support return nothing.
     */
    public java.util.Map<String, java.util.List<String>> getSchemaObjects(String databaseName) {
        return new java.util.LinkedHashMap<>();
    }

    /**
     * Partitions of the tables of a database/schema, by table name (only partitioned tables appear). One catalog
     * query per container. Engines without partitions return nothing.
     */
    public java.util.Map<String, java.util.List<com.queryexe.model.data.PartitionData>> getPartitions(String databaseName) {
        return new java.util.LinkedHashMap<>();
    }

    /** True when the engine's partitions are tables of their own (PostgreSQL), so the tree nests them instead of listing them twice. */
    public boolean partitionsAreTables() {
        return false;
    }

    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public String qualifyTable(String catalog, String schema, String table) {
        if (schema != null && !schema.isEmpty()) {
            return quoteIdentifier(schema) + "." + quoteIdentifier(table);
        }
        return quoteIdentifier(table);
    }

    public boolean isTableReferenceAmbiguous(String catalog, String schema, String table) {
        return false;
    }

    public String generateDefaultsOnlyInsert(String qualifiedTable) {
        return "INSERT INTO " + qualifiedTable + " DEFAULT VALUES";
    }

    public String getSimpleURL() {
        if (databaseName == null || databaseName.isBlank()) {
            return host + ":" + port;
        } else {
            return host + ":" + port + "/" + databaseName;
        }
    }
}