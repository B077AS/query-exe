package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import com.queryexe.components.modals.CreateTableModal;
import com.queryexe.queryexe.App;

public class TablesTreeItem extends CustomTreeItem {

    public TablesTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem createTableMenuItem = new MenuItem("Create Table", menuIcon(MaterialDesignT.TABLE_PLUS));
        createTableMenuItem.setOnAction(event -> {
            DatabaseTreeItem parentItem = (DatabaseTreeItem) this.getParent();
            App.showModal(new CreateTableModal(this.databaseName, parentItem::refreshDatabase));
        });

        MenuItem refreshTablesMenuItem = new MenuItem("Refresh Tables", menuIcon(MaterialDesignR.REFRESH));
        refreshTablesMenuItem.setOnAction(event -> {
            DatabaseTreeItem parentItem = (DatabaseTreeItem) this.getParent();
            parentItem.refreshDatabase();
        });

        contextMenu.getItems().addAll(createTableMenuItem, refreshTablesMenuItem);
    }
}