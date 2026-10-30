plugins {
    id("com.android.application")
}

android {
    namespace = "com.dsh.minesweeper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.minesweeper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("stable") {
            storeFile = rootProject.file("keystore/minesweeper.p12")
            storePassword = "minesweeper"
            keyAlias = "minesweeper"
            keyPassword = "minesweeper"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
