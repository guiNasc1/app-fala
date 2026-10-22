package br.com.appfala.ui.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/*
 * Paleta: branco + vermelho escuro (vermelho "vinho" de oficina) e uma versão escura.
 * O Material 3 trabalha com papéis de cor (primary, surface, onSurface...) em vez de cores soltas;
 * cada componente sabe qual papel usar, então trocar o esquema troca o app inteiro de uma vez,
 * como trocar o tema do PrimeFaces.
 */
val VermelhoEscuro = Color(0xFF8E1B1B)

private val Claro = lightColorScheme(
    primary = VermelhoEscuro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651),
    onSecondary = Color.White,
    background = Color(0xFFFAF7F6),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFAF7F6),
    onSurface = Color(0xFF201A19),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    error = Color(0xFFBA1A1A),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF5F0F0C),
    primaryContainer = VermelhoEscuro,
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB6),
    onSecondary = Color(0xFF442925),
    background = Color(0xFF151111),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF151111),
    onSurface = Color(0xFFEDE0DE),
    surfaceContainer = Color(0xFF231C1B),
    surfaceContainerLow = Color(0xFF1D1716),
    error = Color(0xFFFFB4AB),
)

// Letras um pouco maiores que o padrão: quem vai usar o app não quer apertar os olhos.
private val Tipografia = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = labelLarge.copy(fontSize = 16.sp),
        titleLarge = titleLarge.copy(fontSize = 22.sp),
    )
}

/**
 * "Variável de contexto" do Compose: qualquer componente abaixo do tema pode ler se o modo
 * escuro está ligado, sem receber isso por parâmetro (parecido com um atributo de request no JSF).
 */
private val LocalModoEscuro = staticCompositionLocalOf { false }

@Composable
fun AppFalaTheme(modoEscuro: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalModoEscuro provides modoEscuro) {
        MaterialTheme(
            colorScheme = if (modoEscuro) Escuro else Claro,
            typography = Tipografia,
            content = content,
        )
    }
}

/** Cores do cabeçalho: faixa vermelha no claro; no escuro, superfície escura com o nome em rosado. */
object CoresCabecalho {
    val fundo: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalModoEscuro.current) MaterialTheme.colorScheme.surfaceContainer else VermelhoEscuro

    val texto: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalModoEscuro.current) MaterialTheme.colorScheme.primary else Color.White
}
