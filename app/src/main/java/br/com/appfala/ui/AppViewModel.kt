package br.com.appfala.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.appfala.data.ConsultaCnpj
import br.com.appfala.data.ContaRepositorio
import br.com.appfala.data.Oficina
import br.com.appfala.data.Preferencias
import br.com.appfala.data.mensagemDeErro
import br.com.appfala.util.Cnpj
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Telas "de página inteira". As abas só existem dentro de PRINCIPAL. */
enum class Tela { CARREGANDO, LOGIN, CADASTRO, PRINCIPAL, DADOS_OFICINA }

/** As quatro abas da barra inferior. `enum class` é igual ao enum do Java. */
enum class Aba(val titulo: String) {
    INICIO("Início"),
    EMITIR("Emitir"),
    HISTORICO("Histórico"),
    PERFIL("Perfil"),
}

/**
 * Estado da tela + ações, no papel do managed bean do JSF (aqui um @SessionScoped do app inteiro).
 *
 * O ViewModel sobrevive a girar a tela (o Android recria a Activity, mas não o ViewModel).
 * Cada `by mutableStateOf(...)` é um campo "observável": quando muda, o Compose redesenha só
 * as partes da tela que leem esse campo, como um update="@form" automático e preciso.
 *
 * Os métodos que vão à rede são `suspend` e devolvem a mensagem de erro, ou null se deu certo
 * (como uma action que devolve a FacesMessage).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val preferencias = Preferencias(app)
    private val conta = ContaRepositorio()

    var tela by mutableStateOf(Tela.CARREGANDO)
        private set
    var aba by mutableStateOf(Aba.INICIO)
        private set
    var modoEscuro by mutableStateOf(preferencias.modoEscuro)
        private set

    var oficina by mutableStateOf<Oficina?>(null)
        private set
    val emailLogado: String? get() = conta.emailLogado

    /** Conta sem oficina salva: o que foi digitado no cadastro, para pré-preencher "Dados da oficina". */
    var oficinaDoCadastro: Oficina? = null
        private set

    /** Recado para a tela de login (conta criada, sessão encerrada...) ou erro ao carregar a oficina. */
    var aviso by mutableStateOf<String?>(null)
    var lembrarDeMim by mutableStateOf(preferencias.lembrarDeMim)
    var emailInicial by mutableStateOf(preferencias.ultimoEmail)
        private set

    init {
        // viewModelScope.launch inicia uma coroutine: um "fio" leve que roda em paralelo e é
        // cancelado sozinho quando o ViewModel morre. É onde se pode chamar funções suspend.
        viewModelScope.launch {
            // Criar o cliente Supabase leva um tempo: faz isso numa thread de fundo (Dispatchers.Default)
            // para a tela não congelar. É como mandar um trabalho para um ExecutorService e esperar o Future.
            withContext(Dispatchers.Default) { conta.preparar() }

            // "Lembrar de mim" desligado: a sessão que a biblioteca guardou não vale, sai dela.
            if (conta.temSessaoSalva() && !preferencias.lembrarDeMim) conta.sair()

            // Fica ouvindo o estado do login pelo resto da vida do app (como um listener).
            // Quem decide entre login e Início é sempre este bloco: entrar, cadastrar e sair
            // só mudam a sessão, e a navegação acontece aqui.
            conta.statusSessao.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated ->
                        if (tela == Tela.CARREGANDO || tela == Tela.LOGIN || tela == Tela.CADASTRO) abrirSessao()
                    is SessionStatus.NotAuthenticated -> {
                        oficina = null
                        oficinaDoCadastro = null
                        aba = Aba.INICIO
                        if (tela != Tela.CADASTRO) tela = Tela.LOGIN
                    }
                    // Initializing (lendo o aparelho) e RefreshFailure (sem rede ao renovar o token):
                    // não muda de tela; a biblioteca tenta renovar de novo sozinha.
                    else -> Unit
                }
            }
        }
    }

    private suspend fun abrirSessao() {
        emailLogado?.let {
            preferencias.ultimoEmail = it
            emailInicial = it
        }
        try {
            val dados = conta.buscarOficina()
            // Sem oficina pode ser conta apagada no painel (cascade apaga a oficina junto).
            if (dados == null && !conta.usuarioExisteNoServidor()) {
                encerrarContaApagada()
                return
            }
            oficina = dados
            // Conta sem oficina (ex.: criada antes do gatilho existir): pede para completar,
            // já com o que a pessoa digitou no cadastro.
            oficinaDoCadastro = if (dados == null) conta.oficinaDoCadastro() else null
            tela = if (dados == null) Tela.DADOS_OFICINA else Tela.PRINCIPAL
            aviso = if (dados == null) "Complete os dados da sua oficina para continuar." else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tela = Tela.PRINCIPAL
            aviso = mensagemDeErro(e)
        }
    }

    fun recarregarOficina() {
        viewModelScope.launch { abrirSessao() }
    }

    // ---------------------------------------------------------------- navegação e tema

    fun irPara(destino: Aba) {
        aba = destino
    }

    fun irParaCadastro() {
        aviso = null
        tela = Tela.CADASTRO
    }

    fun voltarParaLogin() {
        tela = Tela.LOGIN
    }

    fun editarOficina() {
        aviso = null
        tela = Tela.DADOS_OFICINA
    }

    fun fecharDadosOficina() {
        if (oficina != null) tela = Tela.PRINCIPAL
    }

    fun alternarModoEscuro() {
        modoEscuro = !modoEscuro
        preferencias.modoEscuro = modoEscuro
    }

    // ---------------------------------------------------------------- conta

    suspend fun entrar(email: String, senha: String): String? {
        val e = email.trim()
        if (e.isEmpty() || senha.isEmpty()) return "Preencha o e-mail e a senha."
        preferencias.lembrarDeMim = lembrarDeMim
        return tentar { conta.entrar(e, senha) }
    }

    suspend fun cadastrar(email: String, senha: String, confirmacao: String, dados: Oficina): String? {
        val e = email.trim()
        validar(dados)?.let { return it }
        when {
            !e.contains('@') -> return "Confira o e-mail digitado."
            senha.length < 6 -> return "A senha precisa ter pelo menos 6 caracteres."
            senha != confirmacao -> return "As senhas não conferem."
        }
        preferencias.lembrarDeMim = lembrarDeMim
        return tentar {
            val entrou = conta.cadastrar(e, senha, normalizar(dados))
            if (!entrou) {
                // Confirmação de e-mail ligada no Supabase: só entra depois de clicar no link.
                emailInicial = e
                aviso = "Conta criada! Enviamos um link para $e. Abra o e-mail, toque no link e depois entre aqui."
                tela = Tela.LOGIN
            }
        }
    }

    suspend fun salvarOficina(dados: Oficina): String? {
        validar(dados)?.let { return it }
        val erro = tentar {
            oficina = conta.salvarOficina(normalizar(dados))
            aviso = null
            tela = Tela.PRINCIPAL
        }
        // Falhou porque a conta foi apagada enquanto o app estava aberto? Então sai dela.
        if (erro != null && runCatching { !conta.usuarioExisteNoServidor() }.getOrDefault(false)) {
            encerrarContaApagada()
            return null
        }
        return erro
    }

    /** A sessão guardada é de um usuário que não existe mais: volta ao login explicando o porquê. */
    private suspend fun encerrarContaApagada() {
        aviso = "Esta conta não existe mais. Entre com outra conta ou crie uma nova."
        conta.sair()
    }

    suspend fun consultarCnpj(cnpj: String): Oficina? = withContext(Dispatchers.IO) {
        runCatching { ConsultaCnpj.buscar(Cnpj.limpar(cnpj)) }.getOrNull()
    }

    /** Sai e volta para o login com o último e-mail preenchido (para entrar de novo rápido). */
    fun sair() {
        viewModelScope.launch { conta.sair() }
    }

    /** Sai e esquece o e-mail: o login abre em branco para outra pessoa entrar. */
    fun trocarDeConta() {
        preferencias.ultimoEmail = ""
        emailInicial = ""
        sair()
    }

    private fun validar(d: Oficina): String? = when {
        !Cnpj.valido(d.cnpj) -> "CNPJ inválido. Confira os dígitos."
        d.razaoSocial.isBlank() && d.nomeFantasia.isBlank() -> "Informe o nome da oficina."
        d.nomeUsuario.isBlank() -> "Informe o seu nome."
        else -> null
    }

    private fun normalizar(d: Oficina) = d.copy(
        cnpj = Cnpj.limpar(d.cnpj),
        nomeUsuario = d.nomeUsuario.trim(),
        razaoSocial = d.razaoSocial.trim(),
        nomeFantasia = d.nomeFantasia.trim(),
        email = d.email.trim().lowercase(),
        endereco = d.endereco.trim(),
        bairro = d.bairro.trim(),
        cidade = d.cidade.trim(),
        uf = d.uf.trim().uppercase(),
    )

    /** Roda uma chamada ao Supabase e devolve a mensagem de erro, ou null se deu certo. */
    private suspend fun tentar(bloco: suspend () -> Unit): String? = try {
        bloco()
        null
    } catch (e: CancellationException) {
        // Cancelamento não é erro: é a coroutine sendo encerrada (tela fechada). Deixa seguir.
        throw e
    } catch (e: Exception) {
        mensagemDeErro(e)
    }
}
