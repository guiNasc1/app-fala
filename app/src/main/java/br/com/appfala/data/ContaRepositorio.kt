package br.com.appfala.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Conta (Supabase Auth) e dados da oficina (tabela public.oficinas).
 *
 * É o "Repository/DAO" do MVVM: a tela nunca fala com o Supabase direto, só com o ViewModel,
 * que chama esta classe. Os métodos são `suspend`: podem esperar a rede sem travar a tela
 * (pense numa chamada assíncrona que o Kotlin deixa escrever como se fosse síncrona).
 */
class ContaRepositorio {
    // `get() =` busca o cliente a cada uso em vez de guardá-lo na construção: assim criar o
    // repositório (na thread da tela) não dispara a criação do cliente Supabase, que é pesada.
    private val auth get() = Supabase.cliente.auth

    /** Cria o cliente Supabase (carrega Ktor, OkHttp, serialização...). Chamar fora da thread principal. */
    fun preparar() {
        Supabase.cliente
    }

    /** Estado do login, observável: Initializing → Authenticated / NotAuthenticated. */
    val statusSessao: StateFlow<SessionStatus> get() = auth.sessionStatus

    val emailLogado: String? get() = auth.currentUserOrNull()?.email

    /** Espera a biblioteca ler a sessão que ela mesma guardou no aparelho; true se havia uma. */
    suspend fun temSessaoSalva(): Boolean {
        auth.awaitInitialization()
        return auth.currentSessionOrNull() != null
    }

    suspend fun entrar(email: String, senha: String) {
        auth.signInWith(Email) {
            this.email = email
            password = senha
        }
    }

    /**
     * Cria a conta mandando os dados da oficina como metadados do usuário; o gatilho do banco
     * (supabase/001_oficinas.sql) copia esses dados para a tabela oficinas.
     * Devolve true se já entrou, ou false se o Supabase exige confirmar o e-mail antes.
     */
    suspend fun cadastrar(email: String, senha: String, oficina: Oficina): Boolean {
        auth.signUpWith(Email) {
            this.email = email
            password = senha
            data = Json.encodeToJsonElement(oficina.copy(userId = null)).jsonObject
        }
        return auth.currentSessionOrNull() != null
    }

    /**
     * Dados da oficina que foram enviados no cadastro e ficaram guardados nos metadados do usuário.
     * Servem para pré-preencher o "completar cadastro" de uma conta sem linha em oficinas
     * (ex.: conta criada antes de o SQL do gatilho ser rodado).
     */
    fun oficinaDoCadastro(): Oficina? {
        val metadados = auth.currentUserOrNull()?.userMetadata ?: return null
        return runCatching { jsonTolerante.decodeFromJsonElement(Oficina.serializer(), metadados) }
            .getOrNull()
            ?.takeIf { it.cnpj.isNotBlank() }
    }

    /**
     * Pergunta ao servidor se o usuário da sessão guardada ainda existe. Uma conta apagada no painel
     * do Supabase continua com um token válido no celular por até 1 hora, e o app tentaria gravar
     * dados de um usuário que não existe mais. Sem internet a exceção sobe para quem chamou.
     */
    suspend fun usuarioExisteNoServidor(): Boolean = try {
        auth.retrieveUserForCurrentSession(updateSession = true)
        true
    } catch (e: AuthRestException) {
        e.errorCode != AuthErrorCode.UserNotFound && e.errorCode != AuthErrorCode.SessionNotFound
    }

    /** Oficina do usuário logado. O RLS garante que o select só enxerga a linha dele. */
    suspend fun buscarOficina(): Oficina? =
        Supabase.cliente.from("oficinas").select().decodeSingleOrNull<Oficina>()

    /** Cria ou atualiza (upsert = insert ... on conflict do update) a oficina do usuário logado. */
    suspend fun salvarOficina(oficina: Oficina): Oficina {
        val id = auth.currentUserOrNull()?.id ?: error("Sem usuário logado")
        return Supabase.cliente.from("oficinas")
            .upsert(oficina.copy(userId = id)) { select() }
            .decodeSingle<Oficina>()
    }

    /**
     * Encerra a sessão no servidor e no aparelho. Sem internet o servidor não responde, mas a
     * sessão guardada no celular é apagada do mesmo jeito: quem pediu para sair não fica preso.
     */
    suspend fun sair() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            auth.clearSession()
        }
    }

    private companion object {
        // O Supabase acrescenta chaves próprias aos metadados (sub, email_verified...): ignoramos.
        val jsonTolerante = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Traduz as exceções do Supabase numa frase para mostrar na tela, como um ExceptionHandler
 * que vira FacesMessage. `when` com `is` é um instanceof encadeado que já faz o cast.
 */
fun mensagemDeErro(e: Throwable): String = when (e) {
    is AuthWeakPasswordException -> "Senha fraca. Use pelo menos 6 caracteres."
    is AuthRestException -> when (e.errorCode) {
        AuthErrorCode.InvalidCredentials -> "E-mail ou senha incorretos."
        AuthErrorCode.EmailNotConfirmed -> "Você ainda não confirmou o e-mail. Abra o link que enviamos e tente de novo."
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "Já existe uma conta com esse e-mail."
        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> "Confira o e-mail digitado."
        AuthErrorCode.WeakPassword -> "Senha fraca. Use pelo menos 6 caracteres."
        AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
            "Muitas tentativas seguidas. Espere alguns minutos e tente de novo."
        AuthErrorCode.SignupDisabled -> "O cadastro de novas contas está desligado."
        else -> e.errorDescription
    }
    is HttpRequestException, is HttpRequestTimeoutException -> "Sem conexão com a internet. Confira e tente de novo."
    is RestException -> "O servidor recusou a operação: ${e.error}"
    else -> e.message ?: "Algo deu errado. Tente de novo."
}
