package id.go.tabalong.inspektorat.notara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF14233F)
val Navy2 = Color(0xFF1D3358)
val Teal = Color(0xFF15B8A6)
val TealDeep = Color(0xFF0F766E)
val TealSoft = Color(0xFFE6F7F4)
val RecRed = Color(0xFFE2574C)
val BgGray = Color(0xFFF3F5F9)
val SurfaceWhite = Color(0xFFFFFFFF)
val InkDark = Color(0xFF16202F)
val Muted = Color(0xFF5B6B80)
val LineGray = Color(0xFFE4E9F1)

private val NotaraColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = TealDeep,
    onSecondary = Color.White,
    tertiary = Teal,
    background = BgGray,
    onBackground = InkDark,
    surface = SurfaceWhite,
    onSurface = InkDark,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Muted,
    error = RecRed,
    outline = Color(0xFFD3DBE7)
)

private val NotaraTypography = Typography()

@Composable
fun NotaraTheme(content: @Composable () -> Unit) {
    // Mode terang dipakai untuk konsistensi dokumen resmi (abaikan dark system).
    @Suppress("UNUSED_VARIABLE")
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = NotaraColors,
        typography = NotaraTypography,
        content = content
    )
}
