package id.go.tabalong.inspektorat.notara.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.go.tabalong.inspektorat.notara.audio.RecState
import id.go.tabalong.inspektorat.notara.audio.RecordingService
import id.go.tabalong.inspektorat.notara.ui.NotaraViewModel
import id.go.tabalong.inspektorat.notara.ui.fmtDuration
import id.go.tabalong.inspektorat.notara.ui.theme.*

@Composable
fun RecordScreen(
    vm: NotaraViewModel,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current

    // Ikat ke RecordingService untuk membaca status real-time.
    var service by remember { mutableStateOf<RecordingService?>(null) }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? RecordingService.LocalBinder)?.service
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
    }
    DisposableEffect(Unit) {
        context.bindService(
            Intent(context, RecordingService::class.java),
            connection, Context.BIND_AUTO_CREATE
        )
        onDispose { runCatching { context.unbindService(connection) } }
    }

    // Amati StateFlow milik service (null-safe).
    val state by produceState(initialValue = RecState(), service) {
        val flow = service?.state
        if (flow != null) flow.collect { value = it }
    }

    // Tampilkan error & tangani selesai.
    LaunchedEffect(state.error) { state.error?.let { vm.showToast(it) } }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            if (state.savedNoteId != null) onSaved(state.savedNoteId!!) else onClose()
        }
    }

    fun sendAction(action: String) {
        val i = Intent(context, RecordingService::class.java).apply { this.action = action }
        if (action == RecordingService.ACTION_START) ContextCompat.startForegroundService(context, i)
        else context.startService(i)
    }

    val recording = state.recording

    Surface(Modifier.fillMaxSize(), color = Navy) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!recording) onClose() else vm.showToast("Hentikan rekaman dulu") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tutup", tint = Color(0xFFCDD7E6))
                }
                Text("Rekam Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(fmtDuration(state.elapsedSec), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).clip(CircleShape)
                        .background(if (recording) RecRed else Color(0xFF5B6B80)))
                    Spacer(Modifier.width(8.dp))
                    Text(if (recording) "Merekam…" else "Siap merekam",
                        color = Color(0xFFAEB9CC), fontSize = 13.sp)
                }
                Spacer(Modifier.height(28.dp))
                Waveform(amp = state.amp, active = recording)
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF8FA0BB), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tetap merekam walau layar terkunci atau aplikasi ditutup.",
                        color = Color(0xFF8FA0BB), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }

            // Kontrol
            if (!recording) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp, 8.dp, 20.dp, 30.dp),
                    contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(88.dp).clip(CircleShape).background(RecRed),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { sendAction(RecordingService.ACTION_START) },
                            modifier = Modifier.size(88.dp)) {
                            Icon(Icons.Default.Mic, "Mulai", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp, 8.dp, 20.dp, 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { sendAction(RecordingService.ACTION_STOP_DISCARD) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Buang")
                    }
                    Button(
                        onClick = { sendAction(RecordingService.ACTION_STOP_SAVE) },
                        modifier = Modifier.weight(1.4f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Selesai & simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Waveform(amp: Float, active: Boolean) {
    val bars = 28
    Row(
        Modifier.fillMaxWidth().height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { i ->
            val factor = if (active) (0.3f + 0.7f * ((i * 37 % 13) / 13f)) else 0f
            val h by animateFloatAsState(
                targetValue = if (active) (8f + amp * 46f * factor).coerceIn(8f, 54f) else 8f,
                label = "bar$i"
            )
            Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(3.dp)).background(Teal))
        }
    }
}
