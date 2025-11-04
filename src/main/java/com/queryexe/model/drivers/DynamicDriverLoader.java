package com.queryexe.model.drivers;

import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.queryexe.Launcher;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DynamicDriverLoader {

    private static DynamicDriverLoader instance;
    private final Map<String, Driver> loadedDrivers;
    private final Map<String, URLClassLoader> driverClassLoaders;

    private DynamicDriverLoader() {
        this.loadedDrivers = new HashMap<>();
        this.driverClassLoaders = new HashMap<>();
        loadDefaultDrivers();
    }

    public static synchronized DynamicDriverLoader getInstance() {
        if (instance == null) {
            instance = new DynamicDriverLoader();
        }
        return instance;
    }

    private void loadDefaultDrivers() {
        try {

            for(ConnectionTypes connectionType: ConnectionTypes.values()){
                registerDriver(connectionType.name(), (Driver) Class.forName(connectionType.getDefaultDriverInfo().getDriverClass()).getDeclaredConstructor().newInstance());
            }

        } catch (Exception e) {
            System.err.println("Error loading default drivers: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerDriver(String key, Driver driver) throws SQLException {
        Driver driverShim = new DriverShim(driver);
        DriverManager.registerDriver(driverShim);
        loadedDrivers.put(key, driverShim);
        //System.out.println("Registered driver: " + key + " - " + driver.getClass().getName());
    }

    public boolean loadCustomDriver(DriverInfo driverInfo, ConnectionTypes connectionType) {
        try {
            File jarFile = Launcher.getJdbcDriversDirectory().resolve(driverInfo.getFileName()).toFile();

            if (!jarFile.exists()) {
                System.err.println("Driver JAR file not found: " + jarFile.getAbsolutePath());
                return false;
            }

            // Unregister previous driver for this connection type if it exists
            unloadDriver(connectionType.name());

            // Create a new URLClassLoader for the driver JAR
            URL jarUrl = jarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{jarUrl},
                    this.getClass().getClassLoader()
            );

            // Load the driver class
            Class<?> driverClass = Class.forName(
                    driverInfo.getDriverClass(),
                    true,
                    classLoader
            );

            // Create an instance of the driver
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();

            // Register the driver
            DriverManager.registerDriver(new DriverShim(driver));

            // Store references
            String key = connectionType.name() + "_CUSTOM";
            loadedDrivers.put(key, driver);
            driverClassLoaders.put(key, classLoader);

            System.out.println("Successfully loaded custom driver: " + driverInfo.getVersion() +
                    " for " + connectionType.name());

            return true;

        } catch (Exception e) {
            System.err.println("Error loading custom driver: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void unloadDriver(String key) {
        try {
            // Try to deregister custom driver
            String customKey = key + "_CUSTOM";
            Driver driver = loadedDrivers.get(customKey);

            if (driver != null) {
                DriverManager.deregisterDriver(driver);
                loadedDrivers.remove(customKey);

                // Close the class loader
                URLClassLoader classLoader = driverClassLoaders.get(customKey);
                if (classLoader != null) {
                    classLoader.close();
                    driverClassLoaders.remove(customKey);
                }

                System.out.println("Unloaded custom driver for: " + key);

                // Re-register the default driver
                reloadDefaultDriver(key);
            }
        } catch (Exception e) {
            System.err.println("Error unloading driver: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void reloadDefaultDriver(String connectionTypeKey) {
        try {
            Driver defaultDriver = loadedDrivers.get(connectionTypeKey);

            if (defaultDriver == null) {
                registerDriver(connectionTypeKey, (Driver) Class.forName(ConnectionTypes.valueOf(connectionTypeKey).getDefaultDriverInfo().getDriverClass()).getDeclaredConstructor().newInstance());
            } else {
                DriverManager.registerDriver(defaultDriver);
            }
        } catch (Exception e) {
            System.err.println("Error reloading default driver: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isCustomDriverLoaded(ConnectionTypes connectionType) {
        return loadedDrivers.containsKey(connectionType.name() + "_CUSTOM");
    }

    public Map<String, Driver> getLoadedDrivers() {
        return new HashMap<>(loadedDrivers);
    }

    public void cleanup() {
        for (URLClassLoader classLoader : driverClassLoaders.values()) {
            try {
                classLoader.close();
            } catch (Exception e) {
                System.err.println("Error closing class loader: " + e.getMessage());
            }
        }
        driverClassLoaders.clear();
        loadedDrivers.clear();
    }
}