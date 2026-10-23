package br.com.appfala.voz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Fala → texto no próprio celular, com o reconhecedor de voz do Android (o mesmo do teclado do Google).
 * Só o texto final vai para a IA. Precisa ser criado e usado na thread principal.
 *
 * Os parâmetros são funções (lambdas): o equivalente a passar um listener anônimo em Java.
 */
class ReconhecedorDeFala(
    contexto: Context,
    private val aoComecar: () -> Unit,
    private val aoOuvirParcial: (String) -> Unit,
    private val aoVolume: (Float) -> Unit,
    private val aoTerminar: (String) -> Unit,
    private val aoFalhar: (String) -> Unit,
) {
    private val reconhecedor: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(contexto)) SpeechRecognizer.createSpeechRecognizer(contexto) else null

    /** false quando o celular não tem serviço de reconhecimento de voz (aí só dá para digitar). */
    val disponivel: Boolean get() = reconhecedor != null

    init {
        reconhecedor?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = aoComecar()
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = aoVolume(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                primeiroTexto(partialResults)?.let(aoOuvirParcial)
            }

            override fun onResults(results: Bundle?) {
                val texto = primeiroTexto(results)
                if (texto.isNullOrBlank()) aoFalhar("Não entendi. Toque no microfone e fale de novo.")
                else aoTerminar(texto)
            }

            override fun onError(error: Int) = aoFalhar(mensagem(error))
        })
    }

    fun ouvir() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Espera um pouco mais antes de encerrar: o mecânico pensa no preço no meio da frase.
            // (Alguns celulares ignoram esses dois pedidos.)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        reconhecedor?.startListening(intent)
    }

    /** Encerra a escuta e entrega o que já foi ouvido. */
    fun parar() {
        reconhecedor?.stopListening()
    }

    fun liberar() {
        reconhecedor?.destroy()
    }

    private fun primeiroTexto(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    /** Texto vazio = parou sem nada a avisar (ex.: a pessoa tocou em parar antes de falar). */
    private fun mensagem(erro: Int): String = when (erro) {
        SpeechRecognizer.ERROR_CLIENT -> ""
        SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi. Toque no microfone e fale de novo."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nada. Toque no microfone e fale."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "O reconhecimento de voz precisa de internet. Confira a conexão."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "O app não tem permissão para usar o microfone."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O microfone está ocupado. Tente de novo."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Este celular não reconhece fala em português. Use o botão Digitar."
        else -> "O microfone falhou (erro $erro). Tente de novo ou use o botão Digitar."
    }
}
