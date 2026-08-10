package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;

public class UserTreeItem extends CustomTreeItem {

    public UserTreeItem(String name, FontIcon icon) {
        super(name, icon);
        // No context menu for user items currently
    }
}