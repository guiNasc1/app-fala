package br.com.appfala.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.appfala.util.Cnpj
import br.com.appfala.util.Mascaras
import br.com.appfala.util.aplicarMascara

/** O que pedir confirmação antes de fazer. `null` = nenhuma janela aberta. */
private enum class Confirmar { SAIR, TROCAR }

@Composable
fun TelaPerfil(vm: AppViewModel) {
    val oficina = vm.oficina
    var confirmar by remember { mutableStateOf<Confirmar?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        // Foto: por enquanto as iniciais. A foto de verdade entra junto com o Storage, na fase do PDF.
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                iniciais(oficina?.nomeUsuario ?: vm.emailLogado.orEmpty()),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            oficina?.nomeUsuario?.ifBlank { null } ?: "Sem nome",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(vm.emailLogado.orEmpty(), style = MaterialTheme.typography.bodyLarge)

        if (oficina != null) {
            Secao("Oficina") {
                Informacao("Nome", oficina.nomeExibicao)
                if (oficina.nomeFantasia.isNotBlank() && oficina.razaoSocial.isNotBlank()) {
                    Informacao("Razão social", oficina.razaoSocial)
                }
                Informacao("CNPJ", Cnpj.formatar(oficina.cnpj))
                if (oficina.telefone.isNotBlank()) {
                    Informacao("Telefone", aplicarMascara(oficina.telefone, Mascaras.telefone(oficina.telefone)))
                }
                val cidade = listOf(oficina.cidade, oficina.uf).filter { it.isNotBlank() }.joinToString(" / ")
                if (cidade.isNotBlank()) Informacao("Cidade", cidade)
            }
        }

        BotaoPerfil("Editar dados da oficina", aoClicar = vm::editarOficina)
        BotaoPerfil("Trocar de conta") { confirmar = Confirmar.TROCAR }
        BotaoPerfil("Sair", perigo = true) { confirmar = Confirmar.SAIR }
        Spacer(Modifier.height(16.dp))
    }

    // Diálogo de confirmação (o p:confirmDialog). Só existe na tela enquanto `confirmar` não é null.
    confirmar?.let { acao ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(if (acao == Confirmar.SAIR) "Sair do app?" else "Trocar de conta?") },
            text = {
                Text(
                    if (acao == Confirmar.SAIR) "Você vai precisar da senha para entrar de novo."
                    else "Você sai desta conta e pode entrar com outro e-mail.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = null
                    if (acao == Confirmar.SAIR) vm.sair() else vm.trocarDeConta()
                }) { Text(if (acao == Confirmar.SAIR) "Sair" else "Trocar", fontSize = 17.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = null }) { Text("Cancelar", fontSize = 17.sp) }
            },
        )
    }
}

@Composable
private fun Informacao(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BotaoPerfil(texto: String, perigo: Boolean = false, aoClicar: () -> Unit) {
    val cor = if (perigo) MaterialTheme.colorScheme.error else Color.Unspecified
    OutlinedButton(
        onClick = aoClicar,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = if (perigo) ButtonDefaults.outlinedButtonColors(contentColor = cor) else ButtonDefaults.outlinedButtonColors(),
    ) { Text(texto, fontSize = 17.sp) }
}

/** "Guilherme Nascimento" -> "GN"; "joao@x.com" -> "J". */
private fun iniciais(nome: String): String =
    nome.substringBefore('@').split(' ', '.', '_').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
