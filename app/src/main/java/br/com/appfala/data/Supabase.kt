package br.com.appfala.data

import br.com.appfala.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * Cliente único do Supabase para o app inteiro.
 *
 * `object` em Kotlin é um singleton pronto: uma classe com uma única instância, criada na primeira
 * vez que alguém a usa. Equivale a um bean @ApplicationScoped no JSF (ou @Component no Spring).
 *
 * Só recebe a URL do projeto e a chave publishable: quem protege os dados é o RLS no banco,
 * nunca uma chave secreta dentro do APK.
 */
object Supabase {
    val cliente = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY,
    ) {
        // Como o ObjectMapper do Jackson: ignora colunas que o app não conhece (criado_em...)
        // e manda todos os campos, inclusive os vazios, para um update conseguir limpar um campo.
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
        install(Auth)       // login/cadastro por e-mail e senha; guarda a sessão no aparelho sozinho
        install(Postgrest)  // consultas às tabelas (select/insert/update/delete via API REST)
        install(Functions)  // chamadas às Edge Functions (a IA da emissão por voz)
    }
}
