plugins {
    id("com.android.application")
}

android {
    namespace = "com.gnayoah.quickwallet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gnayoah.quickwallet"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 只参与编译，运行时由 LSPosed 提供
    compileOnly("de.robv.android.xposed:api:82")
}
