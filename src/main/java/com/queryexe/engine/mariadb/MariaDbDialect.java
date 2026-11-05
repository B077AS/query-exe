package com.queryexe.engine.mariadb;


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

public class MariaDbDialect extends MySqlDialect {



    private String[] dataTypes = new String[]{
            "INT", "VARCHAR(255)", "DECIMAL", "BINARY(8)", "BLOB", "LONGBLOB",
            "MEDIUMBLOB", "TINYBLOB", "VARBINARY(255)", "DATE", "DATETIME", "TIME",
            "TIMESTAMP", "YEAR", "GEOMETRY", "GEOMETRYCOLLECTION", "LINESTRING",
            "MULTILINESTRING", "MULTIPOINT", "MULTIPOLYGON", "POINT", "POLYGON",
            "BIGINT", "DOUBLE", "FLOAT", "MEDIUMINT", "REAL",
            "SMALLINT", "TINYINT", "CHAR", "JSON", "NCHAR", "NVARCHAR(255)",
            "VARCHAR", "LONGTEXT", "MEDIUMTEXT", "TEXT", "TINYTEXT", "BIT",
            "BOOLEAN", "ENUM", "SET"
    };

    @Override
    public String[] getDataTypes() {
        return this.dataTypes;
    }
}
