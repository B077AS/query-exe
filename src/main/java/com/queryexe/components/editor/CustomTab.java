package com.queryexe.components.editor;

import javafx.scene.control.*;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import com.queryexe.utils.IconColorUtil;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import com.queryexe.components.tree.CustomTree;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;
import com.queryexe.components.results.ResultBox;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import javafx.concurrent.Task;

public class CustomTab<T extends Node> extends Tab {

    private final String selectedStyle = "-fx-background-color: -color-bg-subtle; -fx-border-color: transparent, transparent, -color-tab-border-selected, transparent; -fx-border-width: 0 0 2px 0;";
    private final String unSelectedStyle = "-fx-background-color: transparent; -fx-border-color: transparent, transparent, transparent, transparent; -fx-border-width: 0 0 2px 0;";
    private FontIcon icon;
    private Label tabLabel;
    private HBox graphicBox;
    private CustomTabProgressIndicator progressIndicator;
    private boolean error = false;
    private Task<ResultBox> runningQueryTask;
    private ResultBox resultBox;
    private T content;
    private TabPane tabPane;
    private ConnectionObject connectionObject;
    private static ConnectionObject previousConnectionObject = null;
    private PreparedStatement currentStatement;

    public CustomTab(T content, TabPane tabPane) {
        this.content = content;
        this.tabPane = tabPane;
        initializeTab();
    }

    public CustomTab(T content, TabPane tabPane, ConnectionObject connectionObject) {
        this.content = content;
        this.tabPane = tabPane;
        this.connectionObject = connectionObject;
        initializeTab();
    }

    private void initializeTab() {
        this.setContent(content);
        this.setClosable(false);
        this.setStyle(unSelectedStyle);
        this.progressIndicator = new CustomTabProgressIndicator(this);

        graphicBox = new HBox();
        graphicBox.setSpacing(10);
        graphicBox.setAlignment(Pos.CENTER_LEFT);

        icon = new FontIcon(MaterialDesignC.CLOSE);
        IconColorUtil.apply(icon, "-color-fg-default", 17);
        icon.setCursor(Cursor.HAND);
        icon.setOnMouseClicked(event -> {
            tabPane.getTabs().remove(this);
            if (content instanceof VirtualizedScrollPane) {
                SQLEditor codeArea = (SQLEditor) ((VirtualizedScrollPane<?>) content).getContent();
                codeArea.shutdown();
            }
        });
        icon.setVisible(false);

        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != this) {
                icon.setVisible(false);
                this.setStyle(unSelectedStyle);
            } else {
                icon.setVisible(true);
                this.setStyle(selectedStyle);

                if (connectionObject != null) {
                    if (!connectionObject.equals(previousConnectionObject)) {

                        try {
                            DatabaseConnection.getInstance().initialize(this.connectionObject);
                        } catch (SQLException e) {
                        }

                        CustomTree cachedTree = App.getDatabaseTreeForConnection(connectionObject);
                        previousConnectionObject = connectionObject;

                        Platform.runLater(() -> {
                            content.requestFocus();
                            updateSplitPaneWithResult();

                            if (App.getCodeAndResultSplitPane() != null && App.getCodeAndResultSplitPane().getParent() != null) {
                                SplitPane mainSplit = (SplitPane) App.getCodeAndResultSplitPane().getParent().getParent();
                                double divider = mainSplit.getDividerPositions()[0];
                                if (mainSplit != null && mainSplit.getItems().size() > 0) {
                                    mainSplit.getItems().set(0, cachedTree);
                                    mainSplit.setDividerPositions(divider);
                                }
                            }
                        });
                    } else {
                        // Connection didn't change, just update UI
                        Platform.runLater(() -> {
                            content.requestFocus();
                            updateSplitPaneWithResult();
                        });
                    }
                } else {
                    // No connection object, just update UI
                    Platform.runLater(() -> {
                        content.requestFocus();
                    });
                }
            }
        });

        graphicBox.setOnMouseEntered(event -> {
            this.setStyle(selectedStyle);
            icon.setVisible(true);
        });

        graphicBox.setOnMouseExited(event -> {
            if (tabPane.getSelectionModel().getSelectedItem() != this) {
                this.setStyle(unSelectedStyle);
                icon.setVisible(false);
            }
        });

        if (connectionObject != null) {
            tabLabel = new Label(connectionObject.getConnectionName() + " - Query 1");
        } else {
            tabLabel = new Label("Result");
        }

        graphicBox.getChildren().addAll(tabLabel, icon);
        this.setGraphic(graphicBox);

        content.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.F4) {
                App.getTabPane().getTabs().remove(this);
            }
        });
    }

    public void setCustomText(String text) {
        tabLabel.setText(text);
    }

    public void setErrorHeader() {
        tabLabel.setText("Error");
        tabLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #c84164;");
        error = true;
    }

    public void setLoading() {
        graphicBox.getChildren().remove(1);
        graphicBox.getChildren().add(1, progressIndicator);
    }

    public void stopLoading() {
        graphicBox.getChildren().remove(1);
        graphicBox.getChildren().add(1, icon);
    }

    public void setResultBox(ResultBox resultBox) {
        this.resultBox = resultBox;
        if (App.getTabPane().getSelectionModel().getSelectedItem() == this) {
            updateSplitPaneWithResult();
        }
    }

    private void updateSplitPaneWithResult() {
        if (resultBox != null) {
            double dividerPosition = 0.7;
            if (App.getCodeAndResultSplitPane().getItems().size() > 1) {
                dividerPosition = App.getCodeAndResultSplitPane().getDividerPositions()[0];
                App.getCodeAndResultSplitPane().getItems().remove(1);
            }
            App.getCodeAndResultSplitPane().getItems().add(1, resultBox);
            App.getCodeAndResultSplitPane().setDividerPositions(dividerPosition);
        } else {
            if (App.getCodeAndResultSplitPane() != null && App.getCodeAndResultSplitPane().getItems().size() > 1) {
                App.getCodeAndResultSplitPane().getItems().remove(1);
            }
        }
    }

    public FontIcon getIcon() {
        return icon;
    }

    public void setIcon(FontIcon icon) {
        this.icon = icon;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public Label getTabLabel() {
        return tabLabel;
    }

    public void setRunningQueryTask(Task<ResultBox> task) {
        this.runningQueryTask = task;
    }

    public boolean isQueryRunning() {
        return runningQueryTask != null && runningQueryTask.isRunning();
    }

    public void cancelRunningQuery() {
        if (runningQueryTask != null) {
            runningQueryTask.cancel(true);
        }
    }

    public ConnectionObject getConnectionObject() {
        return connectionObject;
    }

    public void setCurrentStatement(PreparedStatement stmt) {
        this.currentStatement = stmt;
    }

    public PreparedStatement getCurrentStatement() {
        return currentStatement;
    }
}