package com.queryexe.components.modals;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import com.queryexe.utils.IconColorUtil;
import com.queryexe.queryexe.App;

import java.util.LinkedHashMap;
import java.util.Map;

public class ShortcutsModal extends HBox {

    private record ShortcutCategory(String title, Ikon icon, String[][] shortcuts) {
    }

    private final ShortcutCategory[] categories = new ShortcutCategory[]{
            new ShortcutCategory("Editor", MaterialDesignC.CODE_TAGS, new String[][]{
                    {"Ctrl + Space", "Trigger autocomplete"},
                    {"Ctrl + Enter", "Run current statement"},
                    {"Ctrl + Shift + Enter", "Run entire query/script"},
                    {"Ctrl + D", "Duplicate current line"},
                    {"Ctrl + F", "Find and replace"},
                    {"Ctrl + O", "Open file"},
                    {"Ctrl + S", "Save file"}
            }),
            new ShortcutCategory("Find & Replace", MaterialDesignM.MAGNIFY, new String[][]{
                    {"Enter", "Find next match"},
                    {"Shift + Enter", "Find previous match"},
                    {"Ctrl + Enter", "Replace all occurrences"},
                    {"Enter (replace field)", "Replace current match"},
                    {"Escape", "Close find popup"}
            }),
            new ShortcutCategory("Autocomplete", MaterialDesignA.AUTO_FIX, new String[][]{
                    {"Enter / Tab", "Insert selected suggestion"},
                    {"↑ / ↓", "Navigate through suggestions"},
                    {"Escape / Space", "Close autocomplete popup"}
            })
    };

    private final VBox contentBox = new VBox(16);
    private final Map<ShortcutCategory, VBox> navItems = new LinkedHashMap<>();
    private VBox activeNavItem;

    public ShortcutsModal() {
        this.setAlignment(Pos.TOP_LEFT);
        this.getStyleClass().add("modal-container");
        this.setMaxSize(800, 560);
        this.setMinSize(800, 560);
        this.setPrefSize(800, 560);
        this.setSpacing(0);
        this.setTranslateY(10);

        Separator vDivider = new Separator(Orientation.VERTICAL);
        vDivider.setPadding(new Insets(0));

        VBox rightColumn = createRightColumn();
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        this.getChildren().addAll(createLeftPanel(), vDivider, rightColumn);

        selectCategory(categories[0]);
    }

    // ── Left panel + nav ──────────────────────────────────────────────────────

    private VBox createLeftPanel() {
        VBox pane = new VBox(0);
        pane.setPrefWidth(220);
        pane.setMinWidth(220);
        pane.setMaxWidth(220);
        pane.getStyleClass().add("help-left-panel");

        StackPane iconTile = new StackPane();
        iconTile.setPrefSize(64, 64);
        iconTile.setMaxSize(64, 64);
        iconTile.setStyle("-fx-background-color: -color-accent-subtle; -fx-background-radius: 14px;");
        iconTile.getChildren().add(IconColorUtil.colored(MaterialDesignK.KEYBOARD_OUTLINE, "-color-accent-emphasis", 28));

        VBox iconSection = new VBox(iconTile);
        iconSection.setPadding(new Insets(24, 20, 18, 20));
        iconSection.setAlignment(Pos.CENTER);

        Label navLabel = new Label("SHORTCUTS");
        navLabel.getStyleClass().add("help-nav-section-label");
        navLabel.setPadding(new Insets(12, 0, 4, 18));

        VBox nav = new VBox(2);
        nav.setPadding(new Insets(0, 8, 12, 8));
        for (ShortcutCategory category : categories) {
            nav.getChildren().add(createNavItem(category));
        }

        pane.getChildren().addAll(iconSection, new Separator(Orientation.HORIZONTAL), navLabel, nav);
        return pane;
    }

    private VBox createNavItem(ShortcutCategory category) {
        FontIcon icon = IconColorUtil.colored(category.icon(), "-color-fg-default", 15);

        Label label = new Label(category.title());
        label.getStyleClass().add("help-nav-label");

        HBox row = new HBox(10, icon, label);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox item = new VBox(row);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(9, 12, 9, 12));
        item.getStyleClass().add("help-nav-item");
        item.setOnMouseClicked(e -> selectCategory(category));

        navItems.put(category, item);
        return item;
    }

    private void selectCategory(ShortcutCategory category) {
        VBox item = navItems.get(category);
        if (activeNavItem != item) {
            if (activeNavItem != null) setNavInactive(activeNavItem);
            if (item != null) setNavActive(item);
            activeNavItem = item;
        }
        showCategory(category);
    }

    private void setNavActive(VBox item) {
        item.getStyleClass().remove("help-nav-inactive");
        if (!item.getStyleClass().contains("help-nav-active")) item.getStyleClass().add("help-nav-active");
    }

    private void setNavInactive(VBox item) {
        item.getStyleClass().remove("help-nav-active");
        if (!item.getStyleClass().contains("help-nav-inactive")) item.getStyleClass().add("help-nav-inactive");
    }

    // ── Right column ──────────────────────────────────────────────────────────

    private VBox createRightColumn() {
        VBox column = new VBox(0);
        column.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        contentBox.setPadding(new Insets(20, 24, 24, 24));

        column.getChildren().addAll(createHeader(), scrollPane);
        return column;
    }

    private HBox createHeader() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 20, 14, 24));
        header.setStyle("-fx-border-width: 0 0 1px 0; -fx-border-color: -color-border-default;");

        VBox titles = new VBox(1);
        Label title = new Label("Keyboard Shortcuts");
        title.getStyleClass().add(Styles.TITLE_3);
        Label subtitle = new Label("Speed up your workflow with these shortcuts");
        subtitle.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        titles.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(e -> App.closeModal());

        header.getChildren().addAll(titles, spacer, closeButton);
        return header;
    }

    private void showCategory(ShortcutCategory category) {
        HBox categoryHeader = new HBox(10);
        categoryHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon categoryIcon = IconColorUtil.colored(category.icon(), "-color-accent-emphasis", 22);
        Label categoryTitle = new Label(category.title());
        categoryTitle.getStyleClass().add(Styles.TITLE_4);
        categoryHeader.getChildren().addAll(categoryIcon, categoryTitle);

        VBox list = new VBox(8);
        for (String[] shortcut : category.shortcuts()) {
            list.getChildren().add(createShortcutRow(shortcut[0], shortcut[1]));
        }

        contentBox.getChildren().setAll(categoryHeader, list);
    }

    private HBox createShortcutRow(String keys, String description) {
        HBox row = new HBox(14);
        row.getStyleClass().add("help-shortcut-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));

        Label keysLabel = new Label(keys);
        keysLabel.getStyleClass().add("help-kbd-chip");
        keysLabel.setMinWidth(190);

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 13px;");
        descLabel.setWrapText(true);
        HBox.setHgrow(descLabel, Priority.ALWAYS);

        row.getChildren().addAll(keysLabel, descLabel);
        return row;
    }
}
