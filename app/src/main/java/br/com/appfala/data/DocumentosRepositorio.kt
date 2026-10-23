package br.com.appfala.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Uma linha de public.documentos: o documento já emitido. */
@Serializable
data class DocumentoSalvo(
    val id: String,
    val tipo: TipoDocumento,
    val numero: Int,
    val veiculo: Veiculo = Veiculo(),
    val cliente: Cliente = Cliente(),
    val oficina: Oficina = Oficina(),
    @SerialName("total_centavos") val totalCentavos: Long,
    @SerialName("criado_em") val criadoEm: String,
) {
    /** 7 -> "0007" */
    val numeroFormatado: String get() = numero.toString().padStart(4, '0')

    /** O banco guarda em UTC; aqui vira o horário do celular. */
    val emitidoEm: ZonedDateTime get() = OffsetDateTime.parse(criadoEm).atZoneSameInstant(ZoneId.systemDefault())

    val dataFormatada: String get() = emitidoEm.format(DATA)
    val dataHoraFormatada: String get() = emitidoEm.format(DATA_HORA)

    private companion object {
        val DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val DATA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
    }
}

/** Uma linha de public.itens_documento. Também é o formato mandado para a função emitir_documento. */
@Serializable
data class ItemSalvo(
    val ordem: Int = 0,
    val descricao: String,
    val tipo: TipoItem = TipoItem.PECA,
    val quantidade: Double,
    @SerialName("valor_unitario_centavos") val valorUnitarioCentavos: Long,
    @SerialName("valor_total_centavos") val valorTotalCentavos: Long,
    val fala: String = "",
)

/** Documento com os itens: tudo o que o PDF precisa. */
data class DocumentoCompleto(val documento: DocumentoSalvo, val itens: List<ItemSalvo>)

/** Só o necessário para os gráficos do Início: tipo, total e quando foi emitido. */
@Serializable
data class LinhaResumo(
    val tipo: TipoDocumento,
    @SerialName("total_centavos") val totalCentavos: Long,
    @SerialName("criado_em") val criadoEm: String,
) {
    /** Data/hora no fuso dado (padrão: o do celular). O parâmetro existe para os testes fixarem o fuso. */
    fun emitidoEm(zona: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        OffsetDateTime.parse(criadoEm).atZoneSameInstant(zona)
}

/** Parâmetros da função SQL emitir_documento (supabase/002_documentos.sql). */
@Serializable
private data class ParametrosEmissao(
    @SerialName("p_tipo") val tipo: TipoDocumento,
    @SerialName("p_veiculo") val veiculo: Veiculo,
    @SerialName("p_cliente") val cliente: Cliente,
    @SerialName("p_oficina") val oficina: Oficina,
    @SerialName("p_itens") val itens: List<ItemSalvo>,
)

/**
 * Documentos emitidos e seus itens. O RLS do banco garante que cada oficina só enxerga os seus,
 * por isso nenhuma consulta aqui precisa filtrar pelo usuário.
 */
class DocumentosRepositorio {

    /** Salva documento e itens numa transação só (função emitir_documento) e devolve o que foi gravado. */
    suspend fun emitir(
        tipo: TipoDocumento,
        veiculo: Veiculo,
        cliente: Cliente,
        oficina: Oficina,
        itens: List<ItemDocumento>,
    ): DocumentoCompleto {
        val paraSalvar = itens.mapIndexed { i, item ->
            ItemSalvo(
                ordem = i + 1,
                descricao = item.descricao,
                tipo = item.tipo,
                quantidade = item.quantidade,
                valorUnitarioCentavos = item.valorUnitarioCentavos,
                valorTotalCentavos = item.valorTotalCentavos,
                fala = item.fala,
            )
        }
        val documento = Supabase.cliente.postgrest
            .rpc("emitir_documento", ParametrosEmissao(tipo, veiculo, cliente, oficina.copy(userId = null), paraSalvar))
            .decodeAs<DocumentoSalvo>()
        return DocumentoCompleto(documento, paraSalvar)
    }

    /** Histórico: do mais novo para o mais antigo. */
    suspend fun listar(): List<DocumentoSalvo> =
        Supabase.cliente.from("documentos").select {
            order("criado_em", Order.DESCENDING)
            limit(300)
        }.decodeList<DocumentoSalvo>()

    /**
     * Documentos emitidos a partir de `inicio`, só com tipo, total e data. Vem em páginas de 1.000
     * linhas (o limite padrão da API do Supabase), para não cortar nada num mês movimentado.
     */
    suspend fun resumoDesde(inicio: Instant): List<LinhaResumo> {
        val todas = mutableListOf<LinhaResumo>()
        var de = 0L
        while (true) {
            val pagina = Supabase.cliente.from("documentos").select(Columns.list("tipo", "total_centavos", "criado_em")) {
                filter { gte("criado_em", inicio.toString()) }
                order("criado_em", Order.ASCENDING)
                range(de, de + TAMANHO_PAGINA - 1)
            }.decodeList<LinhaResumo>()
            todas += pagina
            if (pagina.size < TAMANHO_PAGINA) return todas
            de += TAMANHO_PAGINA
        }
    }

    suspend fun itens(documentoId: String): List<ItemSalvo> =
        Supabase.cliente.from("itens_documento").select {
            filter { eq("documento_id", documentoId) }
            order("ordem", Order.ASCENDING)
        }.decodeList<ItemSalvo>()

    private companion object {
        const val TAMANHO_PAGINA = 1000L
    }
}
