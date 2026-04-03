tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    val dokkaBaseConfiguration = """
    {
      "footerMessage": "(c) 2025 bitdrift, Inc.",
      "separateInheritedMembers": true
    }
    """
    pluginsMapConfiguration.set(mapOf("org.jetbrains.dokka.base.DokkaBase" to dokkaBaseConfiguration))

    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set("io\\.bitdrift\\.capture\\..*")
            suppress.set(false)
        }
        perPackageOption {
            matchingRegex.set(".*")
            suppress.set(true)
        }
    }
}
