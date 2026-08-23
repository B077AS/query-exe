package com.queryexe.service;

import lombok.extern.slf4j.Slf4j;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionTypes;

@Slf4j
public class SQLFormatterUtils {

    public static String format(String sql, ConnectionObject connectionObject) {
        if (sql == null || sql.isBlank() || sql.stripLeading().startsWith("-- ERROR:")) {
            return sql;
        }

        try {
            Dialect dialect = dialectFor(connectionObject);
            return SqlFormatter.of(dialect).format(sql);
        } catch (Exception e) {
            log.warn("Failed to format generated SQL, returning it unformatted", e);
            return sql;
        }
    }

    public static Dialect dialectFor(ConnectionObject connectionObject) {
        if (connectionObject == null || connectionObject.getDbType() == null) {
            return Dialect.StandardSql;
        }

        ConnectionTypes type;
        try {
            type = ConnectionTypes.valueOf(connectionObject.getDbType());
        } catch (IllegalArgumentException e) {
            return Dialect.StandardSql;
        }

        return switch (type) {
            case MySQL -> Dialect.MySql;
            case MariaDB -> Dialect.MariaDb;
            case PostgreSQL -> Dialect.PostgreSql;
            case SQLServer -> Dialect.TSql;
            case H2, SQLite -> Dialect.StandardSql;
        };
    }
}
