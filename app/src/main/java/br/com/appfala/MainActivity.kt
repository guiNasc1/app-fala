package br.com.appfala

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.appfala.ui.AppViewModel
import br.com.appfala.ui.EmissaoViewModel
import br.com.appfala.ui.EstruturaPrincipal
import br.com.appfala.ui.Tela
import br.com.appfala.ui.TelaCadastro
import br.com.appfala.ui.TelaLogin
import br.com.appfala.ui.tema.AppFalaTheme

/**
 * Ponto de entrada do app (o "index.xhtml"). Em Compose quase não há código na Activity:
 * ela só liga o tema e entrega a tela para as funções @Composable.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // viewModel() devolve sempre a mesma instância enquanto a Activity existir (como um bean com escopo).
            val vm: AppViewModel = viewModel()
            val emissao: EmissaoViewModel = viewModel()
            // Saiu da conta: o rascunho em andamento não pode passar para a próxima pessoa.
            LaunchedEffect(vm.tela) {
                if (vm.tela == Tela.LOGIN) emissao.recomecar()
            }
            // Ícones da barra de status claros sobre o cabeçalho escuro; escuros no login (fundo claro).
            val topoClaro = !vm.modoEscuro && (vm.tela == Tela.LOGIN || vm.tela == Tela.CARREGANDO)
            LaunchedEffect(vm.modoEscuro, topoClaro) {
                enableEdgeToEdge(
                    statusBarStyle = if (topoClaro) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    else SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (vm.modoEscuro) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            AppFalaTheme(modoEscuro = vm.modoEscuro) {
                Surface(Modifier.fillMaxSize()) {
                    // "Navegação" por estado: troca a tela inteira conforme vm.tela, como um ui:include dinâmico.
                    when (vm.tela) {
                        Tela.CARREGANDO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        Tela.LOGIN -> TelaLogin(vm)
                        Tela.CADASTRO -> TelaCadastro(vm, edicao = false)
                        Tela.DADOS_OFICINA -> TelaCadastro(vm, edicao = true)
                        Tela.PRINCIPAL -> EstruturaPrincipal(vm, emissao)
                    }
                }
            }
        }
    }
}
