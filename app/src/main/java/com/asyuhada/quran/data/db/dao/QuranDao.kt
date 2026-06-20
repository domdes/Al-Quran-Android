package com.asyuhada.quran.data.db.dao

import androidx.room.*
import com.asyuhada.quran.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranDao {

    // --- SETTINGS ---
    @Query("SELECT * FROM quran_settings WHERE userId = :userId LIMIT 1")
    fun getSettings(userId: String): Flow<QuranSettingsEntity?>

    @Query("SELECT * FROM quran_settings LIMIT 1")
    fun getSettingsFlow(): Flow<QuranSettingsEntity?>

    @Query("SELECT * FROM quran_settings WHERE userId = :userId LIMIT 1")
    fun getSettingsSync(userId: String): QuranSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: QuranSettingsEntity)

    @Query("SELECT * FROM quran_settings WHERE isDirty = 1")
    suspend fun getDirtySettings(): List<QuranSettingsEntity>

    // --- BOOKMARKS ---
    @Query("SELECT * FROM quran_bookmarks ORDER BY bookmarkIndex ASC")
    fun getBookmarks(): Flow<List<QuranBookmarkEntity>>

    @Query("SELECT * FROM quran_bookmarks ORDER BY bookmarkIndex ASC")
    fun getBookmarksFlow(): Flow<List<QuranBookmarkEntity>>

    @Query("SELECT * FROM quran_bookmarks ORDER BY bookmarkIndex ASC")
    suspend fun getBookmarksSync(): List<QuranBookmarkEntity>

    @Query("SELECT * FROM quran_bookmarks WHERE bookmarkIndex = :index LIMIT 1")
    suspend fun getBookmarkByIndex(index: Int): QuranBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: QuranBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<QuranBookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: QuranBookmarkEntity)

    @Query("DELETE FROM quran_bookmarks WHERE bookmarkIndex = :index")
    suspend fun deleteBookmarkByIndex(index: Int)

    @Query("SELECT * FROM quran_bookmarks WHERE isDirty = 1")
    suspend fun getDirtyBookmarks(): List<QuranBookmarkEntity>

    @Query("DELETE FROM quran_bookmarks")
    suspend fun clearAllBookmarks()

    // --- TAFSIR CACHE ---
    @Query("SELECT * FROM quran_tafsir WHERE ayahKey = :ayahKey LIMIT 1")
    suspend fun getTafsirByAyah(ayahKey: String): TafsirEntity?

    @Query("SELECT * FROM quran_tafsir WHERE surahNumber = :surahNumber ORDER BY ayahNumber ASC")
    suspend fun getTafsirBySurah(surahNumber: Int): List<TafsirEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTafsir(tafsir: TafsirEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTafsirs(tafsirs: List<TafsirEntity>)

    // Search in Indonesian Tafsir (Kemenag or Jalalayn) offline
    @Query("SELECT * FROM quran_tafsir WHERE tafsirKemenag LIKE '%' || :query || '%' OR tafsirJalalayn LIKE '%' || :query || '%'")
    suspend fun searchTafsir(query: String): List<TafsirEntity>

    // --- TRANSLATION CACHE ---
    @Query("SELECT * FROM quran_translation WHERE ayahKey = :ayahKey LIMIT 1")
    suspend fun getTranslationByAyah(ayahKey: String): TranslationEntity?

    @Query("SELECT * FROM quran_translation WHERE ayahKey = :ayahKey LIMIT 1")
    fun getTranslationByAyahFlow(ayahKey: String): Flow<TranslationEntity?>

    @Query("SELECT * FROM quran_translation WHERE surahNumber = :surahNumber ORDER BY ayahNumber ASC")
    suspend fun getTranslationBySurahSync(surahNumber: Int): List<TranslationEntity>

    @Query("SELECT * FROM quran_translation WHERE surahNumber = :surahNumber ORDER BY ayahNumber ASC")
    suspend fun getTranslationsBySurah(surahNumber: Int): List<TranslationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<TranslationEntity>)

    @Query("SELECT * FROM quran_translation WHERE textId LIKE '%' || :query || '%' OR textAr LIKE '%' || :query || '%'")
    suspend fun searchTranslation(query: String): List<TranslationEntity>

    // --- DOWNLOAD TRACKING ---
    @Query("SELECT * FROM download_progress WHERE downloadKey = :key LIMIT 1")
    fun getDownloadProgress(key: String): Flow<DownloadProgressEntity?>

    @Query("SELECT * FROM download_progress WHERE downloadKey = :key LIMIT 1")
    suspend fun getDownloadProgressSync(key: String): DownloadProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadProgress(progress: DownloadProgressEntity)

    @Query("SELECT * FROM download_progress WHERE type = :type")
    fun getDownloadsByType(type: String): Flow<List<DownloadProgressEntity>>

    @Query("SELECT * FROM download_progress")
    fun getAllDownloads(): Flow<List<DownloadProgressEntity>>

    @Query("SELECT * FROM download_progress")
    suspend fun getAllDownloadsSync(): List<DownloadProgressEntity>

    @Query("DELETE FROM download_progress WHERE downloadKey = :key")
    suspend fun deleteDownloadProgress(key: String)

    @Query("UPDATE download_progress SET status = 'PAUSED' WHERE status = 'DOWNLOADING'")
    suspend fun pauseAllActiveDownloads()
}
