rootProject.name = "capture-sdk"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

include(":capture")
include(":capture-apollo")
include(":capture-timber")
include(":common")
include(":replay")
include(":capture-plugin")

include(":gradle-test-app")

include(":microbenchmark")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
