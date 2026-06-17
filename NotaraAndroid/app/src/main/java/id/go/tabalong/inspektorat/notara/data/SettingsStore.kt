package id.go.tabalong.inspektorat.notara.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val inst: String = "Inspektorat Daerah Kabupaten Tabalong",
    val lang: String = "id-ID",
    val aiEndpoint: String = "",         // kosong = mode bawaan (perlu key)
    val aiKey: String = "",
    val aiModel: String = "claude-sonnet-4-6",
    val sttEndpoint: String = ""         // endpoint Speech-to-Text internal (opsional)
)

private val Context.dataStore by preferencesDataStore(name = "notara_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val INST = stringPreferencesKey("inst")
        val LANG = stringPreferencesKey("lang")
        val AI_ENDPOINT = stringPreferencesKey("ai_endpoint")
        val AI_KEY = stringPreferencesKey("ai_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val STT_ENDPOINT = stringPreferencesKey("stt_endpoint")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            inst = p[Keys.INST] ?: d.inst,
            lang = p[Keys.LANG] ?: d.lang,
            aiEndpoint = p[Keys.AI_ENDPOINT] ?: d.aiEndpoint,
            aiKey = p[Keys.AI_KEY] ?: d.aiKey,
            aiModel = p[Keys.AI_MODEL] ?: d.aiModel,
            sttEndpoint = p[Keys.STT_ENDPOINT] ?: d.sttEndpoint
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.INST] = s.inst
            p[Keys.LANG] = s.lang
            p[Keys.AI_ENDPOINT] = s.aiEndpoint
            p[Keys.AI_KEY] = s.aiKey
            p[Keys.AI_MODEL] = s.aiModel
            p[Keys.STT_ENDPOINT] = s.sttEndpoint
        }
    }
}
