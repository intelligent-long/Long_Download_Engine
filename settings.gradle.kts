pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "central-aliyun"
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            name = "jcenter-central-aliyun"
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            name = "google-aliyun"
            url = uri("https://maven.aliyun.com/repository/google")
        }
        mavenCentral()
        google()
        maven {
            name = "JetBrainsReleases"
            url = uri("https://www.jetbrains.com/intellij-repository/releases")
        }
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

rootProject.name = "LongDownloadEngine"

include(":longdownloadengine-core")
include(":longdownloadengine-android")
