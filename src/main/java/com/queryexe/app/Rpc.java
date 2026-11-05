package com.queryexe.app;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.b077as.jolt.WebWindow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Request/response and pushed events on top of Jolt's plain string messages. The page half is {@code frontend/src/jolt.ts}.
 *
 * <p>The page sends {@code {"id": 1, "method": "mail.list", "params": {...}}}; this answers with
 * {@code {"id": 1, "result": ...}} or {@code {"id": 1, "error": "..."}}. Java can also push {@code {"event": name, "data": ...}}
 * with {@link #emit}. Handlers run on virtual threads, never on the UI thread, so slow work such as IMAP or disk access
 * cannot freeze the window.
 */
public class Rpc {

    @FunctionalInterface
    public interface Handler {
        /** @return the value to send back to the page (any Gson-serializable object, or {@code null}) */
        Object handle(JsonObject params) throws Exception;
    }

    private static final Gson GSON = new Gson();

    private WebWindow window;
    private Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public Rpc(WebWindow window) {
        this.window = window;
        window.onMessage(this::onMessage);
        window.onClosed(workers::shutdown);
    }

    public Rpc on(String method, Handler handler) {
        handlers.put(method, handler);
        return this;
    }

    /** Pushes an event to the page. Safe to call from any thread. */
    public void emit(String event, Object data) {
        JsonObject message = new JsonObject();
        message.addProperty("event", event);
        message.add("data", GSON.toJsonTree(data));
        window.postMessage(GSON.toJson(message));
    }

    /** Runs on the UI thread: parse quickly, then hand the real work to a worker. */
    private void onMessage(String text) {
        JsonObject request;
        try {
            request = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException notJson) {
            return;
        }
        if (!request.has("id") || !request.has("method")) {
            return;
        }
        JsonElement id = request.get("id");
        String method = request.get("method").getAsString();
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params") : new JsonObject();
        workers.execute(() -> window.postMessage(respond(id, method, params)));
    }

    private String respond(JsonElement id, String method, JsonObject params) {
        JsonObject response = new JsonObject();
        response.add("id", id);
        Handler handler = handlers.get(method);
        if (handler == null) {
            response.addProperty("error", "Unknown method: " + method);
            return GSON.toJson(response);
        }
        try {
            response.add("result", GSON.toJsonTree(handler.handle(params)));
        } catch (Exception e) {
            response.addProperty("error", e.getMessage() != null ? e.getMessage() : e.toString());
        }
        return GSON.toJson(response);
    }
}
