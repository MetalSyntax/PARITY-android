plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.metalsyntax.paritykt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.metalsyntax.paritykt" // distinto de la Parity actual: conviven en el teléfono
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.6.0"
    }

    // A/B: Legacy = la app parecida a la web (mismo paquete, se actualiza sin perder datos);
    // Modern = el rediseño de docs/REDISENO_DASHBOARD.md, paquete aparte para instalarlas lado a lado
    flavorDimensions += "variant"
    productFlavors {
        create("legacy") {
            dimension = "variant"
            resValue("string", "app_name", "Parity Legacy")
            buildConfigField("boolean", "MODERN", "false")
        }
        create("modern") {
            dimension = "variant"
            applicationIdSuffix = ".modern"
            versionNameSuffix = "-modern"
            resValue("string", "app_name", "Parity Modern")
            buildConfigField("boolean", "MODERN", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Google Drive (alcance drive.file, mismo que la web): token OAuth con la cuenta del teléfono
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("androidx.biometric:biometric:1.1.0") // huella / rostro, como YiSecure
    // OCR de facturas 100 % en el teléfono: modelo neuronal de ML Kit empaquetado en el APK (no usa Google Play ni internet)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013") // org.json real (el de android.jar es un stub en pruebas locales)
}
