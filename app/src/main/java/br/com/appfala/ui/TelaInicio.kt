package br.com.appfala.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.appfala.ui.tema.CoresDocumento
import br.com.appfala.util.mesCurto
import br.com.appfala.util.mesPorExtenso
import br.com.appfala.util.reais
import br.com.appfala.util.reaisCompacto
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Início: os números do mês escolhido em destaque, o gráfico dos últimos meses (orçamentos x notas)
 * e a mesma informação em tabela. Tocar num mês do gráfico ou da tabela troca o mês em destaque.
 */
@Composable
fun TelaInicio(vm: DocumentosViewModel, nomeUsuario: String?) {
    // Recarrega toda vez que o Início aparece (pega o que acabou de ser emitido).
    LaunchedEffect(Unit) { vm.carregarResumo() }
    val resumo = vm.resumo

    when {
        resumo == null && vm.erroResumo != null -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(vm.erroResumo.orEmpty(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            OutlinedButton(onClick = vm::carregarResumo) { Text("Tentar de novo") }
        }

        resumo == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        else -> ConteudoInicio(resumo, nomeUsuario, atualizando = vm.carregandoResumo)
    }
}

@Composable
private fun ConteudoInicio(meses: List<TotalDoMes>, nomeUsuario: String?, atualizando: Boolean) {
    // Índice do mês em destaque; começa no atual (o último da lista).
    var selecionado by rememberSaveable { mutableIntStateOf(meses.lastIndex) }
    val mes = meses[selecionado.coerceIn(meses.indices)]
    val vazio = meses.all { it.orcamentosQuantidade == 0 && it.notasQuantidade == 0 }

    Column(
        Modifier
            .fillMaxSize()
            // Atualizando: mantém o que já estava na tela, só mais apagado (sem piscar).
            .alpha(if (atualizando) 0.6f else 1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val primeiroNome = nomeUsuario?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        Text(
            if (primeiroNome != null) "Olá, $primeiroNome!" else "Olá!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Bloco(mesPorExtenso(mes.mes).uppercase()) {
            LinhaDoMes(CoresDocumento.orcamento, "Orçamentos", mes.orcamentosQuantidade, "orçamento", "orçamentos", mes.orcamentosCentavos)
            HorizontalDivider()
            LinhaDoMes(CoresDocumento.nota, "Notas de serviço", mes.notasQuantidade, "nota", "notas", mes.notasCentavos)
        }

        if (vazio) {
            Text(
                "Ainda não há documentos nos últimos $MESES_NO_GRAFICO meses. " +
                    "Os gráficos aparecem aqui quando você emitir orçamentos e notas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Bloco("VALORES POR MÊS") {
                Legenda()
                GraficoMensal(meses, selecionado, aoSelecionar = { selecionado = it })
                Text(
                    "Toque num mês para ver os números dele.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Bloco("MÊS A MÊS") {
                TabelaMensal(meses, selecionado, aoSelecionar = { selecionado = it })
            }
        }
    }
}

@Composable
private fun Bloco(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            conteudo()
        }
    }
}

/** Uma linha do mês em destaque: quadradinho da cor do tipo, nome, quantidade e valor. */
@Composable
private fun LinhaDoMes(cor: Color, rotulo: String, quantidade: Int, singular: String, plural: String, centavos: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(cor))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(rotulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                when (quantidade) {
                    0 -> "nenhum${if (singular == "nota") "a" else ""} $singular"
                    1 -> "1 $singular"
                    else -> "$quantidade $plural"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(reais(centavos), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Legenda sempre visível: a cor nunca é a única pista de qual barra é qual. */
@Composable
private fun Legenda() {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ItemLegenda(CoresDocumento.orcamento, "Orçamentos")
        ItemLegenda(CoresDocumento.nota, "Notas de serviço")
    }
}

@Composable
private fun ItemLegenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(cor))
        Text(texto, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Barras agrupadas desenhadas à mão no Canvas (como um paintComponent do Swing): para cada mês,
 * uma barra de orçamentos e uma de notas, com a mesma escala. A área de toque é a coluna inteira
 * do mês, bem maior que a barra, para acertar fácil com o dedo.
 */
@Composable
private fun GraficoMensal(meses: List<TotalDoMes>, selecionado: Int, aoSelecionar: (Int) -> Unit) {
    val corOrcamento = CoresDocumento.orcamento
    val corNota = CoresDocumento.nota
    val corGrade = MaterialTheme.colorScheme.outlineVariant
    val corBase = MaterialTheme.colorScheme.outline
    val corDestaque = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val estiloEixo = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val estiloMes = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val estiloMesSelecionado = estiloMes.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val medidor = rememberTextMeasurer()

    val divisoes = divisoesDoEixo(meses.maxOf { max(it.orcamentosCentavos, it.notasCentavos) })
    val teto = divisoes.last().toFloat()
    val descricao = "Gráfico dos últimos ${meses.size} meses. " + meses.joinToString("; ") {
        "${mesPorExtenso(it.mes)}: orçamentos ${reais(it.orcamentosCentavos)}, notas ${reais(it.notasCentavos)}"
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics { contentDescription = descricao }
            .pointerInput(meses.size) {
                detectTapGestures { toque ->
                    val esquerda = LARGURA_EIXO.toPx()
                    val fatia = (size.width - esquerda) / meses.size
                    val i = floor((toque.x - esquerda) / fatia).toInt()
                    if (i in meses.indices) aoSelecionar(i)
                }
            },
    ) {
        val esquerda = LARGURA_EIXO.toPx()
        val topoPlot = 10.dp.toPx()
        val base = size.height - ALTURA_MESES.toPx()
        val alturaPlot = base - topoPlot
        val fatia = (size.width - esquerda) / meses.size
        fun yDe(centavos: Long) = base - alturaPlot * (centavos / teto)

        // fundo do mês escolhido (uma "lavada" neutra, igual nos dois temas)
        drawRoundRect(
            corDestaque,
            topLeft = Offset(esquerda + fatia * selecionado + 2.dp.toPx(), 0f),
            size = Size(fatia - 4.dp.toPx(), size.height),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )

        // linhas de grade finas e os valores do eixo à esquerda
        divisoes.forEach { v ->
            val y = yDe(v)
            drawLine(if (v == 0L) corBase else corGrade, Offset(esquerda, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val rotulo = medidor.measure(reaisCompacto(v), estiloEixo)
            drawText(rotulo, topLeft = Offset(esquerda - rotulo.size.width - 8.dp.toPx(), y - rotulo.size.height / 2f))
        }

        // barras: finas, ponta arredondada, quadradas na base, 2dp de vão entre as duas do mês
        val vao = 2.dp.toPx()
        val larguraBarra = min(16.dp.toPx(), (fatia - 14.dp.toPx() - vao) / 2f)
        val raio = 4.dp.toPx()
        fun barra(x: Float, centavos: Long, cor: Color) {
            if (centavos <= 0) return
            val topo = yDe(centavos)
            val r = CornerRadius(min(raio, base - topo))
            val forma = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = x, top = topo, right = x + larguraBarra, bottom = base,
                        topLeftCornerRadius = r, topRightCornerRadius = r,
                        bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
                    )
                )
            }
            drawPath(forma, cor)
        }

        meses.forEachIndexed { i, m ->
            val inicioGrupo = esquerda + fatia * i + (fatia - (2 * larguraBarra + vao)) / 2f
            barra(inicioGrupo, m.orcamentosCentavos, corOrcamento)
            barra(inicioGrupo + larguraBarra + vao, m.notasCentavos, corNota)

            val rotulo = medidor.measure(mesCurto(m.mes), if (i == selecionado) estiloMesSelecionado else estiloMes)
            drawText(rotulo, topLeft = Offset(esquerda + fatia * i + (fatia - rotulo.size.width) / 2f, base + 6.dp.toPx()))
        }
    }
}

/** A mesma informação do gráfico, em números (do mês mais recente para o mais antigo). */
@Composable
private fun TabelaMensal(meses: List<TotalDoMes>, selecionado: Int, aoSelecionar: (Int) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("Mês", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Orçamentos", Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            Text("Notas", Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
        meses.indices.reversed().forEach { i ->
            val m = meses[i]
            val escolhido = i == selecionado
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (escolhido) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
                    .clickable { aoSelecionar(i) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                val peso = if (escolhido) FontWeight.Bold else FontWeight.Normal
                Text("${mesCurto(m.mes)}/${m.mes.year % 100}", Modifier.weight(1f), fontWeight = peso)
                Text(reais(m.orcamentosCentavos), Modifier.weight(1.4f), fontWeight = peso, textAlign = TextAlign.End)
                Text(reais(m.notasCentavos), Modifier.weight(1.4f), fontWeight = peso, textAlign = TextAlign.End)
            }
        }
    }
}

private val LARGURA_EIXO = 64.dp
private val ALTURA_MESES = 30.dp

/**
 * Divisões "redondas" do eixo vertical, em centavos. Ex.: máximo de R$ 8.220 vira
 * 0 / 2.500 / 5.000 / 7.500 / 10.000 (passos de 1, 2, 2,5 ou 5 vezes uma potência de 10).
 */
internal fun divisoesDoEixo(maximoCentavos: Long, partes: Int = 4): List<Long> {
    if (maximoCentavos <= 0) return listOf(0L, 100_000L) // R$ 0 a R$ 1.000 quando não há nada
    val passoBruto = maximoCentavos.toDouble() / partes
    val potencia = 10.0.pow(floor(log10(passoBruto)))
    val passo = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * potencia }.first { it >= passoBruto }
    val quantas = ceil(maximoCentavos / passo).toInt().coerceAtLeast(1)
    return (0..quantas).map { (it * passo).toLong() }
}
