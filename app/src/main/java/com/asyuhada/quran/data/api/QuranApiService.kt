package com.asyuhada.quran.data.api

import retrofit2.http.*
import retrofit2.Response

// --- DATA TRANSFER OBJECTS (DTOs) ---

data class QuranSettingsDto(
    val mushaf_source: String?,
    val custom_url_template: String?,
    val arabic_font: String?,
    val arabic_font_size: Int?,
    val audio_reciter: String?,
    val last_read_page: Int?,
    val updated_at: String?
)

data class QuranBookmarkDto(
    val bookmark_index: Int?,
    val surah_number: Int?,
    val ayah_number: Int?,
    val page_number: Int?,
    val color: String?,
    val label: String?,
    val updated_at: String?
)

data class SyncBookmarksRequest(
    val bookmarks: List<QuranBookmarkDto>
)

data class KemenagTafsirResponse(
    val code: Int,
    val message: String,
    val data: KemenagTafsirData
)

data class KemenagTafsirData(
    val nomor: Int,
    val nama: String,
    val tafsir: List<KemenagAyatTafsir>
)

data class KemenagAyatTafsir(
    val ayat: Int,
    val teks: String
)

data class AlquranCloudResponse(
    val code: Int,
    val status: String,
    val data: AlquranCloudAyatData
)

data class AlquranCloudAyatData(
    val number: Int,
    val text: String,
    val page: Int,
    val juz: Int,
    val surah: AlquranCloudSurahInfo
)

data class AlquranCloudSurahInfo(
    val number: Int,
    val name: String,
    val englishName: String,
    val numberOfVerses: Int
)

data class PageCoordinatesResponse(
    val coords: Map<String, WordCoordinate>
)

data class WordCoordinate(
    val h: BoundingBox
)

data class BoundingBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

data class QuranComTafsirResponse(
    val tafsir: QuranComTafsirData
)

data class QuranComTafsirData(
    val id: Int,
    val resource_id: Int,
    val text: String
)

// --- RETROFIT API SERVICE INTERFACE ---

interface QuranApiService {

    // --- NEXT.JS PORTAL API ENDPOINTS ---

    @GET("api/quran/settings")
    suspend fun getSettings(
        @Header("Authorization") token: String
    ): Response<QuranSettingsDto>

    @POST("api/quran/settings")
    suspend fun updateSettings(
        @Header("Authorization") token: String,
        @Body settings: QuranSettingsDto
    ): Response<QuranSettingsDto>

    @GET("api/quran/bookmarks")
    suspend fun getBookmarks(
        @Header("Authorization") token: String
    ): Response<List<QuranBookmarkDto>>

    @POST("api/quran/bookmarks")
    suspend fun syncBookmarks(
        @Header("Authorization") token: String,
        @Body request: SyncBookmarksRequest
    ): Response<List<QuranBookmarkDto>>

    // --- EXTERNAL QURAN API ENDPOINTS ---

    // Kemenag Tafsir from equran.id
    @GET("https://equran.id/api/v2/tafsir/{surahNumber}")
    suspend fun getKemenagTafsir(
        @Path("surahNumber") surahNumber: Int
    ): Response<KemenagTafsirResponse>

    // Indonesian Translation from alquran.cloud
    @GET("https://api.alquran.cloud/v1/ayah/{ayahKey}/id.indonesian")
    suspend fun getIndonesianTranslation(
        @Path("ayahKey") ayahKey: String // Format: "surah:ayah" e.g., "1:1"
    ): Response<AlquranCloudResponse>

    // Arabic text & Page details from alquran.cloud
    @GET("https://api.alquran.cloud/v1/ayah/{ayahKey}")
    suspend fun getArabicTextAndDetails(
        @Path("ayahKey") ayahKey: String // Format: "surah:ayah" e.g., "1:1"
    ): Response<AlquranCloudResponse>

    // Jalalayn Tafsir from alquran.cloud
    @GET("https://api.alquran.cloud/v1/ayah/{ayahKey}/id.jalalayn")
    suspend fun getJalalaynTafsir(
        @Path("ayahKey") ayahKey: String
    ): Response<AlquranCloudResponse>

    // Word coordinates JSON for interactive overlay
    @GET("quran/coords/page-{pagePad3}.json")
    suspend fun getPageCoordinates(
        @Path("pagePad3") pagePad3: String // e.g. "001", "002"
    ): Response<PageCoordinatesResponse>

    // Arabic Ibn Kathir Tafsir from quran.com API
    @GET("https://api.quran.com/api/v4/tafsirs/14/by_ayah/{ayahKey}")
    suspend fun getArabicIbnKathirTafsir(
        @Path("ayahKey") ayahKey: String
    ): Response<QuranComTafsirResponse>

    // Bilingual Search from alquran.cloud
    @GET("https://api.alquran.cloud/v1/search/{query}/all/quran-simple-clean")
    suspend fun searchArabic(
        @Path("query") query: String
    ): Response<QuranSearchResponse>

    @GET("https://api.alquran.cloud/v1/search/{query}/all/id.indonesian")
    suspend fun searchIndonesian(
        @Path("query") query: String
    ): Response<QuranSearchResponse>
}

data class QuranSearchResponse(
    val code: Int,
    val status: String,
    val data: QuranSearchData
)

data class QuranSearchData(
    val count: Int,
    val matches: List<QuranSearchMatch>
)

data class QuranSearchMatch(
    val number: Int,
    val text: String,
    val surah: QuranSearchSurahInfo,
    val numberInSurah: Int
)

data class QuranSearchSurahInfo(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val numberOfVerses: Int,
    val revelationType: String
)

