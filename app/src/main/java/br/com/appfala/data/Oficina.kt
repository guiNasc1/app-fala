package br.com.appfala.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Uma linha da tabela public.oficinas.
 *
 * `data class` é o POJO do Kotlin: equals, hashCode, toString e copy() já vêm prontos.
 * `@Serializable` gera em tempo de compilação o conversor de/para JSON (o papel do Jackson),
 * e `@SerialName` faz o mesmo que `@JsonProperty`: liga o nome Kotlin à coluna snake_case.
 */
@Serializable
data class Oficina(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("nome_usuario") val nomeUsuario: String = "",
    val cnpj: String = "",
    @SerialName("razao_social") val razaoSocial: String = "",
    @SerialName("nome_fantasia") val nomeFantasia: String = "",
    val telefone: String = "",
    val email: String = "",
    val endereco: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val cep: String = "",
) {
    /** Nome que aparece no cabeçalho e no PDF: o fantasia, se tiver; senão a razão social. */
    val nomeExibicao: String get() = nomeFantasia.ifBlank { razaoSocial }
}
