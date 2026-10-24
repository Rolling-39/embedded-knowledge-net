// AGP 9：Kotlin 由 AGP 内建（不应用 org.jetbrains.kotlin.android）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
