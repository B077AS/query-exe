package com.queryexe.queryexe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Launcher {
	
	public static void main(String[] args) {

        try {
            Path path = Paths.get("./data");
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Path path = Paths.get("./jdbc-drivers");
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

		App.appStart(args);
	}
}
