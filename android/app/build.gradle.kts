plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.app"
    compileSdk = 35
    buildToolsVersion = "35.0.1"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "0.6.4"

        ndk {
            // 唯一目标机是 Pixel 10a（arm64-v8a）：只打这一个 ABI，
            // 不再为模拟器编 x86_64（APK 少一半，构建也快）。
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets.getByName("main") {
        // cargo ndk 会把编译好的 .so 输出到这里
        jniLibs.srcDir("src/main/jniLibs")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // GameActivity：NativeActivity 不支持 IME（候选词 commit/预编辑拿不到），
    // GameActivity 通过 GameTextInput 完整支持软键盘输入。
    // GameActivity 继承 AppCompatActivity，需要显式加 appcompat（传递依赖没自动打包）。
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.games:games-activity:4.4.0")
    // 统一 kotlin-stdlib 版本（appcompat 和 games-activity 拉的版本冲突）
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22") {
            because("统一版本避免 Duplicate class")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22") {
            because("统一版本避免 Duplicate class")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22") {
            because("统一版本避免 Duplicate class")
        }
    }
}
