package io.github.b077as.queryexe.api;

import io.github.b077as.queryexe.app.Rpc;
import io.github.b077as.queryexe.query.Workspace;

/** Registers everything the page can ask of Java besides the window itself. */
public class Api {

    private Rpc rpc;
    private FileChooser chooser;

    public Api(Rpc rpc, FileChooser chooser) {
        this.rpc = rpc;
        this.chooser = chooser;
    }

    public void register() {
        Workspace workspace = new Workspace(rpc::emit);
        new ConnectionApi(workspace).register(rpc);
        new SchemaApi().register(rpc);
        new MonitorApi().register(rpc);
        new QueryApi(workspace, chooser).register(rpc);
    }
}
