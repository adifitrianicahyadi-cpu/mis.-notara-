package id.go.tabalong.inspektorat.notara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.go.tabalong.inspektorat.notara.data.AppSettings
import id.go.tabalong.inspektorat.notara.ui.NotaraViewModel
import id.go.tabalong.inspektorat.notara.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NotaraViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()

    var inst by remember(s) { mutableStateOf(s.inst) }
    var lang by remember(s) { mutableStateOf(s.lang) }
    var aiEndpoint by remember(s) { mutableStateOf(s.aiEndpoint) }
    var aiKey by remember(s) { mutableStateOf(s.aiKey) }
    var aiModel by remember(s) { mutableStateOf(s.aiModel) }
    var sttEndpoint by remember(s) { mutableStateOf(s.sttEndpoint) }

    Scaffold(
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingCard("Identitas Instansi", Icons.Default.AccountBalance) {
                Labeled("Nama Instansi / Unit") {
                    OutlinedTextField(inst, { inst = it }, Modifier.fillMaxWidth(), singleLine = true)
                }
                Labeled("Bahasa Dikte / Transkripsi") {
                    LangDropdown(lang) { lang = it }
                }
            }

            SettingCard("Mesin AI (Notula & Ringkasan)", Icons.Default.AutoAwesome) {
                Labeled("Endpoint API (kosongkan untuk endpoint Anthropic default)") {
                    OutlinedTextField(aiEndpoint, { aiEndpoint = it }, Modifier.fillMaxWidth(),
                        singleLine = true, placeholder = { Text("https://server-internal/v1/messages") })
                }
                Labeled("API Key / Token") {
                    OutlinedTextField(aiKey, { aiKey = it }, Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
                Labeled("Nama Model") {
                    OutlinedTextField(aiModel, { aiModel = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("claude-sonnet-4-6") })
                }
                InfoNote("Untuk penggunaan instansi, arahkan ke proxy/LLM internal agar transkrip tidak keluar jaringan. Kredensial hanya tersimpan lokal di perangkat ini.")
            }

            SettingCard("Speech-to-Text Berkas (Opsional)", Icons.Default.Mic) {
                Labeled("Endpoint STT internal (mis. Whisper)") {
                    OutlinedTextField(sttEndpoint, { sttEndpoint = it }, Modifier.fillMaxWidth(),
                        singleLine = true, placeholder = { Text("https://server-internal/stt/transcribe") })
                }
                InfoNote("Bila diisi, tombol \"Transkripsikan otomatis\" mengirim audio sebagai multipart \"file\"; balasan diharapkan JSON {\"text\":\"…\"}.")
            }

            Button(
                onClick = {
                    vm.saveSettings(AppSettings(inst.trim().ifBlank { s.inst }, lang, aiEndpoint.trim(),
                        aiKey.trim(), aiModel.trim().ifBlank { "claude-sonnet-4-6" }, sttEndpoint.trim()))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { Text("Simpan Pengaturan", fontWeight = FontWeight.Bold) }

            Text("Data tersimpan lokal (Room + DataStore) di perangkat ini. Tidak ada sinkronisasi cloud bawaan.",
                fontSize = 11.sp, color = Muted, modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun SettingCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = SurfaceWhite, shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LineGray), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = TealDeep, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Muted, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun InfoNote(text: String) {
    Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LineGray)) {
        Text(text, Modifier.padding(12.dp), fontSize = 12.sp, color = Muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangDropdown(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val map = listOf("id-ID" to "Bahasa Indonesia (id-ID)", "ms-MY" to "Bahasa Melayu (ms-MY)", "en-US" to "English (en-US)")
    val label = map.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(label, {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            map.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(code); expanded = false })
            }
        }
    }
}
