package br.com.appfala.data

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** O que o app manda para a Edge Function: só o texto da fala e um pouco de contexto. */
@Serializable
private data class PedidoIa(val texto: String, val tipo: String, val veiculo: Veiculo?)

/** O que a Edge Function devolve, com os valores já em centavos. */
@Serializable
data class ResultadoIa(
    val veiculo: Veiculo = Veiculo(),
    val cliente: Cliente = Cliente(),
    val itens: List<ItemIa> = emptyList(),
    val observacao: String = "",
)

@Serializable
data class ItemIa(
    val descricao: String,
    val tipo: TipoItem = TipoItem.PECA,
    val quantidade: Double = 1.0,
    val valorUnitarioCentavos: Long,
    val valorTotalCentavos: Long,
)

/**
 * Chama a Edge Function "extrair-item" (supabase/functions/extrair-item). A supabase-kt manda
 * junto o token do usuário logado; a chave da IA fica só no Supabase, nunca no app.
 */
class IaRepositorio {
    suspend fun extrair(texto: String, tipo: TipoDocumento, veiculo: Veiculo?): ResultadoIa {
        val resposta = Supabase.cliente.functions.invoke(
            function = "extrair-item",
            body = PedidoIa(texto, tipo.name, veiculo),
        )
        return json.decodeFromString(ResultadoIa.serializer(), resposta.bodyAsText())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Mensagem para a tela quando a chamada à IA falha. A função devolve {"erro": "..."} já em
 * português; erros do próprio Supabase (função não publicada, token inválido) vêm por status.
 */
fun mensagemDeErroIa(e: Throwable): String {
    if (e !is RestException) return mensagemDeErro(e)
    val daFuncao = listOf(e.error, e.description, e.message).firstNotNullOfOrNull { texto ->
        texto?.let { runCatching { Json.parseToJsonElement(it).jsonObject["erro"]?.jsonPrimitive?.content }.getOrNull() }
    }
    return daFuncao ?: when (e.statusCode) {
        401 -> "Sua sessão expirou. Saia e entre de novo."
        404 -> "A função de IA ainda não foi publicada no Supabase."
        429 -> "A IA chegou ao limite de uso. Espere um pouco e tente de novo."
        else -> "A IA não respondeu (erro ${e.statusCode}). Tente de novo."
    }
}
