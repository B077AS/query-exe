package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** A single sequence, synonym, index or trigger. */
public class SchemaObjectTreeItem extends CustomTreeItem {

    public SchemaObjectTreeItem(String name, FontIcon icon) {
        super(name, icon);

        contextMenu = new ContextMenu();
        MenuItem copyName = new MenuItem("Copy Name", menuIcon(MaterialDesignC.CONTENT_COPY));
        copyName.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        contextMenu.getItems().add(copyName);
    }
}
