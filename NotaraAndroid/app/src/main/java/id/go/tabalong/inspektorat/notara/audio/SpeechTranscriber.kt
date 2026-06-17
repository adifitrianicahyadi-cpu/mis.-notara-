package id.go.tabalong.inspektorat.notara.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Dikte langsung (mengubah suara mikrofon menjadi teks) memakai SpeechRecognizer bawaan Android.
 * Catatan: mesin pengenal (mis. Google) menguasai mikrofon, jadi tidak dijalankan bersamaan
 * dengan AudioRecorder. Untuk transkrip berkas .aac unggahan, gunakan endpoint STT internal.
 */
class SpeechTranscriber(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        languageTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAvailable()) { onError("Pengenalan suara tidak tersedia di perangkat ini"); return }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { onPartial(it) }
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onFinal(text)
                    // Lanjutkan mendengarkan untuk dikte berkelanjutan
                    if (isListening) restart(languageTag, onPartial, onFinal, onError)
                }
                override fun onError(error: Int) {
                    // Error umum (mis. timeout/no match) -> lanjutkan mendengarkan
                    if (isListening &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH ||
                         error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        restart(languageTag, onPartial, onFinal, onError)
                    } else if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        onError("Kode kesalahan pengenalan: $error")
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        isListening = true
        recognizer?.startListening(buildIntent(languageTag))
    }

    private fun restart(
        lang: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try { recognizer?.startListening(buildIntent(lang)) } catch (_: Exception) {}
    }

    private fun buildIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    fun stop() {
        isListening = false
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
}
