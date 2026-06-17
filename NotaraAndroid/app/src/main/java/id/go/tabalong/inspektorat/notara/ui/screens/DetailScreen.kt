package id.go.tabalong.inspektorat.notara.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import id.go.tabalong.inspektorat.notara.ai.AiMode
import id.go.tabalong.inspektorat.notara.audio.AudioPlayer
import id.go.tabalong.inspektorat.notara.audio.SpeechTranscriber
import id.go.tabalong.inspektorat.notara.data.Note
import id.go.tabalong.inspektorat.notara.ui.NotaraViewModel
import id.go.tabalong.inspektorat.notara.ui.fmtDurationMs
import id.go.tabalong.inspektorat.notara.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: NotaraViewModel,
    noteId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allNotes by vm.notes.collectAsState()
    val settings by vm.settings.collectAsState()
    val busy by vm.busy.collectAsState()
    val note = remember(allNotes, noteId) { allNotes.firstOrNull { it.id == noteId } }

    if (note == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Catatan tidak ditemukan"); }
        return
    }

    // State editable
    var title by remember(note.id) { mutableStateOf(note.title) }
    var unit by remember(note.id) { mutableStateOf(note.unit) }
    var attendees by remember(note.id) { mutableStateOf(note.attendees) }
    var tags by remember(note.id) { mutableStateOf(note.tags) }
    var classification by remember(note.id) { mutableStateOf(note.classification) }
    var transcript by remember(note.id) { mutableStateOf(note.transcript) }
    // sinkronkan transkrip bila diperbarui dari STT/AI di luar
    LaunchedEffect(note.transcript) { transcript = note.transcript }

    var selectedMode by remember { mutableStateOf(AiMode.RINGKASAN) }
    val aiText = when (selectedMode) {
        AiMode.RINGKASAN -> note.aiRingkasan
        AiMode.NOTULEN -> note.aiNotulen
        AiMode.POIN -> note.aiPoin
        AiMode.TINDAK -> note.aiTindak
        AiMode.RAPI -> note.aiRapi
    }

    // Pemutar
    val player = remember { AudioPlayer() }
    var durationMs by remember { mutableStateOf(note.durationSec * 1000) }
    var positionMs by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(note.id) {
        player.load(note.audioPath, onPrepared = { durationMs = it }, onCompletion = { playing = false; positionMs = 0 })
    }
    LaunchedEffect(playing) {
        while (playing) { positionMs = player.currentPosition(); delay(250) }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Dikte
    val transcriber = remember { SpeechTranscriber(context) }
    var dictating by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { transcriber.stop() } }

    var showDelete by remember { mutableStateOf(false) }

    fun persist(updated: Note) = vm.updateNote(updated)
    fun currentEdited() = note.copy(
        title = title.ifBlank { note.title }, unit = unit, attendees = attendees,
        tags = tags, classification = classification, transcript = transcript
    )

    Scaffold(
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { player.pause(); transcriber.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val f = vm.exportMarkdown(currentEdited())
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Ekspor / bagikan"))
                    }) { Icon(Icons.Default.IosShare, "Ekspor") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, "Hapus") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ----- Pemutar -----
            Surface(color = Navy, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Teal),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (playing) { player.pause(); playing = false }
                            else { player.play(); playing = true }
                        }, modifier = Modifier.size(46.dp)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Putar", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(note.fileName.ifBlank { note.title }, color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${fmtDurationMs(positionMs)} / ${fmtDurationMs(durationMs)}",
                            color = Color(0xFFA9B6CC), fontSize = 11.sp)
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { v -> val target = (v * durationMs).toInt(); positionMs = target; player.seekTo(target) },
                            colors = SliderDefaults.colors(thumbColor = Teal, activeTrackColor = Teal)
                        )
                    }
                }
            }

            // ----- Metadata -----
            PanelCard("METADATA", Icons.Default.Description) {
                Field("Judul", title) { title = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { Field("Unit / OPD", unit) { unit = it } }
                    Box(Modifier.weight(1f)) { ClassificationDropdown(classification) { classification = it } }
                }
                Field("Peserta / Narasumber", attendees) { attendees = it }
                Field("Tag", tags) { tags = it }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { persist(currentEdited()); vm.showToast("Metadata tersimpan") },
                    modifier = Modifier.fillMaxWidth()) { Text("Simpan perubahan") }
            }

            // ----- Transkrip -----
            PanelCard("TRANSKRIP", Icons.Default.Subject) {
                OutlinedTextField(
                    value = transcript + if (dictating && partial.isNotBlank()) " $partial" else "",
                    onValueChange = { transcript = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("Tulis atau tempel transkrip…") }
                )
                Spacer(Modifier.height(10.dp))
                FlowRowButtons {
                    OutlinedButton(onClick = { persist(currentEdited()); vm.showToast("Transkrip tersimpan") }) {
                        Text("Simpan transkrip")
                    }
                    // Dikte langsung
                    Button(
                        onClick = {
                            if (!dictating) {
                                dictating = true; partial = ""
                                transcriber.start(
                                    languageTag = settings.lang,
                                    onPartial = { partial = it },
                                    onFinal = { f -> transcript = (transcript.trim() + " " + f).trim(); partial = "" },
                                    onError = { msg -> vm.showToast(msg); dictating = false }
                                )
                            } else {
                                dictating = false; transcriber.stop()
                                persist(currentEdited())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (dictating) RecRed else TealDeep)
                    ) {
                        Icon(if (dictating) Icons.Default.Stop else Icons.Default.Mic, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (dictating) "Hentikan dikte" else "Dikte langsung")
                    }
                    if (note.type == "upload") {
                        Button(
                            onClick = { vm.autoTranscribe(note) {} },
                            enabled = busy != "stt",
                            colors = ButtonDefaults.buttonColors(containerColor = Navy2)
                        ) {
                            if (busy == "stt") { CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                            Text("Transkripsikan otomatis")
                        }
                    }
                }
            }

            // ----- AI -----
            PanelCard("OLAH DENGAN AI", Icons.Default.AutoAwesome) {
                FlowRowButtons {
                    AiMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.runAi(currentEdited(), selectedMode) {} },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    if (busy == selectedMode.key) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("Memproses…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Hasilkan ${selectedMode.label}")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = if (aiText.isBlank()) Color(0xFFF8FAFC) else TealSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (aiText.isBlank()) LineGray else Color(0xFFC8ECE6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        aiText.ifBlank { "Pilih jenis olahan lalu tekan tombol di atas. Hasil tersimpan otomatis dan ikut diekspor." },
                        Modifier.padding(15.dp),
                        fontSize = 14.sp,
                        color = if (aiText.isBlank()) Muted else Color(0xFF10463F)
                    )
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus catatan?") },
            text = { Text("Audio, transkrip, dan notula akan dihapus permanen dari perangkat ini.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false; player.release(); transcriber.stop()
                    vm.deleteNote(note); onBack()
                }) { Text("Hapus", color = RecRed) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun PanelCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
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
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            singleLine = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassificationDropdown(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Biasa", "Terbatas", "Rahasia")
    Column(Modifier.padding(bottom = 10.dp)) {
        Text("Klasifikasi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = value, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
