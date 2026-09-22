plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val orientCompileSdk = (project.findProperty("orient.compileSdk") as String).toInt()
val orientTargetSdk = (project.findProperty("orient.targetSdk") as String).toInt()
val orientMinSdk = (project.findProperty("orient.minSdk") as String).toInt()

android {
    namespace = "com.orient.manager"
    compileSdk = orientCompileSdk
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.orient.manager"
        minSdk = orientMinSdk
        targetSdk = orientTargetSdk
        versionCode = 27
        versionName = "0.27.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
