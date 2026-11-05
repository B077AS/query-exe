package com.queryexe.api;

import java.util.Map;

import com.queryexe.app.Params;
import com.queryexe.app.Rpc;
import com.queryexe.monitor.MonitorService;

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
