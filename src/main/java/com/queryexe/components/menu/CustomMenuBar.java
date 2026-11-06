package com.queryexe.components.menu;

import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;

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
import com.queryexe.components.modals.ShortcutsModal;
import com.queryexe.components.modals.MultipleConnectionsModal;
import com.queryexe.components.modals.SettingsModal;
import com.queryexe.service.QueryService;
import com.queryexe.service.RecentFilesManager;
import com.queryexe.queryexe.App;
import com.queryexe.utils.OsUtil;

import java.io.File;
import java.util.List;

public class CustomMenuBar extends MenuBar {

    private Menu openRecentMenu;

    public CustomMenuBar() {
        // On macOS the menus live in the global screen menu bar (top of the
        // screen) instead of inside the window. Elsewhere this is a no-op.
        setUseSystemMenuBar(OsUtil.IS_MAC);

        getMenus().addAll(
                fileMenu(),
                editMenu(),
                connectionMenu(),
                settingsMenu(),
                helpMenu()
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

        MenuItem openMenu = createItem("Open", Feather.FOLDER, new KeyCodeCombination(KeyCode.O, SHORTCUT_DOWN));
        openMenu.setOnAction(event -> {
            QueryService.getInstance().openQuery();
        });

        MenuItem saveMenu = createItem("Save", Feather.SAVE, new KeyCodeCombination(KeyCode.S, SHORTCUT_DOWN));
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
        MenuItem undoMenu = createItem("Undo", Feather.CORNER_DOWN_LEFT, new KeyCodeCombination(KeyCode.Z, SHORTCUT_DOWN));
        KeyCodeCombination redoKey = OsUtil.IS_MAC
                ? new KeyCodeCombination(KeyCode.Z, SHORTCUT_DOWN, SHIFT_DOWN)
                : new KeyCodeCombination(KeyCode.Y, SHORTCUT_DOWN);
        MenuItem redoMenu = createItem("Redo", Feather.CORNER_DOWN_RIGHT, redoKey);
        MenuItem cutMenu = createItem("Cut", Feather.SCISSORS, new KeyCodeCombination(KeyCode.X, SHORTCUT_DOWN));
        MenuItem copyMenu = createItem("Copy", MaterialDesignC.CONTENT_COPY, new KeyCodeCombination(KeyCode.C, SHORTCUT_DOWN));
        MenuItem pasteMenu = createItem("Paste", MaterialDesignC.CONTENT_PASTE, new KeyCodeCombination(KeyCode.V, SHORTCUT_DOWN));

        undoMenu.setOnAction(e -> fireShortcut(KeyCode.Z, false));
        redoMenu.setOnAction(e -> fireShortcut(redoKey.getCode(), redoKey.getShift() == KeyCombination.ModifierValue.DOWN));
        cutMenu.setOnAction(e -> fireShortcut(KeyCode.X, false));
        copyMenu.setOnAction(e -> fireShortcut(KeyCode.C, false));
        pasteMenu.setOnAction(e -> fireShortcut(KeyCode.V, false));

        menu.setMnemonicParsing(true);
        menu.getItems().addAll(undoMenu, redoMenu, new SeparatorMenuItem(), cutMenu, copyMenu, pasteMenu);
        return menu;
    }

    /** Replays the platform shortcut (Cmd on macOS, Ctrl elsewhere) + code on the focused node. */
    private void fireShortcut(KeyCode code, boolean shift) {
        Node focused = App.getScene().getFocusOwner();
        if (focused != null) {
            KeyEvent pressEvent = new KeyEvent(
                    focused,
                    focused,
                    KeyEvent.KEY_PRESSED,
                    "", "",
                    code,
                    shift, !OsUtil.IS_MAC, false, OsUtil.IS_MAC
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
        menu.setMnemonicParsing(true);

        MenuItem colorSchemeMenu = createItem("Color Scheme", MaterialDesignP.PALETTE_OUTLINE, null);
        colorSchemeMenu.setOnAction(event -> {
            App.showModal(new SettingsModal());
        });

        menu.getItems().add(colorSchemeMenu);
        return menu;
    }

    private Menu helpMenu() {
        Menu menu = new Menu("Help");
        menu.setMnemonicParsing(true);

        MenuItem shortcutsMenuItem = createItem("Shortcuts", MaterialDesignK.KEYBOARD_OUTLINE, null);
        shortcutsMenuItem.setOnAction(event -> {
            App.showModal(new ShortcutsModal());
        });

        MenuItem aboutMenuItem = createItem("About", MaterialDesignI.INFORMATION_OUTLINE, null);
        aboutMenuItem.setOnAction(event -> {
            App.showModal(new AboutModal());
        });

        menu.getItems().addAll(shortcutsMenuItem, aboutMenuItem);
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