plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "studio.room211.racuni"
    compileSdk = 34

    defaultConfig {
        applicationId = "studio.room211.racuni"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jsoup:jsoup:1.23.2")

    // Ugrađeni ML Kit model pouzdanije čita male, rotirane QR kodove sa fotografija.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Kamera i dekodiranje QR-a rade potpuno lokalno, bez slanja slike servisu.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("junit:junit:4.13.2")
    // Prava implementacija za JVM testove; android.jar sadrži samo prazne metode.
    testImplementation("org.json:json:20260814")
}
