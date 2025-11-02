package com.queryexe.components.results;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import com.queryexe.model.data.TableRowData;

public class ResultColumnContextMenu extends ContextMenu {

    public ResultColumnContextMenu(TableColumn<TableRowData, String> column) {

        MenuItem sortAscendingMenuItem = new MenuItem("Sort Ascending");
        sortAscendingMenuItem.setStyle("-fx-font-weight: normal;");
        sortAscendingMenuItem.setOnAction(event -> {
            applySortAscending(column);
        });

        MenuItem sortDescendingMenuItem = new MenuItem("Sort Descending");
        sortDescendingMenuItem.setStyle("-fx-font-weight: normal;");
        sortDescendingMenuItem.setOnAction(event -> {
            applySortDescending(column);
        });

        MenuItem copyNameMenuItem = new MenuItem("Copy Name");
        copyNameMenuItem.setStyle("-fx-font-weight: normal;");
        copyNameMenuItem.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(column.getText());
            clipboard.setContent(content);
        });

        this.getItems().addAll(sortAscendingMenuItem,
                sortDescendingMenuItem,
                new SeparatorMenuItem(),
                copyNameMenuItem);
    }

    private void applySortAscending(TableColumn<TableRowData, String> column) {
        column.getTableView().getSortOrder().clear();
        column.setSortType(TableColumn.SortType.ASCENDING);
        column.getTableView().getSortOrder().add(column);
        column.getTableView().sort();
    }

    private void applySortDescending(TableColumn<TableRowData, String> column) {
        column.getTableView().getSortOrder().clear();
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.getTableView().getSortOrder().add(column);
        column.getTableView().sort();
    }
}
