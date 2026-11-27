import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Lógica pura (sin dependencias de Android): protocolo, intérprete de gestos, aceleración.
// Se testea en la JVM con `./gradlew :core:test`.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

sourceSets {
    // `shared/themes.json` (raíz del repositorio) es la fuente compartida de los temas: el test la lee como recurso y la compara
    // con `Themes` (docs/spec-v4.8-temas.md §4). Solo se añade a los recursos de test: ni `core` ni la app la empaquetan.
    test {
        resources.srcDir(rootProject.file("../shared"))
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}
