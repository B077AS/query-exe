package com.queryexe.app;

/** Background work: every task runs on its own virtual thread. */
public class Async {

    private Async() {
    }

    public static Thread run(Runnable runnable) {
        return Thread.ofVirtual().start(runnable);
    }
}
