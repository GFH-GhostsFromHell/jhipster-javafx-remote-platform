plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("0.1.0")
val releaseVersionCode = providers.gradleProperty("releaseVersionCode").orElse("1")
val updateChannel = providers.gradleProperty("updateChannel").orElse("development")
val androidKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.gfh.electricalworkbench"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gfh.electricalworkbench"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode.get().toInt()
        versionName = releaseVersionName.get()
        buildConfigField("String", "UPDATE_CHANNEL", "\"${updateChannel.get()}\"")
    }

    if (!androidKeystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(androidKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!androidKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui:1.10.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.4")
    testImplementation("junit:junit:4.13.2")
}
