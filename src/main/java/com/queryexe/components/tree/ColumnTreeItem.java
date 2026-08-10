package com.queryexe.components.tree;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import com.queryexe.components.modals.ColumnPropertiesModal;
import com.queryexe.queryexe.App;

public class ColumnTreeItem extends CustomTreeItem {

    public ColumnTreeItem(String name, FontIcon icon) {
        super(name, icon);
        setupContextMenu();
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem copyColumnName = new MenuItem("Copy Name", menuIcon(MaterialDesignC.CONTENT_COPY));
        copyColumnName.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(this.titleLabel.getText());
            clipboard.setContent(content);
        });

        MenuItem propertiesMenuItem = new MenuItem("Properties", menuIcon(MaterialDesignI.INFORMATION_OUTLINE));
        propertiesMenuItem.setOnAction(event -> {
            TableTreeItem parentItem = (TableTreeItem) this.getParent();
            App.showModal(new ColumnPropertiesModal(
                    this.databaseName,
                    parentItem.getTitleLabel().getText(),
                    this.getTitleLabel().getText()
            ));
        });

        contextMenu.getItems().addAll(copyColumnName, propertiesMenuItem);
    }
}