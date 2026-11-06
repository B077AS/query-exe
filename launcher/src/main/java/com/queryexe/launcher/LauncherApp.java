package com.queryexe.launcher;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import com.queryexe.launcher.update.Phase;
import com.queryexe.launcher.update.UpdateManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * The launcher window: a small, frameless, rounded "card" in the style of
 * Discord's updater. It shows the QueryExe icon, an animated status phrase, and
 * a progress bar, then hands off to {@link UpdateManager} which does the actual
 * version check / download / launch.
 *
 * <p>Styled by {@code launcher.css} alone, using the same {@code -color-*} token
 * names as the query-exe client's stylesheet so it reads as part of the client.
 */
@Slf4j
public class LauncherApp extends Application implements UpdateManager.Listener {

    private static String[] launchArgs = new String[0];

    private Stage stage;
    private Label statusLabel;
    private ProgressBar progressBar;

    private double dragOffsetX;
    private double dragOffsetY;

    private boolean demoMode;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.demoMode = isDemoMode();

        Application.setUserAgentStylesheet(Objects.requireNonNull(
                LauncherApp.class.getClassLoader().getResource("launcher.css")).toExternalForm());

        StackPane root = new StackPane(buildCard());
        root.getStyleClass().add("launcher-root");
        root.setPadding(new Insets(30, 40, 52, 40));

        Scene scene = new Scene(root, 440, 486);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) closeLauncher();
        });

        Image icon = new Image(Objects.requireNonNull(
                LauncherApp.class.getClassLoader().getResourceAsStream("icon.png")));
        primaryStage.getIcons().add(icon);

        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setTitle("QueryExe");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();

        playEntranceAnimation(root);
        startUpdateWorker();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private StackPane buildCard() {
        // Centerpiece: the app icon inside pulsating rings, wordmark below.
        Color ringColor = Color.web("#9580ff");
        StackPane pulseArea = new StackPane(
                createPulseRing(ringColor, 0),
                createPulseRing(ringColor, 800),
                createPulseRing(ringColor, 1600),
                buildLogo(96)
        );
        pulseArea.setMinSize(180, 180);
        pulseArea.setMaxSize(180, 180);

        Label title = new Label("QueryExe");
        title.getStyleClass().add("launcher-title");

        VBox center = new VBox(2, pulseArea, title);
        center.setAlignment(Pos.CENTER);
        VBox.setVgrow(center, Priority.ALWAYS);

        // Bottom: status phrase + progress bar.
        statusLabel = new Label(Phase.CHECKING.getPhrase());
        statusLabel.getStyleClass().add("launcher-status");

        progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.getStyleClass().add("launcher-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        VBox bottom = new VBox(12, statusLabel, progressBar);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, center, bottom);
        card.getStyleClass().add("launcher-card");
        card.setPadding(new Insets(16, 22, 26, 22));
        card.setPrefSize(360, 404);
        card.setAlignment(Pos.TOP_CENTER);
        enableWindowDrag(card);

        // Close button pinned to the card's top-right corner via StackPane overlay.
        Button close = new Button();
        close.setGraphic(FontIcon.of(Feather.X, 14));
        close.getStyleClass().add("launcher-window-button");
        close.setFocusTraversable(false);
        close.setOnAction(e -> closeLauncher());

        StackPane wrapper = new StackPane(card, close);
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(8, 8, 0, 0));
        return wrapper;
    }

    /**
     * A genuinely round badge, matching the pulse rings: a filled circle in the
     * icon's own gradient colors, with the app's database+lightning glyph
     * redrawn directly on top from icon.svg's own path data. Compositing the
     * baked icon.png (a rounded square) on top of the circle left a visible
     * seam where the raster's own background edge met the badge; drawing just
     * the glyph avoids that entirely — same trick komm's launcher uses for its
     * own logo.
     */
    private StackPane buildLogo(double size) {
        Circle badge = new Circle(size / 2);
        badge.setFill(new LinearGradient(0, 1, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#685ab3")),
                new Stop(1, Color.web("#e373fd"))));

        Group glyph = buildGlyph(size * 0.5);

        StackPane badgeGroup = new StackPane(badge, glyph);
        badgeGroup.setMinSize(size, size);
        badgeGroup.setMaxSize(size, size);
        return badgeGroup;
    }

    /**
     * The "database-zap" glyph (Lucide icon set) redrawn from icon.svg's own
     * path data — viewBox 0 0 24 24, white stroke, no fill, matching the
     * source SVG's stroke-width 2.3 (scaled uniformly along with everything
     * else via the group's scale transform).
     */
    private Group buildGlyph(double size) {
        Ellipse cap = new Ellipse(12, 5, 9, 3);
        SVGPath leftSide = new SVGPath();
        leftSide.setContent("M3 5V19A9 3 0 0 0 15 21.84");
        SVGPath rightSide = new SVGPath();
        rightSide.setContent("M21 5V8");
        SVGPath bolt = new SVGPath();
        bolt.setContent("M21 12L18 17H22L19 22");
        SVGPath backRim = new SVGPath();
        backRim.setContent("M3 12A9 3 0 0 0 14.59 14.87");

        Group glyph = new Group(cap, leftSide, rightSide, bolt, backRim);
        for (Node n : glyph.getChildren()) {
            Shape shape = (Shape) n;
            shape.setFill(Color.TRANSPARENT);
            shape.setStroke(Color.WHITE);
            shape.setStrokeWidth(2.3);
            shape.setStrokeLineCap(StrokeLineCap.ROUND);
            shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }
        double scale = size / 24.0;
        glyph.setScaleX(scale);
        glyph.setScaleY(scale);
        return glyph;
    }

    private void enableWindowDrag(Region handle) {
        handle.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        handle.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void playEntranceAnimation(Region root) {
        FadeTransition fade = new FadeTransition(Duration.millis(260), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(300), root);
        scale.setFromX(0.96); scale.setFromY(0.96);
        scale.setToX(1);     scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }

    private Circle createPulseRing(Color color, long delayMillis) {
        Circle ring = new Circle(80);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(color);
        ring.setStrokeWidth(2);
        ring.setOpacity(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(2400), ring);
        scale.setFromX(0.25); scale.setFromY(0.25);
        scale.setToX(1.0);   scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);
        scale.setCycleCount(ScaleTransition.INDEFINITE);

        FadeTransition fade = new FadeTransition(Duration.millis(2400), ring);
        fade.setFromValue(0.55);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);
        fade.setCycleCount(FadeTransition.INDEFINITE);

        ParallelTransition pt = new ParallelTransition(scale, fade);
        pt.setDelay(Duration.millis(delayMillis));
        pt.setCycleCount(ParallelTransition.INDEFINITE);
        pt.play();

        return ring;
    }

    /** Quick cross-fade of the status phrase whenever it changes. */
    private void setStatusText(String text, boolean error) {
        FadeTransition out = new FadeTransition(Duration.millis(110), statusLabel);
        out.setFromValue(statusLabel.getOpacity());
        out.setToValue(0);
        out.setOnFinished(e -> {
            statusLabel.setText(text);
            statusLabel.getStyleClass().remove("is-error");
            if (error) statusLabel.getStyleClass().add("is-error");
            FadeTransition in = new FadeTransition(Duration.millis(140), statusLabel);
            in.setFromValue(0);
            in.setToValue(1);
            in.play();
        });
        out.play();
    }

    // ── Worker wiring ─────────────────────────────────────────────────────────

    private void startUpdateWorker() {
        UpdateManager manager = new UpdateManager(this, launchArgs);
        Thread worker = new Thread(demoMode ? manager::runDemo : manager::run, "launcher-update-worker");
        worker.setDaemon(true);
        worker.start();
    }

    // ── UpdateManager.Listener (called off the FX thread) ─────────────────────

    @Override
    public void onPhase(Phase phase) {
        Platform.runLater(() -> {
            setStatusText(phase.getPhrase(), false);
            if (phase.isDeterminate()) {
                progressBar.setProgress(0);
            } else if (phase == Phase.DONE || phase == Phase.UP_TO_DATE) {
                progressBar.setProgress(1);
            } else {
                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            }
        });
    }

    @Override
    public void onProgress(double fraction) {
        Platform.runLater(() -> progressBar.setProgress(fraction));
    }

    @Override
    public void onError(String message) {
        Platform.runLater(() -> {
            setStatusText(message, true);
            progressBar.setVisible(false);
            progressBar.setManaged(false);
        });
    }

    @Override
    public void onLaunched() {
        Platform.runLater(() -> {
            // Brief beat on "Starting QueryExe…" before the launcher disappears.
            FadeTransition fade = new FadeTransition(Duration.millis(220), stage.getScene().getRoot());
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setOnFinished(e -> closeLauncher());
            fade.play();
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private void closeLauncher() {
        Platform.exit();
        // Under an AppImage the JVM must outlive the window: exiting now would
        // unmount the FUSE image holding the runtime the client is using.
        // UpdateManager's mount keeper calls System.exit once the client is done.
        if (UpdateManager.isAppImageMountGuarded()) return;
        // Ensure the JVM exits even if a non-daemon thread is lingering.
        Thread.ofVirtual().start(() -> System.exit(0));
    }

    private static boolean isDemoMode() {
        if (Boolean.parseBoolean(System.getProperty("launcher.demo", "false"))) return true;
        for (String a : launchArgs) {
            if ("demo".equalsIgnoreCase(a) || "--demo".equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    public static void appStart(String[] args) {
        launchArgs = args == null ? new String[0] : args;
        launch(args);
    }
}
