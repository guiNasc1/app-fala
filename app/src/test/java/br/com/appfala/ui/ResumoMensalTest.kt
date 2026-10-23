package br.com.appfala.ui

import br.com.appfala.data.LinhaResumo
import br.com.appfala.data.TipoDocumento
import br.com.appfala.util.reaisCompacto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * Testes das contas do Início. Rodam no PC, sem emulador (botão ▶ ao lado da classe no
 * Android Studio, ou ./gradlew testDebugUnitTest). Em Kotlin, nome de teste entre crases pode
 * ter espaços: vira uma frase que explica o que está sendo garantido.
 */
class ResumoMensalTest {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `documento das 22h30 de 31 de outubro conta em outubro no horario de Brasilia`() {
        val linhas = listOf(
            // No banco fica em UTC: 1º/11 01:30 UTC = 31/10 22:30 em São Paulo.
            LinhaResumo(TipoDocumento.ORCAMENTO, 822_000, "2026-11-01T01:30:00+00:00"),
            LinhaResumo(TipoDocumento.NOTA, 50_000, "2026-11-02T12:00:00+00:00"),
        )

        val meses = somarPorMes(linhas, YearMonth.of(2026, 10), YearMonth.of(2026, 11), saoPaulo)

        assertEquals(2, meses.size)
        assertEquals(822_000L, meses[0].orcamentosCentavos)
        assertEquals(1, meses[0].orcamentosQuantidade)
        assertEquals(0L, meses[1].orcamentosCentavos)
        assertEquals(50_000L, meses[1].notasCentavos)
    }

    @Test
    fun `soma orcamentos e notas separados dentro do mesmo mes`() {
        val linhas = listOf(
            LinhaResumo(TipoDocumento.ORCAMENTO, 822_000, "2026-10-05T15:00:00.123456+00:00"),
            LinhaResumo(TipoDocumento.ORCAMENTO, 18_000, "2026-10-20T15:00:00+00:00"),
            LinhaResumo(TipoDocumento.NOTA, 300_000, "2026-10-21T15:00:00+00:00"),
        )

        val outubro = somarPorMes(linhas, YearMonth.of(2026, 10), YearMonth.of(2026, 10), saoPaulo).single()

        assertEquals(840_000L, outubro.orcamentosCentavos)
        assertEquals(2, outubro.orcamentosQuantidade)
        assertEquals(300_000L, outubro.notasCentavos)
        assertEquals(1, outubro.notasQuantidade)
    }

    @Test
    fun `meses sem documentos entram com zero para o grafico ter sempre seis colunas`() {
        val meses = somarPorMes(emptyList(), YearMonth.of(2026, 5), YearMonth.of(2026, 10), saoPaulo)

        assertEquals(6, meses.size)
        assertEquals(YearMonth.of(2026, 5), meses.first().mes)
        assertEquals(YearMonth.of(2026, 10), meses.last().mes)
        assertTrue(meses.all { it.orcamentosCentavos == 0L && it.notasCentavos == 0L })
    }

    @Test
    fun `divisoes do eixo sao numeros redondos`() {
        // R$ 8.220 -> 0 / 2.500 / 5.000 / 7.500 / 10.000
        assertEquals(listOf(0L, 250_000L, 500_000L, 750_000L, 1_000_000L), divisoesDoEixo(822_000L))
        // R$ 1.000 exato -> 0 / 250 / 500 / 750 / 1.000
        assertEquals(listOf(0L, 25_000L, 50_000L, 75_000L, 100_000L), divisoesDoEixo(100_000L))
        // nada emitido -> eixo de R$ 0 a R$ 1.000
        assertEquals(listOf(0L, 100_000L), divisoesDoEixo(0L))
    }

    @Test
    fun `valor compacto do eixo`() {
        assertEquals("R$ 0", reaisCompacto(0))
        assertEquals("R$ 750", reaisCompacto(75_000))
        assertEquals("R$ 8,2 mil", reaisCompacto(822_000))
        assertEquals("R$ 10 mil", reaisCompacto(1_000_000))
        assertEquals("R$ 1,3 mi", reaisCompacto(125_000_000))
    }
}
