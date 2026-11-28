import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Datos privados de la firma de subida a Play: en local.properties (fuera de git) o en variables de entorno, nunca en el repositorio.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(property: String, env: String): String? = localProps.getProperty(property) ?: System.getenv(env)

android {
    namespace = "dev.mymouse"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mymouse"
        minSdk = 26
        targetSdk = 35
        // CI los fija a partir de la etiqueta de la release; en local se usan estos.
        versionCode = (System.getenv("MYMOUSE_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("MYMOUSE_VERSION_NAME") ?: "0.0-dev"
    }

    signingConfigs {
        // Clave de desarrollo pública y versionada: así todas las releases de GitHub tienen la misma firma
        // y se pueden instalar una encima de otra (o bajar de versión desinstalando).
        create("dev") {
            storeFile = file("mymouse-dev.keystore")
            storePassword = "mymouse"
            keyAlias = "mymouse"
            keyPassword = "mymouse"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // GitHub: se actualiza sola desde las releases, con modo quiosco. Firmada con la clave de desarrollo del repositorio.
        create("github") {
            dimension = "distribution"
            signingConfig = signingConfigs.getByName("dev")
        }
        // Play: sin autoactualizador, sin REQUEST_INSTALL_PACKAGES y sin administración del dispositivo.
        create("play") {
            dimension = "distribution"
            val storeFile = secret("upload.storeFile", "MYMOUSE_UPLOAD_STORE_FILE")
            if (storeFile != null) {
                signingConfig = signingConfigs.create("upload") {
                    this.storeFile = file(storeFile)
                    storePassword = secret("upload.storePassword", "MYMOUSE_UPLOAD_STORE_PASSWORD")
                    keyAlias = secret("upload.keyAlias", "MYMOUSE_UPLOAD_KEY_ALIAS")
                    keyPassword = secret("upload.keyPassword", "MYMOUSE_UPLOAD_KEY_PASSWORD")
                }
            }
            // Sin clave de subida configurada, el release de Play queda sin firmar y el debug usa la clave de depuración de Android.
        }
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

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Sin abortar la build por avisos de lint: lo importante es que compile.
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
}
