package com.queryexe.components.results;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class SimpleResultBox extends VBox {

    public SimpleResultBox(int updateCount, String query) {
        this.setPadding(new Insets(10, 10, 10, 10));
        this.setSpacing(10);
        this.setAlignment(Pos.TOP_LEFT);

        TextFlow errorFlow = new TextFlow();
        errorFlow.setPadding(new Insets(0, 40, 0, 0));
        errorFlow.setStyle("-fx-background-color: -color-bg-default;");
        errorFlow.prefWidthProperty().bind(this.widthProperty());

        ScrollPane scrollPane = new ScrollPane(errorFlow);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);

        Text errorQueryLabel = new Text("Query run succesfully: ");
        errorQueryLabel.setStyle("-fx-font-weight: bold; -fx-fill: #42C841;");

        Text queryText = new Text(query.trim() + "\n\n");
        queryText.setStyle("-fx-font-style: italic; -fx-fill: #d4d4d7;");

        Text rowsAffectedText = new Text("Rows Affected: ");
        rowsAffectedText.setStyle("-fx-font-weight: bold; -fx-fill: #42C841;");

        Text rowsText = new Text(updateCount + "\n\n");
        rowsText.setStyle("-fx-font-style: italic; -fx-fill: #d4d4d7;");

        errorFlow.getChildren().addAll(
                errorQueryLabel,
                queryText,
                rowsAffectedText,
                rowsText
        );

        this.getChildren().addAll(scrollPane);
    }
}
