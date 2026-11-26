plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

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
        // Clave de desarrollo pública y versionada: así todas las releases tienen la misma firma
        // y se pueden instalar una encima de otra (o bajar de versión desinstalando).
        create("dev") {
            storeFile = file("mymouse-dev.keystore")
            storePassword = "mymouse"
            keyAlias = "mymouse"
            keyPassword = "mymouse"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dev")
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
