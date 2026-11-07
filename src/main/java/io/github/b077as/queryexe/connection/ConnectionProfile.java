package io.github.b077as.queryexe.connection;

import io.github.b077as.queryexe.engine.Dialect;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A saved connection: what the user typed into the connection dialog, nothing more. It knows no SQL and holds no JDBC
 * state; the engine it belongs to (see {@link ConnectionTypes}) supplies the {@link Dialect} and the schema reader.
 * Gson writes these fields to {@code connections.json}, so renaming one changes the file format.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionProfile {

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
    /** The SSH server the database is reached through, or null for a direct connection. */
    private SshConfig ssh;

    /** A profile built from host, port and database: the JDBC URL is made from {@code baseUrl}. */
    public static ConnectionProfile server(String id, String connectionName, String dbType, String baseUrl, String host, String port,
                                           String databaseName, String username, String password, DriverInfo driverInfo) {
        return ConnectionProfile.builder().id(id).connectionName(connectionName).dbType(dbType).baseUrl(baseUrl).host(host).port(port)
                .databaseName(databaseName).username(username).password(password).driverInfo(driverInfo)
                .fullUrl(String.format(baseUrl, host, port, databaseName)).customJdbc(false).build();
    }

    /** A profile that uses a JDBC URL typed by the user as it is. */
    public static ConnectionProfile custom(String id, String connectionName, String dbType, String url, String username, String password,
                                           DriverInfo driverInfo) {
        return ConnectionProfile.builder().id(id).connectionName(connectionName).dbType(dbType).username(username).password(password)
                .driverInfo(driverInfo).fullUrl(url).customJdbc(true).build();
    }

    public ConnectionTypes type() {
        return ConnectionTypes.valueOf(dbType);
    }

    public Dialect dialect() {
        return type().getDialect();
    }

    public String getSimpleURL() {
        if (databaseName == null || databaseName.isBlank()) {
            return host + ":" + port;
        }
        return host + ":" + port + "/" + databaseName;
    }
}
