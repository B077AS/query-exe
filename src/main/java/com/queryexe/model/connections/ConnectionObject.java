package com.queryexe.model.connections;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import com.queryexe.model.data.DetailedColumnData;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.TableRowData;
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

    public abstract String[] getKEYWORDS();

    public abstract String[] getDataTypes();

    public abstract Map<String, ArrayList<ColumnData>> getAllTablesAndColumns(String databaseName);

    public abstract ArrayList<String> getTablesForDatabase(String databaseName) throws SQLException;

    public abstract ArrayList<ColumnData> getColumnsForTable(String databaseName, String tableName) throws SQLException;

    public abstract DetailedColumnData getDetailedColumnInfo(String schemaName, String tableName, String columnName) throws SQLException;

    public abstract Map<String, ForeignKeyData> extractForeignKeys(String databaseName, String tableName) throws SQLException;

    public abstract ArrayList<String> getDatabases(String databaseName);

    public abstract String generateCreateScript(String tableName, String databaseName);

    public abstract String generateInsertScript(String tableName, String databaseName);

    public abstract String generateRowInsertScript(ObservableList<String> row, TableCell<TableRowData, String> cell);

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

    public String getSimpleURL() {
        if (databaseName == null || databaseName.isBlank()) {
            return host + ":" + port;
        } else {
            return host + ":" + port + "/" + databaseName;
        }
    }
}