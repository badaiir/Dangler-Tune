plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Музыкальные решения: строй, струны, режимы. Сигнал приходит из :core:dsp,
// Android и Compose сюда не заглядывают.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:dsp"))
    testImplementation(libs.junit)
}
