package io.github.b077as.queryexe.connection;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SshTunnelTest {

    @Test
    void findsTheDatabaseHostInJdbcUrls() throws SQLException {
        assertArrayEquals(new String[]{"db.internal", "5433"}, SshTunnel.target("jdbc:postgresql://db.internal:5433/app?ssl=true", "5432"));
        assertArrayEquals(new String[]{"db.internal", "3306"}, SshTunnel.target("jdbc:mysql://db.internal/app", "3306"));
        assertArrayEquals(new String[]{"10.0.0.5", "1433"}, SshTunnel.target("jdbc:sqlserver://10.0.0.5:1433;databaseName=x", "1433"));
        assertArrayEquals(new String[]{"h", "9092"}, SshTunnel.target("jdbc:h2:tcp://h:9092/~/test", "9092"));
        assertThrows(SQLException.class, () -> SshTunnel.target("jdbc:sqlite:/tmp/x.db", "0"));
    }
}
