package io.github.b077as.queryexe.app;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.b077as.jolt.JsonCodec;
import io.github.b077as.jolt.Rpc;
import io.github.b077as.jolt.RpcException;
import io.github.b077as.jolt.RpcOptions;
import io.github.b077as.jolt.WebWindow;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CancellationException;

/**
 * What the page can call: Jolt's built-in {@link Rpc} ({@code jolt.call} / {@code jolt.onEvent} in the page, see
 * {@code frontend/src/jolt.ts}) with Gson as its JSON library, so handlers keep reading a {@link JsonObject} through {@link Params} and
 * returning anything Gson can write.
 *
 * <p>Jolt hides the message of an unexpected exception from the page (it could reveal paths or SQL). Here the message is the point: a
 * failed query, a wrong password or a bad file name is shown to the user as a toast. So a handler's exception is passed on as an
 * {@link RpcException} with its message, and the page's error carries that text. Handlers run on virtual threads, never on the UI thread.
 */
@Slf4j
public class Bridge {

    @FunctionalInterface
    public interface Handler {
        /** @return the value to send back to the page (any Gson-serializable object, or {@code null}) */
        Object handle(JsonObject params) throws Exception;
    }

    /** The code of an error whose message is meant for the user (anything a handler threw). */
    public static final String FAILED = "failed";

    private static final Gson GSON = new Gson();

    private final Rpc rpc;

    public Bridge(WebWindow window) {
        RpcOptions options = RpcOptions.defaults().withCodec(JsonCodec.of(GSON::toJson, GSON::fromJson));
        this.rpc = Rpc.create(options).attach(window);
    }

    public Bridge on(String method, Handler handler) {
        rpc.on(method, call -> {
            JsonObject params = call.params(JsonObject.class);
            try {
                return handler.handle(params != null ? params : new JsonObject());
            } catch (RpcException | CancellationException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException(method + " was interrupted");
            } catch (Exception | LinkageError e) {
                // A LinkageError (a class missing from the packaged runtime, a native library that did not load) is an Error, not an
                // Exception: left alone the page would only learn "internal". Say what it was and log it.
                if (e instanceof LinkageError) {
                    log.error("{} failed: {}", method, e.toString(), e);
                }
                throw new RpcException(FAILED, e.getMessage() != null ? e.getMessage() : e.toString(), null, e);
            }
        });
        return this;
    }

    /** Pushes an event to the page ({@code jolt.onEvent}). Safe to call from any thread. */
    public void emit(String event, Object data) {
        rpc.emit(event, data);
    }
}
