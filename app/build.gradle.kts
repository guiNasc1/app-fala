import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * URL e chave publishable do Supabase vêm do local.properties (fora do git) e viram constantes
 * em BuildConfig, uma classe Java gerada no build. É o mesmo papel de um arquivo .properties
 * lido pelo Spring, só que resolvido em tempo de compilação.
 */
val localProperties = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

fun configLocal(chave: String): String =
    localProperties.getProperty(chave)?.takeIf { it.isNotBlank() }
        ?: error("Falta '$chave' no local.properties (veja o README). Use a URL do projeto e a chave publishable.")

android {
    namespace = "br.com.appfala"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "br.com.appfala"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "SUPABASE_URL", "\"${configLocal("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${configLocal("supabase.publishableKey")}\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
}
