package br.com.appfala.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import br.com.appfala.R
import br.com.appfala.data.TipoDocumento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Mostra o PDF gerado, com os botões de compartilhar e salvar. */
@Composable
fun TelaDocumento(aberto: DocumentoAberto, aoFechar: () -> Unit) {
    BackHandler(onBack = aoFechar)
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val documento = aberto.completo.documento
    val titulo = "${documento.tipo.titulo} nº ${documento.numeroFormatado}"
    var recado by remember(aberto) {
        mutableStateOf(
            if (aberto.recemEmitido) {
                val oQue = if (documento.tipo == TipoDocumento.ORCAMENTO) "O orçamento foi salvo" else "A nota foi salva"
                "Pronto! $oQue e já está no Histórico."
            } else null
        )
    }

    // "Salvar PDF": o Android abre a tela de escolher pasta (ex.: Downloads) e o app grava lá.
    // Não precisa de permissão de armazenamento: o usuário escolhe o lugar.
    val salvarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            escopo.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        contexto.contentResolver.openOutputStream(uri)?.use { saida ->
                            aberto.arquivo.inputStream().use { it.copyTo(saida) }
                        } ?: error("sem destino")
                    }.isSuccess
                }
                recado = if (ok) "PDF salvo." else "Não foi possível salvar o PDF."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = aoFechar, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(documento.dataHoraFormatada, style = MaterialTheme.typography.bodyMedium)
            }
        }

        recado?.let {
            Text(
                it,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        PaginasDoPdf(aberto.arquivo, Modifier.weight(1f)) {
            if (!abrirEmOutroApp(contexto, aberto.arquivo)) recado = "Nenhum app para abrir PDF neste celular. Use Compartilhar."
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { compartilhar(contexto, aberto.arquivo, titulo) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_compartilhar), contentDescription = null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { salvarComo.launch(aberto.arquivo.name) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_baixar), contentDescription = null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Salvar PDF", fontSize = 17.sp)
                }
            }
        }
    }
}

/**
 * Desenha as páginas do PDF como imagens (PdfRenderer, do próprio Android), na largura da tela.
 * `produceState` roda o trabalho pesado fora da tela e entrega o resultado quando fica pronto.
 */
@Composable
private fun PaginasDoPdf(arquivo: File, modifier: Modifier, aoTocar: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val larguraPx = constraints.maxWidth
        val paginas by produceState<List<ImageBitmap>?>(null, arquivo, larguraPx) {
            value = withContext(Dispatchers.IO) { renderizar(arquivo, larguraPx) }
        }
        val lista = paginas
        if (lista == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(lista) { i, pagina ->
                    Image(
                        bitmap = pagina,
                        contentDescription = "Página ${i + 1} do PDF",
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp)
                            .background(Color.White)
                            .clickable(onClickLabel = "Abrir para ampliar", onClick = aoTocar),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                item {
                    Text(
                        "Toque na página para abrir e ampliar.",
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun renderizar(arquivo: File, larguraPx: Int): List<ImageBitmap> =
    ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY).use { descritor ->
        PdfRenderer(descritor).use { renderer ->
            (0 until renderer.pageCount).map { i ->
                renderer.openPage(i).use { pagina ->
                    val altura = (larguraPx.toFloat() * pagina.height / pagina.width).toInt()
                    val bitmap = Bitmap.createBitmap(larguraPx, altura, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    }

/**
 * O arquivo fica na pasta privada do app; o FileProvider gera um endereço (content://) que dá
 * acesso temporário só a esse arquivo para o app escolhido (WhatsApp, e-mail...).
 */
private fun uriDoArquivo(contexto: Context, arquivo: File) =
    FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", arquivo)

private fun compartilhar(contexto: Context, arquivo: File, titulo: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uriDoArquivo(contexto, arquivo))
        putExtra(Intent.EXTRA_SUBJECT, titulo)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(intent, "Compartilhar $titulo"))
}

/** Abre no leitor de PDF do celular (para dar zoom). Devolve false se não houver nenhum. */
private fun abrirEmOutroApp(contexto: Context, arquivo: File): Boolean = try {
    contexto.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriDoArquivo(contexto, arquivo), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}
