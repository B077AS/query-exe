package io.github.b077as.queryexe.api;

import java.util.Map;

import io.github.b077as.queryexe.app.Params;
import io.github.b077as.queryexe.app.Rpc;
import io.github.b077as.queryexe.monitor.MonitorService;

/** The server monitor: live numbers, sessions and locks of an open connection. */
class MonitorApi {

    private MonitorService monitor = MonitorService.get();

    void register(Rpc rpc) {
        rpc.on("monitor.open", p -> monitor.open(Params.string(p, "connectionId")));
        rpc.on("monitor.sample", p -> monitor.sample(Params.string(p, "connectionId"), Params.bool(p, "tables")));
        rpc.on("monitor.close", p -> {
            monitor.close(Params.string(p, "connectionId"));
            return Map.of("ok", true);
        });
    }
}
