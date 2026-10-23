package br.com.appfala.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Componentes reaproveitados pelas telas, como os composite components (cc:) do JSF.
 * Cada um é só uma função @Composable com parâmetros; os que têm valor padrão (= ...) podem
 * ser omitidos na chamada, e os nomeados (rotulo = "...") podem vir em qualquer ordem.
 */

/** Bloco com título, equivalente a um p:panel/p:fieldset. */
@Composable
fun Secao(titulo: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                titulo.uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
fun Linha(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

/** OutlinedTextField com os padrões que o app usa em quase todo campo (o p:inputText da casa). */
@Composable
fun Campo(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    maiusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    mascara: VisualTransformation = VisualTransformation.None,
    habilitado: Boolean = true,
    dica: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = habilitado,
        visualTransformation = mascara,
        supportingText = dica?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = maiusculas, keyboardType = teclado),
    )
}

/** Botão principal grande, com rodinha de "carregando" no lugar do texto enquanto espera a rede. */
@Composable
fun BotaoGrande(texto: String, aoClicar: () -> Unit, carregando: Boolean = false, habilitado: Boolean = true) {
    Button(
        onClick = aoClicar,
        enabled = habilitado && !carregando,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (carregando) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        else Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Caixa de marcar com o texto clicável junto (a linha toda liga/desliga, alvo grande para o dedo). */
@Composable
fun CaixaMarcar(texto: String, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = marcado, onValueChange = aoMudar, role = Role.Checkbox)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcado, onCheckedChange = null)
        Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun TextoErro(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
}
