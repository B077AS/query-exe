package com.queryexe.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SingleExecutorService {

    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    public static ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        return executor;
    }

    public static boolean isRunning() {
        return isRunning.get();
    }

    public static boolean tryStartRunning() {
        return isRunning.compareAndSet(false, true);
    }

    public static void finishRunning() {
        isRunning.set(false);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        executor = null;
    }

    public static void stopRunning() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        isRunning.set(false);
        executor = null;
    }
}