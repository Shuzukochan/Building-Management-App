import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

android {
    namespace = "com.app.buildingmanagement"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.app.buildingmanagement"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
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
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
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
    implementation(libs.mpandroidchart)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
