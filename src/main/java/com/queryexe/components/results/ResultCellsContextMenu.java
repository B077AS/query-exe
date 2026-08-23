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
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignN;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.service.SQLFormatterUtils;
import com.queryexe.queryexe.App;

public class ResultCellsContextMenu extends ContextMenu {

    public ResultCellsContextMenu(String item, TableCell<TableRowData, String> cell) {
        TableRowData rowData = cell.getTableView().getItems().get(cell.getIndex());
        ObservableList<String> stringRow = rowData.getStringData();
        int columnIndex = cell.getTableColumn().getTableView().getColumns().indexOf(cell.getTableColumn());
        MenuItem copyCellItem = new MenuItem("Copy Cell", menuIcon(MaterialDesignC.CONTENT_COPY));
        copyCellItem.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(item);
            clipboard.setContent(content);
        });

        MenuItem copyRowItem = new MenuItem("Copy Row", menuIcon(MaterialDesignT.TABLE_ROW));
        copyRowItem.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            if (stringRow != null) {
                String rowString = String.join(";", stringRow);
                content.putString(rowString);
                clipboard.setContent(content);
            }
        });

        MenuItem duplicateRow = new MenuItem("Duplicate Row", menuIcon(MaterialDesignT.TABLE_ROW_PLUS_AFTER));
        duplicateRow.setOnAction(event -> {
            ResultTable table = (ResultTable) cell.getTableView();
            table.duplicateDatabaseRow(rowData);
        });

        MenuItem insertScriptItem = new MenuItem("Insert Script", menuIcon(MaterialDesignS.SCRIPT_TEXT_PLAY_OUTLINE));
        insertScriptItem.setOnAction(event -> {
            SQLEditor codeArea = new SQLEditor();

            String script = SQLFormatterUtils.format(
                    DatabaseConnection.getInstance().getConnectionObject().generateRowInsertScript(stringRow, cell),
                    DatabaseConnection.getInstance().getConnectionObject());

            codeArea.replaceText(script);

            VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<CodeArea>(codeArea);

            CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, App.getTabPane(), DatabaseConnection.getInstance().getCurrentConnectionObject());
            queryTab.setCustomText(DatabaseConnection.getInstance().getCurrentConnectionObject().getConnectionName() + " - Script");

            App.getTabPane().getTabs().add(queryTab);
        });

        MenuItem editCell = new MenuItem("Edit Cell", menuIcon(MaterialDesignP.PENCIL_OUTLINE));
        editCell.setOnAction(event -> {
            if (cell != null && cell.getTableView().isEditable() && cell.getTableColumn().isEditable()) {
                cell.startEdit();
            }
        });

        MenuItem setNullItem = new MenuItem("Set NULL", menuIcon(MaterialDesignN.NULL));
        setNullItem.setOnAction(event -> {
            ResultTable table = (ResultTable) cell.getTableView();
            table.updateDatabaseRow(rowData, columnIndex, cell.getText(), null);
        });

        MenuItem deleteRow = new MenuItem("Delete Row", menuIcon(MaterialDesignT.TABLE_ROW_REMOVE));
        deleteRow.setOnAction(event -> {
            int rowIndex = cell.getIndex();
            ResultTable table = (ResultTable) cell.getTableView();
            table.deleteDatabaseRow(rowIndex);
        });

        if (cell.getTableView().isEditable()) {
            ResultTable table = (ResultTable) cell.getTableView();
            if (table.getPrimaryKeyColumns().contains(cell.getTableColumn().getText().toUpperCase())) {
                this.getItems().addAll(copyCellItem, copyRowItem, duplicateRow, insertScriptItem, new SeparatorMenuItem(), deleteRow);
            } else {
                this.getItems().addAll(copyCellItem, copyRowItem, duplicateRow, insertScriptItem, new SeparatorMenuItem(), setNullItem, deleteRow);
            }

            if (cell.getTableColumn().isEditable()) {
                this.getItems().add(3, editCell);
            }

        } else {
            this.getItems().addAll(copyCellItem, copyRowItem);
        }
    }

    private static FontIcon menuIcon(Ikon ikon) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(14);
        return icon;
    }
}