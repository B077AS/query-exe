package io.github.b077as.queryexe.connection;

import com.microsoft.credentialstorage.SecretStore;
import com.microsoft.credentialstorage.StorageProvider;
import com.microsoft.credentialstorage.StorageProvider.SecureOption;
import com.microsoft.credentialstorage.model.StoredCredential;
import lombok.extern.slf4j.Slf4j;

/**
 * Connection passwords, kept in the operating system's credential store (never in {@code connections.json}), keyed by
 * connection id. Every call is a no-op or returns null when the OS offers no secure store.
 */
@Slf4j
public class PasswordVault {

    private static final String KEY_PREFIX = "QueryExe_Connection_";

    private SecretStore<StoredCredential> store;

    public PasswordVault() {
        this.store = StorageProvider.getCredentialStorage(true, SecureOption.REQUIRED);
        if (store == null) {
            log.warn("No secure credential storage available!");
        } else {
            log.info("Secure credential storage initialized successfully");
        }
    }

    public boolean isAvailable() {
        return store != null;
    }

    /** Stores or replaces the password of a connection. An empty password stores nothing. */
    public void store(String connectionId, String username, String password) {
        if (store == null || password == null || password.isEmpty()) {
            return;
        }
        try {
            store.add(key(connectionId), new StoredCredential(username, password.toCharArray()));
        } catch (Exception e) {
            log.error("Error storing password for connection {}", connectionId, e);
        }
    }

    /** The stored password, or null when there is none. */
    public String retrieve(String connectionId) {
        if (store == null) {
            return null;
        }
        try {
            StoredCredential credential = store.get(key(connectionId));
            if (credential != null) {
                char[] password = credential.getPassword();
                return password != null ? new String(password) : null;
            }
        } catch (Exception e) {
            log.error("Error retrieving password for connection {}", connectionId, e);
        }
        return null;
    }

    public boolean has(String connectionId) {
        return retrieve(connectionId) != null;
    }

    public void forget(String connectionId) {
        if (store == null) {
            return;
        }
        try {
            store.delete(key(connectionId));
        } catch (Exception e) {
            log.error("Error deleting password for connection {}", connectionId, e);
        }
    }

    private String key(String connectionId) {
        return KEY_PREFIX + connectionId;
    }
}
