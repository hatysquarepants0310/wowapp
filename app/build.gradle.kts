plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.azeroth.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azeroth.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Registrado en el Blizzard Developer Portal (cliente público PKCE).
        // Se inyecta con -PblizzardClientId=... o en gradle.properties local.
        buildConfigField(
            "String",
            "BLIZZARD_CLIENT_ID",
            "\"${project.findProperty("blizzardClientId") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "BLIZZARD_CLIENT_SECRET",
            "\"${project.findProperty("blizzardClientSecret") ?: ""}\"",
        )
    }

    signingConfigs {
        // Keystore comunitario versionado en el repo: garantiza que TODOS los
        // APK publicados (CI incluido) compartan firma, para que las
        // actualizaciones instalen sin desinstalar. No acredita autoría (la
        // clave es pública); solo da continuidad de firma. No usar para Play Store.
        create("community") {
            storeFile = rootProject.file("signing/community.keystore")
            storePassword = "azeroth-community"
            keyAlias = "azeroth"
            keyPassword = "azeroth-community"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("community")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("community")
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
