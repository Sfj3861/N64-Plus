plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.sfj.n64plus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sfj.n64plus"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-integration"
    }
    buildFeatures { }
}