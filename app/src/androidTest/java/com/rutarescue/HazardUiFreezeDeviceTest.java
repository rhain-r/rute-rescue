package com.rutarescue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.PointHazard;
import com.rutarescue.repository.Callback;
import com.rutarescue.service.HazardQueryService;
import com.rutarescue.ui.map.FloodOverlayLoader;
import com.rutarescue.util.AppExecutors;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The UI-freeze scenario on a real phone or emulator, with the app's own thread pools, the real
 * bundled NOAH files and the real Android main thread: all three GeoJSON overlays (5-7 MB each)
 * load while 1,000 rapid map taps are answered across the three scenarios.
 *
 * It measures two things:
 *  1. Our share: the CPU time of every task the hazard code puts on the main thread. Thread CPU
 *     time does not grow when the device is busy with other apps, so this is a fair pass/fail.
 *  2. Responsiveness: a heartbeat every frame, first with no work (the device's own baseline),
 *     then during the heavy work. The work may not add more than MAX_EXTRA_GAP_MS to the
 *     baseline. Android calls a frame over 700 ms "frozen".
 */
@RunWith(AndroidJUnit4.class)
public class HazardUiFreezeDeviceTest {

    private static final String TAG = "RR-FreezeTest";
    private static final int TAPS = 1_000;
    /** One frame at 60 Hz: the most CPU any single main-thread task of ours may use. */
    private static final long MAX_TASK_CPU_MS = 16;
    /** How much longer than the idle baseline the main thread may pause during the heavy work. */
    private static final long MAX_EXTRA_GAP_MS = 150;
    private static final long BASELINE_MS = 3_000;

    @Test
    public void heavyHazardUseKeepsTheMainThreadResponsive() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppContainer container = RutaRescueApp.container(context);
        Handler main = new Handler(Looper.getMainLooper());

        // The app's real io/compute pools, with a main executor that measures each task's CPU time.
        AtomicLong longestTaskCpuNanos = new AtomicLong();
        Executor timedMain = task -> main.post(() -> {
            long start = Debug.threadCpuTimeNanos();
            task.run();
            longestTaskCpuNanos.accumulateAndGet(Debug.threadCpuTimeNanos() - start, Math::max);
        });
        AppExecutors executors = new AppExecutors(container.executors.io(), container.executors.compute(), timedMain);

        // 1. Baseline: how long the main thread pauses on this device with no work at all.
        long baselineGap = heartbeat(main, BASELINE_MS, null);

        // 2. The heavy work, started from the main thread like the map screens do.
        Random random = new Random(11);
        GeoPoint[] taps = new GeoPoint[TAPS];
        for (int i = 0; i < TAPS; i++) {
            taps[i] = new GeoPoint(14.36 + random.nextDouble() * 0.42, 120.91 + random.nextDouble() * 0.22);
        }
        FloodScenario[] scenarios = FloodScenario.values();
        CountDownLatch overlaysLoaded = new CountDownLatch(scenarios.length);
        CountDownLatch newestShown = new CountDownLatch(1);
        AtomicReference<PointHazard> shown = new AtomicReference<>();
        HazardQueryService queries = new HazardQueryService(container.hazards, executors);
        Callback<PointHazard> screen = new Callback<PointHazard>() {
            @Override
            public void onSuccess(PointHazard answer) {
                shown.set(answer);
                if (answer.getPoint().equals(taps[TAPS - 1])) newestShown.countDown();
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Lookup failed", error);
            }
        };
        long[] workMs = {0};
        long loadGap = heartbeat(main, 0, () -> {
            long start = SystemClock.uptimeMillis();
            main.post(() -> {
                FloodOverlayLoader loader = new FloodOverlayLoader(container.hazards, executors,
                        (scenario, geoJson) -> overlaysLoaded.countDown());
                for (FloodScenario scenario : scenarios) loader.request(scenario);
            });
            for (int i = 0; i < TAPS; i++) {
                GeoPoint point = taps[i];
                FloodScenario scenario = scenarios[i % scenarios.length];
                main.post(() -> queries.inspect(point, scenario, screen)); // one tap = one main-thread event
            }
            assertTrue("Overlays did not load", overlaysLoaded.await(60, TimeUnit.SECONDS));
            assertTrue("The newest tap was never answered", newestShown.await(60, TimeUnit.SECONDS));
            workMs[0] = SystemClock.uptimeMillis() - start;
        });

        long taskCpuMs = TimeUnit.NANOSECONDS.toMillis(longestTaskCpuNanos.get());
        String summary = String.format(Locale.US, "3 overlays + %d taps in %d ms; longest main-thread task of ours "
                        + "%d ms CPU; main-thread pause: idle baseline %d ms, during the work %d ms",
                TAPS, workMs[0], taskCpuMs, baselineGap, loadGap);
        Log.i(TAG, summary);
        Bundle status = new Bundle();
        status.putString("stream", "\n" + summary + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);

        assertEquals(scenarios[(TAPS - 1) % scenarios.length], shown.get().getScenario());
        assertTrue("A main-thread task used " + taskCpuMs + " ms of CPU", taskCpuMs < MAX_TASK_CPU_MS);
        assertTrue("The work made the main thread pause " + loadGap + " ms (idle baseline " + baselineGap + " ms)",
                loadGap < baselineGap + MAX_EXTRA_GAP_MS);
    }

    /** Things the test does while the heartbeat runs. */
    private interface Work {
        void run() throws Exception;
    }

    /**
     * Posts a tiny task to the main thread every 16 ms while the work runs (or for idleMs when
     * there is no work) and returns the longest gap between two of them.
     */
    private static long heartbeat(Handler main, long idleMs, Work work) throws Exception {
        AtomicLong longestGap = new AtomicLong();
        AtomicBoolean beating = new AtomicBoolean(true);
        long[] lastBeat = {0};
        Runnable beat = new Runnable() {
            @Override
            public void run() {
                long now = SystemClock.uptimeMillis();
                if (lastBeat[0] != 0) longestGap.accumulateAndGet(now - lastBeat[0], Math::max);
                lastBeat[0] = now;
                if (beating.get()) main.postDelayed(this, 16);
            }
        };
        main.post(beat);
        if (work == null) {
            Thread.sleep(idleMs);
        } else {
            work.run();
            Thread.sleep(250); // a few more heartbeats after the work is done
        }
        beating.set(false);
        return longestGap.get();
    }
}
