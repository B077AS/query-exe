package io.github.b077as.queryexe.app;

import com.google.gson.Gson;
import io.github.b077as.jolt.JsonCodec;
import io.github.b077as.jolt.Rpc;
import io.github.b077as.jolt.RpcException;
import io.github.b077as.jolt.RpcOptions;
import io.github.b077as.jolt.WebWindow;
import lombok.extern.slf4j.Slf4j;

/**
 * What the page can call: Jolt's built-in {@link Rpc} ({@code jolt.call} / {@code jolt.onEvent} in the page, see
 * {@code frontend/src/jolt.ts}) with Gson as its JSON library, so handlers read their parameters as a {@code JsonObject} (through
 * {@link Params}) and return anything Gson can write. Handlers run on virtual threads, never on the UI thread.
 *
 * <p>Jolt hides the message of an unexpected exception from the page (it could reveal paths or SQL). Here the message is the point: a
 * failed query, a wrong password or a bad file name is shown to the user as a toast. So every exception a handler throws is answered
 * with code {@value #FAILED} and its message, and the page's {@code JoltRpcError} carries that text.
 */
@Slf4j
public final class PageRpc {

    /** The code of an error whose message is meant for the user (anything a handler threw). */
    public static final String FAILED = "failed";

    private static final Gson GSON = new Gson();

    private PageRpc() {
    }

    /** A new {@link Rpc} serving {@code window}. Register handlers with {@code rpc.on(name, JsonObject.class, params -> ...)}. */
    public static Rpc create(WebWindow window) {
        RpcOptions options = RpcOptions.defaults().withCodec(JsonCodec.of(GSON::toJson, GSON::fromJson));
        return Rpc.create(options)
                .onException(Exception.class, (call, e) -> failed(e))
                .onException(LinkageError.class, (call, e) -> {
                    // A class missing from the packaged runtime or a native library that did not load is an Error, not an Exception:
                    // it is a bug of the build, so say what it was to the page and log it as one.
                    log.error("{} failed: {}", call.method(), e.toString(), e);
                    return failed(e);
                })
                .attach(window);
    }

    private static RpcException failed(Throwable e) {
        return new RpcException(FAILED, e.getMessage() != null ? e.getMessage() : e.toString());
    }
}
