import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

/**
 * 签名材料（keystore + 口令）不进仓库。查找顺序：
 *  1) 环境变量 LUMEN_KEYSTORE_FILE / LUMEN_KEYSTORE_PASSWORD / LUMEN_KEY_ALIAS / LUMEN_KEY_PASSWORD（CI 用）；
 *  2) 用户私有目录 ~/.lumen/keystore.properties（.jks 放同目录，本机构建用）；
 *  3) 兼容旧位置 <repo>/keystore.properties：仅告警，便于历史环境迁移。
 */
val privateKeystoreDir = File(System.getProperty("user.home"), ".lumen")
val privateKeystoreProps = File(privateKeystoreDir, "keystore.properties")
val legacyKeystoreProps = rootProject.file("keystore.properties")

val keystorePropsFile: File? = when {
    privateKeystoreProps.exists() -> privateKeystoreProps
    legacyKeystoreProps.exists() -> legacyKeystoreProps.also {
        logger.warn("签名材料仍在仓库内(" + it + ")，请迁移到 " + privateKeystoreDir + "，见 docs/plans/optimization-plan.md A2")
    }
    else -> null
}

val keystoreProps = Properties().apply {
    keystorePropsFile?.inputStream()?.use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)

/** storeFile 允许：绝对路径 / 相对 props 文件目录 / 相对工程根目录。 */
fun resolveKeystoreFile(raw: String?): File? {
    if (raw.isNullOrBlank()) return null
    val direct = File(raw)
    if (direct.isAbsolute) return direct.takeIf { it.exists() }
    keystorePropsFile?.parentFile?.resolve(raw)?.takeIf { it.exists() }?.let { return it }
    return rootProject.file(raw).takeIf { it.exists() }
}

val releaseStoreFile: File? = resolveKeystoreFile(signingValue("LUMEN_KEYSTORE_FILE", "storeFile"))
val hasReleaseKey = releaseStoreFile != null


/**
 * 条款版本号从 assets/legal/privacy.json 读取并注入 BuildConfig，
 * 让「改了条款 → 自动要求重新同意」成为编译期事实，而不是靠人手改常量。
 */
val legalVersion: String = runCatching {
    val json = file("src/main/assets/legal/privacy.json").readText()
    Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "0"

// 更新日志单一来源：仓库根 CHANGELOG.md。
// 应用内「更新日志」读的是 assets/CHANGELOG.md；0.75.0 之前这里一直是一份从未同步的副本，
// 导致正式版里仍显示 0.74.0。现在每次构建配置阶段就把它对齐（只差 150 KB，开销可忽略），
// 副本仍留在仓库里，diff 里能直接看到这一版带了哪些日志。
run {
    val rootChangelog = rootProject.projectDir.parentFile.resolve("CHANGELOG.md")
    val assetChangelog = layout.projectDirectory.file("src/main/assets/CHANGELOG.md").asFile
    val rootText = rootChangelog.takeIf { it.isFile }?.readText()
    if (rootText != null && (!assetChangelog.isFile || assetChangelog.readText() != rootText)) {
        assetChangelog.parentFile.mkdirs()
        assetChangelog.writeText(rootText)
    }
}

android {
    namespace = "app.lumen.player"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "app.lumen.player"
        minSdk = 26
        targetSdk = 37
        versionCode = 234
        versionName = "0.94.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("String", "LEGAL_VERSION", "\"" + legalVersion + "\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // 只把「真实缺陷」当错误；刻意为之的项在 lint.xml 里带理由关闭
        lintConfig = file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("LUMEN_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("LUMEN_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("LUMEN_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 仅调试包：独立包名，可与正式包共存，便于并行验证
            applicationIdSuffix = ".debug"
        }
        // 仅用于本地取数：与 release 同签名、同包名，但 debuggable，便于 run-as 导出数据库
        create("verify") {
            initWith(getByName("release"))
            // 与正式包隔离：可调试包绝不能和 app.lumen.player 同包名，否则会被误装覆盖正式版
            applicationIdSuffix = ".verify"
            isDebuggable = true
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
        }
        release {
            // 正式分发只允许 release 证书；缺少密钥时产出未签名 APK，绝不回退 debug 签名
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = false }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    configurations.all {
        resolutionStrategy.force("androidx.compose.material3:material3:1.5.0")
    }

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.5.0")
    // 只维护 Pixel 10a 单栏布局：曾用的 material3-window-size-class 与 androidx.window
    // （双栏 + 折叠屏姿态）已随双栏布局一并移除，见 docs/plans/ui-ux-plan.md §8
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
    implementation("io.github.p2o51:yoin-symbols:0.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // UI 点击流（登录→播放→清除数据）用 Compose 测试规则驱动
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}