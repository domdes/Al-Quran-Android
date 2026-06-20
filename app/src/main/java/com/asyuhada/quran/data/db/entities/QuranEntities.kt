package com.asyuhada.quran.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quran_settings")
data class QuranSettingsEntity(
    @PrimaryKey val userId: String,
    val mushafSource: String = "standard",
    val customUrlTemplate: String = "",
    val arabicFont: String = "Scheherazade New",
    val arabicFontSize: Int = 32,
    val audioReciter: String = "Alafasy_128kbps",
    val lastReadPage: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = false // Set to true when updated offline, to push to server later
)

@Entity(tableName = "quran_bookmarks")
data class QuranBookmarkEntity(
    @PrimaryKey(autoGenerate = false)
    val bookmarkIndex: Int, // Slot 1-10
    val surahNumber: Int,
    val ayahNumber: Int,
    val pageNumber: Int,
    val color: String,
    val label: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = false // Set to true when modified offline, to push to server later
)

@Entity(tableName = "quran_tafsir")
data class TafsirEntity(
    @PrimaryKey val ayahKey: String, // Format "surah:ayah" e.g., "1:1"
    val surahNumber: Int,
    val ayahNumber: Int,
    val tafsirKemenag: String = "",
    val tafsirJalalayn: String = "",
    val tafsirIbnKathirEn: String = "",
    val tafsirIbnKathirAr: String = "",
    val lastFetched: Long = System.currentTimeMillis()
)

@Entity(tableName = "quran_translation")
data class TranslationEntity(
    @PrimaryKey val ayahKey: String, // Format "surah:ayah" e.g., "1:1"
    val surahNumber: Int,
    val ayahNumber: Int,
    val textAr: String = "", // Arabic text
    val textId: String = "", // Indonesian translation
    val lastFetched: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_progress")
data class DownloadProgressEntity(
    @PrimaryKey val downloadKey: String, // e.g. "image_page_001" or "audio_qori_1_1"
    val type: String, // "IMAGE" or "AUDIO"
    val targetId: String, // page number or "surah_number:ayah_number"
    val status: String, // "PENDING", "DOWNLOADING", "COMPLETED", "FAILED"
    val progress: Int = 0, // 0 to 100
    val localFilePath: String = "",
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
