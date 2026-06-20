package com.asyuhada.quran

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import okhttp3.Request
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.work.*
import com.asyuhada.quran.data.db.entities.QuranBookmarkEntity
import com.asyuhada.quran.data.db.entities.QuranSettingsEntity
import com.asyuhada.quran.data.db.entities.TafsirEntity
import com.asyuhada.quran.data.db.entities.TranslationEntity
import com.asyuhada.quran.data.sync.QuranSyncWorker
import com.asyuhada.quran.data.api.PageCoordinatesResponse
import com.asyuhada.quran.data.api.WordCoordinate
import com.asyuhada.quran.data.api.BoundingBox
import com.asyuhada.quran.ui.audio.QuranAudioPlayer
import com.asyuhada.quran.ui.theme.QuranTheme
import com.asyuhada.quran.data.helper.SurahDataHelper
import com.asyuhada.quran.data.helper.SurahExtraInfoHelper
import com.asyuhada.quran.data.helper.QuranPageHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.zIndex

val ScheherazadeNewFontFamily = FontFamily(
    Font(R.font.scheherazade_new_regular)
)

val AmiriFontFamily = FontFamily(
    Font(R.font.amiri_regular)
)

val NotoNaskhArabicFontFamily = FontFamily(
    Font(R.font.noto_naskh_arabic_regular)
)

val CairoFontFamily = FontFamily(
    Font(R.font.cairo_regular)
)

class MainActivity : ComponentActivity() {

    private var onDeepLinkCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as QuranApplication
        handleDeepLink(intent, app)

        // Reset semua unduhan yang menggantung (DOWNLOADING) menjadi PAUSED
        // karena proses unduh (Coroutine) sudah mati saat aplikasi ditutup/direstart.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                app.database.quranDao().pauseAllActiveDownloads()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to pause active downloads", e)
            }
        }

        setContent {
            QuranTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var deepLinkTrigger by remember { mutableStateOf(0) }
                    onDeepLinkCallback = {
                        deepLinkTrigger++
                    }

                    MainScreen(
                        app = app,
                        deepLinkTrigger = deepLinkTrigger,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as QuranApplication
        handleDeepLink(intent, app)
    }

    private fun handleDeepLink(intent: android.content.Intent?, app: QuranApplication) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "com.asyuhada.quran" && uri.host == "auth-callback") {
            val params = mutableMapOf<String, String>()
            
            // Try parsing hash fragment parameters first (standard for implicit grant)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                fragment.split("&").forEach { pair ->
                    val parts = pair.split("=")
                    if (parts.size >= 2) {
                        params[parts[0]] = parts[1]
                    } else if (parts.size == 1) {
                        params[parts[0]] = ""
                    }
                }
            }
            
            // Try query parameters if access_token is not in fragment
            if (params["access_token"].isNullOrBlank()) {
                try {
                    uri.queryParameterNames?.forEach { name ->
                        uri.getQueryParameter(name)?.let { value ->
                            params[name] = value
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error parsing query parameters", e)
                }
            }
            
            val accessToken = params["access_token"]
            if (!accessToken.isNullOrBlank()) {
                val userId = getUserIdFromJwt(accessToken)
                if (!userId.isNullOrBlank()) {
                    val sharedPref = getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit()
                        .putString("sync_token", accessToken)
                        .putString("sync_user_id", userId)
                        .apply()
                    
                    val dao = app.database.quranDao()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val existing = dao.getSettingsSync(userId) ?: QuranSettingsEntity(userId = userId)
                        dao.insertSettings(existing.copy(updatedAt = System.currentTimeMillis()))
                        
                        // Enqueue WorkManager sync job immediately
                        val syncData = workDataOf(
                            "KEY_AUTH_TOKEN" to "Bearer $accessToken",
                            "KEY_USER_ID" to userId
                        )
                        val syncRequest = OneTimeWorkRequestBuilder<QuranSyncWorker>()
                            .setInputData(syncData)
                            .build()
                        WorkManager.getInstance(this@MainActivity).enqueue(syncRequest)
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Login Google Berhasil! Sinkronisasi dimulai.", Toast.LENGTH_LONG).show()
                        onDeepLinkCallback?.invoke()
                    }
                }
            }
        }
    }
}

fun getUserIdFromJwt(token: String): String? {
    return try {
        val parts = token.split(".")
        if (parts.size >= 2) {
            val payloadBase64 = parts[1]
            val decodedBytes = android.util.Base64.decode(payloadBase64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            val gson = Gson()
            val map = gson.fromJson(decodedString, Map::class.java)
            map["sub"] as? String
        } else null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error decoding JWT sub", e)
        null
    }
}

fun getEmailFromJwt(token: String): String? {
    return try {
        val parts = token.split(".")
        if (parts.size >= 2) {
            val payloadBase64 = parts[1]
            val decodedBytes = android.util.Base64.decode(payloadBase64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            val gson = Gson()
            val map = gson.fromJson(decodedString, Map::class.java)
            map["email"] as? String
        } else null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error decoding JWT email", e)
        null
    }
}

fun getFullNameFromJwt(token: String): String? {
    return try {
        val parts = token.split(".")
        if (parts.size >= 2) {
            val payloadBase64 = parts[1]
            val decodedBytes = android.util.Base64.decode(payloadBase64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            val gson = Gson()
            val map = gson.fromJson(decodedString, Map::class.java)
            val userMeta = map["user_metadata"] as? Map<*, *>
            userMeta?.get("full_name") as? String
        } else null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error decoding JWT name", e)
        null
    }
}

fun startQuranDownloadService(context: Context, actionStr: String, targetId: String) {
    val intent = android.content.Intent(context, com.asyuhada.quran.data.download.QuranDownloadService::class.java).apply {
        action = actionStr
        putExtra(com.asyuhada.quran.data.download.QuranDownloadService.EXTRA_TARGET_ID, targetId)
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun getAvatarUrlFromJwt(token: String): String? {
    return try {
        val parts = token.split(".")
        if (parts.size >= 2) {
            val payloadBase64 = parts[1]
            val decodedBytes = android.util.Base64.decode(payloadBase64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            val gson = Gson()
            val map = gson.fromJson(decodedString, Map::class.java)
            val userMeta = map["user_metadata"] as? Map<*, *>
            userMeta?.get("avatar_url") as? String ?: userMeta?.get("picture") as? String
        } else null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error decoding JWT avatar", e)
        null
    }
}

fun toArabicNumerals(number: Int): String {
    val builder = java.lang.StringBuilder()
    val numStr = number.toString()
    for (char in numStr) {
        if (char in '0'..'9') {
            builder.append((char - '0' + 0x0660).toChar())
        } else {
            builder.append(char)
        }
    }
    return builder.toString()
}

val RECITER_OPTIONS = listOf(
    "Alafasy_128kbps" to "Mishary Rashid Alafasy",
    "Abdurrahmaan_As-Sudais_192kbps" to "Abdurrahman As-Sudais",
    "Saood_ash-Shuraym_128kbps" to "Sa'ud Asy-Syuraim",
    "MaherAlMuaiqly128kbps" to "Maher Al-Muaiqly",
    "Hudhaify_128kbps" to "Ali Al-Hudhaify",
    "Muhsin_Al_Qasim_192kbps" to "Muhsin Al-Qasim",
    "Salah_Al_Budair_128kbps" to "Salah Al-Budair",
    "Husary_128kbps" to "Mahmoud Khalil Al-Husary (Murattal)",
    "Husary_128kbps_Mujawwad" to "Mahmoud Khalil Al-Husary (Mujawwad)",
    "Husary_Muallim_128kbps" to "Mahmoud Khalil Al-Husary (Muallim)",
    "Abdul_Basit_Murattal_192kbps" to "Abdul Basit Abdus-Samad (Murattal)",
    "Abdul_Basit_Mujawwad_128kbps" to "Abdul Basit Abdus-Samad (Mujawwad)",
    "Minshawy_Murattal_128kbps" to "Muhammad Siddiq Al-Minshawi (Murattal)",
    "Minshawy_Mujawwad_192kbps" to "Muhammad Siddiq Al-Minshawi (Mujawwad)",
    "Mohammad_al_Tablaway_128kbps" to "Muhammad At-Tablawi",
    "Abu_Bakr_Ash-Shaatree_128kbps" to "Abu Bakr Asy-Syatri",
    "Hani_Rifai_192kbps" to "Hani Ar-Rifai",
    "Muhammad_Ayyoub_128kbps" to "Muhammad Ayyub",
    "Muhammad_Jibreel_128kbps" to "Muhammad Jibril",
    "Abdullah_Basfar_192kbps" to "Abdullah Basfar",
    "Abdullah_Matroud_128kbps" to "Abdullah Matroud",
    "Yasser_Ad-Dussary_128kbps" to "Yasser Ad-Dossari",
    "Nasser_Alqatami_128kbps" to "Nasser Al-Qatami",
    "Salaah_AbdulRahman_Bukhatir_128kbps" to "Salah Bukhatir",
    "Khaalid_Abdullaah_al-Qahtaanee_192kbps" to "Khalid Al-Qahtani",
    "Fares_Abbad_64kbps" to "Fares Abbad",
    "Ali_Jaber_64kbps" to "Ali Jaber",
    "Ayman_Sowaid_64kbps" to "Ayman Sowaid"
)

// 114 Surah Indonesian Names & Verse Counts
val SURAH_NAMES = listOf(
    "Al-Fatihah", "Al-Baqarah", "Ali 'Imran", "An-Nisa'", "Al-Ma'idah", "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Taubah", "Yunus",
    "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Isra'", "Al-Kahf", "Maryam", "Thaha",
    "Al-Anbiya'", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan", "Asy-Syu'ara'", "An-Naml", "Al-Qashash", "Al-Ankabut", "Ar-Rum",
    "Luqman", "As-Sajdah", "Al-Ahzab", "Saba'", "Fathir", "Yasin", "Ash-Shaffat", "Shad", "Az-Zumar", "Ghafir",
    "Fushshilat", "Asy-Syura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jatsiyah", "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf",
    "Adz-Dzariyat", "Ath-Thur", "An-Najm", "Al-Qamar", "Ar-Rahman", "Al-Waqi'ah", "Al-Hadid", "Al-Mujadilah", "Al-Hasyr", "Al-Mumtahanah",
    "Ash-Shaff", "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "Ath-Thalaq", "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij",
    "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddatstsir", "Al-Qiyamah", "Al-Insan", "Al-Mursalat", "An-Naba'", "An-Nazi'at", "Abasa",
    "At-Takwir", "Al-Infitar", "Al-Muthaffifin", "Al-Insyiqaq", "Al-Buruj", "Ath-Thariq", "Al-A'la", "Al-Ghasyiyah", "Al-Fajr", "Al-Balad",
    "Asy-Syams", "Al-Lail", "Ad-Dhuha", "Asy-Syarh", "At-Tin", "Al-Alaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-Adiyat",
    "Al-Qari'ah", "At-Takatsur", "Al-Ashr", "Al-Humazah", "Al-Fil", "Quraisy", "Al-Ma'un", "Al-Kautsar", "Al-Kafirun", "An-Nashr",
    "Al-Lahab", "Al-Ikhlas", "Al-Falaq", "An-Nas"
)

val SURAH_VERSES = listOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
)

val SURAH_START_PAGES = listOf(
    1, 2, 50, 77, 106, 128, 151, 177, 187, 208, 221, 235, 249, 255, 262, 267, 282, 293, 305, 312, 322, 332, 342, 350, 359, 367, 377, 385, 396, 404, 411, 415, 418, 428, 434, 440, 446, 453, 458, 467, 477, 483, 489, 496, 499, 502, 507, 511, 515, 518, 520, 523, 526, 528, 531, 534, 537, 542, 545, 549, 551, 553, 554, 556, 558, 560, 562, 564, 566, 568, 570, 572, 574, 576, 578, 580, 582, 585, 587, 589, 591, 593, 594, 595, 596, 597, 598, 599, 600, 601, 601, 602, 602, 603, 603, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604, 604
)

fun getJuzNumber(page: Int): Int {
    if (page <= 21) return 1
    return minOf(30, ((page - 22) / 20) + 2)
}

fun getSurahNameForPage(page: Int, coordsMap: Map<String, WordCoordinate>): String {
    if (coordsMap.isNotEmpty()) {
        val surahNumbers = coordsMap.keys.mapNotNull { key ->
            val parts = key.split(":")
            if (parts.isNotEmpty()) parts[0].toIntOrNull() else null
        }.distinct().sorted()
        
        if (surahNumbers.isNotEmpty()) {
            val displaySurahs = surahNumbers.take(3).map { SURAH_NAMES[it - 1] }
            return if (surahNumbers.size > 3) {
                displaySurahs.joinToString(" / ") + "..."
            } else {
                displaySurahs.joinToString(" / ")
            }
        }
    }
    
    val surahsOnPage = mutableListOf<String>()
    for (i in SURAH_START_PAGES.indices) {
        if (SURAH_START_PAGES[i] == page) {
            surahsOnPage.add(SURAH_NAMES[i])
        }
    }
    
    if (surahsOnPage.isNotEmpty()) {
        return if (surahsOnPage.size > 2) {
            surahsOnPage.take(2).joinToString(" / ") + "..."
        } else {
            surahsOnPage.joinToString(" / ")
        }
    } else {
        var activeIdx = 0
        for (i in SURAH_START_PAGES.indices) {
            if (SURAH_START_PAGES[i] <= page) {
                activeIdx = i
            } else {
                break
            }
        }
        return SURAH_NAMES[activeIdx]
    }
}

data class ParsedBookmark(
    val customName: String,
    val displayLabel: String,
    val isEmpty: Boolean,
    val hasRealBookmark: Boolean
)

data class SurahExtraInfo(
    val number: Int,
    val name: String,
    val translation: String,
    val revelation: String,
    val numberOfAyahs: Int,
    val description: String
)

fun parseBookmarkLabel(label: String?, slotIndex: Int): ParsedBookmark {
    if (label == null || label.isBlank()) {
        return ParsedBookmark(
            customName = "Bookmark $slotIndex",
            displayLabel = "Kosong",
            isEmpty = true,
            hasRealBookmark = false
        )
    }
    val parts = label.split("||")
    if (parts.size >= 2) {
        val customName = parts[0].trim()
        val displayLabel = parts[1].trim()
        val hasReal = displayLabel != "Kosong"
        
        var cleanedCustomName = customName
        val oldSlotRegex = Regex("^Slot\\s+(\\d+)(?:\\s*\\([^)]*\\))?$", RegexOption.IGNORE_CASE)
        val match = oldSlotRegex.find(cleanedCustomName)
        if (match != null) {
            cleanedCustomName = "Bookmark ${match.groupValues[1]}"
        }
        return ParsedBookmark(
            customName = cleanedCustomName,
            displayLabel = displayLabel,
            isEmpty = !hasReal,
            hasRealBookmark = hasReal
        )
    } else if (parts.size == 1) {
        val valStr = parts[0].trim()
        if (valStr.contains("(Hlm")) {
            return ParsedBookmark(
                customName = "Bookmark $slotIndex",
                displayLabel = valStr,
                isEmpty = false,
                hasRealBookmark = true
            )
        } else {
            var cleanedCustomName = valStr
            val oldSlotRegex = Regex("^Slot\\s+(\\d+)(?:\\s*\\([^)]*\\))?$", RegexOption.IGNORE_CASE)
            val match = oldSlotRegex.find(cleanedCustomName)
            if (match != null) {
                cleanedCustomName = "Bookmark ${match.groupValues[1]}"
            }
            return ParsedBookmark(
                customName = cleanedCustomName,
                displayLabel = "Kosong",
                isEmpty = true,
                hasRealBookmark = false
            )
        }
    }
    return ParsedBookmark(
        customName = "Bookmark $slotIndex",
        displayLabel = "Kosong",
        isEmpty = true,
        hasRealBookmark = false
    )
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000)
        onTimeout()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_placeholder),
            contentDescription = "Splash Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(app: QuranApplication, deepLinkTrigger: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = app.database.quranDao()
    val sharedPref = remember { context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    val surahInfoMap = remember {
        try {
            val inputStream = context.resources.openRawResource(R.raw.quran_info)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(jsonString, Array<SurahExtraInfo>::class.java).associateBy { it.number }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to load quran_info.json", e)
            emptyMap<Int, SurahExtraInfo>()
        }
    }

    // 1. Navigation & Screen States
    var currentScreen by remember { mutableStateOf<String>("splash") }
    var isOnline by remember { mutableStateOf<Boolean>(isNetworkAvailable(context)) }
    var currentPage by remember { mutableStateOf<Int>(sharedPref.getInt("last_page", 1)) }
    var isUiVisible by remember { mutableStateOf<Boolean>(true) }
    var isLeftDrawerOpen by remember { mutableStateOf<Boolean>(false) }
    var isRightDrawerOpen by remember { mutableStateOf<Boolean>(false) }
    var isDetailsDrawerOpen by remember { mutableStateOf<Boolean>(false) }
    var pendingAudioPlayRequest by remember { mutableStateOf<String?>(null) }

    // 2. Room Database Observation
    val settingsFlow = remember { dao.getSettingsFlow() }
    val settings by settingsFlow.collectAsState(initial = null as com.asyuhada.quran.data.db.entities.QuranSettingsEntity?)

    val bookmarksFlow = remember { dao.getBookmarksFlow() }
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList<com.asyuhada.quran.data.db.entities.QuranBookmarkEntity>())

    // Save page automatically
    LaunchedEffect(Unit) {
        sharedPref.edit().putInt("last_page", currentPage).apply()
        withContext(Dispatchers.IO) {
            // Note: Now we just pause any active downloads on startup so that user can resume manually.
            // Foreground service will handle background processing naturally without us restarting it explicitly here.
        }
    }
    
    LaunchedEffect(currentPage) {
        sharedPref.edit().putInt("last_page", currentPage).apply()
        withContext(Dispatchers.IO) {
            val userId = sharedPref.getString("sync_user_id", "")?.takeIf { it.isNotBlank() } ?: "default_dev_user"
            val existing = dao.getSettingsSync(userId)
            if (existing != null && existing.lastReadPage != currentPage) {
                dao.insertSettings(existing.copy(
                    lastReadPage = currentPage,
                    isDirty = true,
                    updatedAt = System.currentTimeMillis()
                ))
            } else if (existing == null) {
                dao.insertSettings(com.asyuhada.quran.data.db.entities.QuranSettingsEntity(
                    userId = userId,
                    lastReadPage = currentPage,
                    isDirty = false
                ))
            }
        }
    }

    // React to settings changes from SyncWorker (e.g. read from web)
    LaunchedEffect(settings?.lastReadPage) {
        if (settings != null) {
            val remotePage = settings!!.lastReadPage
            if (remotePage > 0 && remotePage != currentPage && !settings!!.isDirty) {
                currentPage = remotePage
                sharedPref.edit().putInt("last_page", remotePage).apply()
            }
        }
    }

    // Monitor connectivity updates
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }
            override fun onLost(network: Network) {
                isOnline = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose {
            cm.unregisterNetworkCallback(callback)
        }
    }

    // Wake Lock to prevent screen dimming/sleeping
    val activity = context as? Activity
    DisposableEffect(currentScreen) {
        if (currentScreen == "main") {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 3. Sync states
    var tokenInput by remember { 
        mutableStateOf(sharedPref.getString("sync_token", "") ?: "") 
    }
    var userIdInput by remember { 
        mutableStateOf(sharedPref.getString("sync_user_id", "") ?: "") 
    }

    // React to deep link triggers to refresh state
    LaunchedEffect(deepLinkTrigger) {
        if (deepLinkTrigger > 0) {
            tokenInput = sharedPref.getString("sync_token", "") ?: ""
            userIdInput = sharedPref.getString("sync_user_id", "") ?: ""
        }
    }

    // 4. Audio Playback and Highlights states
    var downloadState by remember { mutableStateOf("IDLE") }
    var activeSurahDownloadProgress by remember { mutableStateOf<Int?>(null) }
    var allSurahsDownloadProgress by remember { mutableStateOf<Int?>(null) }
    var allSurahsCurrentSurahName by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    var currentPlayingAyah by remember { mutableStateOf<String?>(null) } // "surah:ayah" format
    var selectedAyah by remember { mutableStateOf<String?>(null) } // "surah:ayah" format

    val reciter = settings?.audioReciter ?: "Alafasy_128kbps"

    // 5. File paths
    val padPage = String.format("%03d", currentPage)
    val mushafSource = settings?.mushafSource ?: "standard"
    val mushafDir = File(context.filesDir, "mushaf")
    val imageFile = File(mushafDir, "page_${mushafSource}_${padPage}.webp")
    val coordsFile = File(mushafDir, "page_${padPage}_coords.json")

    // Image & Coords Loading
    var imageBitmap by remember(currentPage, mushafSource) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var coordsMap by remember(currentPage) { mutableStateOf<Map<String, WordCoordinate>>(emptyMap()) }
    var isImageLoading by remember(currentPage) { mutableStateOf(false) }

    LaunchedEffect(currentPage, mushafSource, isOnline) {
        // Load Coords
        withContext(Dispatchers.IO) {
            if (coordsFile.exists() && coordsFile.length() > 0) {
                try {
                    val jsonString = coordsFile.readText()
                    val response = Gson().fromJson(jsonString, PageCoordinatesResponse::class.java)
                    coordsMap = response.coords
                } catch (e: Exception) {
                    coordsMap = emptyMap()
                }
            } else if (isOnline) {
                try {
                    val baseUrl = (context.applicationContext as QuranApplication).getPortalBaseUrl()
                    val padPageStr = String.format("%03d", currentPage)
                    val coordsUrl = "${baseUrl}quran/coords/page-${padPageStr}.json"
                    val request = Request.Builder().url(coordsUrl).build()
                    val response = app.downloadManager.okHttpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val jsonString = response.body!!.string()
                        val res = Gson().fromJson(jsonString, PageCoordinatesResponse::class.java)
                        coordsMap = res.coords
                        val fos = FileOutputStream(coordsFile)
                        fos.write(jsonString.toByteArray())
                        fos.flush()
                        fos.close()
                    }
                } catch (e: Exception) {
                    coordsMap = emptyMap()
                }
            }
        }

        // Load Image
        withContext(Dispatchers.IO) {
            if (imageFile.exists() && imageFile.length() > 0) {
                try {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    imageBitmap = bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    imageBitmap = null
                }
            } else if (isOnline) {
                isImageLoading = true
                try {
                    val padPageStr = String.format("%03d", currentPage)
                    val url = if (mushafSource == "tajweed") {
                        "https://raw.githubusercontent.com/NeaByteLab/Quran-Data/main/public/mushaf/ksu-tajweed/${padPageStr}.png"
                    } else {
                        "https://raw.githubusercontent.com/GovarJabbar/Quran-PNG/master/${padPageStr}.png"
                    }
                    val request = Request.Builder().url(url).build()
                    val response = app.downloadManager.okHttpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val inputStream = response.body!!.byteStream()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = false }
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        imageBitmap = bitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    imageBitmap = null
                } finally {
                    isImageLoading = false
                }
            }
        }
    }

    val ayahsOnPage = remember(coordsMap) {
        coordsMap.keys.mapNotNull { key ->
            val parts = key.split(":")
            if (parts.size == 3) {
                val surah = parts[0].toIntOrNull()
                val ayah = parts[1].toIntOrNull()
                if (surah != null && ayah != null) {
                    Pair(surah, ayah)
                } else null
            } else null
        }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }

    // 6. Audio Player setup
    val audioPlayer = remember { QuranAudioPlayer(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    // ExoPlayer callback bindings
    audioPlayer.onAyahCompleted = {
        val current = currentPlayingAyah
        if (current != null && current != "PENDING_NEXT_PAGE" && current.contains(":")) {
            val parts = current.split(":")
            val surah = parts[0].toIntOrNull() ?: 1
            val ayah = parts[1].toIntOrNull() ?: 1
            val maxAyah = SURAH_VERSES.getOrNull(surah - 1) ?: 1
            
            val (nextSurah, nextAyah) = if (ayah < maxAyah) {
                Pair(surah, ayah + 1)
            } else if (surah < 114) {
                Pair(surah + 1, 1)
            } else {
                Pair(null, null)
            }
            
            if (nextSurah != null && nextAyah != null) {
                val nextKey = "$nextSurah:$nextAyah"
                val nextPage = QuranPageHelper.getPageForAyah(nextSurah, nextAyah)
                if (nextPage != currentPage) {
                    currentPage = nextPage
                    currentPlayingAyah = "PENDING_NEXT_PAGE"
                } else {
                    currentPlayingAyah = nextKey
                    pendingAudioPlayRequest = "$nextSurah:$nextAyah"
                }
            } else {
                isPlaying = false
                currentPlayingAyah = null
            }
        }
    }

    audioPlayer.onPlaybackStateChanged = { playing, loading ->
        isPlaying = playing
        isLoadingAudio = loading
    }

    // Trigger auto-play on next page when page changes
    LaunchedEffect(currentPage, coordsMap) {
        if (currentPlayingAyah == "PENDING_NEXT_PAGE" && ayahsOnPage.isNotEmpty()) {
            val first = ayahsOnPage.first()
            currentPlayingAyah = "${first.first}:${first.second}"
            pendingAudioPlayRequest = "${first.first}:${first.second}"
        }
    }

    // Auto-update details drawer selection to current playing ayah
    LaunchedEffect(currentPlayingAyah) {
        val key = currentPlayingAyah
        if (key != null && key != "PENDING_NEXT_PAGE") {
            if (isDetailsDrawerOpen) {
                selectedAyah = key
            }
        }
    }



    // 7. Dialog / Overlay Trigger States
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSurahInfoDialog by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var editingCustomNameInput by remember { mutableStateOf("") }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var surahSearchQuery by remember { mutableStateOf("") }

    // 8. Translation & Tafsir states
    var activeTranslationText by remember { mutableStateOf("") }
    var activeTafsirKemenag by remember { mutableStateOf("") }
    var activeTafsirJalalayn by remember { mutableStateOf("") }
    var activeTafsirIbnKathirAr by remember { mutableStateOf("") }
    var activeArabicText by remember { mutableStateOf("") }
    var isDetailsLoading by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("terjemahan") }

    LaunchedEffect(pendingAudioPlayRequest) {
        val req = pendingAudioPlayRequest
        if (req != null) {
            val parts = req.split(":")
            val s = parts[0].toIntOrNull()
            val a = parts[1].toIntOrNull()
            if (s != null && a != null) {
                while (isDetailsLoading) {
                    kotlinx.coroutines.delay(100)
                }
                audioPlayer.playAyah(s, a, reciter)
            }
            pendingAudioPlayRequest = null
        }
    }

    LaunchedEffect(selectedAyah) {
        val key = selectedAyah
        if (key != null) {
            isDetailsLoading = true
            val parts = key.split(":")
            val surah = parts[0].toIntOrNull() ?: 1
            val ayah = parts[1].toIntOrNull() ?: 1
            
            withContext(Dispatchers.IO) {
                val localTrans = dao.getTranslationByAyah(key)
                val localTafsir = dao.getTafsirByAyah(key)
                
                if (localTrans != null && localTafsir != null && localTrans.textId.isNotEmpty()) {
                    activeTranslationText = localTrans.textId
                    activeArabicText = localTrans.textAr
                    activeTafsirKemenag = localTafsir.tafsirKemenag
                    activeTafsirJalalayn = localTafsir.tafsirJalalayn
                    activeTafsirIbnKathirAr = localTafsir.tafsirIbnKathirAr
                    isDetailsLoading = false
                } else {
                    if (isOnline) {
                        try {
                            var transText = ""
                            var arText = ""
                            var jalalaynText = ""
                            var kemenagText = ""
                            var ibnKathirArText = ""
                            
                            val transResp = app.apiService.getIndonesianTranslation(key)
                            if (transResp.isSuccessful && transResp.body() != null) {
                                transText = transResp.body()!!.data.text
                            }
                            val arResp = app.apiService.getArabicTextAndDetails(key)
                            if (arResp.isSuccessful && arResp.body() != null) {
                                arText = arResp.body()!!.data.text
                            }
                            val jalalaynResp = app.apiService.getJalalaynTafsir(key)
                            if (jalalaynResp.isSuccessful && jalalaynResp.body() != null) {
                                jalalaynText = jalalaynResp.body()!!.data.text
                            }
                            val kemenagResp = app.apiService.getKemenagTafsir(surah)
                            if (kemenagResp.isSuccessful && kemenagResp.body() != null) {
                                val tafsirList = kemenagResp.body()!!.data.tafsir
                                kemenagText = tafsirList.find { it.ayat == ayah }?.teks ?: ""
                            }
                            try {
                                val ibnKathirResp = app.apiService.getArabicIbnKathirTafsir(key)
                                if (ibnKathirResp.isSuccessful && ibnKathirResp.body() != null) {
                                    ibnKathirArText = ibnKathirResp.body()!!.tafsir.text
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error loading online Ibn Kathir", e)
                            }
                            
                            val transEntity = TranslationEntity(
                                ayahKey = key,
                                surahNumber = surah,
                                ayahNumber = ayah,
                                textAr = arText,
                                textId = transText
                            )
                            val tafsirEntity = TafsirEntity(
                                ayahKey = key,
                                surahNumber = surah,
                                ayahNumber = ayah,
                                tafsirKemenag = kemenagText,
                                tafsirJalalayn = jalalaynText,
                                tafsirIbnKathirAr = ibnKathirArText
                            )
                            dao.insertTranslation(transEntity)
                            dao.insertTafsir(tafsirEntity)
                            
                            activeTranslationText = transText
                            activeArabicText = arText
                            activeTafsirKemenag = kemenagText
                            activeTafsirJalalayn = jalalaynText
                            activeTafsirIbnKathirAr = ibnKathirArText
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error loading online details", e)
                        }
                    } else {
                        activeTranslationText = ""
                        activeArabicText = ""
                        activeTafsirKemenag = ""
                        activeTafsirJalalayn = ""
                        activeTafsirIbnKathirAr = ""
                    }
                    isDetailsLoading = false
                }
            }
        }
    }

    if (currentScreen == "splash") {
        SplashScreen(onTimeout = { currentScreen = "main" })
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // A. SC scrim container when any side drawer or info drawer is open
            if (isLeftDrawerOpen || isRightDrawerOpen || showSurahInfoDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isLeftDrawerOpen = false
                            isRightDrawerOpen = false
                            showSurahInfoDialog = false
                        }
                        .zIndex(9f)
                )
            }

            // B. Center Mushaf Image Canvas
            val currentImageBitmap = imageBitmap
            if (currentImageBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isImageLoading) {
                            CircularProgressIndicator(color = Color(0xFF059669))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Memuat Halaman $currentPage...",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 18.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Gagal memuat halaman $currentPage",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Periksa koneksi internet Anda.",
                                color = Color(0xFF475569),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                var totalDragX by remember(currentPage) { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                totalDragX += delta
                            },
                            onDragStarted = {
                                totalDragX = 0f
                            },
                            onDragStopped = { velocity ->
                                if (totalDragX > 80f) { // Swipe Left-to-Right -> Halaman Berikutnya (RTL Layout)
                                    if (currentPage < 604) {
                                        currentPage += 1
                                        selectedAyah = null
                                    }
                                } else if (totalDragX < -80f) { // Swipe Right-to-Left -> Halaman Sebelumnya (RTL Layout)
                                    if (currentPage > 1) {
                                        currentPage -= 1
                                        selectedAyah = null
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(900f / 1437f)
                    ) {
                        val canvasWidth = maxWidth
                        val canvasHeight = maxHeight

                        Image(
                            bitmap = currentImageBitmap,
                            contentDescription = "Mushaf Page Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isUiVisible = !isUiVisible
                                }
                        )

                        // Highlights bounding boxes
                        coordsMap.forEach { (wordKey, coord) ->
                            val parts = wordKey.split(":")
                            if (parts.size == 3) {
                                val surah = parts[0].toIntOrNull() ?: 1
                                val ayah = parts[1].toIntOrNull() ?: 1
                                val box = coord.h
                                val isTajweed = settings?.mushafSource == "tajweed"
                                val boxX = if (isTajweed) box.x * 0.98373f + 16.57f else box.x
                                val boxY = if (isTajweed) box.y * 0.89941f + 52.07f else box.y
                                val boxW = if (isTajweed) box.w * 0.98373f else box.w
                                val boxH = if (isTajweed) box.h * 0.89941f else box.h

                                val left = canvasWidth * (boxX / 900f)
                                val top = canvasHeight * (boxY / 1437f)
                                val width = canvasWidth * (boxW / 900f)
                                val height = canvasHeight * (boxH / 1437f)

                                val ayahKey = "$surah:$ayah"
                                val isCurrentPlaying = currentPlayingAyah == ayahKey
                                val isSelected = selectedAyah == ayahKey

                                val overlayColor = when {
                                    isCurrentPlaying -> Color(0xFFFBBF24).copy(alpha = 0.35f)
                                    isSelected -> Color(0xFF059669).copy(alpha = 0.2f)
                                    else -> Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .absoluteOffset(x = left, y = top)
                                        .size(width = width, height = height)
                                        .background(overlayColor)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    selectedAyah = ayahKey
                                                    isDetailsDrawerOpen = true
                                                    isUiVisible = true
                                                }
                                            )
                                        }
                                )
                            }
                        }

                        // Margin Bookmark Ribbons
                        bookmarks.forEach { bookmark ->
                            if (bookmark.pageNumber == currentPage && bookmark.surahNumber > 0) {
                                val surah = bookmark.surahNumber
                                val ayah = bookmark.ayahNumber
                                val prefix = "$surah:$ayah:"
                                val words = coordsMap.filter { it.key.startsWith(prefix) }
                                if (words.isNotEmpty()) {
                                    val isTajweed = settings?.mushafSource == "tajweed"
                                    val minY = words.values.map { 
                                        if (isTajweed) it.h.y * 0.89941f + 52.07f else it.h.y
                                    }.minOrNull() ?: 0f
                                    
                                    val topOffset = canvasHeight * (minY / 1437f)
                                    val ribbonColor = try {
                                        Color(android.graphics.Color.parseColor(bookmark.color))
                                    } catch (e: Exception) {
                                        Color(0xFF059669)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .absoluteOffset(x = canvasWidth - 16.dp, y = topOffset)
                                            .size(width = 16.dp, height = 24.dp)
                                            .background(ribbonColor, shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                            .clickable { selectedAyah = "$surah:$ayah"; isDetailsDrawerOpen = true; isUiVisible = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${bookmark.bookmarkIndex}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // C. Header Bar (Immersive Toggleable)
            AnimatedVisibility(
                visible = isUiVisible && currentScreen == "main",
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val activeSurahIdx = SURAH_START_PAGES.reduceIndexed { idx, acc, curr ->
                    if (currentPage >= curr) idx else acc
                }
                val activeSurahNameAr = SurahDataHelper.SURAH_NAMES_AR.getOrNull(activeSurahIdx) ?: "سورة"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF272117))
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Header: Hamburger + dropdown + activeSurahNameAr
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isLeftDrawerOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Daftar Surah",
                            tint = Color(0xFFFCF8F2),
                            modifier = Modifier.size(22.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFFD8C29D),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeSurahNameAr,
                            color = Color(0xFFFCF8F2),
                            fontSize = 17.sp,
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Middle Header: Circular white-bordered Box with three dots
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(width = 1.dp, color = Color.White, shape = CircleShape)
                            .clip(CircleShape)
                            .clickable { showSurahInfoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Detail Surah",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Right Header: Juz in Arabic + dropdown + hamburger
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isRightDrawerOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val currentJuzNum = getJuzNumber(currentPage)
                        val juzNameAr = SurahDataHelper.JUZ_NAMES_AR.getOrNull(currentJuzNum - 1) ?: "الجزء"
                        Text(
                            text = juzNameAr,
                            color = Color(0xFFFCF8F2),
                            fontSize = 15.sp,
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFFD8C29D),
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Pilih Juz",
                            tint = Color(0xFFFCF8F2),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // D. Non-intrusive Login Suggestion card (if not logged in)
            val isNotLoggedIn = tokenInput.isBlank()
            var dismissLoginCard by remember { mutableStateOf(false) }

            AnimatedVisibility(
                visible = isNotLoggedIn && !dismissLoginCard && isUiVisible && currentScreen == "main",
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sinkronisasi Lintas Perangkat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Masuk dengan akun Google Anda di Pengaturan untuk memulihkan preferensi dan bookmark di semua perangkat.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                        IconButton(onClick = { dismissLoginCard = true }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // E. Footer Bar (Immersive Toggleable)
            AnimatedVisibility(
                visible = isUiVisible && currentScreen == "main",
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF272117))
                        .navigationBarsPadding()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Pengaturan", tint = Color(0xFFFCF8F2))
                    }

                    IconButton(onClick = { showBookmarksDialog = true }) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = "Bookmarks", tint = Color(0xFFFCF8F2))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioPlayer.pause()
                                } else {
                                    val playKey = if (currentPlayingAyah != null && currentPlayingAyah != "PENDING_NEXT_PAGE" && currentPlayingAyah!!.contains(":")) {
                                        currentPlayingAyah
                                    } else {
                                        selectedAyah ?: run {
                                            val first = ayahsOnPage.firstOrNull()
                                            if (first != null) "${first.first}:${first.second}" else null
                                        }
                                    }
                                    if (playKey != null && playKey.contains(":")) {
                                        currentPlayingAyah = playKey
                                        val parts = playKey.split(":")
                                        val s = parts[0].toIntOrNull()
                                        val a = parts[1].toIntOrNull()
                                        if (s != null && a != null) {
                                            pendingAudioPlayRequest = "$s:$a"
                                        }
                                    }
                                }
                            }
                        ) {
                            if (isLoadingAudio) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFFD8C29D))
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Murottal",
                                    tint = Color(0xFFFCF8F2)
                                )
                            }
                        }

                        if (currentPlayingAyah != null) {
                            IconButton(
                                onClick = {
                                    audioPlayer.stop()
                                    currentPlayingAyah = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Setop Murottal",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Cari Ayat", tint = Color(0xFFFCF8F2))
                    }
                }
            }

            // F. Left Drawer (Surah list + Bookmark 1 Shortcut)
            AnimatedVisibility(
                visible = isLeftDrawerOpen && currentScreen == "main",
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .align(Alignment.CenterStart)
                    .zIndex(10f)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFBF7F0),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daftar Surah",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF272117)
                            )
                            IconButton(onClick = { isLeftDrawerOpen = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                            }
                        }

                        // Bookmark 1 card shortcut
                        val bookmark1 = bookmarks.find { it.bookmarkIndex == 1 }
                        val parsed1 = parseBookmarkLabel(bookmark1?.label, 1)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EFE9)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    if (bookmark1 != null && parsed1.hasRealBookmark) {
                                        currentPage = bookmark1.pageNumber
                                        selectedAyah = "${bookmark1.surahNumber}:${bookmark1.ayahNumber}"
                                        isLeftDrawerOpen = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(0xFFEF4444), shape = RoundedCornerShape(8.dp))
                                )
                                Column {
                                    Text(
                                        text = parsed1.customName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF272117)
                                    )
                                    Text(
                                        text = if (parsed1.hasRealBookmark) parsed1.displayLabel else "Kosong (Ketuk ayat untuk tandai)",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // Render 114 Surahs directly without search bar
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(114) { index ->
                                val sNum = index + 1
                                val sNameAr = SurahDataHelper.SURAH_NAMES_AR[index]
                                val sPage = SURAH_START_PAGES[index]

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            isLeftDrawerOpen = false
                                            currentPage = sPage
                                            selectedAyah = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Column: Halaman (Rata Kiri)
                                    Text(
                                        text = toArabicNumerals(sPage),
                                        fontSize = 17.sp,
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF272117),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Left
                                    )
                                    
                                    // Right Column: Nomor. Nama Surah (Rata Kanan)
                                    Text(
                                        text = "${toArabicNumerals(sNum)}. $sNameAr",
                                        fontSize = 17.sp,
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF272117),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // G. Right Drawer (Juz list)
            AnimatedVisibility(
                visible = isRightDrawerOpen && currentScreen == "main",
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .align(Alignment.CenterEnd)
                    .zIndex(10f)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFBF7F0),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isRightDrawerOpen = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                            }
                            Text(
                                text = "Daftar Juz",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF272117)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(30) { index ->
                                val juzNum = index + 1
                                val juzNameAr = SurahDataHelper.JUZ_NAMES_AR[index]
                                val juzPage = SurahDataHelper.JUZ_START_PAGES[index]

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            isRightDrawerOpen = false
                                            currentPage = juzPage
                                            selectedAyah = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left Column: Halaman (Rata Kiri)
                                    Text(
                                        text = toArabicNumerals(juzPage),
                                        fontSize = 17.sp,
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF272117),
                                        textAlign = TextAlign.Left,
                                        modifier = Modifier.wrapContentWidth()
                                    )
                                    
                                    // Right Column: Nomor. Juz Nama (Rata Kanan)
                                    Text(
                                        text = "${toArabicNumerals(juzNum)}. $juzNameAr",
                                        fontSize = 17.sp,
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF272117),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // H. Translation/Tafsir Details Drawer (Fullscreen Overlay)
            AnimatedVisibility(
                visible = isDetailsDrawerOpen && selectedAyah != null && currentScreen == "main",
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                selectedAyah?.let { key ->
                    val parts = key.split(":")
                    val surah = parts[0].toIntOrNull() ?: 1
                    val ayah = parts[1].toIntOrNull() ?: 1
                    val surahName = SURAH_NAMES.getOrNull(surah - 1) ?: "Surah"
                    val isMadaniyah = listOf(2, 3, 4, 5, 8, 9, 13, 22, 24, 33, 47, 48, 49, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 76, 98, 99, 110).contains(surah)
                    val classification = if (isMadaniyah) "Madaniyah" else "Makkiyah"

                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCF8F2)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(16.dp)
                        ) {
                            // Details Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Surah $surahName : Ayat $ayah",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF059669)
                                    )
                                    Text(
                                        text = "Juz ${getJuzNumber(currentPage)} • Halaman $currentPage • $classification",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText(
                                                "Ayat Copy",
                                                "$activeArabicText\n\n$activeTranslationText"
                                            )
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Teks ayat dan terjemahan disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Salin Ayat",
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isDetailsDrawerOpen = false
                                            selectedAyah = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Tutup",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))

                            // Scrollable details column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Verse Navigation Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val maxAyah = SURAH_VERSES[surah - 1]
                                    val isLastAyah = surah == 114 && ayah == maxAyah
                                    val isFirstAyah = surah == 1 && ayah == 1

                                    TextButton(
                                        onClick = {
                                            if (!isLastAyah) {
                                                val nextAyah = if (ayah < maxAyah) ayah + 1 else 1
                                                val nextSurah = if (ayah < maxAyah) surah else surah + 1
                                                selectedAyah = "$nextSurah:$nextAyah"
                                                currentPage = QuranPageHelper.getPageForAyah(nextSurah, nextAyah)
                                            }
                                        },
                                        enabled = !isLastAyah,
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF059669))
                                    ) {
                                        Text("← Ayat Selanjutnya", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    TextButton(
                                        onClick = {
                                            if (!isFirstAyah) {
                                                val prevSurah = if (ayah > 1) surah else surah - 1
                                                val prevAyah = if (ayah > 1) ayah - 1 else SURAH_VERSES[prevSurah - 1]
                                                selectedAyah = "$prevSurah:$prevAyah"
                                                currentPage = QuranPageHelper.getPageForAyah(prevSurah, prevAyah)
                                            }
                                        },
                                        enabled = !isFirstAyah,
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF059669))
                                    ) {
                                        Text("Ayat Sebelumnya →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // 2. Large Arabic Text with Custom relaxed spacing
                                if (activeArabicText.isNotEmpty()) {
                                    val selectedFontFamily = when (settings?.arabicFont ?: "Scheherazade New") {
                                        "Scheherazade New" -> ScheherazadeNewFontFamily
                                        "Amiri" -> AmiriFontFamily
                                        "Noto Naskh Arabic" -> NotoNaskhArabicFontFamily
                                        "Cairo" -> CairoFontFamily
                                        else -> ScheherazadeNewFontFamily
                                    }
                                    val fontSizeVal = settings?.arabicFontSize ?: 32

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFBF7F0), shape = RoundedCornerShape(8.dp))
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = activeArabicText,
                                            fontSize = fontSizeVal.sp,
                                            lineHeight = (fontSizeVal * 2.2f).sp, // Relaxed line spacing
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF272117),
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            fontFamily = selectedFontFamily
                                        )
                                    }
                                }

                                // 3. Murottal Player Controls
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EFE9)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("🎧", fontSize = 14.sp)
                                                Text("Murottal", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF272117))
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        if (currentPlayingAyah == key && isPlaying) {
                                                            audioPlayer.pause()
                                                        } else {
                                                            currentPlayingAyah = key
                                                            pendingAudioPlayRequest = "$surah:$ayah"
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(Color(0xFF059669), shape = RoundedCornerShape(16.dp))
                                                ) {
                                                    if (currentPlayingAyah == key && isLoadingAudio) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color.White
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (currentPlayingAyah == key && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "Putar",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                if (currentPlayingAyah == key) {
                                                    IconButton(
                                                        onClick = {
                                                            audioPlayer.stop()
                                                            currentPlayingAyah = null
                                                        },
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(Color(0xFFEF4444), shape = RoundedCornerShape(16.dp))
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Stop,
                                                            contentDescription = "Setop",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Qari:", fontSize = 10.sp, color = Color.Gray)
                                                val isOfflineCached = remember(reciter, downloadState) {
                                                    getOfflineAyahCount(context, reciter) > 0
                                                }
                                                val reciterName = (RECITER_OPTIONS.find { it.first == reciter }?.second ?: reciter) + 
                                                    (if (isOfflineCached) " [Lokal]" else "")
                                                Text(
                                                    text = reciterName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF272117),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                                                        .clickable { showSettingsDialog = true }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4. Tab selection Card
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val tabs = listOf(
                                        "terjemahan" to "Terjemahan",
                                        "tafsir_kemenag" to "Tafsir Kemenag",
                                        "tafsir_jalalayn" to "Tafsir Jalalayn",
                                        "tafsir_ibnkathir" to "Ibnu Katsir (Ar)"
                                    )
                                    tabs.forEach { (tabId, label) ->
                                        val isSelected = activeTab == tabId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    color = if (isSelected) Color(0xFF059669) else Color(0xFFE2E8F0),
                                                    shape = RoundedCornerShape(20.dp)
                                                )
                                                .clickable { activeTab = tabId }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else Color(0xFF475569),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                // 5. Tab Content Text
                                if (isDetailsLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF059669), modifier = Modifier.size(28.dp))
                                    }
                                } else {
                                    val rawContentText = when (activeTab) {
                                        "terjemahan" -> activeTranslationText
                                        "tafsir_kemenag" -> activeTafsirKemenag
                                        "tafsir_jalalayn" -> activeTafsirJalalayn
                                        else -> activeTafsirIbnKathirAr
                                    }

                                    val contentText = remember(rawContentText, activeTab) {
                                        if (activeTab == "tafsir_ibnkathir" && rawContentText.isNotEmpty()) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                                android.text.Html.fromHtml(rawContentText, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
                                            } else {
                                                @Suppress("DEPRECATION")
                                                android.text.Html.fromHtml(rawContentText).toString().trim()
                                            }
                                        } else {
                                            rawContentText
                                        }
                                    }

                                    if (contentText.isNotEmpty()) {
                                        Text(
                                            text = contentText,
                                            fontSize = 13.sp,
                                            lineHeight = 20.sp,
                                            color = Color(0xFF272117),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    } else {
                                        Text(
                                            text = if (!isOnline) {
                                                "Data tidak tersedia offline. Silakan hubungkan ke internet untuk memuat data Tafsir/Terjemahan."
                                            } else {
                                                "Tafsir tidak ditemukan."
                                            },
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = Color.Gray,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // 6. Bookmark Slots Section (1 to 10)
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Simpan ke Bookmark Slot:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            (1..5).forEach { slotIndex ->
                                                BookmarkSlotChip(
                                                    slotIndex = slotIndex,
                                                    surah = surah,
                                                    ayah = ayah,
                                                    surahName = surahName,
                                                    currentPage = currentPage,
                                                    bookmarks = bookmarks,
                                                    dao = dao,
                                                    coroutineScope = coroutineScope,
                                                    context = context
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            (6..10).forEach { slotIndex ->
                                                BookmarkSlotChip(
                                                    slotIndex = slotIndex,
                                                    surah = surah,
                                                    ayah = ayah,
                                                    surahName = surahName,
                                                    currentPage = currentPage,
                                                    bookmarks = bookmarks,
                                                    dao = dao,
                                                    coroutineScope = coroutineScope,
                                                    context = context
                                                )
                                            }
                                        }
                                    }
                                }

                                // 7. Arabic Font Selection Row
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Pilihan Font Arab:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val fonts = listOf(
                                         "Scheherazade New" to "Scheherazade",
                                         "Amiri" to "Amiri",
                                         "Noto Naskh Arabic" to "Noto Naskh",
                                         "Cairo" to "Cairo"
                                     )
                                    fonts.forEach { (fontKey, fontLabel) ->
                                        val isSelected = (settings?.arabicFont ?: "Scheherazade New") == fontKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    color = if (isSelected) Color(0xFF059669) else Color(0xFFE2E8F0),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    coroutineScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            val curr = settings ?: QuranSettingsEntity(userId = userIdInput.ifBlank { "default_dev_user" })
                                                            dao.insertSettings(curr.copy(arabicFont = fontKey, updatedAt = System.currentTimeMillis(), isDirty = true))
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = fontLabel,
                                                color = if (isSelected) Color.White else Color(0xFF475569),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // 8. Font Size Slider Row
                                Spacer(modifier = Modifier.height(12.dp))
                                var localFontSize by remember(settings?.arabicFontSize) { 
                                    mutableStateOf(settings?.arabicFontSize?.toFloat() ?: 32f) 
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Ukuran Font (${localFontSize.toInt()}sp):", 
                                        fontSize = 12.sp, 
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                    Slider(
                                        value = localFontSize,
                                        onValueChange = { localFontSize = it },
                                        onValueChangeFinished = {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val curr = settings ?: QuranSettingsEntity(userId = userIdInput.ifBlank { "default_dev_user" })
                                                    dao.insertSettings(curr.copy(arabicFontSize = localFontSize.toInt(), updatedAt = System.currentTimeMillis(), isDirty = true))
                                                }
                                            }
                                        },
                                        valueRange = 20f..48f,
                                        modifier = Modifier.weight(1f).height(24.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF059669),
                                            activeTrackColor = Color(0xFF059669)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // I. Surah Info Top Drawer
            AnimatedVisibility(
                visible = showSurahInfoDialog && currentScreen == "main",
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(0.75f)
                    .zIndex(15f)
            ) {
                val activeSurahIdx = SURAH_START_PAGES.reduceIndexed { idx, acc, curr ->
                    if (currentPage >= curr) idx else acc
                }
                val surahNum = activeSurahIdx + 1
                val surahExtra = SurahExtraInfoHelper.SURAH_EXTRA_MAP[surahNum]

                if (surahExtra != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCF8F2)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .heightIn(max = 320.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Surah ${surahExtra.namaLatin}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF272117)
                                )
                                IconButton(
                                    onClick = { showSurahInfoDialog = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = "Surat ${surahExtra.tempatTurun}, ${surahExtra.jumlahAyat} ayat, urutan turun ke-${surahExtra.urutanTurun}",
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val localDesc = surahInfoMap[surahNum]?.description ?: surahExtra.deskripsi
                                Text(
                                    text = localDesc,
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            // J. Settings Dialog (Supabase + Offline Reciter + Mushaf toggle)
            if (showSettingsDialog) {
                var syncStatus by remember { mutableStateOf("IDLE") }
                var pendingMushafSourceChange by remember { mutableStateOf<String?>(null) }
                var pendingReciterChange by remember { mutableStateOf<String?>(null) }
                val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val allDownloads by dao.getAllDownloads().collectAsState(initial = emptyList<com.asyuhada.quran.data.db.entities.DownloadProgressEntity>())

                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showSettingsDialog = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFFF8FAFC) // Sleek light gray background for the sheet
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Pengaturan & Sinkronisasi", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color(0xFF0F172A))
                        
                        // 1. Akun & Sinkronisasi
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Akun & Sinkronisasi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                val userEmail = remember(tokenInput) {
                                    if (tokenInput.isNotBlank()) getEmailFromJwt(tokenInput) else null
                                }
                                val userName = remember(tokenInput) {
                                    if (tokenInput.isNotBlank()) getFullNameFromJwt(tokenInput) else null
                                }
                                val isLoggedIn = tokenInput.isNotBlank() && userIdInput.isNotBlank() && userEmail != null

                                if (isLoggedIn) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        val avatarUrl = remember(tokenInput) {
                                            if (tokenInput.isNotBlank()) getAvatarUrlFromJwt(tokenInput) else null
                                        }
                                        if (avatarUrl != null) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Person, 
                                                contentDescription = "Avatar", 
                                                modifier = Modifier.size(56.dp), 
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                        Column {
                                            Text(text = userName ?: "Pengguna Google", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                            Text(text = userEmail ?: "", fontSize = 14.sp, color = Color(0xFF64748B))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                                Text(text = "Terhubung", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF10B981))
                                            }
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(
                                            onClick = {
                                                syncStatus = "SYNCING"
                                                val syncData = workDataOf("KEY_AUTH_TOKEN" to "Bearer $tokenInput", "KEY_USER_ID" to userIdInput)
                                                val syncRequest = OneTimeWorkRequestBuilder<QuranSyncWorker>().setInputData(syncData).build()
                                                val wm = WorkManager.getInstance(context)
                                                wm.enqueue(syncRequest)
                                                wm.getWorkInfoByIdLiveData(syncRequest.id).observeForever { info ->
                                                    if (info != null) {
                                                        if (info.state == WorkInfo.State.SUCCEEDED) {
                                                            syncStatus = "SUCCESS"
                                                            Toast.makeText(context, "Sinkronisasi Berhasil!", Toast.LENGTH_SHORT).show()
                                                        } else if (info.state == WorkInfo.State.FAILED) {
                                                            syncStatus = "FAILED"
                                                            Toast.makeText(context, "Sinkronisasi Gagal!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                                            Text(if (syncStatus == "SYNCING") "Proses..." else "Sinkronkan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Button(
                                            onClick = {
                                                sharedPref.edit().putString("sync_token", "").putString("sync_user_id", "").apply()
                                                tokenInput = ""
                                                userIdInput = ""
                                                coroutineScope.launch { withContext(Dispatchers.IO) { dao.clearAllBookmarks() } }
                                                Toast.makeText(context, "Berhasil Keluar", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFDC2626)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                                            Text("Keluar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Masuk untuk mensinkronisasi data bookmark dan pengaturan secara otomatis antara website portal dan aplikasi Android.",
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Button(
                                        onClick = {
                                            val redirectUrl = "https://www.asyuhada-jaya.org/auth/quran-callback"
                                            val oauthUrl = "https://faythocihlyzmzrnwwks.supabase.co/auth/v1/authorize?provider=google&redirect_to=${java.net.URLEncoder.encode(redirectUrl, "UTF-8")}&prompt=select_account"
                                            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                            context.startActivity(browserIntent)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                            GoogleLogoIcon(modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Masuk dengan Google", color = Color(0xFF334155), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Manajer Unduhan
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Manajer Unduhan Offline", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { startQuranDownloadService(context, com.asyuhada.quran.data.download.QuranDownloadService.ACTION_DOWNLOAD_MUSHAF, mushafSource) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECFDF5), contentColor = Color(0xFF047857)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Unduh Seluruh Mushaf", fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = { startQuranDownloadService(context, com.asyuhada.quran.data.download.QuranDownloadService.ACTION_DOWNLOAD_AUDIO, reciter) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0FDF4), contentColor = Color(0xFF166534)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Unduh Seluruh Audio", fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Button(
                                    onClick = { startQuranDownloadService(context, com.asyuhada.quran.data.download.QuranDownloadService.ACTION_DOWNLOAD_TEXTS, "all") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF1D4ED8)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Unduh Seluruh Teks Arab, Terjemahan & Tafsir", fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                                }

                                val activeOrPausedDownloadsRaw = allDownloads.filter {
                                    (it.type == "MUSHAF_ALL" || it.type == "AUDIO_ALL" || it.type == "TEXTS_ALL" || (it.type == "AUDIO" && it.targetId.toIntOrNull() != null))
                                }
                                
                                val activeOrPausedDownloads = activeOrPausedDownloadsRaw.mapNotNull { dl ->
                                    val actualProgress = when (dl.type) {
                                        "MUSHAF_ALL" -> (getOfflineMushafCount(context, mushafSource) * 100) / 604
                                        "AUDIO_ALL" -> (getOfflineAyahCount(context, reciter) * 100) / 6236
                                        else -> dl.progress
                                    }
                                    if (actualProgress >= 100) null else dl to actualProgress
                                }

                                if (activeOrPausedDownloads.isEmpty()) {
                                    Text("Tidak ada unduhan aktif.", fontSize = 13.sp, color = Color(0xFF94A3B8), fontStyle = FontStyle.Italic)
                                } else {
                                    activeOrPausedDownloads.forEach { (dl, actualProgress) ->
                                        val isPaused = dl.status == "PAUSED"
                                        val progressColor = if (isPaused) Color(0xFFF59E0B) else Color(0xFF059669)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp)).padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                val title = when (dl.type) {
                                                    "MUSHAF_ALL" -> "Mushaf Seluruh Al-Quran"
                                                    "AUDIO_ALL" -> "Audio Seluruh Al-Quran"
                                                    "TEXTS_ALL" -> "Teks Arab & Tafsir Al-Quran"
                                                    else -> "Surah ${dl.targetId}"
                                                }
                                                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                                Text(if (isPaused) "Dijeda (${actualProgress}%)" else "Mengunduh (${actualProgress}%)", fontSize = 12.sp, color = progressColor)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                androidx.compose.material3.LinearProgressIndicator(
                                                    progress = { actualProgress / 100f },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = progressColor,
                                                    trackColor = Color(0xFFE2E8F0)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            IconButton(
                                                onClick = {
                                                    val targetIdInt = dl.targetId.toIntOrNull() ?: 0
                                                    if (dl.type == "MUSHAF_ALL") {
                                                        if (isPaused) {
                                                            app.downloadManager.resumeBulkDownload(dl.downloadKey)
                                                            startQuranDownloadService(context, com.asyuhada.quran.data.download.QuranDownloadService.ACTION_DOWNLOAD_MUSHAF, mushafSource)
                                                        } else {
                                                            app.downloadManager.pauseBulkDownload(dl.downloadKey)
                                                        }
                                                    } else if (dl.type == "AUDIO_ALL") {
                                                        if (isPaused) {
                                                            app.downloadManager.resumeBulkDownload(dl.downloadKey)
                                                            startQuranDownloadService(context, com.asyuhada.quran.data.download.QuranDownloadService.ACTION_DOWNLOAD_AUDIO, reciter)
                                                        } else {
                                                            app.downloadManager.pauseBulkDownload(dl.downloadKey)
                                                        }
                                                    } else if (dl.type == "TEXTS_ALL") {
                                                        if (isPaused) {
                                                            app.downloadManager.resumeBulkDownload(dl.downloadKey)
                                                            startQuranDownloadService(context, com.asyuhada.quran.data.download.QuranDownloadService.ACTION_DOWNLOAD_TEXTS, "all")
                                                        } else {
                                                            app.downloadManager.pauseBulkDownload(dl.downloadKey)
                                                        }
                                                    } else if (dl.type == "AUDIO") {
                                                        if (isPaused) {
                                                            app.downloadManager.resumeAudioDownload(targetIdInt, reciter)
                                                            coroutineScope.launch {
                                                                withContext(Dispatchers.IO) { dao.insertDownloadProgress(dl.copy(status = "DOWNLOADING")) }
                                                                withContext(Dispatchers.IO) { app.downloadManager.downloadSurahAudio(targetIdInt, reciter) }
                                                            }
                                                        } else {
                                                            app.downloadManager.pauseAudioDownload(targetIdInt, reciter)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp).background(if (isPaused) Color(0xFF10B981) else Color(0xFFF59E0B), CircleShape)
                                            ) {
                                                Icon(imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Preferensi Tampilan
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tampilan & Teks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Text("Sumber Mushaf Offline", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                                Row(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)).padding(4.dp)) {
                                    val isStandard = settings?.mushafSource != "tajweed"
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (isStandard) Color.White else Color.Transparent).clickable { if (!isStandard) pendingMushafSourceChange = "standard" }, contentAlignment = Alignment.Center) {
                                        Text("Standard", fontSize = 13.sp, fontWeight = if (isStandard) FontWeight.Bold else FontWeight.Normal, color = if (isStandard) Color(0xFF059669) else Color(0xFF64748B))
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (!isStandard) Color.White else Color.Transparent).clickable { if (isStandard) pendingMushafSourceChange = "tajweed" }, contentAlignment = Alignment.Center) {
                                        Text("Tajweed", fontSize = 13.sp, fontWeight = if (!isStandard) FontWeight.Bold else FontWeight.Normal, color = if (!isStandard) Color(0xFF059669) else Color(0xFF64748B))
                                    }
                                }

                                Text("Ukuran Font Arab (${settings?.arabicFontSize ?: 32}px)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("A-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    androidx.compose.material3.Slider(
                                        value = (settings?.arabicFontSize ?: 32).toFloat(),
                                        onValueChange = { newVal ->
                                            val curr = settings ?: QuranSettingsEntity(userId = userIdInput.ifBlank { "default_dev_user" })
                                            coroutineScope.launch(Dispatchers.IO) {
                                                dao.insertSettings(curr.copy(arabicFontSize = newVal.toInt(), isDirty = true, updatedAt = System.currentTimeMillis()))
                                            }
                                        },
                                        valueRange = 20f..60f,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color(0xFF059669), activeTrackColor = Color(0xFF059669))
                                    )
                                    Text("A+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        // 4. Murottal (Audio Reciter)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Qari / Reciter (Maks 1 Offline)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Box(
                                    modifier = Modifier.fillMaxWidth().height(240.dp).background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp)).border(width = 1.dp, color = Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp))
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(RECITER_OPTIONS.size) { index ->
                                            val (id, name) = RECITER_OPTIONS[index]
                                            val offlineCount = remember(id, activeSurahDownloadProgress, allSurahsDownloadProgress) { getOfflineAyahCount(context, id) }
                                            val offlinePercent = (offlineCount * 100) / 6236
                                            val isLocal = offlineCount > 0

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { if (reciter != id) pendingReciterChange = id }
                                                    .background(if (reciter == id) Color(0xFFECFDF5) else Color.Transparent)
                                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                            ) {
                                                RadioButton(
                                                    selected = reciter == id,
                                                    onClick = null,
                                                    modifier = Modifier.size(20.dp),
                                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Color(0xFF059669))
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(name, fontSize = 14.sp, fontWeight = if (reciter == id) FontWeight.Bold else FontWeight.Medium, color = if (reciter == id) Color(0xFF065F46) else Color(0xFF334155))
                                                    if (isLocal) {
                                                        Text("Tersedia Offline (${offlinePercent}%)", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                            if (index < RECITER_OPTIONS.size - 1) {
                                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pendingMushafSourceChange != null) {
                    AlertDialog(
                        onDismissRequest = { pendingMushafSourceChange = null },
                        title = { Text("Peringatan Ganti Mushaf", fontWeight = FontWeight.Bold) },
                        text = { Text("Mengganti Sumber Mushaf akan menghapus gambar mushaf offline sebelumnya dan mengulangi proses unduh dari awal. Lanjutkan?") },
                        confirmButton = {
                            Button(onClick = {
                                val newSource = pendingMushafSourceChange!!
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        val oldSource = settings?.mushafSource ?: "standard"
                                        app.downloadManager.deleteOldMushaf(oldSource)
                                        val curr = settings ?: QuranSettingsEntity(userId = userIdInput.ifBlank { "default_dev_user" })
                                        dao.insertSettings(curr.copy(mushafSource = newSource, updatedAt = System.currentTimeMillis(), isDirty = true))
                                    }
                                    pendingMushafSourceChange = null
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                                Text("Ya, Ganti & Hapus")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingMushafSourceChange = null }) { Text("Batal") }
                        }
                    )
                }
                
                if (pendingReciterChange != null) {
                    val newReciterName = RECITER_OPTIONS.find { it.first == pendingReciterChange }?.second ?: pendingReciterChange!!
                    AlertDialog(
                        onDismissRequest = { pendingReciterChange = null },
                        title = { Text("Peringatan Ganti Qari", fontWeight = FontWeight.Bold) },
                        text = { Text("Mengganti Qari ke $newReciterName akan menghapus file audio offline Qari sebelumnya dan mengulangi progresnya dari awal. Lanjutkan?") },
                        confirmButton = {
                            Button(onClick = {
                                val newReciter = pendingReciterChange!!
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        val oldReciter = settings?.audioReciter ?: "Alafasy_128kbps"
                                        app.downloadManager.deleteOldAudio(oldReciter)
                                        val curr = settings ?: QuranSettingsEntity(userId = userIdInput.ifBlank { "default_dev_user" })
                                        dao.insertSettings(curr.copy(audioReciter = newReciter, updatedAt = System.currentTimeMillis(), isDirty = true))
                                    }
                                    pendingReciterChange = null
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                                  Text("Ya, Ganti & Hapus")
                              }
                          },
                          dismissButton = {
                              TextButton(onClick = { pendingReciterChange = null }) { Text("Batal") }
                          }
                      )
                  }
                } // End of Settings ModalBottomSheet
            }

            // K. Bookmarks Dialog
            if (showBookmarksDialog) {
                AlertDialog(
                    onDismissRequest = { showBookmarksDialog = false },
                    title = { Text("Daftar Penanda (Bookmark)", fontWeight = FontWeight.Bold) },
                    text = {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(10) { index ->
                                val slotIndex = index + 1
                                val slotColor = when (slotIndex) {
                                    1 -> Color(0xFFEF4444)
                                    2 -> Color(0xFFF97316)
                                    3 -> Color(0xFFEAB308)
                                    4 -> Color(0xFF22C55E)
                                    5 -> Color(0xFF14B8A6)
                                    6 -> Color(0xFF3B82F6)
                                    7 -> Color(0xFF6366F1)
                                    8 -> Color(0xFFA855F7)
                                    9 -> Color(0xFFEC4899)
                                    else -> Color(0xFF854D0E)
                                }

                                val b = bookmarks.find { it.bookmarkIndex == slotIndex }
                                val parsed = parseBookmarkLabel(b?.label, slotIndex)
                                val isRealBookmark = b != null && parsed.hasRealBookmark

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(slotColor, shape = RoundedCornerShape(8.dp))
                                        )

                                        Column {
                                            val textToDisplay = if (isRealBookmark) {
                                                val surahName = SURAH_NAMES.getOrNull(b!!.surahNumber - 1) ?: "Surah"
                                                val juz = getJuzNumber(b.pageNumber)
                                                "${parsed.customName} | Juz $juz, $surahName: ${b.ayahNumber} (Hlm ${b.pageNumber})"
                                            } else {
                                                "${parsed.customName} | Kosong"
                                            }
                                            Text(
                                                text = textToDisplay,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingSlotIndex = slotIndex
                                                editingCustomNameInput = parsed.customName
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Label",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (isRealBookmark) {
                                            TextButton(
                                                onClick = {
                                                    showBookmarksDialog = false
                                                    currentPage = b!!.pageNumber
                                                    selectedAyah = "${b.surahNumber}:${b.ayahNumber}"
                                                    isDetailsDrawerOpen = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Lompat", fontSize = 11.sp)
                                            }

                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            if (b != null) {
                                                                val entity = b.copy(
                                                                    surahNumber = 1,
                                                                    ayahNumber = 1,
                                                                    pageNumber = 1,
                                                                    label = "${parsed.customName} || Kosong",
                                                                    updatedAt = System.currentTimeMillis(),
                                                                    isDirty = true
                                                                )
                                                                dao.insertBookmark(entity)
                                                            }
                                                        }
                                                        Toast.makeText(context, "Slot $slotIndex dikosongkan!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Hapus",
                                                    tint = Color.Red,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBookmarksDialog = false }) {
                            Text("Tutup")
                        }
                    }
                )
            }

            if (editingSlotIndex != null) {
                AlertDialog(
                    onDismissRequest = { editingSlotIndex = null },
                    title = { Text("Edit Label Bookmark ${editingSlotIndex}", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = editingCustomNameInput,
                            onValueChange = { editingCustomNameInput = it },
                            label = { Text("Nama Bookmark") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val slot = editingSlotIndex!!
                                val newName = editingCustomNameInput.trim().ifBlank { "Bookmark $slot" }
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        val b = bookmarks.find { it.bookmarkIndex == slot }
                                        if (b != null) {
                                            val parsed = parseBookmarkLabel(b.label, slot)
                                            val labelStr = "$newName || ${parsed.displayLabel}"
                                            dao.insertBookmark(b.copy(label = labelStr, isDirty = true))
                                        } else {
                                            val slotColor = when (slot) {
                                                1 -> Color(0xFFEF4444)
                                                2 -> Color(0xFFF97316)
                                                3 -> Color(0xFFEAB308)
                                                4 -> Color(0xFF22C55E)
                                                5 -> Color(0xFF14B8A6)
                                                6 -> Color(0xFF3B82F6)
                                                7 -> Color(0xFF6366F1)
                                                8 -> Color(0xFFA855F7)
                                                9 -> Color(0xFFEC4899)
                                                else -> Color(0xFF854D0E)
                                            }
                                            val hexColor = String.format("#%06X", (0xFFFFFF and slotColor.value.toInt()))
                                            val entity = QuranBookmarkEntity(
                                                bookmarkIndex = slot,
                                                surahNumber = 1,
                                                ayahNumber = 1,
                                                pageNumber = 1,
                                                color = hexColor,
                                                label = "$newName || Kosong",
                                                isDirty = true
                                            )
                                            dao.insertBookmark(entity)
                                        }
                                    }
                                    editingSlotIndex = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Simpan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingSlotIndex = null }) {
                            Text("Batal")
                        }
                    }
                )
            }

            // L. Page Jump Dialog
            if (showPageDialog) {
                var pageTextInput by remember { mutableStateOf("$currentPage") }
                AlertDialog(
                    onDismissRequest = { showPageDialog = false },
                    title = { Text("Lompat ke Halaman (1 - 604)") },
                    text = {
                        OutlinedTextField(
                            value = pageTextInput,
                            onValueChange = { pageTextInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showPageDialog = false
                                val targetPage = pageTextInput.toIntOrNull()
                                if (targetPage != null && targetPage in 1..604) {
                                    currentPage = targetPage
                                    selectedAyah = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Lompat")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPageDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            // M. Bilingual Search Dialog
            if (showSearchDialog) {
                var searchQuery by remember { mutableStateOf("") }
                var searchResults by remember { mutableStateOf<List<TranslationEntity>>(emptyList()) }
                var isSearching by remember { mutableStateOf(false) }
                
                var searchHistory by remember {
                    mutableStateOf<List<String>>(
                        sharedPref.getString("search_history", "")
                            ?.split("||")
                            ?.filter { it.isNotBlank() }
                            ?.take(5) ?: emptyList()
                    )
                }

                val performSearch: (String) -> Unit = { queryText ->
                    val queryTrimmed = queryText.trim()
                    if (queryTrimmed.isNotBlank()) {
                        isSearching = true
                        
                        val updatedHistory = (listOf(queryTrimmed) + searchHistory)
                            .distinct()
                            .take(5)
                        searchHistory = updatedHistory
                        sharedPref.edit().putString("search_history", updatedHistory.joinToString("||")).apply()
                        
                        coroutineScope.launch {
                            searchResults = withContext(Dispatchers.IO) {
                                if (isOnline) {
                                    try {
                                        val isArabicQuery = isArabic(queryTrimmed)
                                        val resp = if (isArabicQuery) {
                                            app.apiService.searchArabic(queryTrimmed)
                                        } else {
                                            app.apiService.searchIndonesian(queryTrimmed)
                                        }
                                        if (resp.isSuccessful && resp.body() != null) {
                                            val matches = resp.body()!!.data.matches
                                            matches.map { match ->
                                                val key = "${match.surah.number}:${match.numberInSurah}"
                                                val local = dao.getTranslationByAyah(key)
                                                if (local != null && local.textAr.isNotEmpty() && local.textId.isNotEmpty()) {
                                                    local
                                                } else {
                                                    TranslationEntity(
                                                        ayahKey = key,
                                                        surahNumber = match.surah.number,
                                                        ayahNumber = match.numberInSurah,
                                                        textAr = if (isArabicQuery) match.text else (local?.textAr ?: ""),
                                                        textId = if (!isArabicQuery) match.text else (local?.textId ?: "[Ketuk untuk memuat terjemahan...]")
                                                    )
                                                }
                                            }
                                        } else {
                                            dao.searchTranslation(queryTrimmed)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Online search failed", e)
                                        dao.searchTranslation(queryTrimmed)
                                    }
                                } else {
                                    dao.searchTranslation(queryTrimmed)
                                }
                            }
                            isSearching = false
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { showSearchDialog = false },
                    title = { Text("Cari Ayat (Arab / Indonesia)", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 450.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Ketik kata kunci...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = { performSearch(searchQuery) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                ) {
                                    Text("Cari")
                                }
                            }

                            if (searchHistory.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Riwayat:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    searchHistory.forEach { historyQuery ->
                                        SuggestionChip(
                                            onClick = {
                                                searchQuery = historyQuery
                                                performSearch(historyQuery)
                                            },
                                            label = { Text(historyQuery, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }

                            if (searchResults.isNotEmpty()) {
                                Text(
                                    text = "Ditemukan ${searchResults.size} hasil pencarian",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }

                            if (isSearching) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF059669))
                                }
                            } else {
                                if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                                    Text("Tidak ada hasil ditemukan.", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Color.Gray)
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(searchResults) { result ->
                                            val sName = SURAH_NAMES.getOrNull(result.surahNumber - 1) ?: "Surah"
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EFE9)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        currentPage = QuranPageHelper.getPageForAyah(result.surahNumber, result.ayahNumber)
                                                        selectedAyah = "${result.surahNumber}:${result.ayahNumber}"
                                                        isDetailsDrawerOpen = true
                                                        showSearchDialog = false
                                                        isUiVisible = true
                                                    }
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Surah $sName: ${result.ayahNumber}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF059669)
                                                    )
                                                    Text(
                                                        text = result.textAr,
                                                        fontSize = 16.sp,
                                                        fontFamily = AmiriFontFamily,
                                                        textAlign = TextAlign.Right,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Text(
                                                        text = result.textId,
                                                        fontSize = 12.sp,
                                                        lineHeight = 16.sp,
                                                        color = Color.DarkGray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSearchDialog = false }) {
                            Text("Tutup")
                        }
                    }
                )
            }

        }
    }

@Composable
fun BookmarkSlotChip(
    slotIndex: Int,
    surah: Int,
    ayah: Int,
    surahName: String,
    currentPage: Int,
    bookmarks: List<QuranBookmarkEntity>,
    dao: com.asyuhada.quran.data.db.dao.QuranDao,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    val slotColor = when (slotIndex) {
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF97316)
        3 -> Color(0xFFEAB308)
        4 -> Color(0xFF22C55E)
        5 -> Color(0xFF14B8A6)
        6 -> Color(0xFF3B82F6)
        7 -> Color(0xFF6366F1)
        8 -> Color(0xFFA855F7)
        9 -> Color(0xFFEC4899)
        else -> Color(0xFF854D0E)
    }
    val b = bookmarks.find { it.bookmarkIndex == slotIndex }
    val parsed = parseBookmarkLabel(b?.label, slotIndex)
    val isBookmarkedHere = b != null && b.surahNumber == surah && b.ayahNumber == ayah

    Row(
        modifier = Modifier
            .background(
                color = if (isBookmarkedHere) slotColor else slotColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable {
                coroutineScope.launch {
                    if (isBookmarkedHere) {
                        withContext(Dispatchers.IO) {
                            if (b != null) {
                                val entity = b.copy(
                                    surahNumber = 1,
                                    ayahNumber = 1,
                                    pageNumber = 1,
                                    label = "${parsed.customName} || Kosong",
                                    updatedAt = System.currentTimeMillis(),
                                    isDirty = true
                                )
                                dao.insertBookmark(entity)
                            }
                        }
                        Toast.makeText(context, "Slot $slotIndex dikosongkan!", Toast.LENGTH_SHORT).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            val labelStr = "${parsed.customName} || $surahName: $ayah (Hlm $currentPage)"
                            val entity = b?.copy(
                                surahNumber = surah,
                                ayahNumber = ayah,
                                pageNumber = currentPage,
                                label = labelStr,
                                isDirty = true
                            ) ?: QuranBookmarkEntity(
                                bookmarkIndex = slotIndex,
                                surahNumber = surah,
                                ayahNumber = ayah,
                                pageNumber = currentPage,
                                color = String.format("#%06X", (0xFFFFFF and slotColor.value.toInt())),
                                label = labelStr,
                                isDirty = true
                            )
                            dao.insertBookmark(entity)
                        }
                        Toast.makeText(context, "Disimpan di ${parsed.customName}!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isBookmarkedHere) Color.White else slotColor,
                    shape = RoundedCornerShape(50)
                )
        )
        Text(
            text = parsed.customName,
            color = if (isBookmarkedHere) Color.White else Color(0xFF272117),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun getOfflineMushafCount(context: Context, mushafSource: String): Int {
    val mushafDir = File(context.filesDir, "mushaf")
    if (!mushafDir.exists() || !mushafDir.isDirectory) return 0
    var count = 0
    mushafDir.walkTopDown().forEach { file ->
        if (file.isFile && file.name.startsWith("page_${mushafSource}_") && file.name.endsWith(".webp") && file.length() > 0) {
            count++
        }
    }
    return count
}

fun getOfflineAyahCount(context: Context, reciterId: String): Int {
    val qoriDir = File(context.filesDir, "audio/$reciterId")
    if (!qoriDir.exists() || !qoriDir.isDirectory) return 0
    var count = 0
    qoriDir.walkTopDown().forEach { file ->
        if (file.isFile && file.name.endsWith(".mp3")) {
            count++
        }
    }
    return count
}

fun hasOtherOfflineQori(context: Context, activeReciterId: String): Boolean {
    val audioDir = File(context.filesDir, "audio")
    if (!audioDir.exists() || !audioDir.isDirectory) return false
    val subdirs = audioDir.listFiles() ?: return false
    for (subdir in subdirs) {
        if (subdir.isDirectory && subdir.name != activeReciterId) {
            val hasMp3 = subdir.walkTopDown().any { it.isFile && it.name.endsWith(".mp3") }
            if (hasMp3) return true
        }
    }
    return false
}

fun isArabic(text: String): Boolean {
    for (char in text) {
        val block = Character.UnicodeBlock.of(char)
        if (block == Character.UnicodeBlock.ARABIC ||
            block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
            block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B ||
            block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
        ) {
            return true
        }
    }
    return false
}

@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = width / 2f
        val center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f)
        
        // Google Blue: #4285F4
        // Google Red: #EA4335
        // Google Yellow: #FBBC05
        // Google Green: #34A853

        val pathBlue = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                startAngleDegrees = -45f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }
        val pathGreen = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                startAngleDegrees = 45f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }
        val pathYellow = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                startAngleDegrees = 135f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }
        val pathRed = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                startAngleDegrees = 225f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }
        val innerCircle = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(width * 0.25f, height * 0.25f, width * 0.75f, height * 0.75f))
        }

        drawPath(pathBlue, color = Color(0xFF4285F4))
        drawPath(pathGreen, color = Color(0xFF34A853))
        drawPath(pathYellow, color = Color(0xFFFBBC05))
        drawPath(pathRed, color = Color(0xFFEA4335))
        drawPath(innerCircle, color = Color.White, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        
        // Add the blue rectangle for the 'G' bar
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.42f),
            size = androidx.compose.ui.geometry.Size(width * 0.45f, height * 0.16f)
        )
        
        // Cut out the top-right triangle gap (simplification for a custom vector)
        val gapPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.5f, 0f)
            lineTo(width, 0f)
            lineTo(width, height * 0.42f)
            lineTo(width * 0.5f, height * 0.42f)
            close()
        }
        drawPath(gapPath, color = Color.White, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
    }
}











