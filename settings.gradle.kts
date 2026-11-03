pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OrbitLauncher"
include(":baselineprofile")
include(":app", ":core:ui", ":core:data", ":core:domain")
include(":feature:home", ":feature:drawer", ":feature:search", ":feature:settings")
