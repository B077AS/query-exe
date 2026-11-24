package io.github.b077as.queryexe.api;

import java.util.Map;

import com.google.gson.JsonObject;

import io.github.b077as.queryexe.app.Params;
import io.github.b077as.jolt.Rpc;
import io.github.b077as.queryexe.monitor.MonitorService;

/** The server monitor: live numbers, sessions and locks of an open connection. */
class MonitorApi {

    private MonitorService monitor = MonitorService.get();

    void register(Rpc rpc) {
        rpc.on("monitor.open", JsonObject.class, p -> monitor.open(Params.string(p, "connectionId")));
        rpc.on("monitor.sample", JsonObject.class, p -> monitor.sample(Params.string(p, "connectionId"), Params.bool(p, "tables")));
        rpc.on("monitor.close", JsonObject.class, p -> {
            monitor.close(Params.string(p, "connectionId"));
            return Map.of("ok", true);
        });
    }
}
