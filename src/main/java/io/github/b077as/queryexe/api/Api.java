package io.github.b077as.queryexe.api;

import java.util.concurrent.CompletableFuture;

import io.github.b077as.jolt.Rpc;
import io.github.b077as.queryexe.app.Async;
import io.github.b077as.queryexe.monitor.MonitorService;
import io.github.b077as.queryexe.query.Workspace;

/** Registers everything the page can ask of Java besides the window itself. */
public class Api {

    private Rpc rpc;
    private FileChooser chooser;
    private Workspace workspace;

    public Api(Rpc rpc, FileChooser chooser) {
        this.rpc = rpc;
        this.chooser = chooser;
        this.workspace = new Workspace(rpc::emit);
    }

    /**
     * Drops what was held for the page's query tabs and server monitor: their connections are closed and open transactions
     * rolled back. The database connections stay open. The future completes when everything is closed.
     */
    public CompletableFuture<Void> closePageState() {
        return CompletableFuture.allOf(workspace.closeAllTabs(),
                CompletableFuture.runAsync(() -> MonitorService.get().closeAll(), Async::run));
    }

    public void register() {
        new ConnectionApi(workspace, chooser).register(rpc);
        new SchemaApi().register(rpc);
        new MonitorApi().register(rpc);
        new QueryApi(workspace, chooser).register(rpc);
    }
}
