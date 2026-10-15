pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RutaRescue"

// The Android application (Java + XML).
include(":app")
// Build-time data preparation tools (plain Java). They are NOT shipped inside the APK.
include(":tools:dataprep")
// Firebase Emulator Suite: security-rule tests and demo-data seeding (plain Java).
include(":tools:emulator")
