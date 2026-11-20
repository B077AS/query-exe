package io.github.b077as.queryexe.connection;

import io.github.b077as.jolt.credentials.CredentialStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Connection passwords, kept in the operating system's keystore through Jolt's {@link CredentialStore} (Windows Credential Manager,
 * the Secret Service on Linux), never in {@code connections.json}. Keyed by connection id. Every call is a no-op or returns null when
 * the system offers no keystore.
 */
@Slf4j
public class PasswordVault {

    /** The namespace of QueryExe's secrets in the keystore (the application id). */
    private static final String SERVICE = "com.queryexe.app";
    private static final String ACCOUNT_PREFIX = "connection.";

    private CredentialStore store;

    public PasswordVault() {
        try {
            this.store = CredentialStore.open(SERVICE);
            log.info("Secure credential storage initialized successfully");
        } catch (RuntimeException e) {
            // CredentialStoreUnavailableException: no libsecret / Secret Service, or an unsupported system.
            log.warn("No secure credential storage available: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return store != null;
    }

    /** Stores or replaces the password of a connection. An empty password stores nothing. */
    public void store(String connectionId, String password) {
        if (store == null || password == null || password.isEmpty()) {
            return;
        }
        try {
            store.put(account(connectionId), password);
        } catch (RuntimeException e) {
            log.error("Error storing password for connection {}", connectionId, e);
        }
    }

    /** The stored password, or null when there is none. */
    public String retrieve(String connectionId) {
        if (store == null) {
            return null;
        }
        try {
            Optional<String> password = store.get(account(connectionId));
            return password.orElse(null);
        } catch (RuntimeException e) {
            log.error("Error retrieving password for connection {}", connectionId, e);
            return null;
        }
    }

    public boolean has(String connectionId) {
        return retrieve(connectionId) != null;
    }

    public void forget(String connectionId) {
        if (store == null) {
            return;
        }
        try {
            store.delete(account(connectionId));
        } catch (RuntimeException e) {
            log.error("Error deleting password for connection {}", connectionId, e);
        }
    }

    private String account(String connectionId) {
        return ACCOUNT_PREFIX + connectionId;
    }
}
