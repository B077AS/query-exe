package com.queryexe.model.drivers;

public class DriverInfo {

    private final String name;
    private final String version;
    private final String driverClass;
    private final String downloadUrl;
    private final String fileName;

    public DriverInfo(String name, String version, String driverClass, String downloadUrl, String fileName) {
        this.name = name;
        this.version = version;
        this.driverClass = driverClass;
        this.downloadUrl = downloadUrl;
        this.fileName = fileName;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return name + " " + version;
    }
}