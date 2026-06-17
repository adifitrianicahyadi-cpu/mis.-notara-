package id.go.tabalong.inspektorat.notara.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.go.tabalong.inspektorat.notara.ai.AiClient
import id.go.tabalong.inspektorat.notara.ai.AiMode
import id.go.tabalong.inspektorat.notara.data.AppSettings
import id.go.tabalong.inspektorat.notara.data.NotaraDatabase
import id.go.tabalong.inspektorat.notara.data.Note
import id.go.tabalong.inspektorat.notara.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class LibFilter { ALL, RECORD, UPLOAD, DONE, DRAFT }

class NotaraViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = NotaraDatabase.get(app).noteDao()
    private val settingsStore = SettingsStore(app)
    private val ai = AiClient()

    val notes: StateFlow<List<Note>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow(LibFilter.ALL)
    val filter: StateFlow<LibFilter> = _filter.asStateFlow()

    // Status pemrosesan AI per (idCatatan + mode)
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun showToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = null }

    fun setQuery(q: String) { _query.value = q }
    fun setFilter(f: LibFilter) { _filter.value = f }

    fun filtered(all: List<Note>): List<Note> {
        val q = _query.value.trim().lowercase()
        return all.filter { n ->
            when (_filter.value) {
                LibFilter.RECORD -> n.type == "record"
                LibFilter.UPLOAD -> n.type == "upload"
                LibFilter.DONE -> n.hasNotes
                LibFilter.DRAFT -> !n.hasNotes
                LibFilter.ALL -> true
            }
        }.filter { n ->
            q.isBlank() || listOf(n.title, n.transcript, n.unit, n.attendees, n.tags)
                .any { it.lowercase().contains(q) }
        }
    }

    fun noteById(id: String): Note? = notes.value.firstOrNull { it.id == id }

    // ---------- Simpan rekaman ----------
    fun saveRecording(file: File, durationSec: Int, transcript: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val note = Note(
                id = "n" + UUID.randomUUID().toString().take(10),
                type = "record",
                title = "Rekaman " + formatStamp(now),
                createdAt = now,
                durationSec = durationSec,
                audioPath = file.absolutePath,
                fileName = file.name,
                mimeType = "audio/aac",
                transcript = transcript
            )
            dao.upsert(note)
            showToast("Rekaman tersimpan")
        }
    }

    // ---------- Impor berkas (.aac dll) ----------
    fun importAudio(uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                try {
                    val name = queryName(uri) ?: "audio_${System.currentTimeMillis()}.aac"
                    val dir = File(ctx.filesDir, "recordings").apply { mkdirs() }
                    val dest = File(dir, "${System.currentTimeMillis()}_$name")
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                    val durationSec = readDurationSec(dest.absolutePath)
                    val now = System.currentTimeMillis()
                    val note = Note(
                        id = "n" + UUID.randomUUID().toString().take(10),
                        type = "upload",
                        title = name.substringBeforeLast('.'),
                        createdAt = now,
                        durationSec = durationSec,
                        audioPath = dest.absolutePath,
                        fileName = name,
                        mimeType = ctx.contentResolver.getType(uri) ?: "audio/aac"
                    )
                    dao.upsert(note)
                    note.id
                } catch (e: Exception) {
                    null
                }
            }
            if (result != null) showToast("Berkas terunggah") else showToast("Gagal mengimpor berkas")
            onDone(result)
        }
    }

    private fun queryName(uri: Uri): String? {
        val ctx = getApplication<Application>()
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) { null }
    }

    private fun readDurationSec(path: String): Int = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(path)
        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        mmr.release()
        (ms / 1000).toInt()
    } catch (e: Exception) { 0 }

    // ---------- Edit catatan ----------
    fun updateNote(note: Note) {
        viewModelScope.launch { dao.upsert(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { File(note.audioPath).delete() } }
            dao.deleteById(note.id)
            showToast("Catatan dihapus")
        }
    }

    // ---------- AI ----------
    fun runAi(note: Note, mode: AiMode, onResult: (Note) -> Unit) {
        if (note.transcript.isBlank()) { showToast("Isi transkrip dulu sebelum diolah AI"); return }
        viewModelScope.launch {
            _busy.value = mode.key
            try {
                val text = ai.generate(note, mode, settings.value)
                val updated = when (mode) {
                    AiMode.RINGKASAN -> note.copy(aiRingkasan = text)
                    AiMode.NOTULEN -> note.copy(aiNotulen = text)
                    AiMode.POIN -> note.copy(aiPoin = text)
                    AiMode.TINDAK -> note.copy(aiTindak = text)
                    AiMode.RAPI -> note.copy(aiRapi = text)
                }
                dao.upsert(updated)
                onResult(updated)
                showToast("${mode.label} selesai")
            } catch (e: Exception) {
                showToast("Gagal AI: ${e.message ?: "tidak diketahui"}")
            } finally {
                _busy.value = null
            }
        }
    }

    fun autoTranscribe(note: Note, onResult: (Note) -> Unit) {
        if (settings.value.sttEndpoint.isBlank()) { showToast("Endpoint STT belum diisi (Pengaturan)"); return }
        viewModelScope.launch {
            _busy.value = "stt"
            try {
                val text = ai.transcribeFile(File(note.audioPath), note.mimeType, settings.value)
                if (text.isBlank()) { showToast("Endpoint tidak mengembalikan teks") }
                else {
                    val updated = note.copy(transcript = text)
                    dao.upsert(updated); onResult(updated); showToast("Transkripsi selesai")
                }
            } catch (e: Exception) {
                showToast("Gagal STT: ${e.message ?: "tidak diketahui"}")
            } finally { _busy.value = null }
        }
    }

    // ---------- Pengaturan ----------
    fun saveSettings(s: AppSettings) {
        viewModelScope.launch { settingsStore.save(s); showToast("Pengaturan tersimpan") }
    }

    // ---------- Ekspor ----------
    fun exportMarkdown(note: Note): File {
        val ctx = getApplication<Application>()
        val dir = File(ctx.filesDir, "exports").apply { mkdirs() }
        val sb = StringBuilder()
        sb.appendLine("# ${note.title}\n")
        sb.appendLine("Instansi: ${settings.value.inst}")
        sb.appendLine("Tanggal: ${formatStamp(note.createdAt)}")
        sb.appendLine("Unit/OPD: ${note.unit.ifBlank { "-" }}")
        sb.appendLine("Peserta/Narasumber: ${note.attendees.ifBlank { "-" }}")
        sb.appendLine("Klasifikasi: ${note.classification}")
        sb.appendLine("Durasi: ${note.durationSec / 60}m ${note.durationSec % 60}d")
        sb.appendLine("Tag: ${note.tags.ifBlank { "-" }}\n")
        sb.appendLine("## Transkrip\n\n${note.transcript.ifBlank { "(belum ada)" }}\n")
        if (note.aiRingkasan.isNotBlank()) sb.appendLine("## Ringkasan\n\n${note.aiRingkasan}\n")
        if (note.aiNotulen.isNotBlank()) sb.appendLine("## Notulen Rapat\n\n${note.aiNotulen}\n")
        if (note.aiPoin.isNotBlank()) sb.appendLine("## Poin Penting\n\n${note.aiPoin}\n")
        if (note.aiTindak.isNotBlank()) sb.appendLine("## Tindak Lanjut\n\n${note.aiTindak}\n")
        if (note.aiRapi.isNotBlank()) sb.appendLine("## Transkrip Rapi\n\n${note.aiRapi}\n")
        val safe = note.title.replace(Regex("[^\\w\\d -]"), "_").ifBlank { "catatan" }
        val out = File(dir, "$safe.md")
        out.writeText(sb.toString())
        return out
    }

    private fun formatStamp(t: Long): String {
        val d = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale("id", "ID"))
        return d.format(java.util.Date(t))
    }
}
