import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env) ?: keystoreProps.getProperty(key)

val releaseStorePath = signingValue("LUMEN_KEYSTORE_FILE", "storeFile")
val hasReleaseKey = !releaseStorePath.isNullOrBlank() && rootProject.file(releaseStorePath).exists()

android {
    namespace = "app.lumen.player"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "app.lumen.player"
        minSdk = 26
        targetSdk = 37
        versionCode = 157
        versionName = "0.54.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    androidResources {
        noCompress += listOf("tflite")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = signingValue("LUMEN_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("LUMEN_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("LUMEN_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 正式分发只允许 release 证书；缺少密钥时产出未签名 APK，绝不回退 debug 签名
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-datasource:1.11.1")
    implementation("androidx.media3:media3-database:1.11.1")

    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    // Coil 3 不再内置网络能力：coil-network-okhttp 提供 OkHttp fetcher，由 LumenApp 显式注册
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("com.google.ai.edge.litert:litert:2.4.0")
    implementation("com.google.ai.edge.litert:litert-api:2.4.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("io.github.p2o51:yoin-symbols:0.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}