rootProject.name = "capture-sdk"

include(":capture")
include(":capture-apollo3")
include(":capture-timber")
include(":common")
include(":replay")

include(":gradle-test-app")

include(":microbenchmark")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
