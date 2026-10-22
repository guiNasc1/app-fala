package br.com.appfala

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.appfala.ui.AppViewModel
import br.com.appfala.ui.EstruturaPrincipal
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
            // Barra de status com ícones claros (o cabeçalho é escuro nos dois modos);
            // barra de navegação do sistema acompanha o modo do app.
            LaunchedEffect(vm.modoEscuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (vm.modoEscuro) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            AppFalaTheme(modoEscuro = vm.modoEscuro) {
                EstruturaPrincipal(vm)
            }
        }
    }
}
