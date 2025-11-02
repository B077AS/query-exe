package com.queryexe.components.editor;

import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import org.fxmisc.richtext.CodeArea;

import java.util.List;

public class AutocompletePopup extends PopupControl {

    private ListView<String> listView;
    private SQLEditor codeArea;

    public AutocompletePopup(SQLEditor codeArea) {
        this.codeArea = codeArea;
        this.listView = new ListView<>();
        this.listView.setPrefWidth(300);
        this.listView.setPrefHeight(200);
        this.listView.getStyleClass().add("autocomplete-list");
        this.listView.setFocusTraversable(false);

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
            listView.scrollTo(current + 1);
        }
    }

    public void selectPrevious() {
        int current = listView.getSelectionModel().getSelectedIndex();
        if (current > 0) {
            listView.getSelectionModel().select(current - 1);
            listView.scrollTo(current - 1);
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
}