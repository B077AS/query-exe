package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;

/** Group node under a database/schema for objects other than tables ("Sequences", "Indexes", ...). */
public class SchemaObjectsTreeItem extends CustomTreeItem {

    public SchemaObjectsTreeItem(String name, FontIcon icon) {
        super(name, icon);
    }
}
