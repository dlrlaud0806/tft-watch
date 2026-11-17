plugins {
    // AGP 9는 Kotlin 지원이 내장돼 있어서 org.jetbrains.kotlin.android 플러그인이 필요 없어요
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
}
