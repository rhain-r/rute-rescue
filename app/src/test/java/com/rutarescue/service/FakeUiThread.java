package com.rutarescue.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stands in for Android's UI (main) thread in plain JVM tests and watches it for freezes.
 *
 * Like Android's main looper it is ONE thread that runs posted tasks in order. It records:
 *   - the longest single task: anything over one 16 ms frame would make the screen stutter;
 *   - the longest gap between "heartbeats" posted every 16 ms: how long a tap would have to
 *     wait before the screen could react to it.
 */
final class FakeUiThread implements Executor, AutoCloseable {

    /** One frame at 60 Hz. */
    static final long FRAME_MS = 16;

    private final ExecutorService looper = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "fake-ui"));
    private final ScheduledExecutorService vsync = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fake-vsync");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong longestTaskNanos = new AtomicLong();
    private final AtomicLong longestGapNanos = new AtomicLong();
    private final AtomicLong tasksRun = new AtomicLong();
    private volatile Thread uiThread;
    private long lastBeatNanos; // only touched on the UI thread

    FakeUiThread() {
        looper.execute(() -> uiThread = Thread.currentThread());
    }

    /** Posts a task, like Handler.post on the main looper, and times it. */
    @Override
    public void execute(Runnable task) {
        looper.execute(() -> {
            long start = System.nanoTime();
            task.run();
            longestTaskNanos.accumulateAndGet(System.nanoTime() - start, Math::max);
            tasksRun.incrementAndGet();
        });
    }

    /** Starts posting a tiny heartbeat every frame. */
    void startHeartbeat() {
        vsync.scheduleAtFixedRate(() -> looper.execute(this::beat), 0, FRAME_MS, TimeUnit.MILLISECONDS);
    }

    private void beat() {
        long now = System.nanoTime();
        if (lastBeatNanos != 0) longestGapNanos.accumulateAndGet(now - lastBeatNanos, Math::max);
        lastBeatNanos = now;
    }

    /** Waits until every task posted so far has run. */
    void drain() throws Exception {
        looper.submit(() -> { }).get(30, TimeUnit.SECONDS);
    }

    boolean isUiThread() {
        return Thread.currentThread() == uiThread;
    }

    long longestTaskMs() {
        return TimeUnit.NANOSECONDS.toMillis(longestTaskNanos.get());
    }

    long longestHeartbeatGapMs() {
        return TimeUnit.NANOSECONDS.toMillis(longestGapNanos.get());
    }

    long tasksRun() {
        return tasksRun.get();
    }

    @Override
    public void close() {
        vsync.shutdownNow();
        looper.shutdownNow();
    }
}
