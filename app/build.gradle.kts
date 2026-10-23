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

/*
 * Assinatura da versão release (a que vai para o celular do usuário). Caminho e senhas da chave
 * ficam em keystore.properties, na raiz, FORA do git (como o local.properties). Passo a passo em
 * docs/INSTALAR.md. Sem esse arquivo o build release para com uma mensagem, para nunca sair um APK
 * assinado com a chave errada. Só para testar no emulador: ./gradlew assembleRelease -PreleaseComChaveDeDebug
 */
val assinatura = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}
val temChaveDeRelease = !assinatura.getProperty("storeFile").isNullOrBlank()
val releaseComChaveDeDebug = providers.gradleProperty("releaseComChaveDeDebug").isPresent

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
        // A cada versão nova instalada no celular: versionCode + 1 (o Android só atualiza para um número maior).
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${configLocal("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${configLocal("supabase.publishableKey")}\"")
    }

    signingConfigs {
        if (temChaveDeRelease) {
            create("release") {
                storeFile = file(assinatura.getProperty("storeFile"))
                storePassword = assinatura.getProperty("storePassword")
                keyAlias = assinatura.getProperty("keyAlias")
                keyPassword = assinatura.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: tira o código que não é usado e encurta nomes. APK menor e app abrindo mais rápido.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                temChaveDeRelease -> signingConfigs.getByName("release")
                releaseComChaveDeDebug -> signingConfigs.getByName("debug")
                else -> null
            }
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

    // Testes que rodam no PC (JVM), sem emulador: ./gradlew testDebugUnitTest
    testImplementation(libs.junit)
}

// Release sem chave configurada: para aqui, com a explicação, em vez de gerar um APK que não instala.
val semAssinatura = !temChaveDeRelease && !releaseComChaveDeDebug
tasks.configureEach {
    if (name == "packageRelease") {
        doFirst {
            if (semAssinatura) {
                throw GradleException(
                    "Falta a chave de assinatura: crie o keystore.properties na raiz do projeto " +
                        "(passo a passo em docs/INSTALAR.md)."
                )
            }
        }
    }
}
