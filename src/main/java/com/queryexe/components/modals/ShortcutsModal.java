package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import com.queryexe.components.modals.shell.ModalUi;
import com.queryexe.components.modals.shell.SidebarModal;
import com.queryexe.components.modals.shell.SimpleTab;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The editor's keyboard shortcuts, one tab per area. */
public class ShortcutsModal extends SidebarModal {

    private record ShortcutCategory(String title, Ikon icon, String[][] shortcuts) {
    }

    private static final Pattern KEY_SEPARATOR = Pattern.compile(" \\+ | / ");

    private final ShortcutCategory[] categories = new ShortcutCategory[]{
            new ShortcutCategory("Editor", MaterialDesignC.CODE_TAGS, new String[][]{
                    {"Ctrl + Space", "Trigger autocomplete"},
                    {"Ctrl + Enter", "Run selection, or current statement if nothing is selected"},
                    {"Ctrl + Shift + Enter", "Run entire query/script"},
                    {"Ctrl + D", "Duplicate current line"},
                    {"Ctrl + Shift + F", "Format selection, or entire script if nothing is selected"},
                    {"Ctrl + F", "Find and replace"},
                    {"Ctrl + O", "Open file"},
                    {"Ctrl + S", "Save file"}
            }),
            new ShortcutCategory("Find & Replace", MaterialDesignM.MAGNIFY, new String[][]{
                    {"Enter", "Find next match"},
                    {"Shift + Enter", "Find previous match"},
                    {"Ctrl + Enter", "Replace all occurrences"},
                    {"Enter", "Replace current match, when in the replace field"},
                    {"Escape", "Close find popup"}
            }),
            new ShortcutCategory("Autocomplete", MaterialDesignA.AUTO_FIX, new String[][]{
                    {"Enter / Tab", "Insert selected suggestion"},
                    {"↑ / ↓", "Navigate through suggestions"},
                    {"Escape / Space", "Close autocomplete popup"}
            })
    };

    public ShortcutsModal() {
        super(MaterialDesignK.KEYBOARD_OUTLINE, "Keyboard Shortcuts", null, "Shortcuts", 900, 600);

        for (ShortcutCategory category : categories) {
            int count = category.shortcuts().length;
            addTab(new SimpleTab(category.title(), count + (count == 1 ? " shortcut" : " shortcuts"),
                    category.icon(), pane(category)));
        }
        setFooter(closeFooterButton("Close"));
    }

    private VBox pane(ShortcutCategory category) {
        VBox list = new VBox();
        for (String[] shortcut : category.shortcuts()) {
            list.getChildren().add(row(shortcut[0], shortcut[1]));
        }
        return ModalUi.listTab(list);
    }

    private static Node row(String keys, String description) {
        Label text = new Label(description);
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        Node keyCaps = keyCaps(keys);
        HBox row = new HBox(16, keyCaps, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("modal-row");
        return row;
    }

    /** Each key as its own cap; the "+" of a combination and the "/" between alternatives stay muted text between them. */
    private static Node keyCaps(String keys) {
        FlowPane caps = new FlowPane(6, 4);
        caps.setAlignment(Pos.CENTER_LEFT);
        caps.setPrefWrapLength(230);
        caps.setMinWidth(230);
        caps.setPrefWidth(230);
        caps.setMaxWidth(230);

        Matcher matcher = KEY_SEPARATOR.matcher(keys);
        int start = 0;
        while (matcher.find()) {
            caps.getChildren().addAll(cap(keys.substring(start, matcher.start())), separator(matcher.group().trim()));
            start = matcher.end();
        }
        caps.getChildren().add(cap(keys.substring(start)));
        return caps;
    }

    private static Label cap(String key) {
        Label cap = new Label(key);
        cap.getStyleClass().add("help-kbd-chip");
        return cap;
    }

    private static Label separator(String symbol) {
        Label label = new Label(symbol);
        label.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        return label;
    }
}
