package com.queryexe.components.results;

import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

public class ResultCellsContextMenu extends ContextMenu {

    public ResultCellsContextMenu(String item, TableRowData row, TableCell<TableRowData, String> cell) {
        TableRowData rowData = cell.getTableView().getItems().get(cell.getIndex());
        ObservableList<String> stringRow = rowData.getStringData();
        int columnIndex = cell.getTableColumn().getTableView().getColumns().indexOf(cell.getTableColumn());
        MenuItem copyCellItem = new MenuItem("Copy Cell");
        copyCellItem.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(item);
            clipboard.setContent(content);
        });

        MenuItem copyRowItem = new MenuItem("Copy Row");
        copyRowItem.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            if (stringRow != null) {
                String rowString = String.join(";", stringRow);
                content.putString(rowString);
                clipboard.setContent(content);
            }
        });

        MenuItem insertScriptItem = new MenuItem("Insert Script");
        insertScriptItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();

            String script = DatabaseConnection.getInstance().getConnectionObject().generateRowInsertScript(stringRow, cell);

            codeArea.replaceText(script);

            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<CodeArea>(codeArea);

            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");

            App.getTabPane().getTabs().add(queryTab);
        });

        MenuItem editCell = new MenuItem("Edit Cell");
        editCell.setOnAction(event -> {
            if (cell != null && cell.getTableView().isEditable() && cell.getTableColumn().isEditable()) {
                cell.startEdit();
            }
        });

        MenuItem setNullItem = new MenuItem("Set NULL");
        setNullItem.setOnAction(event -> {
            ResultTable table = (ResultTable) cell.getTableView();
            table.updateDatabaseRow(rowData, columnIndex, cell.getText(), null);
        });

        MenuItem deleteRow = new MenuItem("Delete Row");
        deleteRow.setOnAction(event -> {
            int rowIndex = cell.getIndex();
            ResultTable table = (ResultTable) cell.getTableView();
            table.deleteDatabaseRow(rowIndex);
        });

        if (cell.getTableView().isEditable()) {
            ResultTable table = (ResultTable) cell.getTableView();
            if (table.getPrimaryKeyColumns().contains(cell.getTableColumn().getText().toUpperCase())) {
                this.getItems().addAll(copyCellItem, copyRowItem, insertScriptItem, new SeparatorMenuItem(), deleteRow);
            } else {
                this.getItems().addAll(copyCellItem, copyRowItem, insertScriptItem, new SeparatorMenuItem(), setNullItem, deleteRow);
            }

            if (cell.getTableColumn().isEditable()) {
                this.getItems().add(3, editCell);
            }

        } else {
            this.getItems().addAll(copyCellItem, copyRowItem);
        }
    }
}