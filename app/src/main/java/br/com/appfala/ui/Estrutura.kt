package br.com.appfala.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.appfala.R
import br.com.appfala.ui.tema.CoresCabecalho

/**
 * Esqueleto das telas logadas: cabeçalho em cima, abas embaixo e o conteúdo da aba no meio.
 * É o "template" (ui:composition + ui:insert) do app: o Scaffold reserva as regiões e cada aba
 * preenche o meio.
 */
@Composable
fun EstruturaPrincipal(vm: AppViewModel) {
    // Voltar do Android numa aba qualquer leva ao Início; no Início, fecha o app (padrão do sistema).
    BackHandler(enabled = vm.aba != Aba.INICIO) { vm.irPara(Aba.INICIO) }

    Scaffold(
        topBar = { Cabecalho(vm.oficina?.nomeExibicao ?: "Fala Oficina", vm.modoEscuro, vm::alternarModoEscuro) },
        bottomBar = { BarraInferior(vm.aba, vm::irPara) },
    ) { espacos ->
        // `espacos` são as margens ocupadas pelo cabeçalho e pela barra; o conteúdo respeita isso.
        Column(Modifier.padding(espacos).fillMaxSize()) {
            vm.aviso?.let { AvisoComBotao(it, "Tentar de novo", vm::recarregarOficina) }
            Box(Modifier.weight(1f)) {
                when (vm.aba) {
                    Aba.INICIO -> TelaEmBreve(R.drawable.ic_inicio, "Início", "Aqui vão os gráficos com os valores de cada mês.")
                    Aba.EMITIR -> TelaEmBreve(R.drawable.ic_emitir, "Emitir", "Aqui você vai ditar o orçamento ou a nota de serviço.")
                    Aba.HISTORICO -> TelaEmBreve(R.drawable.ic_historico, "Histórico", "Aqui aparecem os documentos já emitidos.")
                    Aba.PERFIL -> TelaPerfil(vm)
                }
            }
        }
    }
}

/** Faixa de aviso no topo do conteúdo (ex.: sem internet ao carregar a oficina). */
@Composable
private fun AvisoComBotao(texto: String, botao: String, aoClicar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = aoClicar) { Text(botao) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecalho(nomeOficina: String, modoEscuro: Boolean, aoAlternarModo: () -> Unit) {
    TopAppBar(
        title = {
            Text(nomeOficina, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        actions = {
            IconButton(onClick = aoAlternarModo, modifier = Modifier.size(56.dp)) {
                Icon(
                    painterResource(if (modoEscuro) R.drawable.ic_modo_claro else R.drawable.ic_modo_escuro),
                    contentDescription = if (modoEscuro) "Usar modo claro" else "Usar modo escuro",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CoresCabecalho.fundo,
            titleContentColor = CoresCabecalho.texto,
            actionIconContentColor = CoresCabecalho.texto,
        ),
    )
}

@Composable
private fun BarraInferior(abaAtual: Aba, aoEscolher: (Aba) -> Unit) {
    NavigationBar {
        // `entries` lista os valores do enum, como Aba.values() no Java.
        Aba.entries.forEach { aba ->
            NavigationBarItem(
                selected = aba == abaAtual,
                onClick = { aoEscolher(aba) },
                icon = { Icon(painterResource(aba.icone), contentDescription = null, Modifier.size(28.dp)) },
                label = { Text(aba.titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                // A aba atual fica em destaque com as cores da marca (sem isso o Material usa lilás).
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

// Função de extensão: "adiciona" uma propriedade ao enum Aba sem mexer na declaração dele.
// Fica aqui porque ícone é assunto de tela, não do ViewModel.
@get:DrawableRes
private val Aba.icone: Int
    get() = when (this) {
        Aba.INICIO -> R.drawable.ic_inicio
        Aba.EMITIR -> R.drawable.ic_emitir
        Aba.HISTORICO -> R.drawable.ic_historico
        Aba.PERFIL -> R.drawable.ic_perfil
    }

/** Conteúdo provisório das abas até cada fase ficar pronta. */
@Composable
private fun TelaEmBreve(@DrawableRes icone: Int, titulo: String, texto: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icone), contentDescription = null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(titulo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(texto, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
