package com.queryexe.api;

import java.util.Map;

import com.queryexe.Params;
import com.queryexe.Rpc;
import com.queryexe.service.MonitorService;

/** The server monitor: live numbers, sessions and locks of an open connection. */
final class MonitorApi {

    private final MonitorService monitor = MonitorService.get();

    void register(Rpc rpc) {
        rpc.on("monitor.open", p -> monitor.open(Params.string(p, "connectionId")));
        rpc.on("monitor.sample", p -> monitor.sample(Params.string(p, "connectionId"), Params.bool(p, "tables")));
        rpc.on("monitor.close", p -> {
            monitor.close(Params.string(p, "connectionId"));
            return Map.of("ok", true);
        });
    }
}
