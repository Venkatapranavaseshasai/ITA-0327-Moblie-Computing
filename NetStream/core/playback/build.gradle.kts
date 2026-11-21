plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
    namespace = "dev.netstream.playback"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:domain"))
    api(libs.media3.player)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.androidx.core)
    implementation(libs.coroutines.android)
}
