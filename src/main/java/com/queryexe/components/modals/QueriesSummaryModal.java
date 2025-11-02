package com.queryexe.components.modals;

import java.util.ArrayList;
import java.util.List;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.queryexe.model.data.QueryData;
import com.queryexe.queryexe.App;

public class QueriesSummaryModal extends VBox {

    private VBox queriesBox;
    private List<QueryData> updateQueries;

    public QueriesSummaryModal(List<QueryData> updateQueries, Runnable onConfirmAction) {
        this.setStyle("-fx-background-color: -color-bg-default; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-border-color: -color-border-default; -fx-border-width: 1px;");
        this.setMaxSize(800, 400);
        this.setMinSize(800, 400);
        this.setPrefSize(800, 400);
        this.updateQueries = updateQueries;

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(5, 5, 0, 0));

        Region headerFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> {
            App.closeModal();
        });

        headerBox.getChildren().addAll(headerFillerRegion, closeButton);
        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);

        HBox titleBox = new HBox();
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setSpacing(10);
        titleBox.setPadding(new Insets(0, 20, 0, 20));

        Label titleLabel = new Label("Review Queries");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        FontIcon fileIcon = new FontIcon(MaterialDesignC.CODE_TAGS_CHECK);
        fileIcon.getStyleClass().add("custom-icon-30px");

        titleBox.getChildren().addAll(fileIcon, titleLabel);

        queriesBox = new VBox();
        queriesBox.setSpacing(8);
        queriesBox.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(queriesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPadding(new Insets(20));
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        for (QueryData query : updateQueries) {
            queriesBox.getChildren().add(new QueryBox(query));
        }

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setSpacing(10);
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));

        Button confirmButton = new Button("Confirm");
        confirmButton.setPrefWidth(75);
        confirmButton.setDefaultButton(true);
        confirmButton.setOnAction(event -> {
            getSelectedQueries();
            onConfirmAction.run();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(75);
        cancelButton.setOnAction(event -> {
            App.closeModal();
        });

        buttonsBox.getChildren().addAll(confirmButton, cancelButton);

        VBox.setVgrow(buttonsBox, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, titleBox, scrollPane, buttonsBox);
    }

    private void getSelectedQueries() {
        List<QueryData> selectedQueries = new ArrayList<>();
        for (var node : queriesBox.getChildren()) {
            QueryBox queryBox = (QueryBox) node;
            if (queryBox.getCheckBox().isSelected()) {
                selectedQueries.add(queryBox.getQuery());
            }
        }
        updateQueries.clear();
        updateQueries.addAll(selectedQueries);
    }

    class QueryBox extends HBox {
        private QueryData query;
        private CheckBox checkBox;

        public QueryBox(QueryData query) {
            this.query = query;
            this.setAlignment(Pos.CENTER_LEFT);
            this.getStyleClass().add("custom-query");
            this.setPadding(new Insets(12, 14, 12, 14));

            checkBox = new CheckBox();
            checkBox.setText(query.toString());
            checkBox.setSelected(true);
            checkBox.setStyle("-fx-cursor: hand; -fx-label-padding: 2px 2px 0 20px;");
            checkBox.setMaxWidth(Double.MAX_VALUE);
            checkBox.setWrapText(true);

            HBox.setHgrow(checkBox, Priority.ALWAYS);

            this.getChildren().add(checkBox);

        }

        public QueryData getQuery() {
            return query;
        }

        public void setQuery(QueryData query) {
            this.query = query;
        }

        public CheckBox getCheckBox() {
            return checkBox;
        }

        public void setCheckBox(CheckBox checkBox) {
            this.checkBox = checkBox;
        }
    }
}
