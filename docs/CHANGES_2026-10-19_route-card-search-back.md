# Change log: route card, full-screen search, Back and real Firebase (2026-10-19)

This change covers the user's on-device feedback on the Notifications screen, the Safe Route screen and where the data lives.

Actions: **CREATE** = new file, **MODIFY** = partly edited, **DELETE** = removed.

## 1. Notifications: "Live • just now" too close to the edge

**Cause.** `BaseActivity.applySystemBarInsets` replaced the layout's own padding with the status-bar insets. That removed the 16 dp side margin of the shared list screen (Notifications and Ligtas status).

**Fix.** The helper now adds the insets to the layout's padding. Only `activity_list.xml` had root padding, so no other screen changes.

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/common/BaseActivity.java` | MODIFY | Keep the layout's padding and add the system bars on top of it. |

## 2. Route card slides down to its handle

When the 3 routes show, drag the card's handle or title down, or tap the handle, and the card slides away until only the "–" handle is left. Drag the handle up, or tap it, and the routes come back.

**Map gestures.** Zooming or panning the map while the routes show slides the card down automatically.

**No movement on its own.** The card never moves by itself:
- it comes back only when the resident pulls or taps the handle;
- after the resident moves the map, the map is not re-framed when the card slides.

Every evacuation center a resident picks (from the Route list, Centers, Home or search) opens this same card.

## 3. Full-screen search

Tapping the search box covers the map with a full-screen list, so nothing overlaps the travel pills or the "5-year flood" tag.
- **Before typing:** the 10 nearest evacuation centers, with distances (alphabetical when the location is unknown).
- **While typing:** the matching centers, nearest first, with online places after them.

The current destination's name is pre-selected, so typing replaces it. Picking a place, the back arrow or the phone's Back key closes the search screen.

## 4. Back arrow steps back inside the Route tab

The back arrow and the phone's Back key now go back one step:
1. the search screen closes back to the map;
2. a route goes back to "Choose a destination";
3. from "Choose a destination", the back arrow goes to Home.

During guidance the route stays; "End" stops it. The **X** next to a destination also goes back to "Choose a destination".

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Search mode, nearest centers, sliding card, map-gesture slide-down, one-step Back. |
| `app/src/main/java/com/rutarescue/ui/map/MapController.java` | MODIFY | `setOnUserMove`: tells the screen when a finger zooms or pans (not when the app moves the camera). |
| `app/src/main/res/layout/fragment_safe_route.xml` | MODIFY | Full-screen search panel (replaces the floating results card); 32 dp handle strip. |
| `app/src/main/res/values/strings.xml` | MODIFY | Search headers, empty message, handle description. |

## 5. All data in the real Firebase project

The app has used the real Firebase project "rutarescue" since 2026-10-15 (Authentication + Cloud Firestore; `USE_FIREBASE_EMULATOR=false`). Cloud Firestore sends changes to the app and the console as they happen.

The confusion came from the one-click launcher. It still started the local Firebase emulators and opened their page, which showed demo data instead of the app's real data.

| File path | Action | Purpose |
|---|---|---|
| `start-rutarescue.bat` | MODIFY | Starts only the Android emulator and the app, and opens the real Firestore console. No Firebase emulators, no demo seeding. |
| `Firebase Console.url` | CREATE | Shortcut to the real Firestore data page. |
| `Firebase Emulator Suite.url` | DELETE | Pointed to the local emulators, which are no longer used. |

**Where the data is** ([Firestore console](https://console.firebase.google.com/project/rutarescue/firestore/databases/-default-/data)):

| Collection | What it holds |
|---|---|
| `families` | Family records, one document per resident (document ID = the resident's account ID). |
| `users`, `lgu_staff` | Resident and LGU profiles. |
| `places` | Saved places. |
| `centers` | Evacuation centers published by an LGU. |
| `advisories` | LGU advisories. |
| `closures` | Road closures. |
| `ligtasApplications`, `ligtasProofs` | Ligtas applications and their proof images. |
| `structureStatus` | Bridge and tunnel status. |
| `datasetVersions` | Dataset versions. |
| `auditEvents` | The audit trail. |

The app does not use the separate "Realtime Database" product.

## 6. Train lines for commuters

A new **Train lines (LRT, MRT)** switch in Map layers (resident and LGU maps, off by default) shows the three running lines in their own colours, with white station dots. Station names appear from zoom 13 and hide when they would overlap. The data is bundled in the app, so it also works offline.

| Line | Stations |
|---|---|
| LRT-1 (green) | 25: Fernando Poe Jr. to Dr. Santos |
| LRT-2 (purple) | 13: Recto to Antipolo |
| MRT-3 (yellow) | 13: North Avenue to Taft Avenue |

**Left out on purpose.** The PNR Metro Commuter (suspended since 2024 for the North-South Commuter Railway works) and the Metro Manila Subway (under construction) are not shown, so the map never points a commuter to a train that is not running.

**Source.** OpenStreetMap (ODbL), downloaded 2026-10-19 through the Overpass API. OpenStreetMap names LRT-1's United Nations stop "/", and the build script fixes it.

**Not included.** Train routing (directions that ride a train) is not available from the free routing services the app uses (OSRM, TomTom), so this is a map layer only.

| File path | Action | Purpose |
|---|---|---|
| `data/sources/osm-rail-2026-10-19/*.json` | CREATE | The raw Overpass answers (line shapes and stops). |
| `data/sources/osm-rail-2026-10-19/build_rail_geojson.py` | CREATE | Builds the bundled file from them (running lines only; fixes the "/" name). |
| `app/src/main/assets/transit/metro_manila_rail.geojson` | CREATE | 3 lines and 51 stations (26 KB) bundled with the app. |
| `app/src/main/java/com/rutarescue/model/map/MapLayerSettings.java` | MODIFY | `transitVisible`, saved with the other switches (older saved settings still load). |
| `app/src/main/java/com/rutarescue/ui/map/MapLayersSheet.java` | MODIFY | The Train lines switch row. |
| `app/src/main/java/com/rutarescue/ui/map/MapController.java` | MODIFY | Casing, line, station and name layers; reads the file once, off the UI thread; draws name labels as images (the offline style has no fonts). |
| `app/src/main/res/drawable/ic_train.xml` | CREATE | Train icon in the same outline style. |
| `app/src/main/res/values/strings.xml`, `colors.xml` | MODIFY | Row text and the soft tile colour. |
| `app/src/test/java/com/rutarescue/persistence/TransitAssetTest.java` | CREATE | 3 lines; 25/13/13 stations, all named and inside Metro Manila; "United Nations" present. |
| `app/src/test/java/com/rutarescue/model/MapLayerSettingsTest.java` | MODIFY | Train switch off by default, saved and loaded, and old saved text still loads. |

## 7. "Nearby" centers are really the nearest

**Cause (Route tab).** The "Nearby centers" list was built once, when the centers loaded. If the resident's location was not known yet, the list was sorted by name (for example Alabang and Aldana Elementary School, far away in the south), and it was never re-sorted when the location arrived. Without location permission it always stayed alphabetical.

**Cause (Home card).** `CenterService.nearest` treated every center as 0 m away while the location was unknown, so the card showed whichever center came first.

**Fix.**
- **Route tab:** the list is rebuilt when either the centers or the location arrive, so the closest four always show. Without a location it lists the centers closest to the map centre, under the heading "Centers near the map centre". The search screen works the same way.
- **Home card:** `nearest` returns nothing until the location is known. The card then says "Finding the evacuation center nearest to you…", or "Turn on location to see the evacuation center nearest to you."
- **Status text:** a center with no posted status now reads like "1.1 km • Manila • no status posted yet" instead of only "Status not reported". The status label itself is now "No status posted yet".

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/service/CenterService.java` | MODIFY | `nearest` returns null without a location. New `summary(center, origin)`: distance and status, or the city and "no status posted yet". |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | `bindNearbyCenters()`, re-run when the location arrives; map-centre fallback; search rows use `summary`. |
| `app/src/main/java/com/rutarescue/ui/resident/home/HomeFragment.java` | MODIFY | No "nearby" card before the location is known; explains why. |
| `app/src/main/java/com/rutarescue/ui/common/ViewBinders.java` | MODIFY | Center rows show the city and "no status posted yet" for unposted centers. |
| `app/src/main/java/com/rutarescue/model/center/CenterStatus.java` | MODIFY | `NOT_REPORTED` label: "No status posted yet". |
| `app/src/main/res/layout/fragment_safe_route.xml`, `values/strings.xml` | MODIFY | Heading id and the new messages. |
| `app/src/test/java/com/rutarescue/service/CenterServiceNearestTest.java` | CREATE | Nearest is by distance, not name; no nearest without a location; full centers are skipped; the summary text. |

## 8. Every city gets its own nearest center

**What was reported.** A Mandaluyong resident's "nearest" center was 4+ hours away. That was the alphabetical bug in section 7: Alabang Elementary School is 22.9 km away, and the walking route there takes 4 h 34 min.

**Location first, then the sign-up city.** "Nearest" always uses the phone's location. Until that is known (or without permission), it now uses the resident's **sign-up city hall** instead of the map centre in Manila:
- the Route list heading says "Centers in and near Mandaluyong";
- the Home card shows that city's nearest center (without a distance, since the resident's real position is unknown);
- the Route map opens on the resident's location, or on their city. Before, it stayed over Manila when the location arrived before the map was ready.

**City hall points.** One per LGU, added to `MetroManilaCity`, from OpenStreetMap Nominatim (2026-10-19). Fifteen are town-hall entries. The other two are close stand-ins: Pasay uses the City Health Office in the city hall complex, and San Juan uses the "City Hall" point on Pinaglabanan St.

**Two wrong pins removed.** These came from the old unverified CSV; the bundled list goes from 91 to 89 centers until their real locations are confirmed.

| Center | Labelled | Pinned in |
|---|---|---|
| "Vista Mall" | Manila | Taguig, 12.6 km from Manila City Hall (it showed as "nearest" for Pasig, Pateros and Taguig) |
| "San Roque de Navotas" | Navotas | San Juan |

**Coverage, nearest bundled center to each city hall** (from `CityNearestCenterTest`):

| Cities | Nearest center |
|---|---|
| Caloocan, Las Piñas, Malabon, Mandaluyong, Manila, Muntinlupa, Parañaque, Pasay, Quezon City, San Juan | 0.2–2.3 km away |
| Makati, Marikina, Navotas, Pasig, Pateros, Taguig, Valenzuela | 1.8–5.9 km away, in a neighbouring city: these cities have no bundled centers yet |

The PLM SSC post did not cover the second group, so their LGU accounts should publish their centers in the app.

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/model/common/MetroManilaCity.java` | MODIFY | `getHallLocation()` for all 17 LGUs. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | `referencePoint()` and `nearbyHeading()`; the map opens on the resident or their city. |
| `app/src/main/java/com/rutarescue/ui/resident/home/HomeFragment.java` | MODIFY | Nearest from the sign-up city until the location is known. |
| `app/src/main/assets/seed/centers_plmssc_2024_unverified.csv` | MODIFY | Removes the two mis-pinned centers (91 to 89). |
| `app/src/main/res/values/strings.xml` | MODIFY | "Centers in and near %1$s". |
| `app/src/test/java/com/rutarescue/service/CityNearestCenterTest.java` | CREATE | Halls inside Metro Manila; a Mandaluyong resident gets the Mandaluyong center; each city with centers gets one under 6 km. |
| `app/src/test/java/com/rutarescue/persistence/BundledCenterReaderTest.java` | MODIFY | Expects 89 bundled centers. |

## 9. Locate button: you in the middle, zoomed in

**Cause.** Locate centred the camera on the middle of the *whole* map, which the bottom card partly covers. It also kept leftover padding from the last route framing, so the dot landed in different places. The zoom was always 14. It also waited for a fresh GPS fix (up to 10 s) when the last one was older than 20 s.

**Fix.**
- `MapController.centerOn(point, minZoom, topCover, bottomCover)` puts the point in the middle of the part of the map you can see: below the search bar and travel pills, above the card, wherever the card is (open, slid down, or the idle card).
- It zooms in to at least 16.5 and never zooms out if you are already closer.
- Locate responds at once from the last known position. When the fresh fix arrives, it re-centres only if you have not moved the map since tapping.
- The LGU map's Locate does the same, between its header and its sheet.

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/map/MapController.java` | MODIFY | `centerOn(...)`: camera padding from the covering views and the minimum zoom. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Locate uses `centerOn` at 16.5 (first fix at 15); instant re-centre; `movedSinceLocate` stops a late fix from pulling the map back. |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java`, `res/layout/fragment_lgu_hazard_map.xml` | MODIFY | Same for the LGU map (`lgu_map_top` id on the header). |

## 10. "You" dot: drawn at once, on the handle's line, a little higher

**Feedback.** The resident was "not in the middle" and "too low". The dot should sit on the same vertical line as the card's "–" handle, a bit above the middle.

**Causes.**
- When the location arrived before the map was ready, the Route tab centred on the middle of the *whole* map, which is half covered by the card. It also never drew the dot until Locate was pressed.
- Guidance followed the resident the same whole-map way.

**Fix.**
- `centerOn` puts the point on the screen's centre line (under the "–" handle), two-fifths of the way down the visible map, instead of halfway (`YOU_FROM_TOP = 0.4`).
- Opening the Route tab uses `centerOn` and draws the dot straight away.
- Guidance follows the resident between the turn card and the time card.

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/map/MapController.java` | MODIFY | `YOU_FROM_TOP` lift in `centerOn`. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Map-ready draws the dot and uses `centerOn`; guidance uses `centerOn`. |

**Checked on the emulator** (screen 1080 x 2400; dot x = 540 = the handle's line in every case):

| State | Dot y | Target y (two-fifths down the visible map) |
|---|---|---|
| Opening the Route tab | 780 | 782 |
| Locate, idle | 780 | 782 |
| Locate, route open | 659 | 666 |
| Locate, card slid down | 1160 | 1159 |

## 11. Checks

- **Unit tests:** 182 app unit tests pass (168 before; 5 new for the train map, 6 for nearest centers and 3 for the city fallback).
- **Device check** (Pixel_7, test builds only; the release APK is not rebuilt):
  - the Notifications chip is 16 dp from the edge;
  - search opens full-screen with the nearest centers and shows matches for "manda";
  - the card slides to the handle by drag and by tap, and back;
  - a double-tap zoom and a pan slide it down, and it stays put for 10 s or more;
  - the back arrow goes route → "Choose a destination" → Home;
  - the phone's Back key goes route → "Choose a destination".
- **Train layer on the device:** the switch shows in Map layers, the LRT lines and stations draw on the map, and station names appear when zoomed in (e.g. "Blumentritt").
- **Nearby centers on the device:** after a fresh start, Home and the Route list both show Robinsons Manila (1.1 km) and SM City Manila (1.2 km). With location permission removed, Home asks the resident to turn on location (permission given back afterwards).
- **Mandaluyong on the device:** with the emulator GPS at Mandaluyong City Hall:
  - Home and the Route list show Brgy. Addition Evacuation Center (1.1 km) first;
  - the walking route to it is 17 min (1.4 km);
  - the Route map opens over Mandaluyong.
- **Locate on the device:** the dot lands in the middle of the visible map in every case, and zooms to street level:

  | State | Dot | Visible band | Band middle |
  |---|---|---|---|
  | Idle | 868 px | 430–1311 px | ~870 |
  | Route open | 714 px | 430–1020 px | ~725 |
  | Card slid down | 1344 px | 430–2253 px | ~1341 |

  With a 25-second-old fix, the map moved within 1.5 s.
- **Test-tool note:** taps sent right after a `uiautomator dump` were sometimes lost. Without dumps every tap reached the app, so device checks should use screenshots instead.
- **Signed APK:** `apk/RutaRescue-1.2.apk` was rebuilt with the "you" dot fix (section 10). It keeps version 1.2 (versionCode 3) because the fix belongs to that release; its v2 signature verifies with the RutaRescue certificate.
