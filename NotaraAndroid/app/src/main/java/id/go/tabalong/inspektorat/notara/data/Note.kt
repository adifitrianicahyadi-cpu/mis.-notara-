package id.go.tabalong.inspektorat.notara.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Satu catatan audio: hasil rekaman ("record") atau berkas unggahan ("upload", mis. .aac).
 * Audio disimpan sebagai berkas di penyimpanan internal aplikasi; di sini hanya path-nya.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val type: String,                 // "record" | "upload"
    val title: String,
    val createdAt: Long,
    val durationSec: Int = 0,
    val audioPath: String,            // path absolut berkas audio
    val fileName: String = "",
    val mimeType: String = "audio/aac",
    val transcript: String = "",
    val unit: String = "",
    val attendees: String = "",
    val tags: String = "",
    val classification: String = "Biasa",
    // Hasil olahan AI per jenis
    val aiRingkasan: String = "",
    val aiNotulen: String = "",
    val aiPoin: String = "",
    val aiTindak: String = "",
    val aiRapi: String = ""
) {
    val hasNotes: Boolean
        get() = listOf(aiRingkasan, aiNotulen, aiPoin, aiTindak, aiRapi).any { it.isNotBlank() }
}
