package com.queryexe.engine.mariadb;

import com.queryexe.connection.ConnectionProfile;


import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

import com.queryexe.query.RowTarget;
import com.queryexe.schema.DetailedColumnData;
import com.queryexe.schema.ColumnData;
import com.queryexe.schema.PartitionData;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.connection.driver.DriverInfo;
import com.queryexe.schema.ForeignKeyData;


import com.queryexe.engine.*;
import com.queryexe.engine.mysql.*;

@lombok.extern.slf4j.Slf4j
public class MariaDbReader extends MySqlReader {


    public MariaDbReader(java.sql.Connection connection, ConnectionProfile profile) {
        super(connection, profile);
    }

    @Override
    public ArrayList<String> getDatabases(String name) {
        ArrayList<String> databases = new ArrayList<String>();
        try {
            String query = "SELECT SCHEMA_NAME \n"
                    + "FROM information_schema.SCHEMATA\n"
                    + "WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')\n"
                    + "ORDER BY \n"
                    + "    CASE WHEN SCHEMA_NAME = ? THEN 0 ELSE 1 END,\n"
                    + "    SCHEMA_NAME;";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, name);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                databases.add(result.getString("SCHEMA_NAME"));
            }

            statement.close();
            result.close();
            return databases;
        } catch (SQLException e) {
            log.error("getDatabases failed", e);
        }
        return databases;
    }
}
