package br.com.appfala.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import br.com.appfala.data.DocumentoCompleto
import br.com.appfala.data.ItemSalvo
import br.com.appfala.data.TipoDocumento
import br.com.appfala.data.TipoItem
import br.com.appfala.util.Cnpj
import br.com.appfala.util.Mascaras
import br.com.appfala.util.aplicarMascara
import br.com.appfala.util.formatarQuantidade
import br.com.appfala.util.reais
import java.io.File

/**
 * Desenha o orçamento ou a nota em A4 (595 x 842 pt), adaptado do layout do projeto NotaServico:
 * faixa vermelha no topo com os dados da oficina, blocos de cliente e veículo, tabela de itens
 * agrupada em peças e serviços e quadro de totais à direita.
 *
 * PdfDocument é a API do próprio Android: cada página é um Canvas onde se desenha texto e
 * retângulos por coordenada (parecido com o Graphics2D do Java), sem biblioteca externa.
 */
class GeradorPdf {

    private companion object {
        const val LARGURA = 595
        const val ALTURA = 842

        const val VERMELHO = 0xFF8E1B1B.toInt()
        const val VERMELHO_GRUPO = 0xFF5E1010.toInt()
        const val ACENTO = 0xFFFFB4A9.toInt()
        const val TEXTO = 0xFF201A19.toInt()
        const val SOBRE_VERMELHO = 0xFFF6DEDA.toInt()
        const val CINZA = 0xFF6B6B6B.toInt()
        const val LINHA = 0xFFD5D5D5.toInt()
        const val ZEBRA = 0xFFFAF7F6.toInt()
        const val BORDA = 0xFF9A9A9A.toInt()
        const val BRANCO = 0xFFFFFFFF.toInt()

        const val ML = 46.77f            // margem do conteúdo
        const val CW = 501.73f           // largura do conteúdo
        const val MH = 51.02f            // margem do cabeçalho e rodapé
        const val TOPO_CONTEUDO = 113.7f
        const val LIMITE = 796f          // abaixo disso começa o rodapé

        // colunas da tabela: ITEM | DESCRIÇÃO | QTD | VALOR UNIT. | TOTAL
        val COLUNAS = floatArrayOf(0f, 31.18f, 310f, 360f, 433.70f, CW)
        const val ALT_LINHA_TEXTO = 11f
    }

    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val negrito = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun texto(tamanho: Float, bold: Boolean = false, cor: Int = TEXTO) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tamanho
            typeface = if (bold) negrito else regular
            color = cor
        }

    private fun preenchimento(cor: Int) = Paint().apply { color = cor; style = Paint.Style.FILL }
    private fun traco(cor: Int, largura: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cor; style = Paint.Style.STROKE; strokeWidth = largura
    }

    // estado da página corrente (lateinit = "inicializo depois", como um campo que o construtor não preenche)
    private lateinit var pdf: PdfDocument
    private lateinit var pagina: PdfDocument.Page
    private lateinit var c: Canvas
    private var numPagina = 0
    private var y = 0f
    private lateinit var doc: DocumentoCompleto

    private val ehOrcamento get() = doc.documento.tipo == TipoDocumento.ORCAMENTO

    fun gerar(documento: DocumentoCompleto, destino: File) {
        doc = documento
        pdf = PdfDocument()
        numPagina = 0
        novaPagina()

        val cliente = doc.documento.cliente
        if (!cliente.vazio) {
            secao("DADOS DO CLIENTE")
            linhaCampos(
                listOf("CLIENTE" to cliente.nome, "TELEFONE" to cliente.telefone),
                floatArrayOf(361.73f, 140f),
            )
            y += 12f
        }

        val v = doc.documento.veiculo
        if (!v.vazio) {
            secao("DADOS DO VEÍCULO")
            linhaCampos(
                listOf(
                    "MARCA / MODELO" to v.modelo,
                    "ANO" to v.ano,
                    "PLACA" to v.placa,
                    "COR" to v.cor,
                    "QUILOMETRAGEM" to v.quilometragem,
                ),
                floatArrayOf(180f, 60f, 85f, 86.73f, 90f),
            )
            y += 12f
        }

        tabela()
        totais()

        rodape()
        pdf.finishPage(pagina)
        destino.parentFile?.mkdirs()
        destino.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }

    // ---------------------------------------------------------------- página

    private fun novaPagina() {
        if (numPagina > 0) {
            rodape()
            pdf.finishPage(pagina)
        }
        numPagina++
        pagina = pdf.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numPagina).create())
        c = pagina.canvas
        cabecalho()
        y = TOPO_CONTEUDO
    }

    private fun garantirEspaco(altura: Float) {
        if (y + altura > LIMITE) novaPagina()
    }

    private fun cabecalho() {
        val oficina = doc.documento.oficina
        c.drawRect(0f, 0f, LARGURA.toFloat(), 93.54f, preenchimento(VERMELHO))
        c.drawRect(0f, 93.54f, LARGURA.toFloat(), 99.21f, preenchimento(ACENTO))

        // lado esquerdo: oficina
        val nome = oficina.nomeExibicao.uppercase()
        val pNome = texto(22f, true, BRANCO)
        while (pNome.measureText(nome) > 300f && pNome.textSize > 12f) pNome.textSize -= 1f
        c.drawText(nome, MH, 40f, pNome)

        val pInfo = texto(7.8f, cor = SOBRE_VERMELHO)
        val linhas = buildList {
            if (oficina.nomeFantasia.isNotBlank() && oficina.razaoSocial.isNotBlank() &&
                !oficina.razaoSocial.equals(oficina.nomeFantasia, ignoreCase = true)
            ) add(oficina.razaoSocial)
            add(
                listOfNotNull(
                    "CNPJ ${Cnpj.formatar(oficina.cnpj)}",
                    oficina.telefone.takeIf { it.isNotBlank() }
                        ?.let { "Tel./WhatsApp " + aplicarMascara(it, Mascaras.telefone(it)) },
                ).joinToString("   ")
            )
            val cidade = listOf(oficina.cidade, oficina.uf).filter { it.isNotBlank() }.joinToString("/")
            val cep = oficina.cep.takeIf { it.isNotBlank() }?.let { "CEP " + aplicarMascara(it, Mascaras.CEP) }
            val endereco = listOfNotNull(
                oficina.endereco.takeIf { it.isNotBlank() },
                oficina.bairro.takeIf { it.isNotBlank() },
                cidade.takeIf { it.isNotBlank() },
                cep,
            ).joinToString(" - ")
            if (endereco.isNotBlank()) add(endereco)
            if (oficina.email.isNotBlank()) add(oficina.email)
        }
        linhas.take(4).forEachIndexed { i, s -> c.drawText(cortar(s, pInfo, 330f), MH, 55f + i * 10f, pInfo) }

        // lado direito: identificação do documento
        val dir = LARGURA - MH
        direita(doc.documento.tipo.titulo.uppercase(), dir, 40f, texto(13f, true, BRANCO))
        direita("Nº ${doc.documento.numeroFormatado}", dir, 56f, texto(11f, true, ACENTO))
        direita("Emissão: ${doc.documento.dataFormatada}", dir, 73f, texto(8f, cor = SOBRE_VERMELHO))
    }

    private fun rodape() {
        val oficina = doc.documento.oficina
        c.drawLine(MH, 802.2f, LARGURA - MH, 802.2f, traco(LINHA, 0.5f))
        val p = texto(7f, cor = CINZA)
        val partes = buildList {
            add(oficina.nomeExibicao.uppercase())
            add("CNPJ ${Cnpj.formatar(oficina.cnpj)}")
            if (oficina.telefone.isNotBlank()) add(aplicarMascara(oficina.telefone, Mascaras.telefone(oficina.telefone)))
        }
        c.drawText(cortar(partes.joinToString("  •  "), p, 420f), MH, 813.5f, p)
        direita("Página $numPagina", LARGURA - MH, 813.5f, p)
        c.drawText(
            "Documento de controle interno da oficina, sem valor fiscal. Não substitui a NFS-e emitida pela prefeitura.",
            MH, 823.5f, texto(6f, cor = BORDA),
        )
    }

    // ---------------------------------------------------------------- blocos

    private fun secao(titulo: String) {
        garantirEspaco(19f + 3f + 32f)
        c.drawRect(ML, y, ML + CW, y + 19f, preenchimento(VERMELHO))
        c.drawLine(ML, y, ML, y + 19f, traco(ACENTO, 2.5f))
        c.drawText(titulo, ML + 6f, y + 12.5f, texto(8.5f, true, BRANCO))
        y += 19f + 3f
    }

    /** Uma linha de campos (rótulo pequeno em cima, valor embaixo, traço cinza na base). */
    private fun linhaCampos(campos: List<Pair<String, String>>, larguras: FloatArray) {
        garantirEspaco(32f)
        val pRotulo = texto(7f, true, CINZA)
        val pValor = texto(9.5f)
        val linha = traco(LINHA, 0.6f)
        var x = ML
        campos.forEachIndexed { i, (rotulo, valor) ->
            val w = larguras[i]
            c.drawText(rotulo, x + 5f, y + 11f, pRotulo)
            c.drawText(cortar(valor.ifBlank { "-" }, pValor, w - 10f), x + 5f, y + 25.5f, pValor)
            c.drawLine(x, y + 32f, x + w, y + 32f, linha)
            x += w
        }
        y += 32f
    }

    private sealed interface Linha {
        val altura: Float
    }

    private class LinhaGrupo(val titulo: String) : Linha {
        override val altura = 20f
    }

    private class LinhaItem(val numero: Int, val item: ItemSalvo, val descricao: List<String>, val zebra: Boolean) : Linha {
        override val altura = maxOf(19f, descricao.size * ALT_LINHA_TEXTO + 8f)
    }

    private val pecas get() = doc.itens.filter { it.tipo == TipoItem.PECA }
    private val servicos get() = doc.itens.filter { it.tipo == TipoItem.SERVICO }

    /** Peças primeiro, depois serviços; cada grupo vira "A. PEÇAS", "B. MÃO DE OBRA E SERVIÇOS". */
    private fun montarLinhas(): List<Linha> {
        val pDesc = texto(8.5f)
        val larguraDesc = COLUNAS[2] - COLUNAS[1] - 10f
        val grupos = listOf("PEÇAS" to pecas, "MÃO DE OBRA E SERVIÇOS" to servicos).filter { it.second.isNotEmpty() }

        val linhas = mutableListOf<Linha>()
        var n = 0
        grupos.forEachIndexed { gi, (titulo, itens) ->
            linhas += LinhaGrupo("${'A' + gi}. $titulo")
            itens.forEach { item ->
                n++
                linhas += LinhaItem(n, item, quebrar(item.descricao.ifBlank { "-" }, pDesc, larguraDesc), n % 2 == 0)
            }
        }
        return linhas
    }

    private fun tabela() {
        val linhas = montarLinhas()
        val primeiraAltura = linhas.take(2).sumOf { it.altura.toDouble() }.toFloat()
        garantirEspaco(19f + 3f + 19f + primeiraAltura)
        secao("RELAÇÃO DE PEÇAS E SERVIÇOS")
        var topoTabela = y
        cabecalhoTabela()

        linhas.forEachIndexed { i, linha ->
            // não deixa um título de grupo sozinho no fim da página
            val necessario = linha.altura + if (linha is LinhaGrupo) (linhas.getOrNull(i + 1)?.altura ?: 0f) else 0f
            if (y + necessario > LIMITE) {
                bordaTabela(topoTabela)
                novaPagina()
                secao("RELAÇÃO DE PEÇAS E SERVIÇOS (CONTINUAÇÃO)")
                topoTabela = y
                cabecalhoTabela()
            }
            when (linha) {
                is LinhaGrupo -> desenharGrupo(linha)
                is LinhaItem -> desenharItem(linha)
            }
            y += linha.altura
        }
        bordaTabela(topoTabela)
        y += 3f
    }

    private fun cabecalhoTabela() {
        c.drawRect(ML, y, ML + CW, y + 19f, preenchimento(VERMELHO))
        val p = texto(8f, true, BRANCO)
        val base = y + 12.5f
        c.drawText("ITEM", ML + COLUNAS[0] + 5f, base, p)
        c.drawText("DESCRIÇÃO", ML + COLUNAS[1] + 5f, base, p)
        centro("QTD", ML + COLUNAS[2], ML + COLUNAS[3], base, p)
        direita("VALOR UNIT.", ML + COLUNAS[4] - 5f, base, p)
        direita("TOTAL", ML + COLUNAS[5] - 5f, base, p)
        divisoriasColunas(y, 19f)
        c.drawLine(ML, y + 19f, ML + CW, y + 19f, traco(LINHA, 0.4f))
        y += 19f
    }

    private fun desenharGrupo(g: LinhaGrupo) {
        c.drawRect(ML, y, ML + CW, y + g.altura, preenchimento(VERMELHO_GRUPO))
        c.drawText(cortar(g.titulo, texto(7.8f, true), CW - 10f), ML + 5f, y + 13f, texto(7.8f, true, BRANCO))
        c.drawLine(ML, y, ML, y + g.altura, traco(ACENTO, 2.5f))
        c.drawLine(ML, y + g.altura, ML + CW, y + g.altura, traco(LINHA, 0.4f))
    }

    private fun desenharItem(l: LinhaItem) {
        val h = l.altura
        if (l.zebra) c.drawRect(ML, y, ML + CW, y + h, preenchimento(ZEBRA))
        val p = texto(8.5f)
        val meio = y + h / 2f + 3f

        centro("%02d".format(l.numero), ML + COLUNAS[0], ML + COLUNAS[1], meio, p)
        var base = y + (h - l.descricao.size * ALT_LINHA_TEXTO) / 2f + 8.5f
        l.descricao.forEach {
            c.drawText(it, ML + COLUNAS[1] + 5f, base, p)
            base += ALT_LINHA_TEXTO
        }
        centro(formatarQuantidade(l.item.quantidade), ML + COLUNAS[2], ML + COLUNAS[3], meio, p)
        direita(reais(l.item.valorUnitarioCentavos), ML + COLUNAS[4] - 5f, meio, p)
        direita(reais(l.item.valorTotalCentavos), ML + COLUNAS[5] - 5f, meio, p)

        divisoriasColunas(y, h)
        c.drawLine(ML, y + h, ML + CW, y + h, traco(LINHA, 0.4f))
    }

    private fun divisoriasColunas(topo: Float, h: Float) {
        val t = traco(LINHA, 0.4f)
        for (i in 1 until COLUNAS.size - 1) c.drawLine(ML + COLUNAS[i], topo, ML + COLUNAS[i], topo + h, t)
    }

    private fun bordaTabela(topo: Float) {
        c.drawRect(ML, topo, ML + CW, y, traco(BORDA, 0.7f))
    }

    private fun totais() {
        val w = 320.32f
        val x = ML + CW - 10f - w
        val comSubtotais = pecas.isNotEmpty() && servicos.isNotEmpty()
        garantirEspaco((if (comSubtotais) 38f else 0f) + 25f)
        val p = texto(8.5f)
        val pb = texto(8.5f, true)
        val linha = traco(LINHA, 0.4f)

        if (comSubtotais) {
            direita("Subtotal peças", x + 206f, y + 12.5f, p)
            direita(reais(pecas.sumOf { it.valorTotalCentavos }), x + w - 6f, y + 12.5f, pb)
            c.drawLine(x, y + 19f, x + w, y + 19f, linha)
            y += 19f

            direita("Subtotal mão de obra e serviços", x + 206f, y + 12.5f, p)
            direita(reais(servicos.sumOf { it.valorTotalCentavos }), x + w - 6f, y + 12.5f, pb)
            c.drawLine(x, y + 19f, x + w, y + 19f, linha)
            y += 19f
        }

        c.drawRect(x, y, x + w, y + 25f, preenchimento(VERMELHO))
        c.drawLine(x, y, x, y + 25f, traco(ACENTO, 3f))
        direita(if (ehOrcamento) "VALOR TOTAL DO ORÇAMENTO" else "VALOR TOTAL DA NOTA", x + 206f, y + 16.5f, texto(9.5f, true, BRANCO))
        direita(reais(doc.documento.totalCentavos), x + w - 6f, y + 17f, texto(12f, true, BRANCO))
        y += 25f + 14f
    }

    // ---------------------------------------------------------------- texto

    private fun direita(s: String, xDireita: Float, base: Float, p: Paint) =
        c.drawText(s, xDireita - p.measureText(s), base, p)

    private fun centro(s: String, x0: Float, x1: Float, base: Float, p: Paint) =
        c.drawText(s, (x0 + x1 - p.measureText(s)) / 2f, base, p)

    private fun cortar(s: String, p: TextPaint, largura: Float) =
        TextUtils.ellipsize(s, p, largura, TextUtils.TruncateAt.END).toString()

    /** Quebra o texto para caber na largura da coluna. */
    private fun quebrar(s: String, p: TextPaint, largura: Float): List<String> {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, largura.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        return (0 until layout.lineCount).map {
            s.substring(layout.getLineStart(it), layout.getLineEnd(it)).trimEnd()
        }
    }
}
