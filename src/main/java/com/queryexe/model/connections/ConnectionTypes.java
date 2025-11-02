package com.queryexe.model.connections;

import lombok.Getter;
import com.queryexe.model.drivers.DriverInfo;

@Getter
public enum ConnectionTypes {

    MariaDB(
            "com.queryexe.model.connections.MariaDBConnection",
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
            "com.queryexe.model.connections.MySQLConnection",
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
            "com.queryexe.model.connections.PostgresConnection",
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
            "com.queryexe.model.connections.SQLServerConnection",
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
            "com.queryexe.model.connections.H2Connection",
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
            "com.queryexe.model.connections.SQLiteConnection",
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

    private final String className;
    private final String baseUrl;
    private final String defaultPort;
    private final DatabaseStructure mode;
    private final DriverInfo defaultDriverInfo;
    private final String mavenGroupId;
    private final String mavenArtifactId;
    private final String jdbcPrefix;

    ConnectionTypes(String className, String baseUrl, String defaultPort,
                    DatabaseStructure mode, DriverInfo defaultDriverInfo,
                    String mavenGroupId, String mavenArtifactId, String jdbcPrefix) {
        this.className = className;
        this.baseUrl = baseUrl;
        this.defaultPort = defaultPort;
        this.mode = mode;
        this.defaultDriverInfo = defaultDriverInfo;
        this.mavenGroupId = mavenGroupId;
        this.mavenArtifactId = mavenArtifactId;
        this.jdbcPrefix = jdbcPrefix;
    }

    public boolean isServerBased() {
        return mode == DatabaseStructure.SERVER;
    }

    public boolean isFileBased() {
        return mode == DatabaseStructure.FILE;
    }

    public enum DatabaseStructure {
        SERVER,
        FILE
    }
}