package com.rutarescue.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The app's thread pools (multithreading requirement, DOCX section 10).
 *
 *  io()      - file and network work (reading assets, saving files, calling TomTom)
 *  compute() - CPU work such as assessing several routes in parallel
 *  main()    - posts results back to the Android UI thread
 *
 * Android does not allow network calls on the main thread, and long work there would
 * freeze the screen, so all heavy work goes through these executors.
 */
public final class AppExecutors {

    private final ExecutorService io;
    private final ExecutorService compute;
    private final Executor main;

    public AppExecutors() {
        this(Executors.newFixedThreadPool(3, named("rr-io")),
                Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1), named("rr-compute")),
                new MainThreadExecutor());
    }

    public AppExecutors(ExecutorService io, ExecutorService compute, Executor main) {
        this.io = io;
        this.compute = compute;
        this.main = main;
    }

    public ExecutorService io() {
        return io;
    }

    public ExecutorService compute() {
        return compute;
    }

    public Executor main() {
        return main;
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }
}
