package com.queryexe.components.modals;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import com.queryexe.utils.IconColorUtil;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

@Slf4j
public class SQLEditorModal extends VBox {

    private CodeArea codeArea;
    private VirtualizedScrollPane<CodeArea> scroll;

    public SQLEditorModal(String initialQuery, Runnable onConfirmAction) {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(600, 500);
        this.setMinSize(600, 500);
        this.setPrefSize(600, 500);

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

        Label titleLabel = new Label("SQL Review");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        FontIcon fileIcon = new FontIcon(MaterialDesignC.CODE_TAGS_CHECK);
        IconColorUtil.apply(fileIcon, "-color-fg-default", 30);

        titleBox.getChildren().addAll(fileIcon, titleLabel);

        codeArea = new SQLEditor();
        codeArea.replaceText(initialQuery);
        codeArea.setWrapText(true);

        scroll = new VirtualizedScrollPane<>(codeArea);
        scroll.setMinHeight(0);
        scroll.setPrefHeight(Region.USE_COMPUTED_SIZE);

        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(20));
        contentBox.getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        HBox buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setSpacing(10);
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));

        Button confirmButton = new Button("Confirm");
        confirmButton.setPrefWidth(75);
        confirmButton.setDefaultButton(true);
        confirmButton.setOnAction(event -> {
            String queryText = codeArea.getText();


            Connection conn = null;
            try {
                conn = DatabaseConnection.getInstance().getConnection();
                conn.setAutoCommit(false);

                try (Statement stmt = conn.createStatement()) {
                    for (String statement : queryText.split(";\\s*(\\r?\\n)?")) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty()) {
                            log.debug("Executing statement: {}", trimmed);
                            stmt.execute(trimmed);
                        }
                    }

                    conn.commit();

                    App.closeAllModals();
                    new CustomNotification("Table Updated", "The table was updated successfully.", new FontIcon(MaterialDesignT.TABLE_CHECK)).showNotification();
                    onConfirmAction.run();

                } catch (Exception e) {
                    if (conn != null) {
                        try {
                            conn.rollback();
                            log.warn("Transaction rolled back due to error: {}", e.getMessage());
                        } catch (SQLException rollbackEx) {
                            log.error("SQLEditorModal failed", rollbackEx);
                        }
                    }
                    throw e;
                } finally {
                    if (conn != null) {
                        try {
                            conn.setAutoCommit(true);
                        } catch (SQLException ex) {
                            log.error("SQLEditorModal failed", ex);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("SQLEditorModal failed", e);
                new CustomNotification("Table Operation Failed", e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL)).showNotificationOnCustomPane((StackPane) this.getParent());
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(75);
        cancelButton.setOnAction(event -> {
            App.closeModal();
        });

        buttonsBox.getChildren().addAll(confirmButton, cancelButton);

        this.getChildren().addAll(headerBox, titleBox, contentBox, buttonsBox);
    }
}