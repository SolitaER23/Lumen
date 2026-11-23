plugins {
    id("com.android.application") version "9.5.2" apply false
    // AGP 9 起内置 Kotlin 支持，不再需要 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
