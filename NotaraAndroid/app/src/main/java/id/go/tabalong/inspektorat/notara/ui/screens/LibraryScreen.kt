package id.go.tabalong.inspektorat.notara.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.go.tabalong.inspektorat.notara.data.Note
import id.go.tabalong.inspektorat.notara.ui.*
import id.go.tabalong.inspektorat.notara.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: NotaraViewModel,
    onOpenDetail: (String) -> Unit,
    onRecord: () -> Unit,
    onUpload: () -> Unit,
    onSettings: () -> Unit
) {
    val allNotes by vm.notes.collectAsState()
    val settings by vm.settings.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val list = remember(allNotes, query, filter) { vm.filtered(allNotes) }

    Scaffold(
        containerColor = BgGray,
        topBar = {
            Surface(color = Navy, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(TealDeep),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Mic, null, tint = Color.White) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NOTARA", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text(
                            settings.inst, color = Color(0xFFAEB9CC), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onUpload) {
                        Icon(Icons.Default.UploadFile, "Unggah", tint = Color(0xFFCDD7E6))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Pengaturan", tint = Color(0xFFCDD7E6))
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecord,
                containerColor = RecRed,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Mic, null) },
                text = { Text("Rekam", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Pencarian
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 4.dp),
                placeholder = { Text("Cari judul, transkrip, unit, peserta…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            // Filter chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 4.dp, 12.dp, 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChips(filter, vm::setFilter)
            }
            if (list.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { note ->
                        NoteCard(note) { onOpenDetail(note.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(current: LibFilter, onSelect: (LibFilter) -> Unit) {
    data class F(val f: LibFilter, val label: String)
    val items = listOf(
        F(LibFilter.ALL, "Semua"),
        F(LibFilter.RECORD, "Rekaman"),
        F(LibFilter.UPLOAD, "Unggahan"),
        F(LibFilter.DONE, "Sudah dinotulenkan"),
        F(LibFilter.DRAFT, "Draf")
    )
    items.forEach { it2 ->
        FilterChip(
            selected = current == it2.f,
            onClick = { onSelect(it2.f) },
            label = { Text(it2.label, fontSize = 13.sp) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val isUpload = note.type == "upload"
    val ext = note.fileName.substringAfterLast('.', "").uppercase()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, LineGray),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isUpload) TealSoft else Color(0xFFFDECEB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUpload) Icons.Default.LibraryMusic else Icons.Default.Mic,
                    null, tint = if (isUpload) TealDeep else RecRed
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fmtDate(note.createdAt), fontSize = 12.sp, color = Muted)
                    Text("· ${fmtDuration(note.durationSec)}", fontSize = 12.sp, color = Muted)
                    if (ext.isNotBlank()) Badge2(ext, if (ext == "AAC" || ext == "M4A") Color(0xFFEEF2FB) else Color(0xFFF1F5F9), Navy2)
                    Badge2(if (note.hasNotes) "Notula siap" else "Draf",
                        if (note.hasNotes) Color(0xFFE7F6EE) else Color(0xFFFDF3E2),
                        if (note.hasNotes) Color(0xFF1F9D66) else Color(0xFFB07A14))
                }
                if (note.transcript.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(note.transcript, fontSize = 13.sp, color = Muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Badge2(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(7.dp, 2.dp)) {
        Text(text, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(74.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceWhite)
                .border(1.dp, LineGray, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.GraphicEq, null, tint = Muted, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Belum ada catatan", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Mulai dengan menekan tombol Rekam, atau unggah berkas .aac dari ikon unggah di kanan atas.",
            fontSize = 14.sp, color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
