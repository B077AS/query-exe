package com.queryexe.components.results;

import java.sql.SQLException;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import com.queryexe.components.extra.CustomNotification;

public class ResultErrorBox extends HBox {

    public ResultErrorBox(String query, SQLException e) {
        this.setPadding(new Insets(10, 10, 10, 10));
        this.setSpacing(10);
        this.setAlignment(Pos.TOP_LEFT);

        TextFlow errorFlow = new TextFlow();
        errorFlow.setPadding(new Insets(0, 40, 0, 0));
        errorFlow.setStyle("-fx-background-color: -color-bg-default;");
        errorFlow.prefWidthProperty().bind(this.widthProperty().subtract(50));

        ScrollPane scrollPane = new ScrollPane(errorFlow);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);

        Text errorQueryLabel = new Text("Query: ");
        errorQueryLabel.setStyle("-fx-font-weight: bold; -fx-fill: #c84164;");

        Text queryText = new Text(query.trim() + "\n\n");
        queryText.setStyle("-fx-font-style: italic; -fx-fill: #d4d4d7;");

        Text messageLabel = new Text("Message: ");
        messageLabel.setStyle("-fx-font-weight: bold; -fx-fill: #c84164;");

        Text messageText = new Text(e.getMessage() + "\n\n");
        messageText.setStyle("-fx-font-style: italic; -fx-fill: #d4d4d7;");

        Text errorCodeLabel = new Text("Error Code: ");
        errorCodeLabel.setStyle("-fx-font-weight: bold; -fx-fill: #c84164;");

        Text errorCodeText = new Text(e.getErrorCode() + "\n");
        errorCodeText.setStyle("-fx-font-style: italic; -fx-fill: #d4d4d7;");

        errorFlow.getChildren().addAll(
                errorQueryLabel,
                queryText,
                messageLabel,
                messageText,
                errorCodeLabel,
                errorCodeText
        );

        Region errorSpacer = new Region();

        Button copyButton = new Button(null, new FontIcon(MaterialDesignC.CONTENT_COPY));
        copyButton.setFocusTraversable(false);
        copyButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.BUTTON_OUTLINED);
        copyButton.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(query.trim() + " - " + e.getMessage() + " - " + e.getErrorCode());
            clipboard.setContent(content);

            CustomNotification copiedNotification = new CustomNotification("Error Copied", "The error details were copied to your clipboard.", new FontIcon(MaterialDesignC.CLIPBOARD_CHECK_MULTIPLE_OUTLINE));
            copiedNotification.showNotification();
        });

        HBox.setHgrow(errorSpacer, Priority.ALWAYS);

        this.getChildren().addAll(scrollPane, errorSpacer, copyButton);
    }
}
