import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Llave de publicación: se lee de keystore.properties (en la raíz, fuera de git; ver keystore.properties.ejemplo).
// Si no existe, la versión "release" sale sin firmar y no se puede instalar.
val firma = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Identificador en la Play Store y en Firebase (el código sigue en com.example.myapplication).
        applicationId = "do.micarrera.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!firma.isEmpty) create("publicacion") {
            storeFile = file(firma.getProperty("storeFile"))
            storePassword = firma.getProperty("storePassword")
            keyAlias = firma.getProperty("keyAlias")
            keyPassword = firma.getProperty("keyPassword")
        }
    }

    // Dos versiones de la misma app:
    //  - "web": la que se baja de micarrera-beta.vercel.app y se actualiza sola (Actualizador.kt). La publica publicar.py.
    //  - "play": para Google Play Store. Sin actualizador propio ni permiso para instalar APKs (Google no lo permite):
    //    ahí las actualizaciones llegan por la tienda. Se genera con: gradlew :app:bundlePlayRelease (archivo .aab).
    flavorDimensions += "tienda"
    productFlavors {
        create("web") {
            dimension = "tienda"
            buildConfigField("boolean", "TIENDA_PLAY", "false")
        }
        create("play") {
            dimension = "tienda"
            buildConfigField("boolean", "TIENDA_PLAY", "true")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("publicacion")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.6.0") // OkHttp: al salir de una pantalla cancela sin cerrar la app
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}