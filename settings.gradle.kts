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
        // libsu (root shell) is published on JitPack, not Maven Central. The content filter makes
        // Gradle ask JitPack for that one group only, so nothing else can ever resolve from it.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.topjohnwu.libsu") }
        }
    }
}

rootProject.name = "localbuds"
include(":app")
include(":protocol")
include(":dsp")
