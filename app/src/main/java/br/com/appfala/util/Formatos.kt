package br.com.appfala.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * CNPJ numérico e alfanumérico (formato novo da Receita, a partir de jul/2026):
 * 12 posições [A-Z0-9] + 2 dígitos verificadores, cada caractere vale (código ASCII - 48).
 * Veio do projeto NotaServico.
 */
object Cnpj {
    const val MASCARA = "##.###.###/####-##"

    fun limpar(s: String) = s.uppercase().filter { it in '0'..'9' || it in 'A'..'Z' }.take(14)

    fun valido(entrada: String): Boolean {
        val c = limpar(entrada)
        if (c.length != 14 || !c[12].isDigit() || !c[13].isDigit()) return false
        if (c.all { it == c[0] }) return false
        val d1 = digito(c.substring(0, 12))
        val d2 = digito(c.substring(0, 12) + d1)
        return c[12].digitToInt() == d1 && c[13].digitToInt() == d2
    }

    private fun digito(base: String): Int {
        var peso = 2
        var soma = 0
        for (i in base.indices.reversed()) {
            soma += (base[i].code - 48) * peso
            peso = if (peso == 9) 2 else peso + 1
        }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }

    fun formatar(c: String) = aplicarMascara(limpar(c), MASCARA)

    fun somenteNumeros(c: String) = c.all { it.isDigit() }
}

object Mascaras {
    const val CEP = "#####-###"

    fun telefone(digitos: String) = if (digitos.length > 10) "(##) #####-####" else "(##) ####-####"
}

fun soDigitos(s: String, max: Int) = s.filter { it.isDigit() }.take(max)

/** 103000 -> "1.030,00" (veio do NotaServico). */
fun formatarValor(centavos: Long): String {
    val a = abs(centavos)
    val reais = (a / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    val cents = (a % 100).toString().padStart(2, '0')
    return (if (centavos < 0) "-" else "") + "$reais,$cents"
}

/** 103000 -> "R$ 1.030,00" */
fun reais(centavos: Long) = "R$ " + formatarValor(centavos)

/** 2.0 -> "2"; 1.5 -> "1,5" */
fun formatarQuantidade(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString()
    else BigDecimal(q).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/** Encaixa os caracteres de [raw] nos '#' da máscara, parando quando o raw acaba. */
fun aplicarMascara(raw: String, mascara: String): String {
    val sb = StringBuilder()
    var i = 0
    for (m in mascara) {
        if (i >= raw.length) break
        if (m == '#') sb.append(raw[i++]) else sb.append(m)
    }
    while (i < raw.length) sb.append(raw[i++])
    return sb.toString()
}

/**
 * Máscara só de exibição: o estado guarda o valor cru (ex.: "19806803000166") e o campo
 * mostra "19.806.803/0001-66". Equivale ao p:inputMask do PrimeFaces, mas sem sujar o valor.
 */
class MascaraTransformation(private val mascara: (String) -> String) : VisualTransformation {
    constructor(fixa: String) : this({ _: String -> fixa })

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatado = aplicarMascara(raw, mascara(raw))
        // posição de cada caractere cru dentro do texto formatado
        val posicoes = IntArray(raw.length)
        var ri = 0
        formatado.forEachIndexed { idx, ch ->
            if (ri < raw.length && ch == raw[ri]) posicoes[ri++] = idx
        }
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                if (offset >= raw.length) formatado.length else posicoes[offset]

            override fun transformedToOriginal(offset: Int) = posicoes.count { it < offset }
        }
        return TransformedText(AnnotatedString(formatado), mapa)
    }
}
