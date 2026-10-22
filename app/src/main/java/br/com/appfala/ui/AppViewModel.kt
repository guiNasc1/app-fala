package br.com.appfala.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import br.com.appfala.data.Preferencias

/** As quatro abas da barra inferior. `enum class` é igual ao enum do Java. */
enum class Aba(val titulo: String) {
    INICIO("Início"),
    EMITIR("Emitir"),
    HISTORICO("Histórico"),
    PERFIL("Perfil"),
}

/**
 * Estado da tela + ações, no papel do managed bean @ViewScoped do JSF.
 *
 * O ViewModel sobrevive a girar a tela (o Android recria a Activity, mas não o ViewModel).
 * Cada `by mutableStateOf(...)` é um campo "observável": quando muda, o Compose redesenha só
 * as partes da tela que leem esse campo, como um update="@form" automático e preciso.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val preferencias = Preferencias(app)

    var modoEscuro by mutableStateOf(preferencias.modoEscuro)
        private set

    var aba by mutableStateOf(Aba.INICIO)
        private set

    // Até a fase de login existir, o cabeçalho mostra um nome provisório.
    val nomeOficina = "Minha oficina"

    fun alternarModoEscuro() {
        modoEscuro = !modoEscuro
        preferencias.modoEscuro = modoEscuro
    }

    fun irPara(destino: Aba) {
        aba = destino
    }
}
