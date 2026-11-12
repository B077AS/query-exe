package io.github.b077as.queryexe.launcher;

import io.github.b077as.jpack.Jpack;
import io.github.b077as.jpack.JpackException;
import io.github.b077as.jpack.UpdateCheck;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Drives the launch sequence on a background thread, on top of the jpack stub that started the launcher:
 *
 * <ol>
 *   <li>ask the stub whether the release published on GitHub differs from the installed one,</li>
 *   <li>if so, have it download and verify the changed parts and follow the progress. The jars, the Java runtime, the stub
 *       and this launcher are all parts of one release, so there is a single update path,</li>
 *   <li>tell the stub to start the client once this process has exited. The stub swaps the downloaded parts in while no Java
 *       process is running, then starts the {@value #APP_ENTRY} entry.</li>
 * </ol>
 *
 * <p>An update is mandatory: once a newer release is known, a failed download is an error and the stale install is never
 * started. Only when the update server cannot be asked at all (offline, GitHub down, no release yet) does the installed
 * version start as it is.
 *
 * <p>Everything is reported through {@link Listener}; the callbacks fire on the background thread.
 */
@Slf4j
public class UpdateManager {

    /** The launch entry of the client in {@code jpack.json} (the plugin names it "app"). */
    private static final String APP_ENTRY = "app";

    /** How long the full database stays on screen before the window closes: the water needs about a second to reach the brim. */
    private static final long HOLD_FULL_MS = 900;

    public interface Listener {
        /** The version that is installed now. */
        void onInstalled(String version);

        void onPhase(Phase phase);

        /** A newer release exists: the installed version, the new one and the size of what will be downloaded. */
        void onUpdate(String current, String latest, long bytes);

        /** Download progress in bytes. */
        void onProgress(long received, long total);

        /** The check failed, so the installed version is started. {@code message} says why, for the page. */
        void onNote(String message);

        /** The client was not started. */
        void onError(String message);

        /** The client was handed to the stub; the launcher should close now. */
        void onLaunched();
    }

    private final Listener listener;

    public UpdateManager(Listener listener) {
        this.listener = listener;
    }

    // -- Real flow -----------------------------------------------------------------------------

    public void run() {
        if (!Jpack.isAvailable()) {
            // A dev run of the launcher: there is no stub, hence nothing to update and nothing to hand over to.
            log.warn("Not started by a jpack stub; nothing to update or start (use -Dlauncher.demo=true to preview the window)");
            listener.onError("QueryExe must be started from its installation");
            return;
        }
        try (Jpack jpack = Jpack.connect()) {
            log.info("Installed {} {} (entry '{}', canUpdate={})", jpack.appName(), jpack.appVersion(), jpack.entry(), jpack.canUpdate());
            boolean updateRequired = false;
            try {
                listener.onInstalled(jpack.appVersion());
                listener.onPhase(Phase.CHECKING);
                pause(450); // let the first frame land
                if (jpack.canUpdate()) {
                    UpdateCheck update = jpack.check().get(30, TimeUnit.SECONDS);
                    log.info("Installed {}, latest {} (available={}, {} bytes to download)",
                            jpack.appVersion(), update.version(), update.available(), update.downloadSize());
                    if (update.available()) {
                        updateRequired = true;
                        listener.onUpdate(jpack.appVersion(), update.version(), update.downloadSize());
                        downloadAndStage(jpack);
                    }
                } else {
                    // No update address (a dev build) or an install folder that cannot be written to.
                    log.info("This installation cannot update itself; starting the installed version");
                }
                handOver(jpack, HOLD_FULL_MS);
            } catch (Exception e) {
                log.warn("Update flow failed: {}", describe(e));
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // The update server could not be asked: run what is installed. A known-stale install is never started.
                if (!updateRequired) {
                    try {
                        listener.onNote("Can't reach GitHub, starting " + jpack.appVersion());
                        handOver(jpack, HOLD_FULL_MS);
                        return;
                    } catch (Exception handOverFailure) {
                        log.error("Failed to hand over to the installed client: {}", describe(handOverFailure));
                    }
                }
                listener.onError(friendlyError(e));
            }
        } catch (JpackException e) {
            log.error("Could not reach the jpack stub: {}", e.getMessage());
            listener.onError("QueryExe could not start. Please reinstall it");
        }
    }

    /** Downloads the changed parts of the new release; the swap itself happens once this process has exited. */
    private void downloadAndStage(Jpack jpack) throws Exception {
        listener.onPhase(Phase.DOWNLOADING);
        jpack.download(listener::onProgress).get(30, TimeUnit.MINUTES);
        listener.onPhase(Phase.INSTALLING);
        pause(600);
    }

    /** Tells the stub to swap the downloaded files in and start the client as soon as this process exits. */
    private void handOver(Jpack jpack, long beat) {
        listener.onPhase(Phase.STARTING);
        pause(beat); // the water reaches the top
        jpack.startAfterExit(APP_ENTRY);
        listener.onLaunched();
    }

    private static String describe(Throwable e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        return cause.toString();
    }

    private static String friendlyError(Exception e) {
        if (e instanceof TimeoutException) {
            return "The update server isn't answering. Try again.";
        }
        return "The update stopped. Check your connection and try again.";
    }

    // -- Demo flow -----------------------------------------------------------------------------

    /** A scripted run so the window can be looked at without a stub or a release. */
    public void runDemo() {
        try {
            listener.onInstalled("0.3.1");
            listener.onPhase(Phase.CHECKING);
            pause(1400);
            long total = 15_518_000L;
            listener.onUpdate("0.3.1", "0.4.0", total);
            listener.onPhase(Phase.DOWNLOADING);
            for (int i = 0; i <= 100; i++) {
                listener.onProgress(total * i / 100, total);
                pause(i < 42 ? 60 : 85);
            }
            listener.onPhase(Phase.INSTALLING);
            pause(1300);
            listener.onPhase(Phase.STARTING);
            pause(1600);
            listener.onLaunched();
            // (a real run ends here: the stub swaps the files in and starts the client)
        } catch (RuntimeException e) {
            listener.onError("The demo was interrupted");
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
