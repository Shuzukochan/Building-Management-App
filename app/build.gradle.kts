import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

android {
    namespace = "com.app.buildingmanagement"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.buildingmanagement"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val apiKey = requireNotNull(localProps.getProperty("API_KEY")) { "API_KEY missing from local.properties" }
        val clientId = requireNotNull(localProps.getProperty("CLIENT_ID")) { "CLIENT_ID missing from local.properties" }
        val signature = requireNotNull(localProps.getProperty("SIGNATURE")) { "SIGNATURE missing from local.properties" }
        val debugToken = localProps.getProperty("FIREBASE_APPCHECK_DEBUG_TOKEN", "")

        buildConfigField("String", "API_KEY", "\"$apiKey\"")
        buildConfigField("String", "CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "SIGNATURE", "\"$signature\"")
        buildConfigField("String", "FIREBASE_APPCHECK_DEBUG_TOKEN", "\"$debugToken\"")
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}


dependencies {
    implementation(libs.okhttp)
    implementation(libs.webkit)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database.ktx)
    // Removed mpandroidchart - using native Compose charts now
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.messaging)
    
    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    
    // ConstraintLayout Compose - cần thiết cho layout giống XML gốc
    implementation(libs.constraintlayout.compose)

    // Compose Material Icons - sử dụng BOM để quản lý version
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    
    // Compose UI thêm
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    
    // Fragment KTX cho Fragment
    implementation(libs.fragment.ktx)
    
    // Navigation Compose
    implementation(libs.navigation.compose)
    
    debugImplementation(libs.compose.ui.tooling)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
