package br.com.appfala.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.appfala.data.DocumentoCompleto
import br.com.appfala.data.DocumentoSalvo
import br.com.appfala.data.DocumentosRepositorio
import br.com.appfala.data.LinhaResumo
import br.com.appfala.data.TipoDocumento
import br.com.appfala.data.mensagemDeErro
import br.com.appfala.pdf.GeradorPdf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.ZoneId

/** Documento aberto na tela do PDF. `recemEmitido` mostra o recado "Salvo!". */
data class DocumentoAberto(val completo: DocumentoCompleto, val arquivo: File, val recemEmitido: Boolean)

/** Soma de um mês, separada por tipo. `YearMonth` é o "mês/ano" do java.time (ex.: 2026-10). */
data class TotalDoMes(
    val mes: YearMonth,
    val orcamentosCentavos: Long = 0,
    val orcamentosQuantidade: Int = 0,
    val notasCentavos: Long = 0,
    val notasQuantidade: Int = 0,
)

/** Quantos meses o gráfico do Início mostra (o atual e os 5 anteriores). */
const val MESES_NO_GRAFICO = 6

/** Histórico de documentos e o documento aberto na tela do PDF. */
class DocumentosViewModel(app: Application) : AndroidViewModel(app) {
    private val repositorio = DocumentosRepositorio()

    var historico by mutableStateOf<List<DocumentoSalvo>>(emptyList())
        private set
    var carregando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    /** null = nenhum documento aberto (mostra a aba normal). */
    var aberto by mutableStateOf<DocumentoAberto?>(null)
        private set
    var abrindo by mutableStateOf(false)
        private set

    /** Totais dos últimos meses para o Início, do mais antigo para o atual. null = ainda não carregou. */
    var resumo by mutableStateOf<List<TotalDoMes>?>(null)
        private set
    var carregandoResumo by mutableStateOf(false)
        private set
    var erroResumo by mutableStateOf<String?>(null)
        private set

    fun carregarResumo() {
        if (carregandoResumo) return
        viewModelScope.launch {
            carregandoResumo = true
            erroResumo = null
            try {
                val zona = ZoneId.systemDefault()
                val atual = YearMonth.now(zona)
                val primeiro = atual.minusMonths((MESES_NO_GRAFICO - 1).toLong())
                val inicio = primeiro.atDay(1).atStartOfDay(zona).toInstant()
                val linhas = repositorio.resumoDesde(inicio)
                resumo = somarPorMes(linhas, primeiro, atual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erroResumo = mensagemDeErro(e)
            } finally {
                carregandoResumo = false
            }
        }
    }

    fun carregarHistorico() {
        viewModelScope.launch {
            carregando = true
            erro = null
            try {
                historico = repositorio.listar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = mensagemDeErro(e)
            } finally {
                carregando = false
            }
        }
    }

    /** Abre um documento do histórico: busca os itens e refaz o PDF. */
    fun abrir(documento: DocumentoSalvo) {
        if (abrindo) return
        viewModelScope.launch {
            abrindo = true
            erro = null
            try {
                val itens = repositorio.itens(documento.id)
                mostrar(DocumentoCompleto(documento, itens), recemEmitido = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = mensagemDeErro(e)
            } finally {
                abrindo = false
            }
        }
    }

    /** Logo depois de emitir: abre direto (os dados já estão na mão) e atualiza o histórico. */
    suspend fun mostrarEmitido(completo: DocumentoCompleto) {
        mostrar(completo, recemEmitido = true)
        historico = listOf(completo.documento) + historico.filterNot { it.id == completo.documento.id }
    }

    fun fechar() {
        aberto = null
    }

    /** Saiu da conta: nada do usuário anterior pode ficar na tela. */
    fun limpar() {
        aberto = null
        historico = emptyList()
        erro = null
        resumo = null
        erroResumo = null
    }

    private suspend fun mostrar(completo: DocumentoCompleto, recemEmitido: Boolean) {
        // Desenhar o PDF é trabalho pesado: Dispatchers.Default é o pool de threads de cálculo.
        val arquivo = withContext(Dispatchers.Default) {
            val pasta = File(getApplication<Application>().cacheDir, "pdfs")
            File(pasta, nomeDoArquivo(completo.documento)).also { GeradorPdf().gerar(completo, it) }
        }
        aberto = DocumentoAberto(completo, arquivo, recemEmitido)
    }
}

/**
 * Soma por mês e por tipo, no fuso do celular (um documento das 22h do dia 31, em UTC, já seria
 * do mês seguinte). Meses sem nada entram com zero, para o gráfico ter sempre as mesmas colunas.
 * `groupBy` e `sumOf` fazem o papel do GROUP BY / SUM do SQL, aqui na lista em memória.
 */
internal fun somarPorMes(
    linhas: List<LinhaResumo>,
    primeiro: YearMonth,
    ultimo: YearMonth,
    zona: ZoneId = ZoneId.systemDefault(),
): List<TotalDoMes> {
    val porMes = linhas.groupBy { YearMonth.from(it.emitidoEm(zona)) }
    return generateSequence(primeiro) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(ultimo) }
        .map { mes ->
            val doMes = porMes[mes].orEmpty()
            val orcamentos = doMes.filter { it.tipo == TipoDocumento.ORCAMENTO }
            val notas = doMes.filter { it.tipo == TipoDocumento.NOTA }
            TotalDoMes(
                mes = mes,
                orcamentosCentavos = orcamentos.sumOf { it.totalCentavos },
                orcamentosQuantidade = orcamentos.size,
                notasCentavos = notas.sumOf { it.totalCentavos },
                notasQuantidade = notas.size,
            )
        }
        .toList()
}

/** "Orcamento_0007.pdf" / "Nota_de_servico_0003.pdf" (sem acentos, para não dar problema em nenhum app). */
fun nomeDoArquivo(d: DocumentoSalvo): String =
    (if (d.tipo == TipoDocumento.ORCAMENTO) "Orcamento" else "Nota_de_servico") + "_${d.numeroFormatado}.pdf"
