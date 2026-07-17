package com.queryexe.service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Central entry point for background execution.
 *
 * Fire-and-forget work that may run in parallel goes through {@link #run(Runnable)},
 * which starts a fresh virtual thread. JavaFX {@link javafx.concurrent.Task}s and
 * {@link javafx.concurrent.Service}s are executed on {@link #VIRTUAL_EXECUTOR} so
 * they also run on virtual threads instead of a manually managed pool.
 */
public final class Async {

    public static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private Async() {
    }

    public static Thread run(Runnable runnable) {
        return Thread.ofVirtual().start(runnable);
    }
}
