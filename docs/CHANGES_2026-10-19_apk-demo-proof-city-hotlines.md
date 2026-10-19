# Change log: signed APK, demo proof and city hotlines (2026-10-19)

**Signed APK.** RutaRescue 1.1 is built as a signed release APK, the same as Android Studio's *Build › Generate Signed Bundle / APK*. It installs on Android 8.0 and newer.

**Demo proof.** The Ligtas application attaches the bundled "SYNTHETIC DEMO PROOF" image by itself, so it can be tested without a real document.

**City hotlines.** Home shows the hotline of the resident's own city (the city chosen at sign-up) above the national hotlines. The "From the PLM SSC post" line is gone.

Actions: **CREATE** = new file, **MODIFY** = partly edited.

## 1. Demo proof for the Ligtas application

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/assets/demo/demo-proof.png` | CREATE | The team's synthetic proof image, shipped inside the APK. |
| `app/src/main/java/com/rutarescue/ui/common/ImageCompressor.java` | MODIFY | Can also shrink an image bundled in the app (`compressAsset`), not only a picked one. |
| `app/src/main/java/com/rutarescue/ui/resident/ligtas/LigtasApplyActivity.java` | MODIFY | Attaches the demo proof when the screen opens. "Choose image" replaces it, and "Use the demo proof" puts it back. |
| `app/src/main/res/layout/activity_ligtas_apply.xml` | MODIFY | "Use the demo proof" button; the preview shows the whole image. |
| `app/src/main/res/values/strings.xml` | MODIFY | Button and "Demo proof attached" texts. |

## 2. Each city's own emergency hotline

The PLM SSC post only lists national hotlines, so each city's number was looked up on 2026-10-19. Sources were used in this order:
1. the city's own website;
2. the DILG-NCR iDRRM profile;
3. a recent news report quoting the LGU;
4. the GMA News list of 24 Jul 2024.

The source and link of every number are in the data file. **Las Piñas is the weakest entry** (an undated city web page), so check it first.

| File path | Action | Purpose |
|---|---|---|
| `app/src/main/assets/seed/hotlines_city_2026.json` | CREATE | One hotline entry for each of the 17 Metro Manila LGUs, with its source. |
| `app/src/main/java/com/rutarescue/persistence/CityHotlineReader.java` | CREATE | Reads that file into hotlines per city; unknown cities and entries without numbers are skipped. |
| `app/src/main/java/com/rutarescue/AppContainer.java` | MODIFY | Loads the city hotlines at start-up. |
| `app/src/main/java/com/rutarescue/ui/resident/home/HomeFragment.java` | MODIFY | The resident's city line first, then 911 and the Red Cross; "See all" adds the other national lines. |
| `app/src/main/res/layout/fragment_home.xml`, `values/strings.xml` | MODIFY | Removes "From the PLM SSC post, 24 Jul 2024…". |
| `app/src/test/java/com/rutarescue/persistence/CityHotlineReaderTest.java` | CREATE | Every city has a hotline, and every number can be dialled (no old 7-digit landlines). |

## 3. Signed release APK

| File path | Action | Purpose |
|---|---|---|
| `app/build.gradle.kts` | MODIFY | Signs release builds with the key named in `local.properties`. A fresh clone without it builds an unsigned release. Version 1.1 (code 2). |
| `.gitignore` | MODIFY | Keeps `keystore/`, `*.jks` and the built `apk/` folder out of git. |

The signing key and its passwords are **not in git**:
- the key is `keystore/rutarescue-release.jks`;
- its passwords are the `RELEASE_*` lines in `local.properties`.

Back up both. Every future update of the installed app must be signed with this same key.

To build the APK again:

```bash
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## 4. Checks

- 168 app unit tests pass (165 before, 3 new).
- The APK signature verifies (APK Signature Scheme v2). The package is `com.oop.rutarescue` 1.1, and the demo proof, both hotline files and the flood data are inside.
- On the emulator:
  - the Ligtas screen opened with the demo proof attached (17 KB). No application was submitted.
  - Home showed "Manila DRRM Office (MCDRRMO)" first, and tapping it opened the dialer with 0285270972.
- A demo family record (FR-0002, "Team Ba" and "Demo Lola Ba", made-up data) was created for the test resident in Firebase so the Ligtas screen could open.
- A phone that has the app from Android Studio (debug key) must uninstall it before installing this APK. The signing keys differ.
