plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.graphicanest.client"
 compileSdk = 36
 defaultConfig {
   applicationId = "com.graphicanest.client"
   minSdk = 26
   targetSdk = 35
   versionCode = 6
   versionName = "0.6.0"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 signingConfigs {
  create("release") {
   val filePath = System.getenv("CLIENT_SIGNING_KEYSTORE_FILE")
   if (!filePath.isNullOrBlank()) {
    storeFile = file(filePath)
    storePassword = System.getenv("CLIENT_SIGNING_STORE_PASSWORD")
    keyAlias = System.getenv("CLIENT_SIGNING_KEY_ALIAS")
    keyPassword = System.getenv("CLIENT_SIGNING_KEY_PASSWORD")
   }
  }
 }
 buildTypes {
  getByName("release") { signingConfig = signingConfigs.getByName("release") }
 }
 buildFeatures { compose = true }
}
dependencies {
 val bom = platform("androidx.compose:compose-bom:2025.08.01")
 implementation(bom)
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.activity:activity-compose:1.12.3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui-tooling-preview")
}
