pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "NetStream"
include(":app", ":core", ":core:domain", ":core:playback", ":core:data")

project(":app").projectDir = file("NetStream/app")
project(":core").projectDir = file("NetStream/core")
project(":core:domain").projectDir = file("NetStream/core/domain")
project(":core:playback").projectDir = file("NetStream/core/playback")
project(":core:data").projectDir = file("NetStream/core/data")
