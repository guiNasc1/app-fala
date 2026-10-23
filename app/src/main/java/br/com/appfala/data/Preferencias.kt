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

    /** Se ligado, ao abrir o app a sessão salva é usada e ele vai direto para o Início. */
    var lembrarDeMim: Boolean
        get() = prefs.getBoolean("lembrar_de_mim", true)
        set(valor) = prefs.edit().putBoolean("lembrar_de_mim", valor).apply()

    /** Último e-mail que entrou, para já vir preenchido no login. Nunca guardamos a senha. */
    var ultimoEmail: String
        get() = prefs.getString("ultimo_email", "").orEmpty()
        set(valor) = prefs.edit().putString("ultimo_email", valor).apply()

    /** Documento sendo ditado, em JSON. Sobrevive ao Android fechar o app (ex.: durante uma ligação). */
    var rascunho: String?
        get() = prefs.getString("rascunho", null)
        set(valor) = prefs.edit().apply { if (valor == null) remove("rascunho") else putString("rascunho", valor) }.apply()
}
