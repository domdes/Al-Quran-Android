package com.asyuhada.quran.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asyuhada.quran.data.api.QuranApiService
import com.asyuhada.quran.data.api.QuranBookmarkDto
import com.asyuhada.quran.data.api.QuranSettingsDto
import com.asyuhada.quran.data.api.SyncBookmarksRequest
import com.asyuhada.quran.QuranApplication
import com.asyuhada.quran.data.db.QuranDatabase
import com.asyuhada.quran.data.db.entities.QuranBookmarkEntity
import com.asyuhada.quran.data.db.entities.QuranSettingsEntity
import java.text.SimpleDateFormat
import java.util.*

class QuranSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "QuranSyncWorker"
    private val app = appContext.applicationContext as QuranApplication
    private val apiService = app.apiService
    private val database = app.database
    private val quranDao = database.quranDao()
    
    // ISO 8601 Date formatter to parse updated_at from Next.js server
    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun doWork(): Result {
        val token = inputData.getString("KEY_AUTH_TOKEN") ?: return Result.failure()
        val userId = inputData.getString("KEY_USER_ID") ?: return Result.failure()
        val bearerToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

        Log.d(tag, "Starting Quran offline-to-online sync for user $userId")

        return try {
            // 1. SYNC SETTINGS
            syncSettings(userId, bearerToken)

            // 2. SYNC BOOKMARKS (TWO-WAY MERGE)
            syncBookmarks(bearerToken)

            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun syncSettings(userId: String, bearerToken: String) {
        val localSettings = quranDao.getSettingsSync(userId)

        if (localSettings != null && localSettings.isDirty) {
            // Push local updates to server
            val dto = QuranSettingsDto(
                mushaf_source = localSettings.mushafSource,
                custom_url_template = localSettings.customUrlTemplate,
                arabic_font = localSettings.arabicFont,
                arabic_font_size = localSettings.arabicFontSize,
                audio_reciter = localSettings.audioReciter,
                updated_at = formatIsoDate(localSettings.updatedAt)
            )
            
            val response = apiService.updateSettings(bearerToken, dto)
            if (response.isSuccessful && response.body() != null) {
                val serverSettings = response.body()!!
                quranDao.insertSettings(
                    QuranSettingsEntity(
                        userId = userId,
                        mushafSource = serverSettings.mushaf_source ?: "standard",
                        customUrlTemplate = serverSettings.custom_url_template ?: "",
                        arabicFont = serverSettings.arabic_font ?: "Scheherazade New",
                        arabicFontSize = serverSettings.arabic_font_size ?: 32,
                        audioReciter = serverSettings.audio_reciter ?: "Alafasy_128kbps",
                        updatedAt = parseIsoDate(serverSettings.updated_at ?: ""),
                        isDirty = false
                    )
                )
                Log.d(tag, "Local settings synced and uploaded to server.")
            }
        } else {
            // Fetch newest settings from server
            val response = apiService.getSettings(bearerToken)
            if (response.isSuccessful && response.body() != null) {
                val serverSettings = response.body()!!
                val serverUpdatedAt = parseIsoDate(serverSettings.updated_at ?: "")
                
                if (localSettings == null || serverUpdatedAt > localSettings.updatedAt) {
                    quranDao.insertSettings(
                        QuranSettingsEntity(
                            userId = userId,
                            mushafSource = serverSettings.mushaf_source ?: "standard",
                            customUrlTemplate = serverSettings.custom_url_template ?: "",
                            arabicFont = serverSettings.arabic_font ?: "Scheherazade New",
                            arabicFontSize = serverSettings.arabic_font_size ?: 32,
                            audioReciter = serverSettings.audio_reciter ?: "Alafasy_128kbps",
                            updatedAt = serverUpdatedAt,
                            isDirty = false
                        )
                    )
                    Log.d(tag, "Settings updated from server (Server is newer).")
                }
            }
        }
    }

    private suspend fun syncBookmarks(bearerToken: String) {
        // Find bookmarks updated locally while offline
        val dirtyLocalBookmarks = quranDao.getDirtyBookmarks()

        // Prepare upload request
        val requestList = dirtyLocalBookmarks.map {
            QuranBookmarkDto(
                bookmark_index = it.bookmarkIndex,
                surah_number = it.surahNumber,
                ayah_number = it.ayahNumber,
                page_number = it.pageNumber,
                color = it.color,
                label = it.label,
                updated_at = formatIsoDate(it.updatedAt)
            )
        }

        // Upload and get latest merged state from the server
        val response = apiService.syncBookmarks(bearerToken, SyncBookmarksRequest(requestList))

        if (response.isSuccessful && response.body() != null) {
            val serverBookmarks = response.body()!!
            val localBookmarks = quranDao.getBookmarksSync().associateBy { it.bookmarkIndex }

            val mergedEntities = mutableListOf<QuranBookmarkEntity>()

            // Loop through server records to update local db
            for (serverDto in serverBookmarks) {
                val index = serverDto.bookmark_index ?: continue
                val serverUpdatedAt = parseIsoDate(serverDto.updated_at ?: "")
                val local = localBookmarks[index]

                if (local == null) {
                    // New bookmark from web portal
                    mergedEntities.add(
                        QuranBookmarkEntity(
                            bookmarkIndex = index,
                            surahNumber = serverDto.surah_number ?: 1,
                            ayahNumber = serverDto.ayah_number ?: 1,
                            pageNumber = serverDto.page_number ?: 1,
                            color = serverDto.color ?: "#059669",
                            label = serverDto.label ?: "",
                            updatedAt = serverUpdatedAt,
                            isDirty = false
                        )
                    )
                } else if (!local.isDirty) {
                    // Local isn't modified, overwrite with latest server data
                    mergedEntities.add(
                        QuranBookmarkEntity(
                            bookmarkIndex = index,
                            surahNumber = serverDto.surah_number ?: 1,
                            ayahNumber = serverDto.ayah_number ?: 1,
                            pageNumber = serverDto.page_number ?: 1,
                            color = serverDto.color ?: "#059669",
                            label = serverDto.label ?: "",
                            updatedAt = serverUpdatedAt,
                            isDirty = false
                        )
                    )
                } else {
                    // Local is dirty: compare timestamps
                    if (serverUpdatedAt > local.updatedAt) {
                        // Server is newer (conflict resolved: server wins)
                        mergedEntities.add(
                            QuranBookmarkEntity(
                                bookmarkIndex = index,
                                surahNumber = serverDto.surah_number ?: 1,
                                ayahNumber = serverDto.ayah_number ?: 1,
                                pageNumber = serverDto.page_number ?: 1,
                                color = serverDto.color ?: "#059669",
                                label = serverDto.label ?: "",
                                updatedAt = serverUpdatedAt,
                                isDirty = false
                            )
                        )
                    } else {
                        // Local is newer: keep local dirty state (will sync on next run since server has now received it)
                        mergedEntities.add(local)
                    }
                }
            }

            // Also check if any local bookmarks were deleted on the server
            val serverIndices = serverBookmarks.mapNotNull { it.bookmark_index }.toSet()
            for ((index, localEntity) in localBookmarks) {
                if (!serverIndices.contains(index) && !localEntity.isDirty) {
                    // Server deleted this slot, delete locally
                    quranDao.deleteBookmarkByIndex(index)
                }
            }

            if (mergedEntities.isNotEmpty()) {
                quranDao.insertBookmarks(mergedEntities)
            }
            Log.d(tag, "Two-way Bookmarks Sync completed successfully.")
        } else {
            throw Exception("Failed to sync bookmarks: HTTP ${response.code()}")
        }
    }

    private fun parseIsoDate(dateStr: String): Long {
        return try {
            isoFormatter.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatIsoDate(timestamp: Long): String {
        return isoFormatter.format(Date(timestamp))
    }
}
