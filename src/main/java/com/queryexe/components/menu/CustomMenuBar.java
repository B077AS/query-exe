package com.queryexe.components.menu;

import static javafx.scene.input.KeyCombination.CONTROL_DOWN;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.materialdesign2.*;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import com.queryexe.components.modals.AboutModal;
import com.queryexe.components.modals.ActiveConnectionsModal;
import com.queryexe.components.modals.HelpModal;
import com.queryexe.components.modals.MultipleConnectionsModal;
import com.queryexe.service.QueryService;
import com.queryexe.service.RecentFilesManager;
import com.queryexe.queryexe.App;

import java.io.File;
import java.util.List;

public class CustomMenuBar extends MenuBar {

    private Menu openRecentMenu;

    public CustomMenuBar() {
        getMenus().addAll(
                fileMenu(),
                editMenu(),
                connectionMenu(),
                settingsMenu(),
                aboutMenu()
        );
    }

    private Menu fileMenu() {
        Menu menu = new Menu("File");
        menu.setMnemonicParsing(true);

        MenuItem newQueryMenu = createItem("New Query", Feather.PLUS, null);
        newQueryMenu.setOnAction(event -> {
            QueryService.getInstance().newQuery(null, null);
        });
        newQueryMenu.setMnemonicParsing(true);

        openRecentMenu = new Menu("Open Recent");
        openRecentMenu.setOnShowing(event -> populateRecentFiles());

        populateRecentFiles();

        MenuItem openMenu = createItem("Open", Feather.FOLDER, new KeyCodeCombination(KeyCode.O, CONTROL_DOWN));
        openMenu.setOnAction(event -> {
            QueryService.getInstance().openQuery();
        });

        MenuItem saveMenu = createItem("Save", Feather.SAVE, new KeyCodeCombination(KeyCode.S, CONTROL_DOWN));
        saveMenu.setOnAction(event -> {
            QueryService.getInstance().saveQuery();
        });

        MenuItem saveAsMenu = new MenuItem("Save As");
        saveAsMenu.setOnAction(event -> {
            QueryService.getInstance().saveQueryAs();
        });
        MenuItem exitMenu = new MenuItem("Exit");
        exitMenu.setOnAction(event -> {
            App.goHome();
        });

        menu.getItems().addAll(
                newQueryMenu,
                new SeparatorMenuItem(),
                openMenu,
                openRecentMenu,
                new SeparatorMenuItem(),
                saveMenu,
                saveAsMenu,
                new SeparatorMenuItem(),
                exitMenu
        );
        return menu;
    }

    private void populateRecentFiles() {
        openRecentMenu.getItems().clear();

        List<String> recentFiles = RecentFilesManager.getInstance().getRecentFiles();

        if (recentFiles.isEmpty()) {
            MenuItem emptyItem = new MenuItem("No recent files");
            emptyItem.setDisable(true);
            openRecentMenu.getItems().add(emptyItem);
        } else {
            for (String filePath : recentFiles) {
                File file = new File(filePath);
                String fileName = file.getName();

                MenuItem fileItem = new MenuItem(fileName);
                fileItem.setGraphic(new FontIcon(Feather.FILE_TEXT));
                fileItem.setStyle("-fx-font-size: 12px;");
                fileItem.setOnAction(event -> {
                    QueryService.getInstance().openRecentFile(file);
                });

                openRecentMenu.getItems().add(fileItem);
            }

            openRecentMenu.getItems().add(new SeparatorMenuItem());

            MenuItem clearRecentMenu = createItem("Clear Recent", MaterialDesignC.CLOSE_CIRCLE_OUTLINE, null);
            clearRecentMenu.setOnAction(event -> {
                RecentFilesManager.getInstance().clearRecentFiles();
                populateRecentFiles();
            });

            openRecentMenu.getItems().add(clearRecentMenu);
        }
    }

    private Menu editMenu() {
        Menu menu = new Menu("Edit");
        MenuItem undoMenu = createItem("Undo", Feather.CORNER_DOWN_LEFT, new KeyCodeCombination(KeyCode.Z, CONTROL_DOWN));
        MenuItem redoMenu = createItem("Redo", Feather.CORNER_DOWN_RIGHT, new KeyCodeCombination(KeyCode.Y, CONTROL_DOWN));
        MenuItem cutMenu = createItem("Cut", Feather.SCISSORS, new KeyCodeCombination(KeyCode.X, CONTROL_DOWN));
        MenuItem copyMenu = createItem("Copy", MaterialDesignC.CONTENT_COPY, new KeyCodeCombination(KeyCode.C, CONTROL_DOWN));
        MenuItem pasteMenu = createItem("Paste", MaterialDesignC.CONTENT_PASTE, new KeyCodeCombination(KeyCode.V, CONTROL_DOWN));

        undoMenu.setOnAction(e -> fireShortcut(KeyCode.Z));
        redoMenu.setOnAction(e -> fireShortcut(KeyCode.Y));
        cutMenu.setOnAction(e -> fireShortcut(KeyCode.X));
        copyMenu.setOnAction(e -> fireShortcut(KeyCode.C));
        pasteMenu.setOnAction(e -> fireShortcut(KeyCode.V));

        menu.setMnemonicParsing(true);
        menu.getItems().addAll(undoMenu, redoMenu, new SeparatorMenuItem(), cutMenu, copyMenu, pasteMenu);
        return menu;
    }

    private void fireShortcut(KeyCode code) {
        Node focused = App.getScene().getFocusOwner();
        if (focused != null) {
            KeyEvent pressEvent = new KeyEvent(
                    focused,
                    focused,
                    KeyEvent.KEY_PRESSED,
                    "", "",
                    code,
                    false, true, false, false
            );

            Event.fireEvent(focused, pressEvent);
        }
    }

    private Menu connectionMenu() {
        Menu menu = new Menu("Connections");

        MenuItem newConnectionMenu = createItem("Open More", MaterialDesignP.PLUS_BOX_MULTIPLE_OUTLINE, null);
        newConnectionMenu.setMnemonicParsing(true);
        newConnectionMenu.setOnAction(event -> {
            App.showModal(new MultipleConnectionsModal());
        });

        MenuItem activeConnectionsMenu = createItem("Manage Sessions", MaterialDesignC.CONNECTION, null);
        activeConnectionsMenu.setMnemonicParsing(true);
        activeConnectionsMenu.setOnAction(event -> {
            App.showModal(new ActiveConnectionsModal());
        });

        MenuItem closeConnectionMenu = createItem("Close All", MaterialDesignC.CLOSE, null);
        closeConnectionMenu.setOnAction(event -> {
            App.closeConnection();
        });

        menu.setMnemonicParsing(true);
        menu.getItems().addAll(newConnectionMenu, activeConnectionsMenu, new SeparatorMenuItem(), closeConnectionMenu);
        return menu;
    }

    private Menu settingsMenu() {
        Menu menu = new Menu("Settings");
        return menu;
    }

    private Menu aboutMenu() {
        Menu menu = new Menu("About");
        menu.setMnemonicParsing(true);

        MenuItem helpMenuItem = createItem("Help", Feather.HELP_CIRCLE, null);
        helpMenuItem.setOnAction(event -> {
            App.showModal(new HelpModal());
        });

        MenuItem aboutMenuItem = createItem("About", MaterialDesignI.INFORMATION_OUTLINE, null);
        aboutMenuItem.setOnAction(event -> {
            App.showModal(new AboutModal());
        });

        menu.getItems().addAll(helpMenuItem, aboutMenuItem);
        return menu;
    }

    private MenuItem createItem(String text, Ikon graphic, KeyCombination accelerator) {
        MenuItem item = new MenuItem(text);

        if (graphic != null) {
            item.setGraphic(new FontIcon(graphic));
        }

        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }

        return item;
    }
}