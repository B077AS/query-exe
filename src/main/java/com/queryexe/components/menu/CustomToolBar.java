package com.queryexe.components.menu;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import com.queryexe.service.QueryService;

public class CustomToolBar extends ToolBar {

    public CustomToolBar() {

        Button newButton = new Button("New Query", new FontIcon(Feather.PLUS));
        newButton.setOnAction(event -> {
            QueryService.getInstance().newQuery(null, null);
        });

        Button openButton = new Button("Open Query", new FontIcon(Feather.FOLDER));
        openButton.setOnAction(event -> {
            QueryService.getInstance().openQuery();
        });

        Button saveButton = new Button("Save", new FontIcon(Feather.SAVE));
        saveButton.setOnAction(event -> {
            QueryService.getInstance().saveQuery();
        });

        Separator separator = new Separator(Orientation.VERTICAL);
        Button runButton = new Button("Run", new FontIcon(Feather.PLAY));
        runButton.setOnAction(event -> {
            QueryService.getInstance().runQuery();
        });

        Button runStatementButton = new Button("Run Statement", new FontIcon(MaterialDesignC.CURSOR_TEXT));
        runStatementButton.setOnAction(event -> {
            QueryService.getInstance().runStatement();
        });

        this.getItems().addAll(newButton, openButton, saveButton, separator, runButton, runStatementButton);
    }
}