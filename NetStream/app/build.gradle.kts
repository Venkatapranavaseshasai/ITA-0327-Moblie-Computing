plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
// An unconfigured checkout is a fully playable demo, not a broken Firebase app.
val cloudEnabled = file("google-services.json").isFile
if (cloudEnabled) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "dev.netstream.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.netstream.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "CLOUD_ENABLED", cloudEnabled.toString())
        manifestPlaceholders["analyticsEnabled"] = cloudEnabled.toString()
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
    implementation(libs.androidx.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
}
