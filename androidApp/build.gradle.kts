plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vichua.where"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vichua.where"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }

    lint {
        // Kotlin 2.4.10 当前只声明兼容到 AGP 9.1 和 Gradle 9.5，不能跟随通用升级提示。
        disable += "AndroidGradlePluginVersion"
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":platform:android"))
    implementation(libs.androidx.activity.compose)
}
