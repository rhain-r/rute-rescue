// Build-time data preparation (DOCX section 7). Plain Java, never shipped in the APK.
//   ./gradlew :tools:dataprep:prepareHazards     NOAH shapefiles -> GeoJSON overlays + hazard grids + manifest
//   ./gradlew :tools:dataprep:prepareStructures  OSM Overpass export -> bridge/tunnel dataset
//   ./gradlew :tools:dataprep:prepareCenters     legacy Jabbacodeez center list -> seed CSV
plugins {
    java
}

// Compile for Java 17 with whatever JDK runs Gradle (Android Studio's bundled JBR works).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit)
}

val repoRoot = rootDir
val assetsDir = File(repoRoot, "app/src/main/assets")

tasks.register<JavaExec>("prepareHazards") {
    group = "rutarescue data"
    description = "Validates the Project NOAH shapefiles and writes GeoJSON overlays, hazard grids and manifest.json."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rutarescue.tools.HazardPrep")
    maxHeapSize = "3g"
    args(File(repoRoot, "data/raw/noah").path, File(assetsDir, "flood_data").path)
}

tasks.register<JavaExec>("prepareStructures") {
    group = "rutarescue data"
    description = "Converts an OpenStreetMap Overpass export into the bridge/tunnel dataset."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rutarescue.tools.StructurePrep")
    maxHeapSize = "2g"
    args(File(repoRoot, "data/raw/osm/structures_overpass.json").path, File(assetsDir, "structures").path)
}

tasks.register<JavaExec>("prepareCenters") {
    group = "rutarescue data"
    description = "Converts the legacy evacuation_centers.js list into an UNVERIFIED seed CSV."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rutarescue.tools.LegacyCentersPrep")
    args(File(repoRoot, "data/raw/legacy/evacuation_centers.js").path, File(assetsDir, "seed/centers_legacy_unverified.csv").path)
}
