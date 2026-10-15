// Firebase Local Emulator Suite helpers (plain Java, never shipped in the APK):
//   ./gradlew :tools:emulator:test          security-rule tests (DOCX acceptance journey 9)
//   ./gradlew :tools:emulator:seedEmulator  demo accounts + sample advisory for classroom demos
// Start the emulators first:  cd firebase && firebase emulators:start --project demo-rutarescue
plugins {
    java
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // The tests talk to the local emulator; never cache their result.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<JavaExec>("seedEmulator") {
    group = "rutarescue firebase"
    description = "Creates demo resident/LGU accounts and a sample advisory in the local Firebase emulators."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rutarescue.emulator.SeedDemoData")
}
