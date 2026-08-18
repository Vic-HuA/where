plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vichua.where.platform.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:platform-api"))
    implementation(project(":shared:feature:location"))
}
