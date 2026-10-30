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
        // Xposed API 在这里
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "QuickWallet"
include(":app")
