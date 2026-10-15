# Change log: Phase 2 build of the approved Phase 1 screens (2026-10-15)

The approved frames are in `docs/design/phase1-ui.html`, and the real app screens are in `docs/design/phase2-app-screens.png`.
Every file below is saved in the project with its complete code.
Actions: **CREATE** = new file, **REPLACE** = rewritten, **MODIFY** = partly edited.

## 1. Domain logic (written test-first)

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/model/center/CenterCapacity.java` | CREATE | Immutable capacity and headcount that refuses any count above 120% of capacity. |
| `app/src/main/java/com/rutarescue/model/center/OccupancyLevel.java` | CREATE | Gauge colour band: green under 70%, amber from 70% to 90%, red above 90%. |
| `app/src/main/java/com/rutarescue/model/center/CapacityChange.java` | CREATE | The last headcount change, shown as "+5 by Manila DRRM Demo". |
| `app/src/main/java/com/rutarescue/model/center/CenterStatus.java` | REPLACE | Adds Near capacity, Full and Unavailable; old "Standby" values load as "not reported". |
| `app/src/main/java/com/rutarescue/model/center/AccessibilityFeature.java` | REPLACE | The four facilities from the design; older saved names map to the closest one. |
| `app/src/main/java/com/rutarescue/model/center/EvacuationCenter.java` | MODIFY | Adds capacity, last change, barangay, landmark and the location-verified flag. |
| `app/src/main/java/com/rutarescue/model/route/RouteRole.java` | CREATE | Card names: Lowest risk, Balanced, Most direct, Alternative, Only route. |
| `app/src/main/java/com/rutarescue/model/route/RouteWarning.java` | CREATE | One warning-drawer line, marked serious for closures and high flood hazard. |
| `app/src/main/java/com/rutarescue/risk/RouteRoleAssigner.java` | CREATE | Names the ranked routes without changing the DOCX risk ranking. |
| `app/src/main/java/com/rutarescue/risk/RouteWarningBuilder.java` | CREATE | Builds the warning list: closure, flood by class with street, unverified bridges, traffic. |
| `app/src/main/java/com/rutarescue/risk/RerouteAdvisor.java` | CREATE | Decides when a fresh route clearly beats the rest of the current one during guidance. |
| `app/src/main/java/com/rutarescue/model/map/BaseMap.java` | CREATE | Standard, Topographic and Satellite base maps. |
| `app/src/main/java/com/rutarescue/model/map/MapLayerSettings.java` | CREATE | The Map layers choices; routes use the largest flood switched on. |

## 2. Data, rules and services

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/repository/CenterSnapshot.java` | CREATE | One center plus its "pending sync" flag for the capacity screen. |
| `app/src/main/java/com/rutarescue/repository/CenterRepository.java` | MODIFY | Adds `listen(id)` for one center. |
| `app/src/main/java/com/rutarescue/repository/firestore/FirestoreCenterRepository.java` | MODIFY | Implements `listen(id)` with Firestore's pending-writes flag. |
| `app/src/main/java/com/rutarescue/repository/firestore/FirestoreMappers.java` | MODIFY | Saves and reads capacity, headcount, last change, barangay, landmark and location flag. |
| `app/src/main/java/com/rutarescue/service/CenterService.java` | MODIFY | Adds `updateCapacity()` with one audit event; nearest center skips Full and Unavailable. |
| `app/src/main/java/com/rutarescue/service/MapLayerPreferences.java` | CREATE | Remembers the Map layers choices on the phone. |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | Provides `MapLayerPreferences`. |
| `app/src/main/java/com/rutarescue/persistence/CenterCsvReader.java` | MODIFY | Also reads barangay, landmark and location_verified columns. |
| `firebase/firestore.rules` | MODIFY | Refuses a center headcount above 120% of its capacity. |
| `tools/emulator/src/test/java/com/rutarescue/emulator/SecurityRulesTest.java` | MODIFY | Proves 145 of 120 people is refused and 144 is allowed. |
| `app/src/main/assets/seed/centers_plmssc_2024_unverified.csv` | CREATE | 91 PLM SSC centers with usable pins, all unverified. |
| `data/sources/plmssc-2024-07-24/*.json` | CREATE | All 139 PLM SSC centers and the corrected hotlines (source data). |

## 3. Screens and components

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/map/MapLayersSheet.java` | CREATE | The Map layers sheet; every switch updates the map at once. |
| `app/src/main/res/layout/sheet_map_layers.xml`, `item_layer_row.xml`, `item_base_map.xml` | CREATE | Sheet, switch row and base-map tile from the approved frame. |
| `app/src/main/java/com/rutarescue/ui/map/MapController.java` | MODIFY | Base-map switching, several flood maps at once, coloured route lines and time pills. |
| `app/src/main/java/com/rutarescue/ui/map/MapStyles.java` | MODIFY | Adds OpenTopoMap and Esri imagery sources. |
| `app/src/main/java/com/rutarescue/ui/resident/route/SafeRouteFragment.java` | REPLACE | Route cards, warning drawer, "Start anyway", turn card, quiet reroute and toast. |
| `app/src/main/res/layout/fragment_safe_route.xml` | REPLACE | Icon travel pills, flood tag, turn card, toast and the new result and guidance states. |
| `app/src/main/res/layout/item_route_option.xml`, `item_route_warning.xml` | CREATE | Route card and warning row. |
| `app/src/main/java/com/rutarescue/ui/shell/MainShellActivity.java` | MODIFY | Lets Safe Route hide the bottom navigation while routes are open. |
| `app/src/main/java/com/rutarescue/ui/common/MaxHeightScrollView.java` | MODIFY | Allows a taller sheet while routes are compared. |
| `app/src/main/java/com/rutarescue/ui/lgu/hazardmap/LguHazardMapFragment.java` | MODIFY | The LGU Layers button opens the same sheet; the pills stay in step. |
| `app/src/main/java/com/rutarescue/ui/lgu/data/CenterCapacityActivity.java` | CREATE | The Center capacity screen, with its offline "Pending sync" state. |
| `app/src/main/java/com/rutarescue/ui/common/CapacityGaugeView.java` | CREATE | The coloured gauge with 70% and 90% marks. |
| `app/src/main/res/layout/activity_center_capacity.xml`, `item_status_chip.xml`, `item_facility_checkbox.xml` | CREATE | Capacity screen layout, status pill and facility checkbox. |
| `app/src/main/java/com/rutarescue/ui/lgu/data/LguDataFragment.java` | MODIFY | A center row opens Center capacity and shows its headcount. |
| `app/src/main/java/com/rutarescue/ui/lgu/data/DatasetImportActivity.java`, `res/layout/activity_dataset_import.xml` | MODIFY | Adds "Use PLM SSC 2024 list". |
| `app/src/main/java/com/rutarescue/ui/common/Badges.java` | MODIFY | Badge tones for the new statuses. |
| `app/src/main/AndroidManifest.xml` | MODIFY | Registers `CenterCapacityActivity`. |
| `app/src/main/res/values/styles_phase2.xml` | CREATE | Styles from the approved frames (buttons, chips, rows, cards). |
| `app/src/main/res/values/colors.xml`, `strings.xml` | MODIFY | New colours and labels. |
| `app/src/main/res/color/*.xml`, `drawable/bg_*.xml`, `drawable/thumb_map_*.xml` | CREATE | Switch, checkbox and chip colours, card shapes, and base-map thumbnails. |

## 4. Tests

| File path | Action | Purpose |
|---|---|---|
| `app/src/test/java/com/rutarescue/model/CenterCapacityTest.java` | CREATE | Gauge bands, 120% limit, quick buttons and suggested status. |
| `app/src/test/java/com/rutarescue/model/CenterStatusAndFeatureTest.java` | CREATE | New statuses and loading of older saved values. |
| `app/src/test/java/com/rutarescue/model/EvacuationCenterCapacityTest.java` | CREATE | Saving capacity records the change line and location details. |
| `app/src/test/java/com/rutarescue/model/MapLayerSettingsTest.java` | CREATE | Defaults, route scenario choice and saving the choices. |
| `app/src/test/java/com/rutarescue/risk/RouteRoleAssignerTest.java` | CREATE | Card names, including the one-minute saving rule. |
| `app/src/test/java/com/rutarescue/risk/RouteWarningBuilderTest.java` | CREATE | Warning text, order and the serious flag. |
| `app/src/test/java/com/rutarescue/risk/RerouteAdvisorTest.java` | CREATE | When guidance swaps routes, and when it must not. |
| `app/src/test/java/com/rutarescue/risk/RouteTestData.java` | CREATE | Small builder for test routes. |
| `app/src/test/java/com/rutarescue/persistence/CenterCsvReaderTest.java` | CREATE | Old and new center list formats. |

Result: 131 app unit tests and 11 database rule tests pass.
