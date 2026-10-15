# Change log: centers every day and emergency hotlines (2026-10-15)

Residents now always see the 91 PLM SSC evacuation centers that are bundled with the app, even before the LGU publishes any and even offline.
Each one is labelled "PLM SSC list, not yet confirmed by LGU", and when the LGU publishes the same center (same name and city), the LGU's version replaces it.
Home gains an "Emergency hotlines" card built from the same Facebook post (labels corrected), and tapping a line opens the phone's dialer.
Actions: **CREATE** = new file, **MODIFY** = partly edited.

## 1. Data and logic (written test-first)

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/persistence/BundledCenterReader.java` | CREATE | Reads the bundled PLM SSC center list and gives each center a fixed id such as `plmssc-3`. |
| `app/src/main/java/com/rutarescue/model/contact/Hotline.java` | CREATE | One agency with its numbers, plus the digits the dialer receives. |
| `app/src/main/java/com/rutarescue/persistence/HotlineReader.java` | CREATE | Reads the bundled hotline file and skips entries without a number. |
| `app/src/main/assets/seed/hotlines_plmssc_2024.json` | CREATE | Copy of `data/sources/plmssc-2024-07-24/hotlines.json` bundled into the app. |
| `app/src/main/java/com/rutarescue/service/CenterService.java` | MODIFY | Adds the bundled list to residents' centers, keeps it when the server fails, and finds bundled centers on the phone. |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | Loads the bundled centers and hotlines once at start-up. |

## 2. Screens

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/ui/common/ViewBinders.java` | MODIFY | Center rows say "PLM SSC list, not yet confirmed by LGU" for bundled centers. |
| `app/src/main/java/com/rutarescue/ui/resident/centers/CenterDetailActivity.java` | MODIFY | Bundled centers explain their source, warn that the pin is approximate, and name the landmark. |
| `app/src/main/java/com/rutarescue/ui/resident/home/HomeFragment.java` | MODIFY | Emergency hotlines card (3 lines, "See all" shows 9) that opens the dialer; centers source chip names its source. |
| `app/src/main/res/layout/fragment_home.xml` | MODIFY | Hotlines section title, card and source caption below the nearby center. |
| `app/src/main/res/drawable/ic_phone.xml` | CREATE | Phone icon in the same outline style as the Figma icons. |
| `app/src/main/res/values/strings.xml` | MODIFY | Hotline and bundled-center labels; a shorter "no center" message. |

## 3. Tests

| File path | Action | Purpose |
|---|---|---|
| `app/src/test/java/com/rutarescue/persistence/BundledCenterReaderTest.java` | CREATE | All 91 bundled centers load unverified, unpublished and with unique ids. |
| `app/src/test/java/com/rutarescue/service/CenterServiceBundledTest.java` | CREATE | Merging, replacement by an LGU center, server failure and offline lookup. |
| `app/src/test/java/com/rutarescue/model/HotlineTest.java` | CREATE | Dialled digits for short codes, ranges and extensions. |
| `app/src/test/java/com/rutarescue/persistence/HotlineReaderTest.java` | CREATE | Reads the 9 bundled hotlines in order. |

Result: 149 app unit tests pass (131 before, 18 new), and the debug app builds.

## 4. Follow-up the same day: capacity for residents, a crash fix and the new logo

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/rutarescue/model/center/CenterCapacity.java` | MODIFY | Adds `summary()` ("212 of 300 people · 71%") and `shortSummary()` ("212/300 people") for residents. |
| `app/src/main/java/com/rutarescue/ui/resident/centers/CenterDetailActivity.java` | MODIFY | New Capacity card with the gauge and who posted the count, or a plain note when none is posted. |
| `app/src/main/res/layout/activity_center_detail.xml` | MODIFY | Capacity section between the address card and Accessibility. |
| `app/src/main/java/com/rutarescue/ui/common/ViewBinders.java` | MODIFY | Center rows show the posted headcount. |
| `app/src/main/java/com/rutarescue/repository/firestore/FieldValues.java` | CREATE | Reads a Firestore value of the wrong type (e.g. a number saved as text) without throwing. |
| `app/src/main/java/com/rutarescue/repository/firestore/FirestoreMappers.java` | MODIFY | Every field helper uses `FieldValues`; a hand-typed `createdAt` text crashed sign-in before. |
| `app/src/main/java/com/rutarescue/ui/lgu/data/LguDataFragment.java` | MODIFY | The empty list tells LGU staff to tap Upload, publish, then tap a center for its capacity. |
| `app/src/main/res/drawable-nodpi/ic_logo_mark.png` | CREATE | New shield logo (cut out of the team's logo picture) for the header and sign-in screen. |
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | CREATE | New app-icon foreground, sized to the adaptive-icon safe zone. |
| `app/src/main/res/drawable/ic_logo_mark.xml`, `ic_launcher_foreground.xml` | DELETE | The old logo drawings. |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | MODIFY | White icon background behind the navy shield. |
| `app/src/main/res/values/strings.xml` | MODIFY | Capacity card and LGU empty-list text. |
| `app/src/test/java/com/rutarescue/model/CenterCapacityTextTest.java` | CREATE | The resident headcount text. |
| `app/src/test/java/com/rutarescue/repository/FieldValuesTest.java` | CREATE | Wrong-type values fall back instead of crashing. |
| `docs/media/recording-ads.mp4`, `recording-ads.gif`, `logo.png` | CREATE | The 20 s ad (no flood layer in any frame), its README GIF and the logo. |
| `docs/demo/screens/*.jpg` (6 new files) | CREATE | README screens with the new logo and no flood layer. |
| `README.md` | MODIFY | Logo, ad, new screens, centers, capacity, hotlines and the test count. |

Result: 156 app unit tests pass, and the debug app builds.
