package com.queryexe.components.tree;

import java.util.concurrent.ExecutorService;

import lombok.Getter;
import lombok.Setter;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;

@Getter
@Setter
public class CustomTreeItem extends TreeItem<String> {

    protected Label titleLabel;
    protected String databaseName;
    protected FontIcon icon;
    protected ContextMenu contextMenu;
    protected static ExecutorService executor;

    public CustomTreeItem(String name, FontIcon icon) {
        this.icon = icon;

        HBox graphicBox = new HBox();
        graphicBox.setAlignment(Pos.CENTER_LEFT);
        graphicBox.setSpacing(5);

        titleLabel = new Label(name);
        graphicBox.getChildren().addAll(icon, titleLabel);

        this.setValue("");
        this.setGraphic(graphicBox);
    }

    public void setSelected() {
        titleLabel.setStyle("-fx-font-weight: bold;");
    }

    public void setUnSelected() {
        titleLabel.setStyle("-fx-font-weight: normal;");
    }
}