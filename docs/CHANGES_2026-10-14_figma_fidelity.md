# Change log: verification and Figma fidelity pass (2026-10-13 to 2026-10-15)

Every file below is already saved in the project. The complete code is in each file. Actions:
**CREATE** = new file, **REPLACE** = rewritten, **MODIFY** = partly edited.

## 1. Build fixes (lint errors)

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/persistence/CenterCsvReader.java` | MODIFY | Replaced an invisible byte-order-mark character inside a string with the escape `"﻿"` (lint error *ByteOrderMark*; editors can silently delete the invisible character). |
| `app/src/main/java/com/rutarescue/persistence/FamilyTextFileStore.java` | MODIFY | Same BOM fix for the `family_records.txt` reader. |
| `app/src/main/res/values/themes.xml` | REPLACE | Added `tools:targetApi` to API 27/29 theme attributes (lint *NewApi* errors), then rebuilt the whole theme from the Figma design system (section 2). |

## 2. Figma design system (exact values read from the Figma file)

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/res/font/karla_variable.ttf`, `montserrat_variable.ttf` | CREATE | The two Figma fonts, bundled so the app works offline (SIL Open Font License, from github.com/google/fonts). |
| `app/src/main/res/font/karla_regular.xml`, `karla_medium.xml`, `karla_semibold.xml`, `karla_bold.xml`, `montserrat_semibold.xml`, `montserrat_bold.xml` | CREATE | One font family per Figma weight (variable-font `wght` axis). |
| `app/src/main/assets/licenses/karla_OFL.txt`, `montserrat_OFL.txt` | CREATE | Font licences shipped with the app. |
| `app/src/main/res/values/colors.xml` | REPLACE | Figma colour variables (brand, accent, text, border, status, glass, aura). Existing names are kept, so no reference broke. |
| `app/src/main/res/values/themes.xml` | REPLACE | The 7 Figma text styles (`Mobile/Display` ... `Mobile/Micro`) with exact size, line height and letter spacing. Montserrat/Karla mapped onto every Material 3 type role. Navy-tinted shadows, no M3 tonal tint, ambient gradient window background. Button, chip, input, dialog and snackbar styles. |
| `app/src/main/res/values/dimens.xml` | REPLACE | Figma spacing: 16 dp screen padding, 12 dp block gap (14 dp on Home), 24 dp card radius, 74 dp header, 78 dp navigation bar, elevation equivalents. |
| `app/src/main/res/values/styles_components.xml` | REPLACE | Component styles: nav item, glass cards, center row, icon tiles, section header/action, 44 dp map buttons, sheet, notes, Ligtas action. |
| `app/src/main/res/values/styles_home.xml` | REPLACE | Quick-action tiles (94 dp, padding 12, gap 6). |
| `app/src/main/res/values/styles_lgu.xml` | REPLACE | Metric cards, composer actions, compact row text. |
| `app/src/main/res/values/strings.xml` | MODIFY | New labels from Figma ("Helpful", "Save update", "Manage", "＋ Risk zone", "Routes" layer...) and accessibility descriptions. |
| `app/src/main/res/color/chip_background.xml`, `chip_stroke.xml`, `chip_text.xml` | REPLACE | List filter pills: brand-600 when selected, white with #E8EAED border otherwise. |
| `app/src/main/res/color/chip_background_map.xml`, `chip_stroke_map.xml`, `text_input_stroke.xml` | CREATE | Map pills (navy when selected, glass otherwise) and form-field outline. |
| `app/src/main/res/drawable/bg_ambient.xml` | CREATE | Figma `Surface/Ambient` 4-stop gradient (vector drawable). |
| `app/src/main/res/drawable/bg_card.xml`, `bg_search.xml`, `bg_bottom_nav.xml`, `bg_nav_active.xml`, `bg_icon_tile.xml`, `bg_badge.xml`, `bg_circle_white.xml`, `bg_pill_outline.xml`, `bg_map_button.xml`, `bg_sheet.xml`, `bg_notice.xml`, `bg_info_card.xml`, `bg_drag_handle.xml`, `bg_hero_resident.xml`, `bg_hero_lgu.xml`, `bg_hero_navy.xml` | REPLACE | Exact Figma fills, strokes, corner radii and gradients. |
| `app/src/main/res/drawable/bg_header.xml`, `bg_icon_tile_small.xml`, `bg_data_health.xml`, `bg_qr_button.xml`, `bg_notice_compact.xml`, `bg_map_title.xml`, `bg_route_selected.xml`, `ripple_card.xml`, `ripple_round.xml` | CREATE | Additional Figma shapes and touch feedback. |
| `app/src/main/res/drawable/ic_*.xml` (29 icons) | REPLACE / CREATE | Icons converted from the Figma SVG export. New: `ic_settings`, `ic_prepared_data`, `ic_layers_two`, `ic_status_check`, `ic_shield_check`, `ic_more`, `ic_road_network`. `ic_logo_mark` is the exact Figma "RutaRescue Mark". |

## 3. Screens and components (Figma frames)

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/res/layout/view_app_header.xml` | REPLACE | "RR / Header": full-width glass bar, 26 dp bottom corners, 36 dp logo, 40 dp bell. Pinned above the scrolling content. |
| `app/src/main/res/layout/view_bottom_nav.xml` | REPLACE | "RR / Bottom Nav": full-width glass bar, 28 dp top corners, 96 x 58 dp items. |
| `app/src/main/res/layout/activity_main_shell.xml` | REPLACE | Content fills the screen; the navigation bar sits on the bottom edge. |
| `app/src/main/res/layout/fragment_home.xml` | REPLACE | "Client / Home". |
| `app/src/main/res/layout/fragment_safe_route.xml` | REPLACE | "Client / Safe Route": Back / search / settings row, mode pills, prepared-data tag, Locate/Layers, floating sheet. |
| `app/src/main/res/layout/fragment_centers.xml` | REPLACE | "Client / Centers". |
| `app/src/main/res/layout/fragment_my_record.xml` | REPLACE | "Client / My Record". |
| `app/src/main/res/layout/fragment_lgu_overview.xml` | REPLACE | "LGU / Overview". |
| `app/src/main/res/layout/fragment_lgu_hazard_map.xml` | REPLACE | "LGU / Hazard Map": Back / title pill / More header, layer pills, Locate/Layers, floating sheet. |
| `app/src/main/res/layout/fragment_lgu_reviews.xml` | REPLACE | "LGU / Ligtas Reviews". |
| `app/src/main/res/layout/fragment_lgu_data.xml` | REPLACE | "LGU / Data & Centers". |
| `app/src/main/res/layout/item_center.xml` | REPLACE | "RR / Center Row" (status in the meta line, as in Figma). |
| `app/src/main/res/layout/item_advisory.xml` | REPLACE | "Verified LGU Update" card with the "Helpful" / "Save update" actions. |
| `app/src/main/res/layout/item_ligtas.xml` | REPLACE | Figma application card ("URGENT" / "NEW" state text, "Proof attached", "Review application →"). |
| `app/src/main/res/layout/item_member.xml` | CREATE | Figma "Member" row (status dot, READY / PRIORITY). |
| `app/src/main/res/layout/item_data_row.xml` | CREATE | Figma 66 dp dataset/center row. |
| `app/src/main/res/layout/item_route_alternative.xml`, `item_simple_row.xml`, `view_toolbar.xml` | REPLACE / MODIFY | Same card, text and button language on secondary lists and screens. |
| `app/src/main/res/layout/activity_*.xml` | MODIFY | Removed the flat background so every screen shows the Figma ambient gradient. |

## 4. Java (controllers wired to the new views)

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/service/AdvisoryMarks.java` | CREATE | Stores the resident's "Helpful" / "Save update" marks on the device only (encapsulated SharedPreferences). |
| `app/src/main/java/com/rutarescue/ui/common/MaxHeightScrollView.java` | CREATE | Floating map sheet that keeps Figma's rounded corners and scrolls inside when long (inheritance from `NestedScrollView`). |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | Provides `AdvisoryMarks`. |
| `app/src/main/java/com/rutarescue/ui/common/ViewBinders.java` | REPLACE | Binds the Figma center row and advisory card (with working post actions). |
| `app/src/main/java/com/rutarescue/ui/common/Badges.java` | MODIFY | Figma status chip (tone colours and 15 dp check icon), a glass "prepared data" tag, and `label()` for plain state text. |
| `app/src/main/java/com/rutarescue/ui/shell/BottomNav.java` | MODIFY | Figma active / inactive colours. |
| `app/src/main/java/com/rutarescue/ui/shell/MainShellActivity.java` | MODIFY | Edge-to-edge insets: the navigation bar reaches the screen edge and hides while the keyboard is open. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Back button, Figma route line and stats (distance / time / risk), floating sheet. |
| `app/src/main/java/com/rutarescue/ui/resident/record/MyRecordFragment.java` | MODIFY | Figma member rows and a truthful sync-status line. |
| `app/src/main/java/com/rutarescue/ui/resident/centers/CentersFragment.java` | MODIFY | "Nearby" selected by default once the location is known. |
| `app/src/main/java/com/rutarescue/ui/resident/ligtas/LigtasStatusActivity.java` | MODIFY | Uses the new application card. |
| `app/src/main/java/com/rutarescue/ui/lgu/overview/LguOverviewFragment.java` | MODIFY | Hides resident-only actions on LGU advisory cards; "＋ Risk zone" hint. |
| `app/src/main/java/com/rutarescue/ui/lgu/reviews/LguReviewsFragment.java` | MODIFY | Figma application card binding. |
| `app/src/main/java/com/rutarescue/ui/lgu/data/LguDataFragment.java` | MODIFY | Figma dataset/center rows; "Manage" offers Add a center / Import. |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java` | MODIFY | Back, More (legend), Locate and Layers controls; floating sheet. |
| `app/src/main/java/com/rutarescue/ui/map/MapController.java`, `MapStyles.java` | MODIFY | Figma map colours (cyan safer route, teal centers, #EEF1F6 canvas). Attribution moved where it stays visible. |

## 5. Free map, navigation and live traffic (OpenStreetMap + TomTom free tier), 2026-10-15

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/network/OsrmRouteParser.java` | CREATE | Parses OpenStreetMap routing (OSRM) answers into the same `RouteCandidate` objects as TomTom, including turn-by-turn steps. |
| `app/src/main/java/com/rutarescue/network/OsrmRouteProvider.java` | CREATE | Free, keyless navigation via `routing.openstreetmap.de` (foot / car profiles). |
| `app/src/main/java/com/rutarescue/network/FallbackRouteProvider.java` | CREATE | Uses TomTom (live traffic) first and OpenStreetMap routing when TomTom cannot answer (no key, quota used up, error). |
| `app/src/main/java/com/rutarescue/network/NominatimSearchProvider.java` | CREATE | Free OpenStreetMap place search, restricted to Metro Manila. Follows the Nominatim policy: 1 request/second, User-Agent, no search-as-you-type. |
| `app/src/main/java/com/rutarescue/network/PlaceSearchProvider.java` | MODIFY | Adds `allowsSearchAsYouType()`. |
| `app/src/main/java/com/rutarescue/network/TomTomSearchProvider.java`, `TomTomRouteProvider.java` | MODIFY | Implement the new method and tag answers with their provider. |
| `app/src/main/java/com/rutarescue/network/RouteResponse.java` | MODIFY | Provider name, plus `hasLiveTraffic()` so OSM times are never called live traffic. |
| `app/src/main/java/com/rutarescue/network/HttpJsonClient.java` | MODIFY | Descriptive User-Agent, required by OpenStreetMap services. |
| `app/src/main/java/com/rutarescue/network/CachedRouteProvider.java`, `persistence/CachedRouteEntry.java`, `service/RouteService.java` | MODIFY | The offline cache remembers which provider answered; traffic is labelled live only when TomTom included it. |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | Wiring: Nominatim search; TomTom → OSRM fallback routing (OSRM only without a key). |
| `app/src/main/java/com/rutarescue/ui/map/MapStyles.java`, `MapController.java` | MODIFY | OpenStreetMap basemap always. TomTom live traffic-flow overlay with `setTrafficVisible()`. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Online search on submit. Traffic overlay for motorcycle/car. Honest traffic text. |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java` | MODIFY | Live traffic shown with the "Routes" layer. |
| `app/src/main/java/com/rutarescue/ui/about/AboutActivity.java` | MODIFY | OpenStreetMap / Nominatim / OSRM / TomTom attribution. |
| `app/src/test/java/com/rutarescue/network/OpenStreetMapProvidersTest.java`, `app/src/test/resources/*.json` | CREATE | Tests for the OSRM parser, Nominatim parser and TomTom → OSM fallback, with real saved answers. |
| `local.properties.example`, `README.md` | MODIFY | Free key tutorial (no credit card); free-services table. |

## 6. Tests and documentation

| File path | Action | Purpose |
|---|---|---|
| `app/src/test/java/com/rutarescue/risk/RealDataBridgeTest.java` | CREATE | DOCX journey 4 on the real bundled NOAH grid and OSM bridges (MacArthur Bridge, Pasig River). |
| `README.md` | MODIFY | Figma → Android design-system table; real-data bridge test. |

## 7. Fixes found while testing every journey on the emulator, 2026-10-15

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/model/route/AvoidArea.java` | CREATE | A validated rectangle the router must avoid (`around()`, `overlaps()`, `union()`). |
| `app/src/main/java/com/rutarescue/risk/ClosureAvoidance.java` | CREATE | `allClosed()` checks whether every route crosses a closure. `areasToAvoid()` builds merged rectangles (max 10) around the closed stretches. |
| `app/src/main/java/com/rutarescue/network/AvoidingRouteProvider.java` | CREATE | Interface: a RouteProvider that can also plan detours (`requestRoutesAvoiding`). |
| `app/src/main/java/com/rutarescue/network/TomTomRouteProvider.java` | MODIFY | Detours through TomTom's `avoidAreas` (POST body built by `avoidAreasJson()`). |
| `app/src/main/java/com/rutarescue/network/HttpJsonClient.java` | MODIFY | Adds `post(url, jsonBody)` next to `get()`. |
| `app/src/main/java/com/rutarescue/network/FallbackRouteProvider.java` | MODIFY | Detour requests go to TomTom only (OSRM cannot avoid areas). |
| `app/src/main/java/com/rutarescue/service/RouteService.java` | MODIFY | If every live route is closed, asks for detours and ranks them with the best closed route, then re-assesses and caches. |
| `app/src/main/java/com/rutarescue/model/route/RouteCandidate.java` | MODIFY | `withProviderIndex()` to renumber merged routes. |
| `app/src/main/java/com/rutarescue/model/route/RouteAssessment.java` | MODIFY | Honest headline: "Every available route crosses a reported closure" instead of "Safer" when nothing open exists. |
| `app/src/main/java/com/rutarescue/risk/StructureIndex.java` | MODIFY | `partsOfSameStructure()` finds all OSM ways of one bridge (both directions and sidewalks). |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java` | MODIFY | An LGU bridge status applies to every part of the bridge; the dialog says how many parts. |
| `app/src/main/java/com/rutarescue/service/LocationHelper.java` | MODIFY | `startTracking()` / `stopTracking()` for continuous GPS during guidance. "Locate" reuses only fixes newer than 20 s. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | MODIFY | Guidance follows the GPS and scrolls to the current step. The route is framed above the sheet. "min walk/ride/drive". "Safer" is never shown for a closed route. |
| `app/src/main/java/com/rutarescue/service/ConnectivityMonitor.java` | MODIFY | `onLost` now reports offline, so the offline banner appears. |
| `app/src/test/java/com/rutarescue/risk/ClosureAvoidanceTest.java` | CREATE | Avoid areas cover the closed bridge with a margin; overlaps merge; bad corners are rejected. |
| `app/src/test/java/com/rutarescue/risk/RealDataBridgeTest.java` | MODIFY | `closingOneWayOfJonesBridgeClosesBothDirections`. |
| `app/src/test/java/com/rutarescue/network/OpenStreetMapProvidersTest.java` | MODIFY | TomTom `avoidAreas` body format. |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | `signOut()`: signs out and deletes this phone's copy of the resident's personal data (family file, cached routes, advisory marks). |
| `app/src/main/java/com/rutarescue/ui/about/AboutActivity.java` | MODIFY | "Sign out" calls `container().signOut()`. |
| `app/src/main/java/com/rutarescue/service/FamilyService.java` | MODIFY | `load()` keeps family_records.txt for the signed-in resident only. `clearLocalCopy()`. `load()` and `save()` finish the Lab-file update before calling back, so screens never show an old file. |
| `app/src/main/java/com/rutarescue/persistence/RouteCacheStore.java` | MODIFY | `clear()`. |
| `app/src/main/java/com/rutarescue/service/AdvisoryMarks.java` | MODIFY | `clear()`. |
| `app/src/main/java/com/rutarescue/ui/resident/record/MyRecordFragment.java`, `LegacyDataActivity.java` | MODIFY | Read the Lab files after the record has loaded (the old order showed a stale record count). |
| `app/src/test/java/com/rutarescue/persistence/RouteCacheStoreTest.java` | CREATE | Newest-first listing, damaged files skipped, and `clear()`. |
| `start-rutarescue.bat` | CREATE | One-click launcher: Firebase emulators, Pixel_7 emulator, demo data (only on a fresh start), emulator network, Emulator Suite page and the app. |
| `Firebase Emulator Suite.url` | CREATE | Clickable link to http://127.0.0.1:4000. |
| `README.md` | MODIFY | One-click start in §3; detours and multi-part bridges in §6; 85 unit tests; acceptance steps for guidance, lab files and offline; shared-phone privacy in §9. |
