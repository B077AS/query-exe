package io.github.b077as.queryexe.connection;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.github.b077as.queryexe.Launcher;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An SSH session with one local port forwarded to the database host as the SSH server sees it. The JDBC URL of the
 * connection is pointed at {@code 127.0.0.1:<localPort>} (see {@link #rewrite}). Host keys are remembered in
 * {@code ssh/known_hosts} in the app data folder on first use and checked afterwards.
 */
@Slf4j
public class SshTunnel implements AutoCloseable {

    private static final Pattern AUTHORITY = Pattern.compile("//([^/:?;,]+)(?::(\\d+))?");

    private Session session;
    private int localPort;

    /** The database host and port inside a JDBC URL ({@code //host[:port]}); {@code defaultPort} when the URL has none. */
    public static String[] target(String jdbcUrl, String defaultPort) throws SQLException {
        Matcher m = jdbcUrl == null ? null : AUTHORITY.matcher(jdbcUrl);
        if (m == null || !m.find()) {
            throw new SQLException("An SSH tunnel needs a JDBC URL with a host (jdbc:...://host:port/...).");
        }
        return new String[]{m.group(1), m.group(2) != null ? m.group(2) : defaultPort};
    }

    public static SshTunnel open(SshConfig ssh, String targetHost, int targetPort) throws SQLException {
        SshTunnel tunnel = new SshTunnel();
        try {
            tunnel.connect(ssh, targetHost, targetPort);
        } catch (JSchException | IOException e) {
            tunnel.close();
            throw new SQLException("SSH connection failed: " + e.getMessage(), e);
        }
        return tunnel;
    }

    private void connect(SshConfig ssh, String targetHost, int targetPort) throws JSchException, IOException {
        if (ssh.getHost() == null || ssh.getHost().isBlank() || ssh.getUser() == null || ssh.getUser().isBlank()) {
            throw new JSchException("SSH host and user are required.");
        }
        JSch jsch = new JSch();
        Path dir = Launcher.getAppDataDirectory().resolve("ssh");
        Files.createDirectories(dir);
        Path knownHosts = dir.resolve("known_hosts");
        if (!Files.exists(knownHosts)) {
            Files.createFile(knownHosts);
        }
        jsch.setKnownHosts(knownHosts.toString());

        String secret = ssh.getSecret() == null ? "" : ssh.getSecret();
        boolean key = SshConfig.KEY.equals(ssh.getAuth());
        if (key) {
            if (ssh.getKeyPath() == null || ssh.getKeyPath().isBlank()) {
                throw new JSchException("Choose the private key file.");
            }
            if (secret.isEmpty()) {
                jsch.addIdentity(ssh.getKeyPath());
            } else {
                jsch.addIdentity(ssh.getKeyPath(), secret);
            }
        } else if (secret.isEmpty()) {
            throw new JSchException("No SSH password given.");
        }

        session = jsch.getSession(ssh.getUser(), ssh.getHost(), ssh.getPort() > 0 ? ssh.getPort() : 22);
        session.setConfig("StrictHostKeyChecking", "accept-new");
        session.setConfig("PreferredAuthentications", key ? "publickey" : "password,keyboard-interactive");
        if (!key) {
            session.setPassword(secret);
        }
        session.setServerAliveInterval(30_000);
        session.connect(15_000);
        localPort = session.setPortForwardingL("127.0.0.1", 0, targetHost, targetPort);
        log.info("SSH tunnel to {}:{} through {} on local port {}", targetHost, targetPort, ssh.getHost(), localPort);
    }

    /** {@code jdbcUrl} with its host and port replaced by the local end of the tunnel. */
    public String rewrite(String jdbcUrl) {
        return AUTHORITY.matcher(jdbcUrl).replaceFirst("//127.0.0.1:" + localPort);
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    @Override
    public void close() {
        if (session != null) {
            session.disconnect();
            session = null;
        }
    }
}
