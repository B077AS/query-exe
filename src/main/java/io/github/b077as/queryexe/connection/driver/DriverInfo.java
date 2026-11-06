package io.github.b077as.queryexe.connection.driver;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DriverInfo {

    private String name;
    private String version;
    private String driverClass;
    private String downloadUrl;
    private String fileName;

    @Override
    public String toString() {
        return name + " " + version;
    }
}