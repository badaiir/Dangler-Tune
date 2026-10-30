plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Чистый Kotlin/JVM: ни Android, ни Compose, ни музыкальной модели.
// Всё, что здесь есть, обязано тестироваться обычным JUnit без эмулятора.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
