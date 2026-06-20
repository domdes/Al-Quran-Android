package com.asyuhada.quran.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- COLOR TOKENS (Emerald Green & Amber Accent) ---

val PrimaryEmerald = Color(0xFF059669)
val PrimaryHover = Color(0xFF047857)
val PrimaryLight = Color(0xFFECFDF5)

val SecondaryTeal = Color(0xFF0F766E)
val AccentAmber = Color(0xFFFBBF24)

// Neutral Colors
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF161616)
val TextSecondaryLight = Color(0xFF525252)
val TextTertiaryLight = Color(0xFF8D8D8D)
val BorderLight = Color(0xFFE0E0E0)

// Dark Theme Alternates
val DarkBg = Color(0xFF090D16)
val DarkSurface = Color(0xFF111827)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextSecondaryDark = Color(0xFF9CA3AF)
val TextTertiaryDark = Color(0xFF6B7280)
val BorderDark = Color(0xFF374151)

// Semantic
val SuccessGreen = Color(0xFF24A148)
val ErrorRed = Color(0xFFDA1E28)
val WarningYellow = Color(0xFFF1C21B)

// --- BOOKMARK SLOT COLORS (from Portal BOOKMARK_SLOTS) ---
object BookmarkColors {
    val Merah = Color(0xFFEF4444)
    val Oranye = Color(0xFFF97316)
    val Kuning = Color(0xFFEAB308)
    val Hijau = Color(0xFF22C55E)
    val Toska = Color(0xFF14B8A6)
    val Biru = Color(0xFF3B82F6)
    val Indigo = Color(0xFF6366F1)
    val Ungu = Color(0xFFA855F7)
    val Pink = Color(0xFFEC4899)
    val Cokelat = Color(0xFF854D0E)

    fun getBySlotIndex(index: Int): Color {
        return when (index) {
            1 -> Merah
            2 -> Oranye
            3 -> Kuning
            4 -> Hijau
            5 -> Toska
            6 -> Biru
            7 -> Indigo
            8 -> Ungu
            9 -> Pink
            10 -> Cokelat
            else -> PrimaryEmerald
        }
    }
}

// --- COMPOSE PALETTES ---

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmerald,
    onPrimary = Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryHover,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = LightBg,
    surface = LightSurface,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    outline = BorderLight,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    outline = BorderDark,
    error = ErrorRed
)

@Composable
fun QuranTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// --- UI/UX TRANSITION GUIDELINES (ONLINE/OFFLINE STATE) ---
/**
 * Untuk memastikan transisi UI dari online ke offline tetap mulus:
 *
 * 1. INDIKATOR STATUS ONLINE/OFFLINE
 *    - Tampilkan banner tipis (Snack-bar style) di bagian paling atas jika aplikasi beralih ke offline.
 *      Gunakan warna latar belakang `WarningYellow` (atau `SecondaryTeal`) dengan teks "Mode Offline Aktif".
 *    - Ketika kembali online, ubah banner menjadi `SuccessGreen` dengan pesan "Koneksi Tersambung, Menyinkronkan..." selama 3 detik, lalu sembunyikan.
 *
 * 2. INDIKATOR UNDUHAN (OFFLINE CACHE AVAILABLE)
 *    - Pada daftar surah, tampilkan ikon unduh (📥) kecil di samping nama surah:
 *      * Status Kosong / Belum Diunduh: Tampilkan warna `TextTertiaryLight` (opacity rendah).
 *      * Status Sedang Diunduh: Tampilkan progress bar melingkar (CircularProgressIndicator) kecil dengan warna `PrimaryEmerald`.
 *      * Status Selesai Diunduh (Tafsir, Terjemahan & Audio lengkap): Tampilkan centang hijau (check-circle) `SuccessGreen`.
 *
 * 3. HANDLING JARINGAN TERPUTUS (ERROR RECOVERY)
 *    - Jika koneksi terputus saat proses download sedang berlangsung:
 *      * Simpan state unduhan terakhir di Room DB `DownloadProgressEntity` dengan status "FAILED" dan pesan error.
 *      * Ubah ikon surah menjadi tanda seru oranye (⚠️).
 *      * Tampilkan tombol "Coba Lagi" (Retry) yang menargetkan ulang unduhan surah tersebut dari byte offset terakhir jika memungkinkan.
 */
