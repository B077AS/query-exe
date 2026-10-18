package com.queryexe.components.modals.shell;

import javafx.scene.Node;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.kordamp.ikonli.Ikon;

/** A {@link ModalTab} whose pane is built up front; for tabs that need no lifecycle logic of their own. */
@Getter
@Accessors(fluent = true)
public class SimpleTab implements ModalTab {

    private final String name;
    private final String description;
    private final Ikon icon;
    private final Node pane;
    private final Node headerAction;

    public SimpleTab(String name, String description, Ikon icon, Node pane) {
        this(name, description, icon, pane, null);
    }

    public SimpleTab(String name, String description, Ikon icon, Node pane, Node headerAction) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.pane = pane;
        this.headerAction = headerAction;
    }

    @Override
    public Node getPane() {
        return pane;
    }
}
