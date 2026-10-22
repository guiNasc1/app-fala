package br.com.appfala.data

import android.content.Context

/**
 * Preferências guardadas no próprio aparelho (SharedPreferences: um arquivo chave/valor privado do app,
 * parecido com um java.util.Properties que sobrevive ao fechar o app).
 */
class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    // Propriedade com get/set customizados: por fora parece um campo (prefs.modoEscuro = true),
    // por dentro é o par getter/setter que em Java você escreveria à mão.
    var modoEscuro: Boolean
        get() = prefs.getBoolean("modo_escuro", false)
        set(valor) = prefs.edit().putBoolean("modo_escuro", valor).apply()
}
