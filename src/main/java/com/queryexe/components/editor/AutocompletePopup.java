package com.queryexe.components.editor;

import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ListViewSkin;
import org.fxmisc.richtext.CodeArea;

import java.util.List;

public class AutocompletePopup extends PopupControl {

    private ListView<String> listView;
    private ScrollAwareListViewSkin listViewSkin;
    private SQLEditor codeArea;

    public AutocompletePopup(SQLEditor codeArea) {
        this.codeArea = codeArea;
        this.listView = new ListView<>();
        this.listView.setPrefWidth(300);
        this.listView.setPrefHeight(200);
        this.listView.getStyleClass().add("autocomplete-list");
        this.listView.setFocusTraversable(false);

        // ListView.scrollTo(int) always pins the target row to the very top of the
        // viewport (it fires a scrollToTopIndex event), which makes the selection
        // highlight appear stuck at the top while rows scroll underneath it. The
        // skin's VirtualFlow has a gentler scrollTo(int) that only scrolls as much
        // as needed to bring the row into view, matching normal keyboard navigation.
        listViewSkin = new ScrollAwareListViewSkin(listView);
        listView.setSkin(listViewSkin);

        setAutoHide(true);
        setHideOnEscape(true);
        setAutoFix(false);
    }

    public void show(List<String> suggestions, double x, double y) {
        if (suggestions == null || suggestions.isEmpty()) {
            hide();
            return;
        }

        listView.getItems().setAll(suggestions);
        listView.getSelectionModel().selectFirst();
        listViewSkin.scrollIntoView(0);

        if (!isShowing()) {
            show(codeArea, x, y);
        }
    }

    public void updateSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            hide();
            return;
        }

        String currentSelection = listView.getSelectionModel().getSelectedItem();
        listView.getItems().setAll(suggestions);

        if (currentSelection != null && suggestions.contains(currentSelection)) {
            listView.getSelectionModel().select(currentSelection);
        } else {
            listView.getSelectionModel().selectFirst();
            listViewSkin.scrollIntoView(0);
        }
    }

    public String getSelectedItem() {
        return listView.getSelectionModel().getSelectedItem();
    }

    public ListView<String> getListView() {
        return listView;
    }

    public void selectNext() {
        int current = listView.getSelectionModel().getSelectedIndex();
        if (current < listView.getItems().size() - 1) {
            listView.getSelectionModel().select(current + 1);
            listViewSkin.scrollIntoView(current + 1);
        }
    }

    public void selectPrevious() {
        int current = listView.getSelectionModel().getSelectedIndex();
        if (current > 0) {
            listView.getSelectionModel().select(current - 1);
            listViewSkin.scrollIntoView(current - 1);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new Skin<AutocompletePopup>() {
            @Override
            public AutocompletePopup getSkinnable() {
                return AutocompletePopup.this;
            }

            @Override
            public javafx.scene.Node getNode() {
                return listView;
            }

            @Override
            public void dispose() {
            }
        };
    }

    /**
     * Exposes the VirtualFlow's minimal-scroll {@code scrollTo(int)}, which only moves
     * the viewport as far as needed to reveal a row. The public {@link ListView#scrollTo(int)}
     * instead always pins the target row to the top of the viewport.
     */
    private static class ScrollAwareListViewSkin extends ListViewSkin<String> {
        ScrollAwareListViewSkin(ListView<String> listView) {
            super(listView);
        }

        void scrollIntoView(int index) {
            getVirtualFlow().scrollTo(index);
        }
    }
}