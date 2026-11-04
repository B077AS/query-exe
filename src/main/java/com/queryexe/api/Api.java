package com.queryexe.api;

import com.queryexe.Rpc;
import com.queryexe.service.Workspace;

/** Registers everything the page can ask of Java besides the window itself. */
public final class Api {

    private final Rpc rpc;
    private final FileChooser chooser;

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
