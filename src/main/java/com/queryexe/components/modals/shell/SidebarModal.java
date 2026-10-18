package com.queryexe.components.modals.shell;

import atlantafx.base.theme.Styles;
import com.queryexe.queryexe.App;
import com.queryexe.utils.IconColorUtil;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared shell for the larger dialogs: a sidebar (object tile, title, tab navigation) on the left and a
 * header / tab content / footer column on the right. Subclasses register their sections with
 * {@link #addTab(ModalTab)} and put their buttons in the footer with {@link #setFooter(Node...)}.
 */
public class SidebarModal extends HBox {

    private final VBox tabNav = new VBox(2);
    private final StackPane tabContent = new StackPane();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final HBox headerActions = new HBox(8);
    private final HBox footer = new HBox(10);
    private final Label objectTitle = new Label();
    private final Label objectSubtitle = new Label();

    private final Map<ModalTab, VBox> navItems = new LinkedHashMap<>();
    private VBox activeNavItem;
    private ModalTab activeTab;

    /**
     * @param tileIcon        icon in the sidebar tile
     * @param title           name of the object the dialog is about (table name, "New Table", ...)
     * @param subtitle        where it lives (database / schema); may be null
     * @param navSectionLabel small caps label above the navigation
     */
    protected SidebarModal(Ikon tileIcon, String title, String subtitle, String navSectionLabel, double width, double height) {
        setAlignment(Pos.TOP_LEFT);
        getStyleClass().add("modal-container");
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        setSpacing(0);

        setObjectTitle(title);
        objectSubtitle.setText(subtitle == null ? "" : subtitle);
        objectSubtitle.setManaged(subtitle != null && !subtitle.isEmpty());

        tabContent.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(tabContent, Priority.ALWAYS);

        Separator divider = new Separator(Orientation.VERTICAL);
        divider.setPadding(new Insets(0));

        VBox right = new VBox(0, createHeader(), tabContent, createFooter());
        HBox.setHgrow(right, Priority.ALWAYS);

        getChildren().addAll(createLeftPanel(tileIcon, navSectionLabel), divider, right);
    }

    // ── API for subclasses ────────────────────────────────────────────────────

    protected void addTab(ModalTab tab) {
        Label label = new Label(tab.name());
        label.getStyleClass().add("help-nav-label");
        HBox row = new HBox(10, IconColorUtil.colored(tab.icon(), "-color-fg-default", 15), label);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox item = new VBox(row);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(9, 12, 9, 12));
        item.getStyleClass().add("help-nav-item");
        item.setOnMouseClicked(e -> switchTo(tab));

        navItems.put(tab, item);
        tabNav.getChildren().add(item);
        if (activeTab == null) switchTo(tab);
    }

    protected void switchTo(ModalTab tab) {
        VBox item = navItems.get(tab);
        if (item == null) return;
        if (activeNavItem != item) {
            if (activeNavItem != null) activeNavItem.getStyleClass().remove("help-nav-active");
            item.getStyleClass().add("help-nav-active");
            activeNavItem = item;
        }
        activeTab = tab;
        titleLabel.setText(tab.name());
        subtitleLabel.setText(tab.description());
        subtitleLabel.setManaged(!tab.description().isEmpty());
        subtitleLabel.setVisible(!tab.description().isEmpty());

        Node action = tab.headerAction();
        if (action == null) headerActions.getChildren().clear();
        else headerActions.getChildren().setAll(action);

        tabContent.getChildren().setAll(tab.getPane());
        tab.onShown();
    }

    protected ModalTab getActiveTab() {
        return activeTab;
    }

    protected void setObjectTitle(String title) {
        objectTitle.setText(title);
    }

    /** Replaces the footer buttons; they are right-aligned. */
    protected void setFooter(Node... nodes) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().setAll(spacer);
        footer.getChildren().addAll(nodes);
    }

    protected static Button footerButton(String text) {
        Button button = new Button(text);
        button.setFocusTraversable(false);
        button.getStyleClass().add(Styles.SMALL);
        return button;
    }

    protected static Button closeFooterButton(String text) {
        Button button = footerButton(text);
        button.setOnAction(e -> App.closeModal());
        return button;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private VBox createLeftPanel(Ikon tileIcon, String navSectionLabel) {
        VBox pane = new VBox(0);
        pane.setPrefWidth(230);
        pane.setMinWidth(230);
        pane.setMaxWidth(230);
        pane.getStyleClass().add("help-left-panel");

        StackPane tile = new StackPane(IconColorUtil.colored(tileIcon, "-color-accent-emphasis", 28));
        tile.setPrefSize(64, 64);
        tile.setMaxSize(64, 64);
        tile.setStyle("-fx-background-color: -color-accent-subtle; -fx-background-radius: 14px;");

        objectTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        objectTitle.setWrapText(true);
        objectTitle.setMaxWidth(190);
        objectTitle.setAlignment(Pos.CENTER);
        objectTitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        objectSubtitle.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        objectSubtitle.setWrapText(true);
        objectSubtitle.setMaxWidth(190);
        objectSubtitle.setAlignment(Pos.CENTER);
        objectSubtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox identity = new VBox(8, tile, new VBox(2, objectTitle, objectSubtitle));
        identity.setAlignment(Pos.CENTER);
        ((VBox) identity.getChildren().get(1)).setAlignment(Pos.CENTER);
        identity.setPadding(new Insets(24, 20, 18, 20));

        Label navLabel = new Label(navSectionLabel.toUpperCase());
        navLabel.getStyleClass().add("help-nav-section-label");
        navLabel.setPadding(new Insets(12, 0, 4, 18));

        tabNav.setPadding(new Insets(0, 8, 12, 8));

        pane.getChildren().addAll(identity, new Separator(Orientation.HORIZONTAL), navLabel, tabNav);
        return pane;
    }

    private HBox createHeader() {
        titleLabel.getStyleClass().add(Styles.TITLE_3);
        subtitleLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(e -> App.closeModal());

        headerActions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(10, new VBox(1, titleLabel, subtitleLabel), spacer, headerActions, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 12, 24));
        header.getStyleClass().add("modal-header");
        return header;
    }

    private HBox createFooter() {
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 24, 14, 24));
        footer.getStyleClass().add("modal-footer");
        return footer;
    }
}
