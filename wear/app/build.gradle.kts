plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gm.tftwatch"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gm.tftwatch"
        minSdk = 30          // Wear OS 3 (Galaxy Watch4 이상)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DECKS_URL", "\"${project.property("decksUrl")}\"")
    }
    buildTypes {
        // 워치는 무선 설치가 느리고 잘 끊겨서, 디버그 빌드도 안 쓰는 코드를 걷어내 용량을 줄여요
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

dependencies {
    // Tile
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    // 앱 화면 (Compose for Wear OS)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    // 챔피언·아이템 이미지 로딩
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
