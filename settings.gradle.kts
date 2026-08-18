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

rootProject.name = "Where"

include(":androidApp")

include(":shared:core:common")
include(":shared:core:model")
include(":shared:core:database")
include(":shared:core:search")
include(":shared:core:crypto")
include(":shared:core:platform-api")

include(":shared:feature:item")
include(":shared:feature:location")
include(":shared:feature:search")
include(":shared:feature:backup")
include(":shared:feature:settings")

include(":shared:ui")
include(":platform:android")
