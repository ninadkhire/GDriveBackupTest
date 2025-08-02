plugins {
    id("com.android.application")
}

android {
    namespace = "com.ninadkhire.gdrivebackuptest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ninadkhire.gdrivebackuptest"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    // https://mvnrepository.com/artifact/com.google.auth/google-auth-library-oauth2-http/1.28.0
    implementation("com.google.auth:google-auth-library-oauth2-http:1.28.0")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}