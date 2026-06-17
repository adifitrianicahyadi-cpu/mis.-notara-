package id.go.tabalong.inspektorat.notara.ai

import id.go.tabalong.inspektorat.notara.data.AppSettings
import id.go.tabalong.inspektorat.notara.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class AiMode(val key: String, val label: String) {
    RINGKASAN("ringkasan", "Ringkasan"),
    NOTULEN("notulen", "Notulen Rapat"),
    POIN("poin", "Poin Penting"),
    TINDAK("tindak", "Tindak Lanjut"),
    RAPI("rapi", "Rapikan Transkrip")
}

class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Hasilkan teks olahan AI dari transkrip catatan. */
    suspend fun generate(note: Note, mode: AiMode, s: AppSettings): String = withContext(Dispatchers.IO) {
        require(note.transcript.isNotBlank()) { "Transkrip masih kosong" }

        val endpoint = s.aiEndpoint.ifBlank { "https://api.anthropic.com/v1/messages" }
        val prompt = buildPrompt(note, mode)

        val payload = JSONObject().apply {
            put("model", s.aiModel.ifBlank { "claude-sonnet-4-6" })
            put("max_tokens", 1500)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val builder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")

        if (s.aiKey.isNotBlank()) {
            builder.header("x-api-key", s.aiKey)
            builder.header("anthropic-version", "2023-06-01")
        }

        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${body.take(200)}")
            parseText(body)
        }
    }

    /** Kirim berkas audio ke endpoint STT internal (mis. Whisper). Balasan diharapkan JSON {"text": "..."}. */
    suspend fun transcribeFile(file: File, mimeType: String, s: AppSettings): String =
        withContext(Dispatchers.IO) {
            require(s.sttEndpoint.isNotBlank()) { "Endpoint STT belum diisi di Pengaturan" }
            val media = (mimeType.ifBlank { "audio/aac" }).toMediaType()
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("language", "id")
                .addFormDataPart("file", file.name, file.asRequestBody(media))
                .build()
            val req = Request.Builder().url(s.sttEndpoint).post(multipart).build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${body.take(200)}")
                val json = runCatching { JSONObject(body) }.getOrNull()
                json?.optString("text").orEmpty().ifBlank {
                    json?.optString("transcript").orEmpty()
                }
            }
        }

    /** Mendukung format respons Anthropic (content[]) maupun OpenAI (choices[]). */
    private fun parseText(body: String): String {
        val json = JSONObject(body)
        json.optJSONArray("content")?.let { arr ->
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("text")?.let { if (it.isNotBlank()) sb.appendLine(it) }
            }
            if (sb.isNotBlank()) return sb.toString().trim()
        }
        json.optJSONArray("choices")?.optJSONObject(0)?.let { ch ->
            ch.optJSONObject("message")?.optString("content")?.let { if (it.isNotBlank()) return it.trim() }
            ch.optString("text").let { if (it.isNotBlank()) return it.trim() }
        }
        return json.optString("text").ifBlank { body }
    }

    private fun buildPrompt(note: Note, mode: AiMode): String {
        val ctx = "Konteks: Catatan audio internal \"${note.title}\". " +
                "Unit/OPD: ${note.unit.ifBlank { "-" }}. " +
                "Peserta/Narasumber: ${note.attendees.ifBlank { "-" }}."
        val base = "$ctx\n\nTranskrip:\n\"\"\"${note.transcript}\"\"\"\n\n"
        val guard = "Gunakan hanya informasi dari transkrip; jangan mengarang fakta. " +
                "Jika data tidak ada, tulis \"tidak disebutkan\". " +
                "Tulis dalam Bahasa Indonesia formal yang ringkas dan profesional."
        return base + when (mode) {
            AiMode.RINGKASAN ->
                "Buat ringkasan eksekutif 4–6 kalimat yang menangkap inti pembahasan. $guard"
            AiMode.NOTULEN ->
                "Susun NOTULEN RAPAT terstruktur dengan bagian: (1) Pokok Pembahasan, " +
                "(2) Uraian Diskusi (poin bernomor), (3) Keputusan/Kesepakatan, (4) Tindak Lanjut. $guard"
            AiMode.POIN ->
                "Ekstrak poin-poin penting sebagai daftar bullet padat (maksimal 10). $guard"
            AiMode.TINDAK ->
                "Buat DAFTAR TINDAK LANJUT (action items) dengan format: " +
                "\"- [aktivitas] — [penanggung jawab] — [target waktu]\". " +
                "Bila penanggung jawab/waktu tidak disebut, tulis \"(belum ditetapkan)\". $guard"
            AiMode.RAPI ->
                "Rapikan transkrip menjadi paragraf yang enak dibaca: perbaiki tanda baca dan " +
                "kapitalisasi, hilangkan kata pengisi (eh, anu, gitu) TANPA mengubah substansi " +
                "atau menambah informasi. $guard"
        }
    }
}
