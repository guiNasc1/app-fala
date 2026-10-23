package br.com.appfala.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Orçamento (valores antes do serviço) ou nota de serviço (depois do serviço feito). */
enum class TipoDocumento(val titulo: String, val descricao: String) {
    ORCAMENTO("Orçamento", "Valores antes do serviço"),
    NOTA("Nota de serviço", "Valores depois do serviço feito"),
}

enum class TipoItem { PECA, SERVICO }

@Serializable
data class Veiculo(
    val modelo: String = "",
    val ano: String = "",
    val placa: String = "",
    val cor: String = "",
    val quilometragem: String = "",
) {
    val vazio: Boolean get() = listOf(modelo, ano, placa, cor, quilometragem).all { it.isBlank() }

    /** Junta com o que veio numa fala nova: o que foi dito agora substitui; o que não foi, fica. */
    fun mesclar(novo: Veiculo) = Veiculo(
        modelo = novo.modelo.ifBlank { modelo },
        ano = novo.ano.ifBlank { ano },
        placa = novo.placa.ifBlank { placa },
        cor = novo.cor.ifBlank { cor },
        quilometragem = novo.quilometragem.ifBlank { quilometragem },
    )
}

@Serializable
data class Cliente(val nome: String = "", val telefone: String = "") {
    val vazio: Boolean get() = nome.isBlank() && telefone.isBlank()

    fun mesclar(novo: Cliente) = Cliente(nome = novo.nome.ifBlank { nome }, telefone = novo.telefone.ifBlank { telefone })
}

/**
 * Um item do documento. Os valores ficam em centavos (Long), nunca em Double: dinheiro em ponto
 * flutuante acumula erro de arredondamento (o mesmo motivo de usar BigDecimal no Java).
 * O total é guardado à parte porque o valor dito pode ser o total ("as duas por duzentos").
 */
@Serializable
data class ItemDocumento(
    val id: String = UUID.randomUUID().toString(),
    val descricao: String,
    val tipo: TipoItem = TipoItem.PECA,
    val quantidade: Double = 1.0,
    val valorUnitarioCentavos: Long,
    val valorTotalCentavos: Long,
    /** O que foi dito, para conferência. */
    val fala: String = "",
)
