package com.queryexe.components.editor;

import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import com.queryexe.components.extra.CustomNotification;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

public class CustomTabProgressIndicator extends StackPane {

    private ProgressIndicator progressIndicator;
    private FontIcon cancelButton;
    private ExecutorService executor;
    private CustomTab<?> parentTab;

    public CustomTabProgressIndicator(CustomTab<?> parentTab) {
        this.parentTab = parentTab;
        this.setAlignment(Pos.CENTER);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(17, 17);

        cancelButton = new FontIcon(MaterialDesignS.STOP);
        cancelButton.getStyleClass().add("custom-tab-icon");
        cancelButton.setVisible(false);

        cancelButton.setOnMouseClicked(event -> {
            if (this.parentTab != null && this.parentTab.getCurrentStatement() != null) {
                try {
                    this.parentTab.getCurrentStatement().cancel();
                } catch (SQLException e) {
                    CustomNotification notification = new CustomNotification("Cancel Failed", e.getMessage(), new FontIcon(MaterialDesignC.CANCEL));
                    notification.showNotification();
                }
            }
            this.executor.shutdownNow();
        });

        this.getChildren().addAll(progressIndicator, cancelButton);

        this.setOnMouseEntered(event -> {
            progressIndicator.setVisible(false);
            cancelButton.setVisible(true);
        });

        this.setOnMouseExited(event -> {
            progressIndicator.setVisible(true);
            cancelButton.setVisible(false);
        });
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }
}
