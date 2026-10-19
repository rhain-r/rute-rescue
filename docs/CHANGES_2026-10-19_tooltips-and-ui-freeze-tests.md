# Change log: risk-zone tooltips and UI-freeze test scenarios (2026-10-19)

This change answers two review comments from Prof. Neypes.

1. **Tooltip for risk zones vs weather updates.** Small "i" buttons now explain that the coloured risk zones are prepared Project NOAH flood maps, not live weather, and that LGU advisories are the current updates.
2. **No UI freeze under heavy hazard queries.** Nine test scenarios load and query the real bundled NOAH data under stress, and fail if the UI thread is ever blocked. Writing them found two real problems, which are now fixed.

Actions: **CREATE** = new file, **MODIFY** = partly edited.

## 1. Tooltips (risk zones vs weather updates)

A tap on an "i" shows a navy bubble with a title and a few lines. A tap anywhere closes it. A long-press and TalkBack read the same text.

| Where | Tooltip |
|---|---|
| Home, the advisory card at the top | "LGU advisories are current updates" |
| Home, Data sources: "Project NOAH flood scenarios" | "Risk zones are not live weather" |
| Home, Data sources: "LGU advisories" | "LGU advisories are current updates" |
| Map layers sheet (resident and LGU), the "Prepared, not live" chip and its "i" | "Risk zones are not live weather" |
| LGU Hazard Map, the "Flood-risk map" title pill | "Risk zones are not live weather" |
| LGU Hazard Map "Risk zones" pill, Safe Route flood tag | The same title on a long-press |

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/common/InfoTooltip.java` | CREATE | Shows the bubble under its "i" button and sets the same text as the Android tooltip. |
| `app/src/main/res/layout/view_info_tooltip.xml` | CREATE | The bubble: title with an info icon, then the explanation. |
| `app/src/main/res/drawable/bg_tooltip.xml` | CREATE | Navy rounded background of the bubble. |
| `app/src/main/res/values/styles_components.xml` | MODIFY | `RR.InfoButton`: the small grey "i" button (32 dp touch area). |
| `app/src/main/res/values/strings.xml` | MODIFY | The two tooltip texts and their screen-reader labels. |
| `app/src/main/res/layout/fragment_home.xml` | MODIFY | "i" on the advisory card and on the two data-source rows. |
| `app/src/main/java/com/rutarescue/ui/resident/home/HomeFragment.java` | MODIFY | Attaches the three Home tooltips. |
| `app/src/main/res/layout/sheet_map_layers.xml` | MODIFY | "i" next to the "Prepared, not live" chip. |
| `app/src/main/java/com/rutarescue/ui/map/MapLayersSheet.java` | MODIFY | The chip and the "i" open the risk-zone tooltip. |
| `app/src/main/res/layout/fragment_lgu_hazard_map.xml` | MODIFY | "i" at the end of the map title pill. |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java` | MODIFY | Attaches the map tooltip and the long-press text on "Risk zones". |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Long-press text on the flood tag. |

## 2. Freeze-proofing found by the tests

| Problem | Fix |
|---|---|
| `AssetHazardRepository.loadBlocking` was `synchronized` as a whole, so while one grid loaded, lookups on an already-loaded grid had to wait (and could tie up the 3 io threads). | One lock per scenario, with a lock-free fast path for loaded grids (scenario S4). |
| The LGU map's tap-to-inspect started one background job per tap, and a late answer could overwrite a newer one. The label also used the screen's current scenario, so it could name a different scenario than the class shown. | `HazardQueryService`: the newest tap wins, out-of-date taps are skipped, and each answer carries its own scenario (scenarios S5 and S6). |

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/repository/local/AssetSource.java` | CREATE | Opens a bundled file. The app passes `getAssets()::open`, and the tests read the same files from disk. |
| `app/src/main/java/com/rutarescue/repository/local/AssetHazardRepository.java` | MODIFY | Takes an `AssetSource`, and uses one lock per scenario with a lock-free cache. |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | Passes `app.getAssets()::open` to the hazard repository. |
| `app/src/main/java/com/rutarescue/model/hazard/PointHazard.java` | CREATE | One tap's answer (point, scenario, coverage, class) and its label text. |
| `app/src/main/java/com/rutarescue/service/HazardQueryService.java` | CREATE | Off-UI-thread lookups where the newest tap wins. |
| `app/src/main/java/com/rutarescue/ui/map/FloodOverlayLoader.java` | CREATE | Reads each 5-7 MB GeoJSON overlay once on the io pool and hands it to the UI thread (moved out of `MapController`, so it can be tested). |
| `app/src/main/java/com/rutarescue/ui/map/MapController.java` | MODIFY | Uses `FloodOverlayLoader`. |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java` | MODIFY | Tap-to-inspect goes through `HazardQueryService`; a read error now says so instead of "Outside coverage". |

## 3. UI-freeze test scenarios

The scenarios use the real files in `app/src/main/assets/flood_data`. These are three hazard grids and three GeoJSON overlays (18 MB together). `FakeUiThread` plays the Android UI thread.

A scenario fails in any of these cases:
- a UI task takes longer than one 16 ms frame;
- the UI goes more than 100 ms without answering;
- a hazard file is opened, or a grid is loaded, on the UI thread at all.

The last check does not depend on how fast the test computer is.

| # | Scenario | Result on the dev PC |
|---|---|---|
| S1 | 1,000,000 grid lookups at random Metro Manila points | 43 ms (0.04 µs each) |
| S2 | 8 threads × 3 scenarios × 100,000 lookups at once must equal the single-thread answers | 2.4 M lookups in 124 ms, 0 mismatches |
| S3 | 32 screens ask for the same grid at the same moment | File read once, all served in 27 ms |
| S4 | The 100-year grid is stuck loading; the loaded 5-year grid must still answer | Answered in 1 ms; 25-year still loaded |
| S5 | 1,000 rapid map taps while switching 5/25/100-year | 4 lookups ran, 1 answer shown (the newest), longest UI task 0 ms, longest UI gap 15 ms |
| S6 | A slow 100-year answer arrives after the officer switched to 5-year | Only "NOAH 5-year: Low (0-0.5 m)" is shown |
| S7 | Three 18-26 km routes assessed in parallel on the real 100-year grid | 6,918 samples in 42 ms |
| S8 | Flood switches flicked on and off 300 times | 3 overlays (18 MB, hash-checked) in 55 ms, each file read once, longest UI task 2 ms |
| S9 | A damaged overlay (hash mismatch), then a good one | Rejected off the UI thread, loaded on the next request |

**The tests catch real regressions.** Each of these code breaks was tried on purpose and then undone:

| Code break | Failing scenarios |
|---|---|
| Putting back the old whole-method lock | S4 |
| Removing the newest-tap check | S6 |
| Reading overlays on the UI thread | S8, S9 |

| File path | Action | Purpose |
|---|---|---|
| `app/src/test/java/com/rutarescue/service/FakeUiThread.java` | CREATE | A one-thread stand-in for the Android UI thread that times every task and sends a heartbeat every 16 ms. |
| `app/src/test/java/com/rutarescue/service/HazardUiFreezeScenariosTest.java` | CREATE | Scenarios S1-S9. |
| `app/src/androidTest/java/com/rutarescue/HazardUiFreezeDeviceTest.java` | CREATE | The same stress on a phone or emulator, using the app's real thread pools, assets and main thread. It loads 3 overlays and answers 1,000 taps. See the device check below for how it measures. |

Result: 165 app unit tests pass (156 before, 9 new), and the debug app and test APKs build.

## 4. Device check (Pixel_7 emulator, 2026-10-19)

**On-screen tooltips.** All placements were opened in the real app:
- LGU map title;
- Map layers sheet;
- Home advisory card;
- Home "Project NOAH flood scenarios" row.

The bubble sits under its "i", stays on screen and closes on a tap.

**Device test.** It measures two things:
- **Our share:** the CPU time of every task the hazard code puts on the main thread. Thread CPU time does not grow when the device is busy, so this is the strict pass/fail (under one 16 ms frame).
- **Responsiveness:** a heartbeat every frame, first with no work (the device's own baseline), then during the work. The work may add at most 150 ms to the baseline.

| Run | Result |
|---|---|
| 9 runs on a settled emulator | Pass. Our main-thread tasks used 0 ms of CPU. The longest pause during the work was 65-152 ms, against an idle baseline of 19-61 ms (always under 200 ms). The work took 354-537 ms. |
| First 4 runs, right after a cold boot | Pauses of 365-824 ms. The device itself was overloaded: load average 25-39 on 2 cores, 151 MB free, swapping. Android's system process paused 446 ms for garbage collection and had a 647 ms slow frame at the same time. Same code, settled device: pass. |

**Tried and rejected.** Running the worker threads at a lower priority didn't reduce the pauses (57-126 ms against 65-152 ms) and made the work slower, so that change was undone.

**Flood switches in the real app.** The flood switch and an unrelated switch were each toggled 6 times, twice:

| Switch | Median frame | 90th percentile | Slow UI-thread frames |
|---|---|---|---|
| 5-year flood | 150 / 300 ms | 450 / 500 ms | 40 / 48 |
| Evacuation centers (control) | 200 / 250 ms | 450 ms | 40 / 41 |

The flood overlays add no measurable stutter. This emulator draws the map in software (no GPU), so every frame of the map screen is slow, whatever changes. A real phone with a GPU has not been measured.

**Tap-to-inspect.** After rapid switches between 5, 25 and 100-year with map taps, the card ended on "NOAH 100-year: Medium (0.5-1.5 m)" with the 100-year pill selected, and no crash was logged.

To run the device test without wiping the app's data (Gradle's `connectedAndroidTest` uninstalls the app afterwards):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.rutarescue.HazardUiFreezeDeviceTest com.oop.rutarescue.test/androidx.test.runner.AndroidJUnitRunner
```
