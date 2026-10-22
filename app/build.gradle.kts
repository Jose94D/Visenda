plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "cl.visenda.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "cl.visenda.app"
        minSdk = 26 // Android 8.0
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

room {
    // Esquemas versionados en git: los necesitaremos para migrar la base al restaurar respaldos antiguos.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)

    implementation(libs.sqlcipher) // base de datos cifrada (Hito 4)
    ksp(libs.room.compiler)

    implementation(libs.valhalla.mobile)
    implementation(libs.valhalla.models.config) // la librería lo publica solo en runtime, pero su API lo expone
    implementation(libs.maplibre)

    testImplementation(libs.junit)
}
