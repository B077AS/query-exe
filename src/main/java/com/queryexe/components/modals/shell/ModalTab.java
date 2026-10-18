package com.queryexe.components.modals.shell;

import javafx.scene.Node;
import org.kordamp.ikonli.Ikon;

/** One section of a {@link SidebarModal}: a nav entry in the sidebar plus the pane shown when it is selected. */
public interface ModalTab {

    String name();

    /** Short blurb shown under the tab name in the modal header. */
    default String description() { return ""; }

    Ikon icon();

    Node getPane();

    /** Optional control shown at the right of the header while this tab is active (e.g. an "Add" button). */
    default Node headerAction() { return null; }

    /** Called every time the tab becomes active. Must be idempotent. */
    default void onShown() {}
}
