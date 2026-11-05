package com.queryexe.connection;

import com.queryexe.monitor.ServerMonitor;

import lombok.Getter;
import com.queryexe.engine.Dialect;
import com.queryexe.engine.SchemaReader;
import com.queryexe.engine.h2.*;
import com.queryexe.engine.mariadb.*;
import com.queryexe.engine.mysql.*;
import com.queryexe.engine.postgres.*;
import com.queryexe.engine.sqlite.*;
import com.queryexe.engine.sqlserver.*;
import com.queryexe.connection.driver.DriverInfo;

import java.sql.Connection;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Getter
public enum ConnectionTypes {

    MariaDB(
            new MariaDbDialect(), MariaDbReader::new, () -> new MySqlMonitor(true),
            "jdbc:mariadb://%s:%s/%s",
            "3306",
            DatabaseStructure.SERVER,
            new DriverInfo(
                    "MariaDB",
                    "3.5.6",
                    "org.mariadb.jdbc.Driver",
                    "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.5.0/mariadb-java-client-3.5.6.jar",
                    "mariadb-java-client-3.5.6.jar"
            ),
            "org.mariadb.jdbc",
            "mariadb-java-client",
            "jdbc:mariadb:"
    ),
    MySQL(
            new MySqlDialect(), MySqlReader::new, () -> new MySqlMonitor(false),
            "jdbc:mysql://%s:%s/%s",
            "3306",
            DatabaseStructure.SERVER,
            new DriverInfo(
                    "MySQL",
                    "9.4.0",
                    "com.mysql.cj.jdbc.Driver",
                    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar",
                    "mysql-connector-j-9.4.0.jar"
            ),
            "com.mysql",
            "mysql-connector-j",
            "jdbc:mysql:"
    ),
    PostgreSQL(
            new PostgresDialect(), PostgresReader::new, PostgresMonitor::new,
            "jdbc:postgresql://%s:%s/%s",
            "5432",
            DatabaseStructure.SERVER,
            new DriverInfo(
                    "PostgreSQL",
                    "42.7.4",
                    "org.postgresql.Driver",
                    "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar",
                    "postgresql-42.7.4.jar"
            ),
            "org.postgresql",
            "postgresql",
            "jdbc:postgresql:"
    ),
    SQLServer(
            new SqlServerDialect(), SqlServerReader::new, SqlServerMonitor::new,
            "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=false;SelectMethod=cursor",
            "1433",
            DatabaseStructure.SERVER,
            new DriverInfo(
                    "SQLServer",
                    "12.8.1.jre11",
                    "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                    "https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.8.1.jre11/mssql-jdbc-12.8.1.jre11.jar",
                    "mssql-jdbc-12.8.1.jre11.jar"
            ),
            "com.microsoft.sqlserver",
            "mssql-jdbc",
            "jdbc:sqlserver:"
    ),
    H2(
            new H2Dialect(), H2Reader::new, H2Monitor::new,
            null,
            null,
            DatabaseStructure.FILE,
            new DriverInfo(
                    "H2",
                    "2.4.240",
                    "org.h2.Driver",
                    "https://repo1.maven.org/maven2/com/h2database/h2/2.4.240/h2-2.4.240.jar",
                    "h2-2.4.240.jar"
            ),
            "com.h2database",
            "h2",
            "jdbc:h2:"
    ),
    SQLite(
            new SqliteDialect(), SqliteReader::new, () -> null,
            null,
            null,
            DatabaseStructure.FILE,
            new DriverInfo(
                    "SQLite",
                    "3.50.3.0",
                    "org.sqlite.JDBC",
                    "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.50.3.0/sqlite-jdbc-3.50.3.0.jar",
                    "sqlite-jdbc-3.50.3.0.jar"
            ),
            "org.xerial",
            "sqlite-jdbc",
            "jdbc:sqlite:"
    );

    private Dialect dialect;
    private BiFunction<Connection, ConnectionProfile, SchemaReader> readerFactory;
    private Supplier<ServerMonitor> monitorFactory;
    private String baseUrl;
    private String defaultPort;
    private DatabaseStructure mode;
    private DriverInfo defaultDriverInfo;
    private String mavenGroupId;
    private String mavenArtifactId;
    private String jdbcPrefix;

    ConnectionTypes(Dialect dialect, BiFunction<Connection, ConnectionProfile, SchemaReader> readerFactory,
                    Supplier<ServerMonitor> monitorFactory, String baseUrl, String defaultPort,
                    DatabaseStructure mode, DriverInfo defaultDriverInfo,
                    String mavenGroupId, String mavenArtifactId, String jdbcPrefix) {
        this.dialect = dialect;
        this.readerFactory = readerFactory;
        this.monitorFactory = monitorFactory;
        this.baseUrl = baseUrl;
        this.defaultPort = defaultPort;
        this.mode = mode;
        this.defaultDriverInfo = defaultDriverInfo;
        this.mavenGroupId = mavenGroupId;
        this.mavenArtifactId = mavenArtifactId;
        this.jdbcPrefix = jdbcPrefix;
    }

    /** The reader for this engine on a JDBC connection that belongs to {@code profile}. */
    public SchemaReader reader(Connection connection, ConnectionProfile profile) {
        return readerFactory.apply(connection, profile);
    }

    /** What the server monitor reads for this engine, or null when there is nothing to monitor (SQLite is a file, not a server). */
    public ServerMonitor createMonitor() {
        return monitorFactory.get();
    }

    public boolean isFileBased() {
        return mode == DatabaseStructure.FILE;
    }

    public enum DatabaseStructure {
        SERVER,
        FILE
    }
}
