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

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}
