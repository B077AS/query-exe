package io.github.b077as.queryexe.api;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.b077as.queryexe.Launcher;
import io.github.b077as.queryexe.app.Params;
import io.github.b077as.queryexe.app.Rpc;
import io.github.b077as.queryexe.connection.ConnectionProfile;
import io.github.b077as.queryexe.connection.ConnectionProfileDeserializer;
import io.github.b077as.queryexe.connection.ConnectionTypes;
import io.github.b077as.queryexe.connection.SshConfig;
import io.github.b077as.queryexe.connection.driver.DriverAPIs;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import io.github.b077as.queryexe.store.AppSettings;
import io.github.b077as.queryexe.connection.ProfileStore;
import io.github.b077as.queryexe.connection.DatabaseConnection;
import io.github.b077as.queryexe.query.Workspace;

import lombok.extern.slf4j.Slf4j;

/** Saved connections, connecting, and JDBC drivers. */
@Slf4j
class ConnectionApi {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ConnectionProfile.class, new ConnectionProfileDeserializer()).create();
    private static final String FOLDERS = "connections.folders";
    private static final String COLLAPSED = "connections.collapsedFolders";

    private ProfileStore connections = ProfileStore.getInstance();
    private Workspace workspace;
    private Map<String, Boolean> connecting = new ConcurrentHashMap<>();

    ConnectionApi(Workspace workspace) {
        this.workspace = workspace;
    }

    /** Keeps only well-formed colour keys ("teal", "pink", ...); the page decides which ones it knows how to draw. */
    private static Map<String, String> folderColors(Map<String, String> requested) {
        Map<String, String> colors = new LinkedHashMap<>();
        requested.forEach((folder, color) -> {
            if (!folder.isBlank() && color.matches("[a-z]{1,16}")) {
                colors.put(folder, color);
            }
        });
        return colors;
    }

    void register(Rpc rpc) {
        rpc.on("connections.types", p -> types());
        rpc.on("connections.list", p -> list());
        rpc.on("connections.get", p -> {
            // the edit dialog is the only place that sees the stored password; the list never carries it
            ConnectionProfile c = load(Params.string(p, "id"));
            Map<String, Object> out = describe(c);
            out.put("password", c.getPassword() == null ? "" : c.getPassword());
            if (c.getSsh() != null) {
                ((Map<String, Object>) out.get("ssh")).put("password", c.getSsh().getSecret() == null ? "" : c.getSsh().getSecret());
            }
            return out;
        });
        rpc.on("connections.save", this::save);
        rpc.on("connections.test", this::test);
        rpc.on("connections.delete", p -> {
            String id = Params.string(p, "id");
            disconnect(id);
            connections.deleteConnection(id);
            return Map.of("ok", true);
        });
        rpc.on("connections.clone", p -> {
            String id = connections.cloneConnection(Params.string(p, "id"), Params.string(p, "name"));
            return Map.of("id", id);
        });
        rpc.on("connections.setFolder", p -> {
            connections.setConnectionFolder(Params.string(p, "id"), Params.optionalString(p, "folder"));
            return Map.of("ok", true);
        });
        rpc.on("connections.setPinned", p -> {
            connections.setConnectionPinned(Params.string(p, "id"), Params.bool(p, "pinned"));
            return Map.of("ok", true);
        });
        rpc.on("connections.folders", p -> Map.of(
                "folders", AppSettings.get().getStringList(FOLDERS),
                "collapsed", AppSettings.get().getStringList(COLLAPSED),
                "colors", AppSettings.get().getFolderColors()));
        rpc.on("connections.setFolders", p -> {
            AppSettings.get().setStringList(FOLDERS, Params.optionalStrings(p, "folders"));
            AppSettings.get().setStringList(COLLAPSED, Params.optionalStrings(p, "collapsed"));
            AppSettings.get().setFolderColors(folderColors(Params.optionalStringMap(p, "colors")));
            return Map.of("ok", true);
        });
        rpc.on("connections.renameFolder", p -> {
            String to = Params.optionalString(p, "to");
            connections.renameFolder(Params.string(p, "from"), to.isBlank() ? null : to);
            return Map.of("ok", true);
        });
        rpc.on("connections.connect", this::connect);
        rpc.on("connections.disconnect", p -> {
            disconnect(Params.string(p, "id"));
            return Map.of("ok", true);
        });
        rpc.on("connections.reconnect", p -> {
            String id = Params.string(p, "id");
            workspace.closeConnection(id);
            DatabaseConnection.getInstance().reconnect(id);
            return Map.of("ok", true);
        });
        rpc.on("connections.open", p -> new ArrayList<>(DatabaseConnection.getInstance().getAllConnectionIds()));
        // For the active connections list: is each one still answering, and how many tab sessions does it hold.
        rpc.on("connections.status", p -> {
            DatabaseConnection databases = DatabaseConnection.getInstance();
            List<Map<String, Object>> out = new ArrayList<>();
            for (String id : new ArrayList<>(databases.getAllConnectionIds())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", id);
                entry.put("alive", databases.isAlive(id));
                entry.put("sessions", databases.sessionCount(id));
                out.add(entry);
            }
            return out;
        });
        rpc.on("drivers.list", this::drivers);
        rpc.on("drivers.download", this::downloadDriver);
    }

    // ------------------------------------------------------------------------------------ reading

    private static Object types() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConnectionTypes type : ConnectionTypes.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", type.name());
            entry.put("defaultPort", type.getDefaultPort());
            entry.put("fileBased", type.isFileBased());
            entry.put("jdbcPrefix", type.getJdbcPrefix());
            entry.put("driver", driver(type.getDefaultDriverInfo()));
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> driver(DriverInfo info) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", info.getName());
        out.put("version", info.getVersion());
        out.put("driverClass", info.getDriverClass());
        out.put("downloadUrl", info.getDownloadUrl());
        out.put("fileName", info.getFileName());
        return out;
    }

    private Object list() {
        List<Object> out = new ArrayList<>();
        for (ConnectionProfile connection : connections.loadConnections()) {
            out.add(describe(connection));
        }
        return out;
    }

    private ConnectionProfile load(String id) {
        ConnectionProfile connection = connections.loadConnection(id);
        if (connection == null) {
            throw new IllegalArgumentException("This connection no longer exists.");
        }
        return connection;
    }

    private Map<String, Object> describe(ConnectionProfile c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId());
        out.put("name", c.getConnectionName());
        out.put("type", c.getDbType());
        out.put("host", c.getHost());
        out.put("port", c.getPort());
        out.put("database", c.getDatabaseName());
        out.put("username", c.getUsername());
        out.put("url", c.isCustomJdbc() ? c.getFullUrl() : null);
        out.put("target", c.isCustomJdbc() ? c.getFullUrl() : c.getSimpleURL());
        out.put("folder", c.getFolder());
        out.put("pinned", c.isPinned());
        out.put("hasPassword", c.getPassword() != null && !c.getPassword().isEmpty());
        SshConfig ssh = c.getSsh();
        if (ssh != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("host", ssh.getHost());
            s.put("port", String.valueOf(ssh.getPort()));
            s.put("user", ssh.getUser());
            s.put("auth", ssh.getAuth());
            s.put("keyPath", ssh.getKeyPath());
            s.put("hasPassword", ssh.getSecret() != null && !ssh.getSecret().isEmpty());
            out.put("ssh", s);
        } else {
            out.put("ssh", null);
        }
        out.put("connected", DatabaseConnection.getInstance().hasConnection(c.getId()));
        DriverInfo info = c.getDriverInfo();
        boolean custom = info != null && !info.equals(ConnectionTypes.valueOf(c.getDbType()).getDefaultDriverInfo());
        out.put("driver", custom ? driver(info) : null);
        return out;
    }

    // ------------------------------------------------------------------------------------ saving

    /** The JSON a saved connection is stored as, built from the form the page sends. */
    private JsonObject definition(JsonObject p, JsonObject base) {
        ConnectionTypes type = ConnectionTypes.valueOf(Params.string(p, "type"));
        String customUrl = Params.optionalString(p, "url").trim();
        boolean useUrl = type.isFileBased() || !customUrl.isEmpty();
        if (useUrl && customUrl.isEmpty()) {
            throw new IllegalArgumentException("Enter the database location, e.g. " + type.getJdbcPrefix() + "<path>.");
        }
        if (type == ConnectionTypes.SQLServer && useUrl && !customUrl.toLowerCase().contains("selectmethod=cursor")) {
            customUrl += ";SelectMethod=cursor";
        }
        JsonObject out = base == null ? new JsonObject() : base;
        out.addProperty("connectionName", Params.optionalString(p, "name"));
        out.addProperty("dbType", type.name());
        out.addProperty("host", useUrl ? null : orDefault(Params.optionalString(p, "host"), "localhost"));
        out.addProperty("port", useUrl ? null : orDefault(Params.optionalString(p, "port"), type.getDefaultPort()));
        out.addProperty("databaseName", useUrl ? null : Params.optionalString(p, "database").trim());
        out.addProperty("username", Params.optionalString(p, "username"));
        out.addProperty("customUrl", useUrl ? customUrl : null);

        out.remove("ssh");
        out.remove("sshPassword");
        if (p.has("ssh") && p.get("ssh").isJsonObject() && Params.bool(p.getAsJsonObject("ssh"), "enabled")) {
            JsonObject s = p.getAsJsonObject("ssh");
            if (type.isFileBased() && !useUrl) {
                throw new IllegalArgumentException("A file database cannot be reached through SSH.");
            }
            String sshHost = Params.optionalString(s, "host").trim();
            String sshUser = Params.optionalString(s, "user").trim();
            if (sshHost.isEmpty() || sshUser.isEmpty()) {
                throw new IllegalArgumentException("Enter the SSH host and user.");
            }
            String sshPort = orDefault(Params.optionalString(s, "port"), "22");
            if (!sshPort.matches("[0-9]{1,5}")) {
                throw new IllegalArgumentException("The SSH port must be a number.");
            }
            boolean key = SshConfig.KEY.equals(Params.optionalString(s, "auth"));
            String keyPath = Params.optionalString(s, "keyPath").trim();
            if (key && keyPath.isEmpty()) {
                throw new IllegalArgumentException("Choose the SSH private key file.");
            }
            JsonObject ssh = new JsonObject();
            ssh.addProperty("host", sshHost);
            ssh.addProperty("port", sshPort);
            ssh.addProperty("user", sshUser);
            ssh.addProperty("auth", key ? SshConfig.KEY : SshConfig.PASSWORD);
            ssh.addProperty("keyPath", key ? keyPath : null);
            out.add("ssh", ssh);
            String secret = Params.optionalString(s, "password");
            if (!secret.isEmpty()) {
                out.addProperty("sshPassword", secret);
            }
        }

        if (p.has("driver") && p.get("driver").isJsonObject()) {
            JsonObject d = p.getAsJsonObject("driver");
            JsonObject driver = new JsonObject();
            driver.addProperty("name", Params.string(d, "name"));
            driver.addProperty("version", Params.string(d, "version"));
            driver.addProperty("driverClassName", Params.string(d, "driverClass"));
            driver.addProperty("downloadUrl", Params.string(d, "downloadUrl"));
            driver.addProperty("jarFileName", Params.string(d, "fileName"));
            out.add("customDriver", driver);
        } else {
            out.remove("customDriver");
        }
        return out;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Object save(JsonObject p) {
        String given = Params.optionalString(p, "id");
        boolean isNew = given.isBlank();
        String id = isNew ? UUID.randomUUID().toString() : given;
        if (Params.optionalString(p, "name").isBlank()) {
            throw new IllegalArgumentException("Give the connection a name.");
        }
        JsonObject base = isNew ? null : connections.getConnection(id);
        JsonObject json = definition(p, base);

        boolean savePassword = Params.bool(p, "savePassword");
        String password = Params.optionalString(p, "password");
        if (savePassword) {
            if (!password.isEmpty()) {
                json.addProperty("password", password); // moved into the credential store by saveConnection
            }
        } else {
            json.remove("password");
            connections.getVault().forget(id);
            if (ConnectionTypes.SQLite.name().equals(json.get("dbType").getAsString())) {
                json.addProperty("password", "");
            }
        }
        connections.saveConnection(id, json);
        return Map.of("id", id);
    }

    private Object test(JsonObject p) throws Exception {
        String id = Params.optionalString(p, "id");
        JsonObject json = definition(p, new JsonObject());
        String password = Params.optionalString(p, "password");
        if (password.isEmpty() && !id.isBlank()) {
            JsonObject stored = connections.getConnection(id);
            if (stored != null && stored.has("password") && !stored.get("password").isJsonNull()) {
                password = stored.get("password").getAsString();
            }
        }
        json.addProperty("password", password);
        if (json.has("ssh") && !json.has("sshPassword") && !id.isBlank()) {
            JsonObject stored = connections.getConnection(id);
            if (stored != null && stored.has("sshPassword")) {
                json.add("sshPassword", stored.get("sshPassword"));
            }
        }
        json.addProperty("id", "test-" + UUID.randomUUID());
        ConnectionProfile candidate = GSON.fromJson(json, ConnectionProfile.class);
        DatabaseConnection.getInstance().test(candidate);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------------------------ connecting

    private Object connect(JsonObject p) throws Exception {
        String id = Params.string(p, "id");
        ConnectionProfile connection = load(id);
        String given = Params.optionalString(p, "password");
        if (connection.getPassword() == null && ConnectionTypes.SQLite.name().equals(connection.getDbType())) {
            connection.setPassword(""); // a SQLite file has no password to ask for
        }
        boolean mustAsk = connection.getPassword() == null && !p.has("password");
        if (mustAsk && !DatabaseConnection.getInstance().hasConnection(id)) {
            return Map.of("needsPassword", true);
        }
        if (p.has("password")) {
            connection.setPassword(given);
        }
        if (connecting.putIfAbsent(id, true) != null) {
            throw new IllegalStateException("Already connecting.");
        }
        try {
            DatabaseConnection.getInstance().initialize(connection);
            if (p.has("remember") && Params.bool(p, "remember") && !given.isEmpty()) {
                connections.getVault().store(id, connection.getUsername() == null ? "" : connection.getUsername(), given);
            }
        } finally {
            connecting.remove(id);
        }
        return Map.of("ok", true);
    }

    private void disconnect(String id) {
        workspace.closeConnection(id);
        DatabaseConnection.getInstance().close(id);
    }

    // ------------------------------------------------------------------------------------ drivers

    private Object drivers(JsonObject p) throws Exception {
        ConnectionTypes type = ConnectionTypes.valueOf(Params.string(p, "type"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (DriverInfo info : DriverAPIs.fetchAllDrivers(type)) {
            Map<String, Object> entry = driver(info);
            entry.put("downloaded", new File(Launcher.getJdbcDriversDirectory().toFile(), info.getFileName()).exists());
            list.add(entry);
        }
        return list;
    }

    private Object downloadDriver(JsonObject p) throws Exception {
        DriverInfo info = new DriverInfo(Params.string(p, "name"), Params.string(p, "version"), Params.string(p, "driverClass"),
                Params.string(p, "downloadUrl"), Params.string(p, "fileName"));
        File target = new File(Launcher.getJdbcDriversDirectory().toFile(), info.getFileName());
        if (!target.getName().equals(info.getFileName()) || !info.getFileName().endsWith(".jar")) {
            throw new IllegalArgumentException("Not a driver file name.");
        }
        DriverAPIs.downloadDriverInternal(info, target);
        return Map.of("ok", true);
    }
}
