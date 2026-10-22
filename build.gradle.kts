plugins {
    alias(libs.plugins.android.application) apply false
    // O AGP 9 já traz Kotlin embutido (2.2.10). A supabase-kt 3.8 exige Kotlin 2.4, então
    // declaramos o plugin Kotlin mais novo aqui (sem aplicar) só para o AGP usar essa versão.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
