rootProject.name = "Nuvio"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":androidApp")
