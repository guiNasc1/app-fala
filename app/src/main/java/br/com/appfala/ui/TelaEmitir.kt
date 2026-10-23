package br.com.appfala.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.appfala.R
import br.com.appfala.data.Cliente
import br.com.appfala.data.DocumentoCompleto
import br.com.appfala.data.ItemDocumento
import br.com.appfala.data.Oficina
import br.com.appfala.data.TipoDocumento
import br.com.appfala.data.TipoItem
import br.com.appfala.data.Veiculo
import br.com.appfala.ui.tema.VermelhoEscuro
import br.com.appfala.util.formatarQuantidade
import br.com.appfala.util.reais
import br.com.appfala.voz.ReconhecedorDeFala

/** Vermelho vivo do microfone enquanto ouve (igual nos dois temas, para chamar atenção). */
private val VermelhoGravando = Color(0xFFD32F2F)

/**
 * @param oficina dados da oficina, copiados para o documento no momento da emissão.
 * @param aoEmitir recebe o documento salvo (para gerar e mostrar o PDF).
 */
@Composable
fun TelaEmitir(vm: EmissaoViewModel, oficina: Oficina?, aoEmitir: suspend (DocumentoCompleto) -> Unit) {
    val tipo = vm.tipo
    if (tipo == null) EscolhaDoTipo(vm::escolher) else TelaDitado(vm, tipo) { vm.emitir(oficina, aoEmitir) }
}

// ==================================================================== escolha do tipo

@Composable
private fun EscolhaDoTipo(aoEscolher: (TipoDocumento) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("O que você vai fazer?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        TipoDocumento.entries.forEach { tipo -> CartaoTipo(tipo) { aoEscolher(tipo) } }
    }
}

/** Orçamento: cartão vermelho cheio. Nota: cartão com contorno vermelho (diferentes nos dois temas). */
@Composable
private fun CartaoTipo(tipo: TipoDocumento, aoClicar: () -> Unit) {
    val modificador = Modifier.fillMaxWidth().heightIn(min = 120.dp)
    val conteudo: @Composable () -> Unit = {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tipo.titulo, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(tipo.descricao, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (tipo == TipoDocumento.ORCAMENTO) {
        Card(
            onClick = aoClicar,
            modifier = modificador,
            colors = CardDefaults.cardColors(containerColor = VermelhoEscuro, contentColor = Color.White),
        ) { conteudo() }
    } else {
        OutlinedCard(
            onClick = aoClicar,
            modifier = modificador,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) { conteudo() }
    }
}

// ==================================================================== ditado

@Composable
private fun TelaDitado(vm: EmissaoViewModel, tipo: TipoDocumento, aoRealizar: () -> Unit) {
    val contexto = LocalContext.current
    var digitando by rememberSaveable { mutableStateOf(false) }
    var confirmarDescarte by rememberSaveable { mutableStateOf(false) }
    var confirmarEmissao by rememberSaveable { mutableStateOf(false) }

    // O reconhecedor vive enquanto esta tela estiver aberta: `remember` cria uma vez e o
    // DisposableEffect libera ao sair (como @PostConstruct / @PreDestroy).
    val reconhecedor = remember {
        ReconhecedorDeFala(
            contexto,
            aoComecar = vm::comecouAOuvir,
            aoOuvirParcial = { vm.textoAoVivo = it },
            aoVolume = { vm.volume = it },
            aoTerminar = { texto ->
                vm.parouDeOuvir(null)
                vm.processar(texto)
            },
            aoFalhar = vm::parouDeOuvir,
        )
    }
    DisposableEffect(reconhecedor) {
        onDispose {
            reconhecedor.liberar()
            vm.parouDeOuvir(null)
        }
    }

    // Pedido de permissão do microfone (a janelinha "Permitir que o app grave áudio?").
    val pedirMicrofone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) reconhecedor.ouvir()
        else vm.aviso = Aviso("Sem permissão para usar o microfone. Libere nas configurações ou use o botão Digitar.")
    }
    val aoTocarMicrofone: () -> Unit = {
        val temPermissao = ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            vm.ouvindo -> reconhecedor.parar()
            !reconhecedor.disponivel -> vm.aviso = Aviso("Este celular não tem reconhecimento de voz. Use o botão Digitar.")
            temPermissao -> reconhecedor.ouvir()
            else -> pedirMicrofone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Rola a lista até o fim sempre que entra um item novo, para o último ditado ficar à vista.
    val lista = rememberLazyListState()
    LaunchedEffect(vm.itens.size, vm.pendentes.size) {
        val total = lista.layoutInfo.totalItemsCount
        if (total > 0) lista.animateScrollToItem(total - 1)
    }

    val vazio = vm.itens.isEmpty() && vm.pendentes.isEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tipo.titulo.uppercase(),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            TextButton(onClick = { if (vazio) vm.recomecar() else confirmarDescarte = true }) {
                Text("Cancelar", fontSize = 16.sp)
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = lista,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { CartaoVeiculo(vm.veiculo, vm.cliente) }
            items(vm.itens, key = { it.id }) { item -> LinhaItem(item) { vm.remover(item) } }
            items(vm.pendentes) { fala -> LinhaPendente(fala) }
            if (vazio) item { Dica() }
        }

        PainelDeGravacao(vm, tipo, aoTocarMicrofone, aoDigitar = { digitando = true }, aoRealizar = { confirmarEmissao = true })
    }

    if (confirmarEmissao) {
        val quantos = vm.itens.size
        AlertDialog(
            onDismissRequest = { confirmarEmissao = false },
            title = { Text(if (tipo == TipoDocumento.ORCAMENTO) "Gerar o orçamento?" else "Gerar a nota de serviço?") },
            text = {
                Text(
                    "$quantos ${if (quantos == 1) "item" else "itens"} · Total ${reais(vm.total)}\n" +
                        "Depois de gerado, ele vai para o Histórico e não pode mais ser mudado.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarEmissao = false
                    aoRealizar()
                }) { Text("Gerar", fontSize = 17.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEmissao = false }) { Text("Voltar", fontSize = 17.sp) }
            },
        )
    }

    if (digitando) {
        DialogoDigitar(
            aoEnviar = {
                vm.processar(it)
                digitando = false
            },
            aoFechar = { digitando = false },
        )
    }
    if (confirmarDescarte) {
        AlertDialog(
            onDismissRequest = { confirmarDescarte = false },
            title = { Text("Descartar este ${tipo.titulo.lowercase()}?") },
            text = { Text("Os itens ditados serão apagados.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarDescarte = false
                    vm.recomecar()
                }) { Text("Descartar", fontSize = 17.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarDescarte = false }) { Text("Continuar", fontSize = 17.sp) }
            },
        )
    }
}

@Composable
private fun CartaoVeiculo(veiculo: Veiculo, cliente: Cliente) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MOTO E CLIENTE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            if (veiculo.vazio && cliente.vazio) {
                Text("Diga primeiro a moto e a placa.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Ex.: \"Orçamento de uma Suzuki GSX R1000 2020, placa ABC1D23\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val moto = listOf(veiculo.modelo, veiculo.ano).filter { it.isNotBlank() }.joinToString(" ")
                if (moto.isNotBlank()) Text(moto, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val detalhes = listOfNotNull(
                    veiculo.placa.takeIf { it.isNotBlank() }?.let { "Placa $it" },
                    veiculo.cor.takeIf { it.isNotBlank() },
                    veiculo.quilometragem.takeIf { it.isNotBlank() }?.let { "$it km" },
                )
                if (detalhes.isNotEmpty()) Text(detalhes.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                val pessoa = listOf(cliente.nome, cliente.telefone).filter { it.isNotBlank() }.joinToString(" · ")
                if (pessoa.isNotBlank()) Text("Cliente: $pessoa", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LinhaItem(item: ItemDocumento, aoApagar: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.descricao, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val servico = if (item.tipo == TipoItem.SERVICO) "  · serviço" else ""
                    Text(
                        "${formatarQuantidade(item.quantidade)} × ${reais(item.valorUnitarioCentavos)}$servico",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(reais(item.valorTotalCentavos), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = aoApagar, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_lixeira), contentDescription = "Apagar ${item.descricao}")
            }
        }
    }
}

@Composable
private fun LinhaPendente(fala: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text("Entendendo…", fontWeight = FontWeight.SemiBold)
                Text(
                    "“$fala”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Dica() {
    Text(
        "Toque no microfone e fale um item por vez, com a quantidade e o valor.\n" +
            "Ex.: \"duas bombas de óleo completas, mil cento e cinco cada\"",
        Modifier.fillMaxWidth().padding(top = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

// ==================================================================== rodapé com o microfone

@Composable
private fun PainelDeGravacao(
    vm: EmissaoViewModel,
    tipo: TipoDocumento,
    aoTocarMicrofone: () -> Unit,
    aoDigitar: () -> Unit,
    aoRealizar: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.aviso?.let { FaixaAviso(it, aoAcao = vm::executar, aoFechar = { vm.aviso = null }) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total", style = MaterialTheme.typography.titleMedium)
                    Text(reais(vm.total), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = aoRealizar,
                    enabled = vm.podeEmitir,
                    modifier = Modifier.height(56.dp),
                ) {
                    if (vm.emitindo) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    else Text(
                        if (tipo == TipoDocumento.ORCAMENTO) "Realizar orçamento" else "Realizar nota",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (vm.ouvindo) {
                Text(
                    vm.textoAoVivo.ifBlank { "Pode falar…" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = aoDigitar, enabled = !vm.ouvindo) {
                        Icon(painterResource(R.drawable.ic_teclado), contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Digitar")
                    }
                }
                BotaoMicrofone(vm.ouvindo, vm.volume, aoTocarMicrofone)
                Spacer(Modifier.weight(1f))
            }
            Text(
                if (vm.ouvindo) "Ouvindo… toque para parar" else "Toque e fale um item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BotaoMicrofone(ouvindo: Boolean, volume: Float, aoTocar: () -> Unit) {
    // O botão "pulsa" com o volume da voz: sinal de que o celular está ouvindo.
    val escala by animateFloatAsState(if (ouvindo) 1f + volume.coerceIn(0f, 10f) / 40f else 1f, label = "volume")
    @DrawableRes val icone = if (ouvindo) R.drawable.ic_parar else R.drawable.ic_emitir
    Box(
        Modifier
            .size(96.dp)
            .scale(escala)
            .clip(CircleShape)
            .background(if (ouvindo) VermelhoGravando else VermelhoEscuro)
            .clickable(role = Role.Button, onClickLabel = if (ouvindo) "Parar de ouvir" else "Falar um item", onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icone),
            contentDescription = if (ouvindo) "Parar de ouvir" else "Falar um item",
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun FaixaAviso(aviso: Aviso, aoAcao: (AcaoAviso) -> Unit, aoFechar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            aviso.texto,
            Modifier.weight(1f).padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        aviso.acao?.let { acao ->
            TextButton(onClick = { aoAcao(acao) }) {
                Text(
                    when (acao) {
                        is AcaoAviso.Repetir -> "Tentar de novo"
                        is AcaoAviso.Desfazer -> "Desfazer"
                    }
                )
            }
        }
        IconButton(onClick = aoFechar) {
            Icon(painterResource(R.drawable.ic_fechar), contentDescription = "Fechar aviso", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun DialogoDigitar(aoEnviar: (String) -> Unit, aoFechar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Digitar um item") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
                placeholder = { Text("Ex.: duas bombas de óleo, 1105 cada") },
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = { aoEnviar(texto) }, enabled = texto.isNotBlank()) { Text("Enviar", fontSize = 17.sp) }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text("Cancelar", fontSize = 17.sp) }
        },
    )
}
