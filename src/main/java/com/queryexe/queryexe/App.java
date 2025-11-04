package com.queryexe.queryexe;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.queryexe.components.home.ConnectionsPane;
import com.queryexe.components.menu.CustomMenuBar;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.menu.CustomToolBar;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.components.tree.CustomTree;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.DatabaseConnection;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import atlantafx.base.controls.ModalPane;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

public class App extends Application {

    private static Scene scene;
    private static StackPane stackPane;
    private static CustomToolBar toolbar;
    private static CustomMenuBar menuBar;
    private static TabPane tabPane;
    private static SplitPane codeAndResultSplitPane;
    private static ModalPane modalPane;
    private static ModalPane secondaryModalPane;
    private static VBox headerBox;
    private static CustomTree customTree;
    private static Map<ConnectionObject, CustomTree> databaseTreeCache = new HashMap<>();
    private static Stack<Node> modalStack = new Stack<>();

    @Override
    public void start(Stage stage) {

        Image appIcon = new Image(getClass().getResourceAsStream("/icon.png"));
        stage.getIcons().add(appIcon);

        stackPane = new StackPane();
        BorderPane borderPane = new BorderPane();

        headerBox = new VBox();
        headerBox.setAlignment(Pos.CENTER);

        toolbar = new CustomToolBar();
        toolbar.setDisable(true);

        menuBar = new CustomMenuBar();
        for (Menu menu : menuBar.getMenus()) {
            if (!"About".equals(menu.getText())) {
                menu.setDisable(true);
            }
        }

        headerBox.getChildren().addAll(menuBar, toolbar);

        borderPane.setTop(headerBox);

        tabPane = new TabPane();
        tabPane.setTabDragPolicy(TabDragPolicy.REORDER);

        modalPane = new ModalPane();
        modalPane.setPersistent(false);

        secondaryModalPane = new ModalPane();
        secondaryModalPane.setPersistent(false);

        ConnectionsPane connectionsPane = new ConnectionsPane();
        stackPane.getChildren().add(connectionsPane);
        borderPane.setCenter(stackPane);

        StackPane mainStackPane = new StackPane();
        mainStackPane.getChildren().addAll(borderPane, modalPane, secondaryModalPane);

        scene = new Scene(mainStackPane, 1100, 500);
        Application.setUserAgentStylesheet(App.class.getClassLoader().getResource("dracula.css").toExternalForm());
        stage.setTitle("QueryExe");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(640);
        stage.setMaximized(true);
        stage.setOpacity(0);
        stage.show();

        stage.setOnCloseRequest(event -> {
            try {
                DatabaseConnection.getInstance().shutdown();
                System.out.println("Connection Closed");
            } catch (Exception e) {
                //e.printStackTrace();
            }
        });

        Platform.runLater(() -> {
            if (com.sun.jna.Platform.isWindows()) {
                enableDarkTitleBar(stage);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            stage.setOpacity(1);
        });

    }

    private void enableDarkTitleBar(Stage stage) {
        try {
            HWND hwnd = User32.INSTANCE.FindWindow(null, stage.getTitle());

            if (hwnd == null) {
                System.err.println("Could not find window handle");
                return;
            }

            final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
            final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

            boolean success = DwmSupport.DwmSetWindowAttribute(
                    hwnd,
                    DWMWA_USE_IMMERSIVE_DARK_MODE,
                    true
            );

            if (!success) {
                DwmSupport.DwmSetWindowAttribute(
                        hwnd,
                        DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
                        true
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to enable dark titlebar: " + e.getMessage());
        }
    }

    private static class DwmSupport {
        static {
            if (com.sun.jna.Platform.isWindows()) {
                Native.register("dwmapi");
            }
        }

        public static native int DwmSetWindowAttribute(
                HWND hwnd,
                int dwAttribute,
                Pointer pvAttribute,
                int cbAttribute
        );

        public static boolean DwmSetWindowAttribute(HWND hwnd, int dwAttribute, boolean value) {
            try {
                int[] attrValue = new int[]{value ? 1 : 0};
                Pointer ptr = new com.sun.jna.Memory(4);
                ptr.setInt(0, attrValue[0]);
                int result = DwmSetWindowAttribute(hwnd, dwAttribute, ptr, 4);
                return result == 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static void connect() {

        if (!tabPane.getTabs().isEmpty()) {
            Platform.runLater(() -> {
                tabPane.getTabs().clear();
            });
        }

        customTree = new CustomTree();
        SQLEditor codeArea = new SQLEditor();
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<CodeArea>(codeArea);

        CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, tabPane, DatabaseConnection.getInstance().getCurrentConnectionObject());

        tabPane.getTabs().add(queryTab);
        tabPane.getSelectionModel().select(queryTab);
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            if (tabPane.getTabs().isEmpty()) {
                if (codeAndResultSplitPane.getItems().size() > 1) {
                    codeAndResultSplitPane.getItems().remove(1);
                }
            }
        });

        VBox.setVgrow(codeArea, Priority.ALWAYS);

        codeAndResultSplitPane = new SplitPane();
        codeAndResultSplitPane.setStyle("-fx-background-color: -color-bg-default;");
        codeAndResultSplitPane.setOrientation(Orientation.VERTICAL);
        codeAndResultSplitPane.getItems().add(tabPane);

        SplitPane splitpane = new SplitPane(customTree, codeAndResultSplitPane);
        splitpane.setOrientation(Orientation.HORIZONTAL);
        splitpane.setDividerPositions(0.15);
        splitpane.widthProperty().addListener((obs, oldVal, newVal) -> {
            double oldPosition = splitpane.getDividerPositions()[0];
            Platform.runLater(() -> {
                splitpane.setDividerPosition(0, oldPosition);
            });
        });

        Platform.runLater(() -> {
            for (Menu menu : menuBar.getMenus()) {
                menu.setDisable(false);
            }
            toolbar.setDisable(false);
            stackPane.getChildren().clear();
            stackPane.getChildren().add(splitpane);
            codeArea.layout();
        });
    }

    public static CustomTree getDatabaseTreeForConnection(ConnectionObject connection) {
        customTree = databaseTreeCache.computeIfAbsent(connection, conn -> {
            return new CustomTree();
        });
        return customTree;
    }

    public static void removeDatabaseTreeForConnection(ConnectionObject connection) {
        databaseTreeCache.remove(connection);
    }

    public static void showModal(Node modal) {
        modalPane.show(modal);
        modalPane.usePredefinedTransitionFactories(null);
        modal.requestFocus();
        modalStack.push(modal);
    }

    public static void showModalOnTop(Node modal) {
        secondaryModalPane.show(modal);
        secondaryModalPane.usePredefinedTransitionFactories(null);
        modal.requestFocus();
        modalStack.push(modal);
    }

    public static void closeTopModal() {
        if (!modalStack.isEmpty()) {
            Node topModal = modalStack.pop();

            if (secondaryModalPane.getContent() == topModal) {
                secondaryModalPane.hide(true);
            } else if (modalPane.getContent() == topModal) {
                modalPane.hide(true);
            }
        }
    }

    public static void closeAllModals() {
        modalStack.clear();
        secondaryModalPane.hide(true);
        modalPane.hide(true);
    }

    public static void closeModal() {
        if (!modalStack.isEmpty()) {
            closeTopModal();
        } else {
            modalPane.hide(true);
        }
    }

    public static void closeConnection() {
        goHome();

        try {
            DatabaseConnection.getInstance().closeAll();
        } catch (Exception e) {
        }
    }

    public static void goHome() {
        for (Menu menu : menuBar.getMenus()) {
            if (!"About".equals(menu.getText())) {
                menu.setDisable(true);
            }
        }
        toolbar.setDisable(true);
        stackPane.getChildren().clear();

        ConnectionsPane connectionsPane = new ConnectionsPane();
        stackPane.getChildren().add(connectionsPane);
    }

    public static ModalPane getSecondaryModalPane() {
        return secondaryModalPane;
    }

    public static boolean isModalShowing() {
        return !modalStack.isEmpty();
    }

    public static int getModalStackSize() {
        return modalStack.size();
    }

    public static StackPane getStackPane() {
        return stackPane;
    }

    public static TabPane getTabPane() {
        return tabPane;
    }

    public static SplitPane getCodeAndResultSplitPane() {
        return codeAndResultSplitPane;
    }

    public static VBox getHeaderBox() {
        return headerBox;
    }

    public static CustomTree getDatabaseTree() {
        return customTree;
    }

    public static ModalPane getModalPane() {
        return modalPane;
    }

    public static Scene getScene() {
        return scene;
    }

    public static void appStart(String[] args) {
        launch();
    }
}