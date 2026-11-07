package io.github.b077as.queryexe.connection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The SSH server a connection is reached through. Everything but {@code secret} is written to {@code connections.json};
 * the secret (the SSH password, or the passphrase of the private key) lives in the credential store.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SshConfig {

    public static final String PASSWORD = "password";
    public static final String KEY = "key";

    private String host;
    private int port;
    private String user;
    /** {@link #PASSWORD} or {@link #KEY}. */
    private String auth;
    /** Path of the private key file, for {@link #KEY}. */
    private String keyPath;
    /** Not persisted in the JSON file. */
    private transient String secret;
}
