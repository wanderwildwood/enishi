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

rootProject.name = "enishi"
include(":app")
// The contacts sync engine and glue, a copy of /opt/projects/shared-davsync (see its README).
include(":dav4jvm", ":synctools", ":davsync")
