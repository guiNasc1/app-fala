package br.com.appfala.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.appfala.data.Cliente
import br.com.appfala.data.DocumentoCompleto
import br.com.appfala.data.DocumentosRepositorio
import br.com.appfala.data.IaRepositorio
import br.com.appfala.data.ItemDocumento
import br.com.appfala.data.Oficina
import br.com.appfala.data.Preferencias
import br.com.appfala.data.ResultadoIa
import br.com.appfala.data.TipoDocumento
import br.com.appfala.data.Veiculo
import br.com.appfala.data.mensagemDeErro
import br.com.appfala.data.mensagemDeErroIa
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Recado na faixa de baixo da tela, com uma ação opcional (tentar de novo / desfazer). */
data class Aviso(val texto: String, val acao: AcaoAviso? = null)

/**
 * `sealed interface` = conjunto fechado de tipos, como um enum em que cada opção carrega dados
 * próprios. O `when` sobre ele obriga a tratar todos os casos.
 */
sealed interface AcaoAviso {
    data class Repetir(val fala: String) : AcaoAviso
    data class Desfazer(val item: ItemDocumento, val posicao: Int) : AcaoAviso
}

/** O que é guardado no aparelho enquanto o documento é ditado. */
@Serializable
private data class Rascunho(
    val tipo: TipoDocumento?,
    val veiculo: Veiculo,
    val cliente: Cliente,
    val itens: List<ItemDocumento>,
)

/**
 * Estado do documento sendo ditado (o "rascunho"). Fica separado do AppViewModel porque é outra
 * responsabilidade: como ter um bean só para a tela de emissão no JSF.
 *
 * O rascunho também é salvo no aparelho a cada mudança: se o Android fechar o app no meio do
 * ditado (uma ligação, outro app pesado), ao voltar está tudo lá.
 */
class EmissaoViewModel(app: Application) : AndroidViewModel(app) {
    private val ia = IaRepositorio()
    private val documentos = DocumentosRepositorio()
    private val preferencias = Preferencias(app)

    // Garante que as falas sejam processadas uma de cada vez, na ordem em que foram ditas
    // (como um `synchronized`, mas que espera sem travar a thread).
    private val fila = Mutex()

    var tipo by mutableStateOf<TipoDocumento?>(null)
        private set
    var veiculo by mutableStateOf(Veiculo())
        private set
    var cliente by mutableStateOf(Cliente())
        private set

    /** mutableStateListOf: uma lista que o Compose observa (adicionar/remover redesenha a tela). */
    val itens = mutableStateListOf<ItemDocumento>()

    /** Falas enviadas para a IA que ainda não voltaram (aparecem como "entendendo..."). */
    val pendentes = mutableStateListOf<String>()

    var ouvindo by mutableStateOf(false)
    var textoAoVivo by mutableStateOf("")
    var volume by mutableStateOf(0f)
    var aviso by mutableStateOf<Aviso?>(null)
    var emitindo by mutableStateOf(false)
        private set

    val total: Long get() = itens.sumOf { it.valorTotalCentavos }

    /** Dá para gerar o documento: tem item, nada esperando a IA e o microfone parado. */
    val podeEmitir: Boolean get() = itens.isNotEmpty() && pendentes.isEmpty() && !ouvindo && !emitindo

    init {
        preferencias.rascunho?.let { salvo ->
            runCatching { Json.decodeFromString(Rascunho.serializer(), salvo) }.getOrNull()?.let { r ->
                tipo = r.tipo
                veiculo = r.veiculo
                cliente = r.cliente
                itens.addAll(r.itens)
            }
        }
    }

    fun escolher(t: TipoDocumento) {
        tipo = t
        salvarRascunho()
    }

    /** Descarta o rascunho e volta para a escolha entre orçamento e nota. */
    fun recomecar() {
        tipo = null
        veiculo = Veiculo()
        cliente = Cliente()
        itens.clear()
        pendentes.clear()
        aviso = null
        ouvindo = false
        textoAoVivo = ""
        preferencias.rascunho = null
    }

    private fun salvarRascunho() {
        preferencias.rascunho = Json.encodeToString(Rascunho.serializer(), Rascunho(tipo, veiculo, cliente, itens.toList()))
    }

    // ---------------------------------------------------------------- microfone

    fun comecouAOuvir() {
        ouvindo = true
        textoAoVivo = ""
        aviso = null
    }

    fun parouDeOuvir(mensagem: String?) {
        ouvindo = false
        volume = 0f
        textoAoVivo = ""
        if (!mensagem.isNullOrBlank()) aviso = Aviso(mensagem)
    }

    // ---------------------------------------------------------------- falas

    /** Manda uma fala (ditada ou digitada) para a IA e junta o resultado ao documento. */
    fun processar(fala: String) {
        val texto = fala.trim()
        if (texto.isEmpty()) return
        aviso = null
        pendentes.add(texto)
        viewModelScope.launch {
            fila.withLock {
                try {
                    val resultado = ia.extrair(texto, tipo ?: TipoDocumento.ORCAMENTO, veiculo.takeUnless { it.vazio })
                    aplicar(resultado, texto)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aviso = Aviso(mensagemDeErroIa(e), AcaoAviso.Repetir(texto))
                } finally {
                    pendentes.remove(texto)
                }
            }
        }
    }

    private fun aplicar(r: ResultadoIa, fala: String) {
        veiculo = veiculo.mesclar(r.veiculo)
        cliente = cliente.mesclar(r.cliente)
        r.itens.forEach { i ->
            itens.add(
                ItemDocumento(
                    descricao = i.descricao,
                    tipo = i.tipo,
                    quantidade = i.quantidade,
                    valorUnitarioCentavos = i.valorUnitarioCentavos,
                    valorTotalCentavos = i.valorTotalCentavos,
                    fala = fala,
                )
            )
        }
        aviso = when {
            r.observacao.isNotBlank() -> Aviso(r.observacao)
            r.itens.isEmpty() && r.veiculo.vazio && r.cliente.vazio ->
                Aviso("Não achei peça, serviço nem dados da moto nessa fala.", AcaoAviso.Repetir(fala))
            else -> null
        }
        salvarRascunho()
    }

    // ---------------------------------------------------------------- emissão

    /**
     * Salva o documento no Supabase e, se deu certo, entrega para `aoEmitir` (que gera e mostra o
     * PDF) e limpa o rascunho. Roda no escopo do ViewModel: termina mesmo se a tela mudar no meio.
     */
    fun emitir(oficina: Oficina?, aoEmitir: suspend (DocumentoCompleto) -> Unit) {
        val t = tipo ?: return
        if (!podeEmitir) return
        if (oficina == null) {
            aviso = Aviso("Os dados da oficina não carregaram. Confira a internet e tente de novo.")
            return
        }
        emitindo = true
        aviso = null
        viewModelScope.launch {
            try {
                val completo = documentos.emitir(t, veiculo, cliente, oficina, itens.toList())
                aoEmitir(completo)
                recomecar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aviso = Aviso("Não foi possível salvar: ${mensagemDeErro(e)}")
            } finally {
                emitindo = false
            }
        }
    }

    fun remover(item: ItemDocumento) {
        val posicao = itens.indexOf(item)
        if (posicao < 0) return
        itens.removeAt(posicao)
        aviso = Aviso("Item apagado.", AcaoAviso.Desfazer(item, posicao))
        salvarRascunho()
    }

    fun executar(acao: AcaoAviso) {
        aviso = null
        when (acao) {
            is AcaoAviso.Repetir -> processar(acao.fala)
            is AcaoAviso.Desfazer -> {
                itens.add(acao.posicao.coerceAtMost(itens.size), acao.item)
                salvarRascunho()
            }
        }
    }
}
