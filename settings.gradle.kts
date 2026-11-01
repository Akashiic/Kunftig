pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.33")
}

// The root project is the Forge 1.7.10 client mod (GTNH convention).
// :common is a dependency-free Java 8 library shared by the mod and the plugin.
// :api is kunftig-api, what Bukkit plugins compile against (embedded in the mod for runtime).
// :bukkit is the server plugin (Thermos/Crucible 1.7.10 and Paper 1.21+).
include(":common", ":api", ":bukkit")
