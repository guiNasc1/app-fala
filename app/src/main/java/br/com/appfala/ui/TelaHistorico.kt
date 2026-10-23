package br.com.appfala.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.appfala.R
import br.com.appfala.data.DocumentoSalvo
import br.com.appfala.data.TipoDocumento
import br.com.appfala.ui.tema.VermelhoEscuro
import br.com.appfala.util.reais

@Composable
fun TelaHistorico(vm: DocumentosViewModel) {
    // Toda vez que a aba abre, busca de novo (pega o que foi emitido em outro aparelho também).
    LaunchedEffect(Unit) { vm.carregarHistorico() }

    Column(Modifier.fillMaxSize()) {
        if (vm.abrindo || (vm.carregando && vm.historico.isNotEmpty())) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when {
            vm.carregando && vm.historico.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            vm.historico.isEmpty() && vm.erro != null -> Mensagem(vm.erro.orEmpty(), "Tentar de novo", vm::carregarHistorico)

            vm.historico.isEmpty() ->
                Mensagem("Nada emitido ainda.\nOs orçamentos e notas que você gerar aparecem aqui.")

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                vm.erro?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(vm.historico, key = { it.id }) { doc -> CartaoDocumento(doc) { vm.abrir(doc) } }
            }
        }
    }
}

@Composable
private fun CartaoDocumento(doc: DocumentoSalvo, aoAbrir: () -> Unit) {
    Card(
        onClick = aoAbrir,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Etiqueta(doc.tipo)
                Text(
                    "Nº ${doc.numeroFormatado}",
                    Modifier.padding(start = 10.dp).weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(doc.dataFormatada, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val moto = listOf(doc.veiculo.modelo, doc.veiculo.ano).filter { it.isNotBlank() }.joinToString(" ")
            val linhaMoto = listOf(moto, doc.veiculo.placa).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                linhaMoto.ifBlank { "Sem dados da moto" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (doc.cliente.nome.isNotBlank()) {
                Text("Cliente: ${doc.cliente.nome}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(reais(doc.totalCentavos), Modifier.fillMaxWidth(), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
    }
}

/** Etiqueta que diferencia os tipos: Orçamento cheia (vermelha), Nota com contorno. */
@Composable
private fun Etiqueta(tipo: TipoDocumento) {
    val forma = RoundedCornerShape(6.dp)
    val modificador = if (tipo == TipoDocumento.ORCAMENTO) {
        Modifier.clip(forma).background(VermelhoEscuro)
    } else {
        Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), forma)
    }
    Text(
        if (tipo == TipoDocumento.ORCAMENTO) "ORÇAMENTO" else "NOTA DE SERVIÇO",
        modificador.padding(horizontal = 8.dp, vertical = 3.dp),
        color = if (tipo == TipoDocumento.ORCAMENTO) Color.White else MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun Mensagem(texto: String, botao: String? = null, aoClicar: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_historico), contentDescription = null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(texto, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (botao != null) OutlinedButton(onClick = aoClicar) { Text(botao) }
    }
}
