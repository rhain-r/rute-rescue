# RutaRescue

Flood preparedness and safer-route support for Metro Manila. It is a native Android app written in Java with XML layouts.

Residents can:
- keep a family evacuation record that also works offline
- compare route alternatives against the prepared Project NOAH 5-, 25- and 100-year flood scenarios, with bridge- and tunnel-aware rules
- find LGU-verified evacuation centers
- apply for **Ligtas Priority** assistance

LGU personnel can:
- publish advisories
- review Ligtas applications
- report road closures
- confirm bridge and tunnel status
- import and verify evacuation-center data

> RutaRescue supports decisions. It does **not** guarantee a safe route, predict exact flooding, or replace emergency services. The best route is labelled **"Safer among available alternatives"**, never "safe". In an emergency, call 911.

### Ad

![RutaRescue explainer ad (plays automatically)](docs/media/recording-ads.gif)


---

## 1. Requirements

| Tool | Version used |
|---|---|
| Android Studio | Recent stable build; its bundled JBR is used as `JAVA_HOME` |
| Android SDK | compileSdk / targetSdk **37** (Android 17), minSdk 26 |
| Gradle / AGP | Gradle 9.6 wrapper (included), AGP 9.4 |
| Node.js (optional) | Only for the Firebase Local Emulator Suite (`firebase-tools`) |

Main libraries: Firebase BoM 34.19 (Auth, Firestore, Storage), MapLibre Android 13.6, Gson, ZXing Android Embedded, WorkManager and Material Components.

---

## 2. Secrets and private configuration (never commit these)

| File | What it holds | Status |
|---|---|---|
| `local.properties` | SDK path, `TOMTOM_API_KEY`, feature flags | git-ignored; copy it from `local.properties.example` |
| `app/google-services.json` | Your Firebase project config | git-ignored |
| `data/raw/` | Raw NOAH shapefiles and OSM exports (large) | git-ignored; keep them in Google Drive |

The build reads `local.properties` into `BuildConfig`. **No key is written in the source code.**

> **Important:** the old React/Expo repository (`Pewwww53/Jabbacodeez`) contained real TomTom, OpenWeather and Geoapify keys in its history. Rotate those keys in each provider's console. Deleting them from the repo is not enough.

---

## 3. Quick start: run everything locally with the Firebase Emulator Suite

This needs no Firebase account and no billing. All data stays on your computer.

**One click (Windows):** after steps 1 and 2 below, double-click **`start-rutarescue.bat`** in the project folder. It starts the Firebase emulators and the Pixel_7 emulator, loads the demo data, turns on the emulator's network, and opens the app. It also opens the **Firebase Emulator Suite** at http://127.0.0.1:4000, where you can browse the accounts, Firestore data and requests. `Firebase Emulator Suite.url` is a clickable link to that page. Running the launcher again reuses whatever is already running and keeps your data. Closing the "Firebase emulators" window stops Firebase and erases its data.

1. **Local properties.** Copy `local.properties.example` to `local.properties` and set:
   ```properties
   USE_FIREBASE_EMULATOR=true
   EMULATOR_HOST=10.0.2.2
   USE_FIREBASE_STORAGE=false
   TOMTOM_API_KEY=            # optional, see section 5
   ```
2. **Demo Firebase config.** Copy `firebase/emulator/google-services.demo.json` to `app/google-services.json`. It points at the fake project `demo-rutarescue`, which only works with the emulators.
3. **Start the emulators** in a terminal:
   ```bash
   cd firebase
   npx firebase-tools@latest emulators:start --project demo-rutarescue --only auth,firestore,storage
   ```
   They listen on `127.0.0.1` only: Auth on 9099, Firestore on 8080, Storage on 9199, and the UI on 4000. The Android emulator reaches them through `10.0.2.2`.
4. **Seed the demo data** in a second terminal:
   ```bash
   ./gradlew :tools:emulator:seedEmulator
   ```
   This creates a demo resident, Manila and Pasig LGU officers, and one advisory. The demo e-mails and passwords are in `tools/emulator/src/main/java/com/rutarescue/emulator/SeedDemoData.java`. They are demo values for the emulator only; never reuse them for a real project.
5. **Run the app** from Android Studio on an emulator. On Android 17 (API 37), the debug build asks for the **"Nearby devices"** permission on the login screen. That is Android's local-network permission, needed to reach the emulators. Release builds never request it.
6. **Publish evacuation centers.** Sign in as the Manila LGU account and open **Data → Upload → Use bundled legacy list**. Enter a verification note and tap **Publish valid rows**. Only Manila rows are published, and each one gets an audit event.

Emulator data is lost when the emulators stop. Run the seed task again after each restart.

---

## 4. Using a real Firebase project

1. Create a project in the [Firebase console](https://console.firebase.google.com/) and add an Android app with package **`com.rutarescue`**.
2. Download `google-services.json` into `app/`. It is git-ignored.
3. Enable **Authentication → Email/Password**.
4. Create **Firestore** and deploy the rules and indexes:
   ```bash
   cd firebase
   npx firebase-tools@latest deploy --only firestore:rules,firestore:indexes --project YOUR_PROJECT_ID
   ```
5. Proof images. Choose one:
   - `USE_FIREBASE_STORAGE=false` (default, free Spark plan): a compressed synthetic thumbnail is stored inside Firestore.
   - `USE_FIREBASE_STORAGE=true` (Blaze plan): images go to Storage. Also deploy `storage.rules`.
6. Set `USE_FIREBASE_EMULATOR=false` in `local.properties`.

### Creating LGU accounts

Residents register in the app. LGU personnel are **never** self-registered, and the database rules block every client write to `lgu_staff`. An administrator sets them up in the Firebase console:

1. **Authentication → Add user**: give the officer an e-mail address and a temporary password.
2. Copy the new user's UID.
3. **Firestore → `lgu_staff/{uid}`**: create the document with these fields:
   - `displayName` (string)
   - `email` (string)
   - `jurisdiction` (string): one of `CALOOCAN`, `LAS_PINAS`, `MAKATI`, `MALABON`, `MANDALUYONG`, `MANILA`, `MARIKINA`, `MUNTINLUPA`, `NAVOTAS`, `PARANAQUE`, `PASAY`, `PASIG`, `PATEROS`, `QUEZON_CITY`, `SAN_JUAN`, `TAGUIG`, `VALENZUELA`
   - `position` (string)

The app reads the role from the database after sign-in. Users never choose it.

---

## 5. Map, navigation and traffic (free, no payment)

Every map service RutaRescue uses is free, and none of them needs a credit card:

| Feature | Service | Key needed? |
|---|---|---|
| Street basemap | OpenStreetMap tiles (`tile.openstreetmap.org`) | No |
| Place search | OpenStreetMap Nominatim | No. Called only when you press Search; the provider waits 1 second between requests. |
| Navigation (route alternatives and turn-by-turn steps) with **live traffic** | TomTom Routing API, free developer tier | Yes, a free TomTom key |
| **Live traffic overlay** on the OSM map (flow colours) | TomTom Traffic API flow tiles, free tier | Yes, the same key |
| Navigation without a key, or when TomTom fails or the monthly quota is used up | OpenStreetMap routing: OSRM on `routing.openstreetmap.de` | No. Typical speeds, labelled "traffic not included". |

### Getting the free TomTom key

1. Register at <https://my.tomtom.com> ("No credit card required"). Never add a payment method and never upgrade the plan, so nothing can ever be charged.
2. Open **API & SDK Keys** (<https://my.tomtom.com/keys>). TomTom has already created **"My first API key"**.
3. Click **"…" → Edit**. Make sure the assigned products include **Routing API** and **Traffic API**, and leave the domain whitelist empty.
4. Copy the key into `local.properties` as `TOMTOM_API_KEY=...` (git-ignored), then rebuild.

The free monthly allowance (from TomTom's pricing page) is 20,000 routing requests and 200,000 traffic tiles. When it runs out, TomTom refuses the requests and the app switches to OpenStreetMap routing on its own (`FallbackRouteProvider`).

How it fits together (polymorphism):
- `RouteProvider` is implemented by `TomTomRouteProvider`, `OsrmRouteProvider`, `FallbackRouteProvider` (TomTom first, then OSRM) and `CachedRouteProvider` (offline).
- `PlaceSearchProvider` is implemented by `NominatimSearchProvider` and `TomTomSearchProvider`.

The route screen asks for up to 3 alternatives and assesses each one on the device with the bridge-aware `RiskEngine`. Travel time is called **live traffic** only when TomTom actually included traffic. The traffic overlay appears for motorcycle and car trips, and on the LGU map's "Routes" layer.

---

## 6. How route assessment works

1. **Sample.** `RouteSampler` cuts each provider route (TomTom or OpenStreetMap) into 10 m samples.
2. **Apply rules.** Each sample passes through the rules in this order (`risk/`):
   1. `ClosureRule`: an LGU-reported closure makes the route **unavailable**.
   2. `TunnelRule`: uses TomTom tunnel sections and OSM tunnels.
   3. `BridgeRule`: LGU-confirmed **open** bridges are not penalised by the river hazard below them. **Closed** bridges make the route unavailable.
   4. `UnknownStructureRule`: bridges and tunnels the LGU has not confirmed are flagged **unverified** (amber).
   5. `SurfaceRule`: ordinary road over NOAH LOW/MEDIUM/HIGH takes that hazard.
3. **Rank.** `RouteRanker` compares routes on these criteria, in order:
   1. not closed
   2. lowest maximum effective hazard
   3. fewest metres in hazard
   4. fewest unverified-structure metres
   5. travel time
4. **Explain.** `RouteExplainer` writes the plain-language reasons shown on each route card.
5. **Detour around closures.** If **every** returned route crosses an LGU closure, `RouteService` asks TomTom again with `avoidAreas` rectangles around the closed stretches (`ClosureAvoidance`, 40 m margin). The detours are assessed and ranked with the closed route, which stays last. If no detour exists (or only OpenStreetMap routing is available, since it cannot avoid areas), the headline says **"Every available route crosses a reported closure"**, never "Safer".

A bridge in OSM is often several ways: two directions plus sidewalks. When the LGU sets a bridge's status, it applies to all mapped parts of the same structure (`StructureIndex.partsOfSameStructure`). Closing Jones Bridge therefore closes both directions.

The bridge regression tests in `BridgeRegressionTest` cover four cases:

| Scenario | Result |
|---|---|
| Surface road over HIGH | RED |
| Unverified bridge over HIGH | AMBER |
| Closed bridge | UNAVAILABLE |
| LGU-verified open bridge | GREEN |

`RealDataBridgeTest` repeats the check on the **real** bundled data: a walk across MacArthur Bridge over the Pasig River in Manila, where the 5-year NOAH grid is HIGH under the bridge.

| Scenario (real data) | Result |
|---|---|
| Same crossing treated as a surface road | RED, HIGH |
| Unverified MacArthur Bridge | AMBER: not given the river's HIGH class, and counted as unverified-structure distance |
| MacArthur Bridge marked closed by the LGU | UNAVAILABLE |
| Three alternatives (bridge and two surface crossings) | The bridge route ranks first |

### Prepared data (`app/src/main/assets/`)

| Folder | Contents |
|---|---|
| `flood_data/` | Per scenario: a GeoJSON display overlay and a 2-bit hazard grid (0.0001° cells), plus `manifest.json` with SHA-256 hashes. The app checks both files against the manifest before use. |
| `structures/road_structures.json` | 5,023 bridges and 240 tunnels from OpenStreetMap. They are treated as **unverified** until an LGU confirms them. |
| `seed/centers_legacy_unverified.csv` | 90 centers from the old app. They are unverified until an LGU publishes them with a verification note. |

### Regenerating the data

1. Put the raw files in `data/raw/`:
   - `data/raw/noah/MetroManila5yrs/MetroManila_Flood_5year.shp` (with its .dbf, .shx and .prj files). The 25-year and 100-year folders follow the same layout.
   - `data/raw/osm/structures_overpass.json`. Export it with the query in `tools/dataprep/overpass_structures.ql`.
   - `data/raw/legacy/evacuation_centers.js`
2. Run the tasks:
   ```bash
   ./gradlew :tools:dataprep:prepareHazards
   ./gradlew :tools:dataprep:prepareStructures
   ./gradlew :tools:dataprep:prepareCenters
   ```
   `prepareHazards` stops if any data acceptance gate fails, and records the results in `manifest.json`. The gates check:
   - the required files exist
   - the CRS is WGS84 and the geometry is polygons
   - the .dbf and .shp record counts match
   - every record maps to LOW, MEDIUM or HIGH
   - the data lies inside Metro Manila
   - random control points keep their class after simplification

---

## 7. Tests

```bash
./gradlew :app:testDebugUnitTest      # 85 JVM unit tests
./gradlew :tools:emulator:test        # 10 Firestore security-rule tests (emulators must be running)
```

**Unit tests:**
- validation: `RecordValidatorTest`, `InputValidatorTest`
- lab files and caches: `FamilyTextFileStoreTest`, `SystemStateStoreTest`, `RouteCacheStoreTest`
- risk engine: `RiskEngineTest`, `BridgeRegressionTest`, `RealDataBridgeTest` (MacArthur Bridge; closing one direction of Jones Bridge closes both), `ClosureAvoidanceTest`, `RouteRankerTest`, `RouteSamplerTest`, `HazardGridTest`, `StructureIndexTest`
- providers: `TomTomRouteParserTest`, `OpenStreetMapProvidersTest` (OSRM, Nominatim and the TomTom → OSM fallback)
- models: `LigtasApplicationTest`, `FamilyRecordTest`

**Security-rule tests** check that:
- registration can only create residents
- residents cannot read other residents' families or write LGU data
- LGU writes need an audit event in the same batch, and only for their own city
- audit events are append-only
- unverified centers cannot be published
- only a same-city LGU decides an application, once, with a reason
- bridge and tunnel status changes need a valid status and an audit event

The rule tests **wipe the emulator data** first. Run `seedEmulator` again afterwards.

---

## 8. Architecture (MVC + OOP)

```
app/src/main/java/com/rutarescue/
├── model/         Model: entities with private fields and validated setters
├── validation/    Lab 7 rules (names, 09XXXXXXXXX numbers, record IDs)
├── exception/     Custom checked exceptions (ValidationException, InvalidFamilyRecordException, ...)
├── persistence/   Lab files: family_records.txt, system.dat, atomic writes, route cache
├── risk/          Route-risk engine (rules, sampler, ranker, explainer)
├── network/       TomTom and OpenStreetMap (OSRM, Nominatim) REST clients and parsers
├── repository/    Interfaces + firestore/ and local/ implementations
├── service/       Use cases (auth, session, family, routes, Ligtas, datasets, sync)
└── ui/            Controllers: Activities and Fragments, grouped by role
app/src/main/res/layout/   Views: XML layouts
```

| OOP concept | Where to look |
|---|---|
| Encapsulation | `FamilyRecord`, `LigtasApplication`: private state, changed only through validated methods such as `submit()`, `approve()` and `reject()` |
| Inheritance | `UserProfile` → `ResidentProfile`, `LguProfile`; `AuditableEntity` → records, centers, applications; `BaseActivity` / `BaseFragment` |
| Polymorphism | `RiskRule` with five implementations; `UserProfile.canReview()` / `canManage()` |
| Abstraction | `repository/*Repository` interfaces (Firestore or local), `RouteProvider`, `ProofStorage` |

### Lab concepts → files

| Lab | Concept | File |
|---|---|---|
| Lab 5 | Record IDs `FR-0001`; `system.dat` via `DataOutputStream`/`DataInputStream` | `RecordValidator`, `SystemStateStore`, `SystemState` |
| Lab 5 | 7-field legacy rows (still importable) | `LegacyFamilyCodec` |
| Lab 6 | `family_records.txt` (6 fields, pipe-delimited, UTF-8); `NumberFormatException` vs custom exception | `FamilyTextFileStore`, `LegacyFamilyCodec`, `InputValidator`, `InvalidFamilyRecordException` |
| Lab 7 | Name formatting, phone rule, duplicate IDs, safe (atomic) saves | `NameFormatter`, `PhoneNumber`, `DuplicateRecordException`, `AtomicFileWriter` |

The app mirrors each saved family record to `files/data/family_records.txt` and `files/data/system.dat`. The **Record → Import / export family_records.txt** screen reads and writes lab-format files.

> **Team check:** `SystemStateStore` writes `int lastId, int savedCount, UTF version, boolean autoSave`. Confirm that this matches your Lab 6 `SystemState`. If the version was a `double`, change the two marked lines and `SystemStateStoreTest`.

### Figma design system → Android resources

The XML views follow the Figma file exactly. All values were read from its variables, styles and frames, not estimated from screenshots.

| Figma | Android |
|---|---|
| Colour variables (`color/brand/800` #152B3F, `color/brand/600` #24547F, `color/accent/cyan` #12B5D0, text #162235 / #667085 / #98A2B3, border #E8EAED, status colours) | `res/values/colors.xml`, each value labelled with its Figma variable name |
| Paint style `Surface/Ambient` (4-stop vertical gradient) | `drawable/bg_ambient.xml`, the window background of every screen |
| Paint style `Surface/Glass` (white at 93 %) | `@color/rr_glass` on cards, header, navigation bar, sheets and map buttons |
| Fonts: Montserrat (SemiBold, Bold), Karla (Regular, Medium, SemiBold) | `res/font/` (bundled variable fonts, SIL Open Font License; licences in `assets/licenses/`) |
| Text styles `Mobile/Display, Title, Section, Body, Label, Caption, Micro` | `TextAppearance.RR.*` in `themes.xml` (sizes, line heights and letter spacing copied) |
| Effect styles `Elevation/Sm`, `Float`, `Nav` | `@dimen/elevation_sm`, `elevation_float`, `elevation_nav` with navy-tinted shadows |
| Components `RR / Header`, `RR / Bottom Nav`, `RR / Button`, `RR / Status Chip`, `RR / Metric Card`, `RR / Search Field`, `RR / Center Row` | `view_app_header.xml`, `view_bottom_nav.xml`, `Widget.RR.Button.*`, `RR.Badge` + `Badges.java`, `RR.MetricTile`, search blocks, `item_center.xml` |
| Icons (Lucide-style, exported as SVG) | `drawable/ic_*.xml` vector drawables (converted from the Figma export) |
| Frames `Client / Home, Safe Route, Centers, My Record`, `LGU / Overview, Hazard Map, Ligtas Reviews, Data & Centers` | `fragment_home`, `fragment_safe_route`, `fragment_centers`, `fragment_my_record`, `fragment_lgu_overview`, `fragment_lgu_hazard_map`, `fragment_lgu_reviews`, `fragment_lgu_data` |

Where the DOCX requires something the Figma frames do not show, it uses the same components. This covers the Risk Horizon (5/25/100-year) pills, freshness labels, data-source timestamps, the Lab file card, and the full route assessment below the Figma sheet summary. Figma's "SAFE" chip wording is replaced by risk wording such as "No mapped hazard", because the DOCX forbids calling a route safe.

---

## 9. Security and privacy

- **Role enforcement in the database.** `firebase/firestore.rules` checks roles, same-city jurisdiction, one-time decisions with a reason, and append-only `auditEvents`. Every write and its audit event are committed in one batch.
- **Ligtas Priority evidence is synthetic.** The app warns users never to upload real IDs or medical documents.
- **QR codes hold only the application ID** (`RUTARESCUE:LIGTAS:<id>`). They contain no names or medical details. The LGU scanner looks the application up and shows its current status.
- **Backups.** `android:allowBackup="false"`, so family and Ligtas data are not copied into device backups.
- **Network access.** Release builds allow HTTPS only. Debug builds allow cleartext only to `10.0.2.2`, `localhost` and `127.0.0.1` (the emulators), plus the Android 17 local-network permission.
- **Logs.** No personal data or keys are logged (`SafeLog`).
- **Shared phones.** Sign-out (`AppContainer.signOut()`) deletes `family_records.txt`, the cached routes (they start at the user's location) and the advisory marks. `system.dat` holds only counters, so it is kept. When a resident's record loads, `family_records.txt` is rewritten for that resident only. If the server confirms there is no record, the file is removed. Another family's name, phone or address is never shown.

---

## 10. Offline behaviour

- **Firestore cache.** The persistent cache (100 MB) keeps the last records, centers, advisories and applications. Writes made offline are queued and sent when the device reconnects.
- **Freshness labels.** Every list shows a label: **Live**, **Cached** (with its age), **Stale**, or **Prepared** (bundled NOAH data, never live).
- **Bundled data.** NOAH grids, overlays and structures ship inside the app, so they work fully offline. The last route comparisons are cached on the device.
- **Background sync.** `SyncWorker` (WorkManager) refreshes advisories and centers about every hour while online, so the offline cache stays as fresh as possible.

---

## 11. Acceptance walkthrough

1. **Resident sign-in.** Home shows the LGU advisory with its freshness label.
2. **Record → Create record.** Validation errors appear on each field. After saving, `family_records.txt` and `system.dat` are updated.
3. **Route.** Switch between the 5-, 25- and 100-year chips; the flood overlay changes. Pick a center and compare up to three routes (TomTom with live traffic, or OpenStreetMap routing without a key). **Start guidance**: the current step follows your GPS position (on the emulator: `adb emu geo fix <lon> <lat>`).
4. **Ligtas Priority.** Apply for a senior or PWD member with a synthetic image. The status shows **PENDING**.
5. **LGU review.** Sign in as the Manila LGU, open **Reviews**, and approve the application. A reason is required, and the decision is logged.
6. **QR.** Back as the resident, the status is **APPROVED** and **Open verification record** shows the QR code.
7. **LGU map.** Tap a point to see its NOAH class and any nearby bridge or tunnel. Long-press to report a closure, or set a bridge's status (it applies to every mapped part of that bridge). Close Jones Bridge, then plan a resident route across it: the closed route is ranked last and a detour is offered.
8. **Centers.** Import the legacy list, publish it with a verification note, and check that residents see the centers as "LGU verified".
9. **Lab files.** Record → **Import / export family_records.txt**. Malformed rows, bad family sizes and duplicate IDs are skipped, each with its line number. UTF-8 names such as "José Niño Dela Peña" are kept. **Export my record** writes the same pipe-delimited format.
10. **Offline.** Turn on airplane mode. The offline banner appears, lists show **Cached**, and the bundled NOAH scenarios still switch.

---

## 12. Known limitations

- **Flood data is prepared, not live.** NOAH scenarios are modelled flood extents, so the app cannot know the actual water level on a street.
- **Structure data is incomplete.** OSM bridge and tunnel data may be missing or misplaced. Structures stay "unverified" until an LGU confirms them.
- **Center capacity is not live.** Availability is whatever the LGU last posted.
- **Routing needs a network the first time.** Routes are limited to what TomTom or OpenStreetMap routing returns (at most 3 alternatives). Without a TomTom key, travel times use typical speeds, not live traffic, and no detours around closures can be requested.
- **Android 17 local-network permission.** Testing against the emulators on an Android 17 device or emulator needs the "Nearby devices" permission. A physical phone also needs the emulators bound to your computer's LAN address instead of `127.0.0.1`, and that address added to `app/src/debug/res/xml/network_security_config.xml`.

## Data credits

- **Flood hazard maps:** UP NOAH Center (Project NOAH)
- **Bridges and tunnels:** © OpenStreetMap contributors (ODbL)
- **Basemap, place search and fallback routing:** © OpenStreetMap contributors (ODbL), Nominatim, OSRM (FOSSGIS)
- **Navigation with live traffic and the traffic overlay:** © TomTom (free developer tier)
- **Map rendering:** MapLibre Native
