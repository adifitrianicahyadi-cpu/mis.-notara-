package id.go.tabalong.inspektorat.notara

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import id.go.tabalong.inspektorat.notara.ui.NotaraViewModel
import id.go.tabalong.inspektorat.notara.ui.screens.DetailScreen
import id.go.tabalong.inspektorat.notara.ui.screens.LibraryScreen
import id.go.tabalong.inspektorat.notara.ui.screens.RecordScreen
import id.go.tabalong.inspektorat.notara.ui.screens.SettingsScreen
import id.go.tabalong.inspektorat.notara.ui.theme.NotaraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotaraTheme {
                NotaraApp()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NotaraApp() {
    val vm: NotaraViewModel = viewModel()
    val nav = rememberNavController()
    val context = LocalContext.current

    // Toast global
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.clearToast() }
    }

    // Izin mikrofon
    var micGranted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) nav.navigate("record") else vm.showToast("Izin mikrofon diperlukan untuk merekam")
    }

    // Izin notifikasi (Android 13+) agar notifikasi perekaman latar belakang tampil
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Pemilih berkas audio (SAF)
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importAudio(it) { id -> id?.let { nav.navigate("detail/$id") } } }
    }

    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                vm = vm,
                onOpenDetail = { id -> nav.navigate("detail/$id") },
                onRecord = {
                    if (hasMicPermission(context)) nav.navigate("record")
                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onUpload = { pickLauncher.launch(arrayOf("audio/*")) },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable("record") {
            RecordScreen(
                vm = vm,
                onClose = { nav.popBackStack() },
                onSaved = { id ->
                    nav.popBackStack()
                    if (id.isNotBlank()) nav.navigate("detail/$id")
                }
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(vm = vm, noteId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

private fun hasMicPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
