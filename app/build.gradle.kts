plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI подставляет версию из тега: ./gradlew assembleRelease -PversionName=1.13 -PversionCode=11300.
// Локальная сборка без флагов берёт дефолт ниже.
val injectedVersionName = providers.gradleProperty("versionName").orNull
val injectedVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull()

android {
    namespace = "com.dangler.tune"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dangler.tune"
        minSdk = 26
        targetSdk = 36
        versionCode = injectedVersionCode ?: 14
        versionName = injectedVersionName ?: "1.13"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Общий ключ банды: все сборки (CI и релизы) подписаны одинаково,
    // иначе самообновление из приложения упиралось бы в конфликт подписей.
    signingConfigs {
        create("dangler") {
            storeFile = rootProject.file("keystore/dangler.jks")
            storePassword = "dangler123"
            keyAlias = "dangler"
            keyPassword = "dangler123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dangler")
        }
        release {
            signingConfig = signingConfigs.getByName("dangler")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:dsp"))
    implementation(project(":core:tuner"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    testImplementation(libs.junit)
}
