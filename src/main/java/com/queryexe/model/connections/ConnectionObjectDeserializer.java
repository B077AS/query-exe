package com.queryexe.model.connections;

import lombok.extern.slf4j.Slf4j;

import com.google.gson.*;
import com.queryexe.model.drivers.DriverInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;

@Slf4j
public class ConnectionObjectDeserializer implements JsonDeserializer<ConnectionObject> {

    @Override
    public ConnectionObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
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

            if (ConnectionTypes.valueOf(dbType).equals(ConnectionTypes.SQLServer) && customUrl != null) {
                if (!customUrl.toLowerCase().contains("selectmethod=cursor")) {
                    customUrl += (customUrl.contains("?") ? ";" : ";") + "SelectMethod=cursor";
                }
            }

            DriverInfo driverInfo;
            JsonElement customDriverElement = obj.get("customDriver");

            if (customDriverElement != null && !customDriverElement.isJsonNull() && customDriverElement.isJsonObject()) {
                JsonObject driverObj = customDriverElement.getAsJsonObject();
                String name = getStringOrNull(driverObj, "name");
                String version = getStringOrNull(driverObj, "version");
                String driverClassName = getStringOrNull(driverObj, "driverClassName");
                String downloadUrl = getStringOrNull(driverObj, "downloadUrl");
                String jarFileName = getStringOrNull(driverObj, "jarFileName");

                driverInfo = new DriverInfo(name, version, driverClassName, downloadUrl, jarFileName);
            } else {
                driverInfo = ConnectionTypes.valueOf(dbType).getDefaultDriverInfo();
            }

            Class<?> databaseClass = Class.forName(ConnectionTypes.valueOf(dbType).getClassName());
            ConnectionObject databaseConnection;

            if (customUrl == null || customUrl.isBlank()) {
                Constructor<?> constructor = databaseClass.getDeclaredConstructor(
                        String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class, String.class,
                        String.class, DriverInfo.class
                );

                databaseConnection = (ConnectionObject) constructor.newInstance(
                        id, connectionName, dbType, ConnectionTypes.valueOf(dbType).getBaseUrl(),
                        host, port, databaseName, username, password, driverInfo
                );

            } else {
                Constructor<?> constructor = databaseClass.getDeclaredConstructor(
                        String.class, String.class, String.class, String.class,
                        String.class, String.class, DriverInfo.class
                );

                databaseConnection = (ConnectionObject) constructor.newInstance(
                        id, connectionName, dbType, customUrl, username, password, driverInfo
                );
            }

            return databaseConnection;
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