package io.github.b077as.queryexe.connection;

import lombok.extern.slf4j.Slf4j;

import com.google.gson.*;
import io.github.b077as.queryexe.connection.driver.DriverInfo;

import java.lang.reflect.Type;

/** Reads one saved connection from {@code connections.json}; a malformed entry is logged and skipped (null). */
@Slf4j
public class ConnectionProfileDeserializer implements JsonDeserializer<ConnectionProfile> {

    @Override
    public ConnectionProfile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        try {
            JsonObject obj = json.getAsJsonObject();

            String id = context.deserialize(obj.get("id"), String.class);
            String connectionName = getStringOrNull(obj, "connectionName");
            String dbType = getStringOrNull(obj, "dbType");
            String host = getStringOrNull(obj, "host");
            String port = getStringOrNull(obj, "port");
            String databaseName = getStringOrNull(obj, "databaseName");
            String username = getStringOrNull(obj, "username");
            String password = getStringOrNull(obj, "password");
            String customUrl = getStringOrNull(obj, "customUrl");
            ConnectionTypes type = ConnectionTypes.valueOf(dbType);

            if (type == ConnectionTypes.SQLServer && customUrl != null && !customUrl.toLowerCase().contains("selectmethod=cursor")) {
                customUrl += ";SelectMethod=cursor";
            }

            DriverInfo driverInfo;
            JsonElement customDriverElement = obj.get("customDriver");
            if (customDriverElement != null && !customDriverElement.isJsonNull() && customDriverElement.isJsonObject()) {
                JsonObject driverObj = customDriverElement.getAsJsonObject();
                driverInfo = new DriverInfo(getStringOrNull(driverObj, "name"), getStringOrNull(driverObj, "version"),
                        getStringOrNull(driverObj, "driverClassName"), getStringOrNull(driverObj, "downloadUrl"),
                        getStringOrNull(driverObj, "jarFileName"));
            } else {
                driverInfo = type.getDefaultDriverInfo();
            }

            ConnectionProfile profile = customUrl == null || customUrl.isBlank()
                    ? ConnectionProfile.server(id, connectionName, dbType, type.getBaseUrl(), host, port, databaseName, username, password, driverInfo)
                    : ConnectionProfile.custom(id, connectionName, dbType, customUrl, username, password, driverInfo);

            profile.setFolder(getStringOrNull(obj, "folder"));
            JsonElement pinned = obj.get("pinned");
            profile.setPinned(pinned != null && pinned.isJsonPrimitive() && pinned.getAsBoolean());
            JsonElement sshElement = obj.get("ssh");
            if (sshElement != null && sshElement.isJsonObject()) {
                JsonObject ssh = sshElement.getAsJsonObject();
                String sshPort = getStringOrNull(ssh, "port");
                profile.setSsh(SshConfig.builder().host(getStringOrNull(ssh, "host"))
                        .port(sshPort == null || sshPort.isBlank() ? 22 : Integer.parseInt(sshPort.trim()))
                        .user(getStringOrNull(ssh, "user")).auth(getStringOrNull(ssh, "auth"))
                        .keyPath(getStringOrNull(ssh, "keyPath")).secret(getStringOrNull(obj, "sshPassword")).build());
            }
            return profile;
        } catch (Exception e) {
            log.error("deserialize failed", e);
            return null;
        }
    }

    private String getStringOrNull(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }
}
