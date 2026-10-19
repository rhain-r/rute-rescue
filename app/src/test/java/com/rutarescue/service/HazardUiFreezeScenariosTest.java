package com.rutarescue.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.hazard.PointHazard;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.persistence.PreparedDataManifest;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.repository.local.AssetHazardRepository;
import com.rutarescue.repository.local.AssetSource;
import com.rutarescue.risk.ClosureLookup;
import com.rutarescue.risk.HazardLookup;
import com.rutarescue.risk.RiskContext;
import com.rutarescue.risk.RiskEngine;
import com.rutarescue.risk.StructureIndex;
import com.rutarescue.ui.map.FloodOverlayLoader;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.GeoMath;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * UI-freeze test scenarios for heavy use of the NOAH hazard data (review comment under
 * "Persistence & Offline Resiliency": make sure no UI freeze happens on heavy use of the
 * GeoJSON hazard queries).
 *
 * Every scenario uses the REAL prepared files in app/src/main/assets/flood_data: three hazard
 * grids (about 0.5 MB each) and three GeoJSON map overlays (5-7 MB each). FakeUiThread plays
 * the Android UI thread; a scenario fails if any task on it takes longer than one 16 ms frame,
 * if the screen would have stopped answering taps for more than MAX_UI_GAP_MS, or if a hazard
 * file is opened or a grid is loaded on the UI thread at all (a check that does not depend on
 * how fast the test computer is).
 *
 *  S1  1,000,000 grid lookups: one lookup costs far less than a frame
 *  S2  8 threads querying all three scenarios at once get the single-thread answers
 *  S3  32 screens asking for the same grid at once: the file is read only once
 *  S4  a slow 100-year load never blocks lookups on the loaded 5-year grid
 *  S5  1,000 rapid map taps across all scenarios never flood the UI thread
 *  S6  a late answer never overwrites the newest tap (the scenario label always matches)
 *  S7  three long routes assessed in parallel on the real 100-year grid
 *  S8  flicking the three flood overlays on and off 300 times reads each file only once
 *  S9  a damaged overlay fails off the UI thread and loads on the next try
 */
public class HazardUiFreezeScenariosTest {

    /** Longest the UI may go without answering a tap (Android calls 700 ms a frozen frame). */
    private static final long MAX_UI_GAP_MS = 100;

    /** Metro Manila area covered by the prepared NOAH data. */
    private static final double MIN_LAT = 14.36;
    private static final double MAX_LAT = 14.78;
    private static final double MIN_LON = 120.91;
    private static final double MAX_LON = 121.13;

    private FakeUiThread ui;
    private ExecutorService io;
    private ExecutorService compute;
    private AppExecutors executors;

    @Before
    public void setUp() {
        ui = new FakeUiThread();
        io = Executors.newFixedThreadPool(3); // the same size as the app's io() pool
        compute = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
        executors = new AppExecutors(io, compute, ui);
    }

    @After
    public void tearDown() {
        io.shutdownNow();
        compute.shutdownNow();
        ui.close();
    }

    // ------------------------------------------------------------------ S1

    @Test(timeout = 60_000)
    public void s1_oneMillionGridLookupsCostLessThanAFewFrames() throws Exception {
        HazardLookup grid = new AssetHazardRepository(realAssets()).loadBlocking(FloodScenario.FIVE_YEAR);
        int count = 1_000_000;
        double[] lats = randomLats(count, 1);
        double[] lons = randomLons(count, 2);
        for (int i = 0; i < 200_000; i++) grid.classify(lats[i], lons[i]); // warm up the JIT

        int[] perClass = new int[4];
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) perClass[grid.classify(lats[i], lons[i]).getCode()]++;
        long ms = elapsedMs(start);

        report("S1", "1,000,000 lookups on the real 5-year grid took %d ms (%.3f microseconds each)",
                ms, ms * 1000.0 / count);
        assertTrue("1,000,000 lookups took " + ms + " ms", ms < 2_000);
        assertTrue("The real 5-year grid should contain HIGH cells", perClass[HazardClass.HIGH.getCode()] > 0);
    }

    // ------------------------------------------------------------------ S2

    @Test(timeout = 60_000)
    public void s2_manyThreadsQueryingAtOnceGetTheSameAnswers() throws Exception {
        AssetHazardRepository repository = new AssetHazardRepository(realAssets());
        int count = 100_000;
        double[] lats = randomLats(count, 3);
        double[] lons = randomLons(count, 4);
        FloodScenario[] scenarios = FloodScenario.values();

        // Single-thread answers first
        Map<FloodScenario, HazardClass[]> expected = new EnumMap<>(FloodScenario.class);
        for (FloodScenario scenario : scenarios) {
            HazardLookup lookup = repository.loadBlocking(scenario);
            HazardClass[] answers = new HazardClass[count];
            for (int i = 0; i < count; i++) answers[i] = lookup.classify(lats[i], lons[i]);
            expected.put(scenario, answers);
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger mismatches = new AtomicInteger();
        List<Future<Void>> jobs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int firstScenario = t % scenarios.length; // threads start on different scenarios
            jobs.add(pool.submit(() -> {
                go.await();
                for (int s = 0; s < scenarios.length; s++) {
                    FloodScenario scenario = scenarios[(firstScenario + s) % scenarios.length];
                    HazardLookup lookup = repository.loadBlocking(scenario);
                    HazardClass[] answers = expected.get(scenario);
                    for (int i = 0; i < count; i++) {
                        if (lookup.classify(lats[i], lons[i]) != answers[i]) mismatches.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        long start = System.nanoTime();
        go.countDown();
        for (Future<Void> job : jobs) job.get();
        long ms = elapsedMs(start);
        pool.shutdown();

        report("S2", "%d threads x 3 scenarios x %,d lookups (%,d in total) took %d ms; %d mismatches",
                threads, count, threads * 3 * count, ms, mismatches.get());
        assertEquals("Parallel answers must equal the single-thread answers", 0, mismatches.get());
    }

    // ------------------------------------------------------------------ S3

    @Test(timeout = 60_000)
    public void s3_manyScreensAskingAtOnceReadTheGridOnce() throws Exception {
        CountingAssets assets = new CountingAssets(realAssets());
        AssetHazardRepository repository = new AssetHazardRepository(assets);
        int callers = 32;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HazardLookup>> answers = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            answers.add(pool.submit(() -> {
                go.await();
                return repository.loadBlocking(FloodScenario.FIVE_YEAR);
            }));
        }
        long start = System.nanoTime();
        go.countDown();
        HazardLookup first = answers.get(0).get();
        for (Future<HazardLookup> answer : answers) assertSame(first, answer.get());
        long ms = elapsedMs(start);
        pool.shutdown();

        int opens = assets.opens(FloodScenario.FIVE_YEAR.getGridAssetPath());
        report("S3", "%d simultaneous requests for the 5-year grid: file opened %d time(s), all served in %d ms",
                callers, opens, ms);
        assertEquals("The grid file must be read once", 1, opens);
    }

    // ------------------------------------------------------------------ S4

    @Test(timeout = 60_000)
    public void s4_aSlowScenarioNeverBlocksALoadedOne() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        AssetSource real = realAssets();
        AssetSource gated = path -> {
            if (path.equals(FloodScenario.HUNDRED_YEAR.getGridAssetPath())) {
                slowStarted.countDown();
                awaitQuietly(releaseSlow); // stands in for a very slow storage read
            }
            return real.open(path);
        };
        AssetHazardRepository repository = new AssetHazardRepository(gated);
        repository.loadBlocking(FloodScenario.FIVE_YEAR); // loaded earlier, e.g. by the first map tap

        Future<HazardLookup> slow = io.submit(() -> repository.loadBlocking(FloodScenario.HUNDRED_YEAR));
        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));

        long start = System.nanoTime();
        HazardLookup loaded = io.submit(() -> repository.loadBlocking(FloodScenario.FIVE_YEAR))
                .get(500, TimeUnit.MILLISECONDS); // times out if it has to wait for the 100-year load
        long loadedMs = elapsedMs(start);
        HazardLookup other = io.submit(() -> repository.loadBlocking(FloodScenario.TWENTY_FIVE_YEAR))
                .get(10, TimeUnit.SECONDS);
        assertFalse("The 100-year load should still be stuck", slow.isDone());

        releaseSlow.countDown();
        assertEquals(FloodScenario.HUNDRED_YEAR, slow.get(10, TimeUnit.SECONDS).getScenario());
        assertEquals(FloodScenario.FIVE_YEAR, loaded.getScenario());
        assertEquals(FloodScenario.TWENTY_FIVE_YEAR, other.getScenario());
        report("S4", "While the 100-year grid was stuck loading, the loaded 5-year grid answered in %d ms "
                + "and the 25-year grid still loaded", loadedMs);
    }

    // ------------------------------------------------------------------ S5

    @Test(timeout = 60_000)
    public void s5_aBurstOfMapTapsNeverFloodsTheUiThread() throws Exception {
        CountingRepository repository = new CountingRepository(new AssetHazardRepository(realAssets()));
        HazardQueryService queries = new HazardQueryService(repository, executors);
        List<PointHazard> shown = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger offUiAnswers = new AtomicInteger();
        Callback<PointHazard> screen = new Callback<PointHazard>() {
            @Override
            public void onSuccess(PointHazard answer) {
                if (!ui.isUiThread()) offUiAnswers.incrementAndGet();
                shown.add(answer);
            }

            @Override
            public void onError(Exception error) {
                throw new AssertionError(error);
            }
        };

        int taps = 1_000;
        double[] lats = randomLats(taps, 5);
        double[] lons = randomLons(taps, 6);
        FloodScenario[] scenarios = FloodScenario.values();
        ui.startHeartbeat();
        long start = System.nanoTime();
        for (int i = 0; i < taps; i++) {
            GeoPoint point = new GeoPoint(lats[i], lons[i]);
            FloodScenario scenario = scenarios[i % scenarios.length]; // the officer also keeps switching scenarios
            ui.execute(() -> queries.inspect(point, scenario, screen)); // every tap is handled on the UI thread
        }
        ui.drain();                                             // all taps handled
        io.shutdown();
        assertTrue(io.awaitTermination(30, TimeUnit.SECONDS));  // all background lookups done
        ui.drain();                                             // all answers shown
        long ms = elapsedMs(start);

        PointHazard last = shown.get(shown.size() - 1);
        report("S5", "%,d taps in %d ms: %d grid lookup(s) actually ran, %d answer(s) shown, longest UI task %d ms, "
                        + "longest UI gap %d ms", taps, ms, repository.lookups(), shown.size(), ui.longestTaskMs(),
                ui.longestHeartbeatGapMs());
        assertEquals("The screen must end on the newest tap's scenario", scenarios[(taps - 1) % scenarios.length],
                last.getScenario());
        assertEquals(lats[taps - 1], last.getPoint().getLatitude(), 0);
        assertEquals(lons[taps - 1], last.getPoint().getLongitude(), 0);
        assertEquals("Answers must be shown on the UI thread", 0, offUiAnswers.get());
        assertEquals("No grid may be loaded or read on the UI thread", 0, repository.uiThreadLookups());
        assertTrue("Out-of-date taps should be skipped, but " + repository.lookups() + " lookups ran",
                repository.lookups() <= taps / 10);
        assertTrue("Too many answers were posted to the UI: " + shown.size(), shown.size() <= taps / 10);
        assertUiStayedResponsive();
    }

    // ------------------------------------------------------------------ S6

    @Test(timeout = 30_000)
    public void s6_aLateAnswerNeverOverwritesTheNewestTap() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        HazardRepository slowHundredYear = new HazardRepository() {
            @Override
            public HazardLookup loadBlocking(FloodScenario scenario) {
                if (scenario == FloodScenario.HUNDRED_YEAR) {
                    slowStarted.countDown();
                    awaitQuietly(releaseSlow);
                }
                return new UniformLookup(scenario,
                        scenario == FloodScenario.HUNDRED_YEAR ? HazardClass.HIGH : HazardClass.LOW);
            }

            @Override
            public String loadGeoJsonBlocking(FloodScenario scenario) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PreparedDataManifest getManifest() {
                return PreparedDataManifest.empty();
            }
        };
        HazardQueryService queries = new HazardQueryService(slowHundredYear, executors);
        List<PointHazard> shown = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch newestShown = new CountDownLatch(1);
        Callback<PointHazard> screen = new Callback<PointHazard>() {
            @Override
            public void onSuccess(PointHazard answer) {
                shown.add(answer);
                newestShown.countDown();
            }

            @Override
            public void onError(Exception error) {
                throw new AssertionError(error);
            }
        };

        GeoPoint firstTap = new GeoPoint(14.5995, 120.9842);
        GeoPoint secondTap = new GeoPoint(14.6091, 121.0223);
        ui.execute(() -> queries.inspect(firstTap, FloodScenario.HUNDRED_YEAR, screen)); // slow
        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
        ui.execute(() -> queries.inspect(secondTap, FloodScenario.FIVE_YEAR, screen));   // officer switched to 5-year
        assertTrue(newestShown.await(5, TimeUnit.SECONDS));

        releaseSlow.countDown(); // the old 100-year answer finally arrives
        io.shutdown();
        assertTrue(io.awaitTermination(10, TimeUnit.SECONDS));
        ui.drain();

        report("S6", "Shown after a late 100-year answer: \"%s\"", shown.get(shown.size() - 1).describe());
        assertEquals("Only the newest tap may be shown", 1, shown.size());
        assertEquals(FloodScenario.FIVE_YEAR, shown.get(0).getScenario());
        assertEquals(secondTap, shown.get(0).getPoint());
        assertEquals("NOAH 5-year: Low (0-0.5 m)", shown.get(0).describe());
    }

    // ------------------------------------------------------------------ S7

    @Test(timeout = 60_000)
    public void s7_threeLongRoutesAssessedInParallelOnTheRealGrid() throws Exception {
        HazardLookup grid = new AssetHazardRepository(realAssets()).loadBlocking(FloodScenario.HUNDRED_YEAR);
        RiskContext context = new RiskContext(grid, new StructureIndex(Collections.emptyList(), "none"),
                ClosureLookup.NONE, System.currentTimeMillis());
        RiskEngine engine = new RiskEngine();
        List<RouteCandidate> routes = new ArrayList<>();
        routes.add(straightRoute(0, 14.42, 121.00, 14.64, 121.02)); // north-south, about 25 km
        routes.add(straightRoute(1, 14.58, 120.95, 14.60, 121.12)); // west-east, about 18 km
        routes.add(straightRoute(2, 14.45, 120.97, 14.66, 121.08)); // diagonal, about 26 km
        List<Callable<RouteAssessment>> tasks = new ArrayList<>();
        for (RouteCandidate route : routes) tasks.add(() -> engine.assess(route, context));

        ui.startHeartbeat();
        long start = System.nanoTime();
        List<Future<RouteAssessment>> results = compute.invokeAll(tasks);
        int samples = 0;
        double exposure = 0;
        for (Future<RouteAssessment> result : results) {
            samples += result.get().getSamples().size();
            exposure += result.get().getTotalExposureMeters();
        }
        long ms = elapsedMs(start);
        ui.drain();

        report("S7", "3 routes, %,d samples checked against the 100-year grid in %d ms (%.0f m of mapped flood found); "
                + "longest UI gap %d ms", samples, ms, exposure, ui.longestHeartbeatGapMs());
        assertTrue("Expected thousands of 10 m samples, got " + samples, samples > 5_000);
        assertTrue("These lines cross mapped flood areas", exposure > 0);
        assertTrue("Assessing 3 long routes took " + ms + " ms", ms < 3_000);
        assertUiStayedResponsive();
    }

    // ------------------------------------------------------------------ S8

    @Test(timeout = 120_000)
    public void s8_flickingFloodOverlaysOnAndOffReadsEachFileOnce() throws Exception {
        CountingAssets assets = new CountingAssets(realAssets());
        AssetHazardRepository repository = new AssetHazardRepository(assets);
        Map<FloodScenario, Integer> delivered = new ConcurrentHashMap<>();
        AtomicInteger offUiDeliveries = new AtomicInteger();
        FloodOverlayLoader loader = new FloodOverlayLoader(repository, executors, (scenario, geoJson) -> {
            if (!ui.isUiThread()) offUiDeliveries.incrementAndGet();
            delivered.merge(scenario, geoJson.length(), (first, again) -> -1); // -1 marks a second delivery
        });

        int switchFlicks = 300;
        FloodScenario[] scenarios = FloodScenario.values();
        ui.startHeartbeat();
        long start = System.nanoTime();
        for (int i = 0; i < switchFlicks; i++) {
            FloodScenario scenario = scenarios[i % scenarios.length];
            ui.execute(() -> loader.request(scenario)); // Map layers switches are handled on the UI thread
        }
        ui.drain();
        io.shutdown();
        assertTrue(io.awaitTermination(60, TimeUnit.SECONDS));
        ui.drain();
        long ms = elapsedMs(start);

        long totalChars = 0;
        for (FloodScenario scenario : scenarios) {
            assertEquals(scenario.getLabel() + " overlay file reads", 1, assets.opens(scenario.getGeoJsonAssetPath()));
            Integer length = delivered.get(scenario);
            assertTrue(scenario.getLabel() + " overlay should reach the map exactly once", length != null && length > 1_000_000);
            totalChars += length;
        }
        report("S8", "%d switch flicks: 3 overlays (%.1f MB, hash-checked) loaded in %d ms, each file read once; "
                        + "longest UI task %d ms, longest UI gap %d ms", switchFlicks, totalChars / 1_000_000.0, ms,
                ui.longestTaskMs(), ui.longestHeartbeatGapMs());
        assertEquals("Overlays must be handed over on the UI thread", 0, offUiDeliveries.get());
        assertEquals("No overlay file may be read on the UI thread", 0, assets.uiThreadOpens());
        assertUiStayedResponsive();
    }

    // ------------------------------------------------------------------ S9

    @Test(timeout = 60_000)
    public void s9_aDamagedOverlayFailsOffTheUiThreadAndLoadsOnTheNextTry() throws Exception {
        AtomicBoolean damaged = new AtomicBoolean(true);
        AssetSource real = realAssets();
        AssetSource flaky = path -> damaged.get() && path.equals(FloodScenario.FIVE_YEAR.getGeoJsonAssetPath())
                ? new ByteArrayInputStream("{\"type\":\"FeatureCollection\",".getBytes(StandardCharsets.UTF_8))
                : real.open(path); // a cut-off copy: its hash does not match the manifest
        CountingAssets counted = new CountingAssets(flaky);
        AssetHazardRepository repository = new AssetHazardRepository(counted);
        List<String> shown = Collections.synchronizedList(new ArrayList<>());
        FloodOverlayLoader loader = new FloodOverlayLoader(repository, executors, (scenario, geoJson) -> shown.add(geoJson));

        ui.execute(() -> loader.request(FloodScenario.FIVE_YEAR));
        waitUntilOnUi(() -> !loader.isRequested(FloodScenario.FIVE_YEAR), 10_000); // the failure was handled
        assertTrue("A damaged overlay must never reach the map", shown.isEmpty());

        damaged.set(false); // the file can be read correctly again
        ui.execute(() -> loader.request(FloodScenario.FIVE_YEAR));
        waitUntilOnUi(() -> shown.size() == 1, 30_000);

        report("S9", "Damaged 5-year overlay rejected by its hash, then loaded on the next request (%,d characters); "
                + "longest UI task %d ms", shown.get(0).length(), ui.longestTaskMs());
        assertTrue(shown.get(0).length() > 1_000_000);
        assertEquals("No overlay file may be read on the UI thread", 0, counted.uiThreadOpens());
        assertTrue("UI task took " + ui.longestTaskMs() + " ms", ui.longestTaskMs() < FakeUiThread.FRAME_MS);
    }

    // ------------------------------------------------------------------ helpers

    private void assertUiStayedResponsive() {
        assertTrue("A UI task took " + ui.longestTaskMs() + " ms (more than one frame)",
                ui.longestTaskMs() < FakeUiThread.FRAME_MS);
        assertTrue("The UI did not answer for " + ui.longestHeartbeatGapMs() + " ms",
                ui.longestHeartbeatGapMs() < MAX_UI_GAP_MS);
    }

    /** Checks a condition on the UI thread (where FloodOverlayLoader keeps its state) until it holds. */
    private void waitUntilOnUi(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean holds = new AtomicBoolean();
            ui.execute(() -> holds.set(condition.getAsBoolean()));
            ui.drain();
            if (holds.get()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Condition not reached within " + timeoutMs + " ms");
    }

    private static RouteCandidate straightRoute(int index, double lat1, double lon1, double lat2, double lon2) {
        List<GeoPoint> points = new ArrayList<>();
        int vertices = 50;
        for (int i = 0; i <= vertices; i++) {
            double f = (double) i / vertices;
            points.add(new GeoPoint(lat1 + (lat2 - lat1) * f, lon1 + (lon2 - lon1) * f));
        }
        double meters = GeoMath.distanceMeters(lat1, lon1, lat2, lon2);
        return new RouteCandidate(index, points, meters, Math.round(meters / 1.3), 0, false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static double[] randomLats(int count, long seed) {
        return randomBetween(count, seed, MIN_LAT, MAX_LAT);
    }

    private static double[] randomLons(int count, long seed) {
        return randomBetween(count, seed, MIN_LON, MAX_LON);
    }

    private static double[] randomBetween(int count, long seed, double min, double max) {
        Random random = new Random(seed);
        double[] values = new double[count];
        for (int i = 0; i < count; i++) values[i] = min + random.nextDouble() * (max - min);
        return values;
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static void report(String scenario, String format, Object... args) {
        System.out.println("[" + scenario + "] " + String.format(format, args));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Reads the app's bundled files from app/src/main/assets on this computer. */
    private static AssetSource realAssets() {
        return path -> {
            File file = new File("src/main/assets/" + path);
            if (!file.exists()) file = new File("app/src/main/assets/" + path);
            return new FileInputStream(file);
        };
    }

    /** Counts how often each asset file is opened, and how often that happened on the UI thread. */
    private final class CountingAssets implements AssetSource {
        private final AssetSource delegate;
        private final Map<String, AtomicInteger> opens = new ConcurrentHashMap<>();
        private final AtomicInteger uiThreadOpens = new AtomicInteger();

        CountingAssets(AssetSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public InputStream open(String path) throws IOException {
            opens.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
            if (ui.isUiThread()) uiThreadOpens.incrementAndGet();
            return delegate.open(path);
        }

        int opens(String path) {
            AtomicInteger count = opens.get(path);
            return count == null ? 0 : count.get();
        }

        int uiThreadOpens() {
            return uiThreadOpens.get();
        }
    }

    /** Counts grid requests (lookups that ran instead of being skipped) and any made on the UI thread. */
    private final class CountingRepository implements HazardRepository {
        private final HazardRepository delegate;
        private final AtomicInteger lookups = new AtomicInteger();
        private final AtomicInteger uiThreadLookups = new AtomicInteger();

        CountingRepository(HazardRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public HazardLookup loadBlocking(FloodScenario scenario) throws IOException {
            lookups.incrementAndGet();
            if (ui.isUiThread()) uiThreadLookups.incrementAndGet();
            return delegate.loadBlocking(scenario);
        }

        @Override
        public String loadGeoJsonBlocking(FloodScenario scenario) throws IOException {
            return delegate.loadGeoJsonBlocking(scenario);
        }

        @Override
        public PreparedDataManifest getManifest() {
            return delegate.getManifest();
        }

        int lookups() {
            return lookups.get();
        }

        int uiThreadLookups() {
            return uiThreadLookups.get();
        }
    }

    /** The same class everywhere; enough to tell two scenarios apart in S6. */
    private static final class UniformLookup implements HazardLookup {
        private final FloodScenario scenario;
        private final HazardClass hazardClass;

        UniformLookup(FloodScenario scenario, HazardClass hazardClass) {
            this.scenario = scenario;
            this.hazardClass = hazardClass;
        }

        @Override
        public FloodScenario getScenario() { return scenario; }

        @Override
        public String getDatasetVersion() { return "test"; }

        @Override
        public boolean covers(double latitude, double longitude) { return true; }

        @Override
        public HazardClass classify(double latitude, double longitude) { return hazardClass; }
    }
}
