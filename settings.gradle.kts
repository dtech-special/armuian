pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ArmUian"
include(":app", ":core", ":system-detection", ":storage-manager", ":root-bridge", ":image-manager", ":container-engine")
