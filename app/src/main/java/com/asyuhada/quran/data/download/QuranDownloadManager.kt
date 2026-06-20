package com.asyuhada.quran.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.Log
import com.asyuhada.quran.data.api.QuranApiService
import com.asyuhada.quran.data.db.dao.QuranDao
import com.asyuhada.quran.data.db.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class QuranDownloadManager(
    private val context: Context,
    private val apiService: QuranApiService,
    private val quranDao: QuranDao,
    val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val tag = "QuranDownloadManager"

    // Number of verses in each of the 114 Surahs (1-indexed)
    private val surahVerseCounts = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )

    /**
     * Downloads a page image, scales/compresses it based on screen density,
     * and saves it as a WebP file in local storage.
     */
    suspend fun downloadPageImage(pageNumber: Int, mushafSource: String = "standard"): Result<File> = withContext(Dispatchers.IO) {
        val downloadKey = "image_page_${pageNumber}"
        val padPage = String.format("%03d", pageNumber)

        val url = if (mushafSource == "tajweed") {
            "https://raw.githubusercontent.com/NeaByteLab/Quran-Data/main/public/mushaf/ksu-tajweed/${padPage}.png"
        } else {
            "https://raw.githubusercontent.com/GovarJabbar/Quran-PNG/master/${padPage}.png"
        }

        // 0. Periksa pustaka offline terlebih dahulu
        val mushafDir = File(context.filesDir, "mushaf")
        if (!mushafDir.exists()) mushafDir.mkdirs()
        
        val outputFile = File(mushafDir, "page_${mushafSource}_${padPage}.webp")

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(tag, "Halaman $pageNumber ditemukan di pustaka offline.")
            return@withContext Result.success(outputFile)
        }

        try {
            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "IMAGE",
                    targetId = pageNumber.toString(),
                    status = "DOWNLOADING",
                    progress = 10
                )
            )

            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                throw Exception("Failed to download image: HTTP ${response.code}")
            }

            val inputStream: InputStream = response.body!!.byteStream()
            
            // 1. Create target directories (sudah dibuat di atas)
            // val mushafDir = File(context.filesDir, "mushaf")
            // if (!mushafDir.exists()) mushafDir.mkdirs()
            
            // val outputFile = File(mushafDir, "page_${mushafSource}_${padPage}.webp")

            // 2. Decode the downloaded stream to Bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
            }
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options) 
                ?: throw Exception("Failed to decode downloaded stream to Bitmap")

            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "IMAGE",
                    targetId = pageNumber.toString(),
                    status = "DOWNLOADING",
                    progress = 50
                )
            )

            // 3. Compress & Scale based on Screen Density
            val displayMetrics: DisplayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Limit image width to maximum of device width to save storage and RAM
            val targetWidth = minOf(screenWidth, 1080) // Cap at 1080p resolution to avoid loss of clarity
            val scaleFactor = targetWidth.toFloat() / originalBitmap.width
            
            val finalBitmap = if (scaleFactor < 1.0f) {
                // Downscaling
                val targetHeight = (originalBitmap.height * scaleFactor).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            // 4. Save as compressed WebP
            val outputStream = FileOutputStream(outputFile)
            val compressFormat = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            // Quality 80 provides exceptional clarity while dropping file size by 80-90%
            finalBitmap.compress(compressFormat, 80, outputStream)
            outputStream.flush()
            outputStream.close()

            if (finalBitmap != originalBitmap) {
                finalBitmap.recycle()
            }
            originalBitmap.recycle()

            // 5. Download and save interactive coordinates JSON
            try {
                val baseUrl = (context.applicationContext as com.asyuhada.quran.QuranApplication).getPortalBaseUrl()
                val coordsUrl = "${baseUrl}quran/coords/page-${padPage}.json"
                val coordsRequest = Request.Builder().url(coordsUrl).build()
                val coordsResp = okHttpClient.newCall(coordsRequest).execute()
                if (coordsResp.isSuccessful && coordsResp.body != null) {
                    val coordsFile = File(mushafDir, "page_${padPage}_coords.json")
                    val fos = FileOutputStream(coordsFile)
                    fos.write(coordsResp.body!!.bytes())
                    fos.flush()
                    fos.close()
                    Log.d(tag, "Downloaded page $pageNumber coordinates successfully.")
                } else {
                    Log.w(tag, "Failed to download coordinates for page $pageNumber: HTTP ${coordsResp.code}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error downloading coordinates for page $pageNumber", e)
            }

            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "IMAGE",
                    targetId = pageNumber.toString(),
                    status = "COMPLETED",
                    progress = 100,
                    localFilePath = outputFile.absolutePath
                )
            )

            Log.d(tag, "Downloaded and optimized page $pageNumber: ${outputFile.length() / 1024} KB")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(tag, "Error downloading page $pageNumber", e)
            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "IMAGE",
                    targetId = pageNumber.toString(),
                    status = "FAILED",
                    progress = 0,
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    private val pausedDownloads = mutableSetOf<String>()

    fun pauseAudioDownload(surahNumber: Int, reciter: String) {
        pausedDownloads.add("audio_${reciter}_${surahNumber}")
    }

    fun resumeAudioDownload(surahNumber: Int, reciter: String) {
        pausedDownloads.remove("audio_${reciter}_${surahNumber}")
    }

    /**
     * Downloads murattal audio file (.mp3) for a specific Surah or Juz locally.
     */
    suspend fun downloadSurahAudio(surahNumber: Int, reciter: String, onProgress: (progress: Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val totalAyahs = surahVerseCounts[surahNumber - 1]
        var downloadedAyahs = 0
        val surahDownloadKey = "audio_${reciter}_${surahNumber}"

        val audioDir = File(context.filesDir, "audio/$reciter/${surahNumber}")
        if (!audioDir.exists()) audioDir.mkdirs()

        try {
            for (ayah in 1..totalAyahs) {
                if (pausedDownloads.contains(surahDownloadKey)) {
                    quranDao.insertDownloadProgress(
                        DownloadProgressEntity(
                            downloadKey = surahDownloadKey,
                            type = "AUDIO",
                            targetId = surahNumber.toString(),
                            status = "PAUSED",
                            progress = ((downloadedAyahs.toFloat() / totalAyahs) * 100).toInt()
                        )
                    )
                    return@withContext Result.success(Unit)
                }

                val downloadKey = "audio_${reciter}_${surahNumber}_${ayah}"
                val padSurah = String.format("%03d", surahNumber)
                val padAyah = String.format("%03d", ayah)
                val audioFile = File(audioDir, "${padAyah}.mp3")

                if (audioFile.exists() && audioFile.length() > 0) {
                    downloadedAyahs++
                    val totalProgress = ((downloadedAyahs.toFloat() / totalAyahs) * 100).toInt()
                    quranDao.insertDownloadProgress(
                        DownloadProgressEntity(
                            downloadKey = surahDownloadKey,
                            type = "AUDIO",
                            targetId = surahNumber.toString(),
                            status = if (downloadedAyahs == totalAyahs) "COMPLETED" else "DOWNLOADING",
                            progress = totalProgress
                        )
                    )
                    onProgress(totalProgress)
                    continue
                }

                quranDao.insertDownloadProgress(
                    DownloadProgressEntity(
                        downloadKey = surahDownloadKey,
                        type = "AUDIO",
                        targetId = surahNumber.toString(),
                        status = "DOWNLOADING",
                        progress = ((downloadedAyahs.toFloat() / totalAyahs) * 100).toInt()
                    )
                )

                val url = "https://everyayah.com/data/${reciter}/${padSurah}${padAyah}.mp3"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    throw Exception("Failed to download audio $padSurah:$padAyah: HTTP ${response.code}")
                }

                val inputStream = response.body!!.byteStream()
                val fos = FileOutputStream(audioFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
                fos.flush()
                fos.close()
                inputStream.close()

                downloadedAyahs++
                val totalProgress = ((downloadedAyahs.toFloat() / totalAyahs) * 100).toInt()
                quranDao.insertDownloadProgress(
                    DownloadProgressEntity(
                        downloadKey = surahDownloadKey,
                        type = "AUDIO",
                        targetId = surahNumber.toString(),
                        status = if (downloadedAyahs == totalAyahs) "COMPLETED" else "DOWNLOADING",
                        progress = totalProgress
                    )
                )
                onProgress(totalProgress)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error downloading audio for surah $surahNumber", e)
            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = surahDownloadKey,
                    type = "AUDIO",
                    targetId = surahNumber.toString(),
                    status = "FAILED",
                    progress = ((downloadedAyahs.toFloat() / totalAyahs) * 100).toInt(),
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    fun pauseBulkDownload(downloadKey: String) {
        pausedDownloads.add(downloadKey)
    }

    fun resumeBulkDownload(downloadKey: String) {
        pausedDownloads.remove(downloadKey)
    }

    suspend fun deleteOldAudio(reciter: String) = withContext(Dispatchers.IO) {
        try {
            val audioDir = File(context.filesDir, "audio/$reciter")
            if (audioDir.exists()) {
                audioDir.deleteRecursively()
            }
            quranDao.deleteDownloadProgress("audio_all_$reciter")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting old audio", e)
        }
    }

    suspend fun deleteOldMushaf(mushafSource: String) = withContext(Dispatchers.IO) {
        try {
            val mushafDir = File(context.filesDir, "mushaf")
            if (mushafDir.exists()) {
                mushafDir.listFiles { file -> file.name.contains("page_${mushafSource}_") }?.forEach {
                    it.delete()
                }
            }
            quranDao.deleteDownloadProgress("mushaf_all_$mushafSource")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting old mushaf pages", e)
        }
    }

    /**
     * Downloads all 604 pages of the Mushaf.
     */
    suspend fun downloadAllMushafPages(mushafSource: String, onProgress: (progress: Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val totalPages = 604
        var downloadedPages = 0
        var lastReportedProgress = -1
        val downloadKey = "mushaf_all_$mushafSource"
        
        try {
            for (page in 1..totalPages) {
                if (pausedDownloads.contains(downloadKey)) {
                    quranDao.insertDownloadProgress(
                        DownloadProgressEntity(
                            downloadKey = downloadKey,
                            type = "MUSHAF_ALL",
                            targetId = mushafSource,
                            status = "PAUSED",
                            progress = ((downloadedPages.toFloat() / totalPages) * 100).toInt()
                        )
                    )
                    return@withContext Result.success(Unit)
                }

                if (downloadedPages == 0 && lastReportedProgress == -1) {
                    quranDao.insertDownloadProgress(
                        DownloadProgressEntity(
                            downloadKey = downloadKey,
                            type = "MUSHAF_ALL",
                            targetId = mushafSource,
                            status = "DOWNLOADING",
                            progress = 0
                        )
                    )
                }

                // Call the existing single page download to reuse its image scaling logic
                val padPage = String.format("%03d", page)
                val outputFile = File(File(context.filesDir, "mushaf"), "page_${mushafSource}_${padPage}.webp")
                
                if (!outputFile.exists() || outputFile.length() == 0L) {
                    val result = downloadPageImage(page, mushafSource)
                    if (result.isFailure) {
                        throw Exception("Gagal mengunduh halaman $page. Proses dihentikan.")
                    }
                }

                downloadedPages++
                val totalProgress = ((downloadedPages.toFloat() / totalPages) * 100).toInt()
                
                if (totalProgress > lastReportedProgress || downloadedPages == totalPages) {
                    quranDao.insertDownloadProgress(
                        DownloadProgressEntity(
                            downloadKey = downloadKey,
                            type = "MUSHAF_ALL",
                            targetId = mushafSource,
                            status = if (downloadedPages == totalPages) "COMPLETED" else "DOWNLOADING",
                            progress = totalProgress
                        )
                    )
                    onProgress(totalProgress)
                    lastReportedProgress = totalProgress
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error bulk downloading mushaf", e)
            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "MUSHAF_ALL",
                    targetId = mushafSource,
                    status = "FAILED",
                    progress = ((downloadedPages.toFloat() / totalPages) * 100).toInt(),
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Downloads all Murottal audio for the entire Quran (6236 ayahs).
     */
    suspend fun downloadAllAudio(reciter: String, onProgress: (progress: Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val totalAyahsInQuran = 6236
        var downloadedAyahs = 0
        var lastReportedProgress = -1
        val downloadKey = "audio_all_$reciter"

        try {
            if (lastReportedProgress == -1) {
                quranDao.insertDownloadProgress(
                    DownloadProgressEntity(
                        downloadKey = downloadKey,
                        type = "AUDIO_ALL",
                        targetId = reciter,
                        status = "DOWNLOADING",
                        progress = 0
                    )
                )
            }

            for (surahNumber in 1..114) {
                val totalAyahs = surahVerseCounts[surahNumber - 1]
                val audioDir = File(context.filesDir, "audio/$reciter/${surahNumber}")
                if (!audioDir.exists()) audioDir.mkdirs()

                for (ayah in 1..totalAyahs) {
                    if (pausedDownloads.contains(downloadKey)) {
                        quranDao.insertDownloadProgress(
                            DownloadProgressEntity(
                                downloadKey = downloadKey,
                                type = "AUDIO_ALL",
                                targetId = reciter,
                                status = "PAUSED",
                                progress = ((downloadedAyahs.toFloat() / totalAyahsInQuran) * 100).toInt()
                            )
                        )
                        return@withContext Result.success(Unit)
                    }

                    val padSurah = String.format("%03d", surahNumber)
                    val padAyah = String.format("%03d", ayah)
                    val audioFile = File(audioDir, "${padAyah}.mp3")

                    if (audioFile.exists() && audioFile.length() > 0) {
                        downloadedAyahs++
                        val totalProgress = ((downloadedAyahs.toFloat() / totalAyahsInQuran) * 100).toInt()
                        if (totalProgress > lastReportedProgress || downloadedAyahs == totalAyahsInQuran) {
                            quranDao.insertDownloadProgress(
                                DownloadProgressEntity(
                                    downloadKey = downloadKey,
                                    type = "AUDIO_ALL",
                                    targetId = reciter,
                                    status = if (downloadedAyahs == totalAyahsInQuran) "COMPLETED" else "DOWNLOADING",
                                    progress = totalProgress
                                )
                            )
                            onProgress(totalProgress)
                            lastReportedProgress = totalProgress
                        }
                        continue
                    }

                    val url = "https://everyayah.com/data/${reciter}/${padSurah}${padAyah}.mp3"
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful || response.body == null) {
                        throw Exception("Failed to download audio $padSurah:$padAyah: HTTP ${response.code}")
                    }

                    val inputStream = response.body!!.byteStream()
                    val fos = FileOutputStream(audioFile)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                    fos.flush()
                    fos.close()
                    inputStream.close()

                    downloadedAyahs++
                    val totalProgress = ((downloadedAyahs.toFloat() / totalAyahsInQuran) * 100).toInt()
                    
                    if (totalProgress > lastReportedProgress || downloadedAyahs == totalAyahsInQuran) {
                        quranDao.insertDownloadProgress(
                            DownloadProgressEntity(
                                downloadKey = downloadKey,
                                type = "AUDIO_ALL",
                                targetId = reciter,
                                status = if (downloadedAyahs == totalAyahsInQuran) "COMPLETED" else "DOWNLOADING",
                                progress = totalProgress
                            )
                        )
                        onProgress(totalProgress)
                        lastReportedProgress = totalProgress
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error bulk downloading audio", e)
            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "AUDIO_ALL",
                    targetId = reciter,
                    status = "FAILED",
                    progress = ((downloadedAyahs.toFloat() / totalAyahsInQuran) * 100).toInt(),
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Downloads Tafsir & Translation text for all surahs.
     */
    suspend fun downloadAllTextsAndTafsirs(onProgress: (progress: Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val totalSurahs = 114
        var downloadedSurahs = 0
        var lastReportedProgress = -1
        val downloadKey = "texts_all"

        try {
            if (lastReportedProgress == -1) {
                quranDao.insertDownloadProgress(
                    DownloadProgressEntity(
                        downloadKey = downloadKey,
                        type = "TEXTS_ALL",
                        targetId = "all",
                        status = "DOWNLOADING",
                        progress = 0
                    )
                )
            }

            for (surahNumber in 1..totalSurahs) {
                if (pausedDownloads.contains(downloadKey)) {
                    quranDao.insertDownloadProgress(
                        DownloadProgressEntity(
                            downloadKey = downloadKey,
                            type = "TEXTS_ALL",
                            targetId = "all",
                            status = "PAUSED",
                            progress = ((downloadedSurahs.toFloat() / totalSurahs) * 100).toInt()
                        )
                    )
                    return@withContext Result.success(Unit)
                }

                val result = downloadTafsirAndTranslation(surahNumber)
                if (result.isSuccess) {
                    downloadedSurahs++
                    val totalProgress = ((downloadedSurahs.toFloat() / totalSurahs) * 100).toInt()
                    
                    if (totalProgress > lastReportedProgress || downloadedSurahs == totalSurahs) {
                        quranDao.insertDownloadProgress(
                            DownloadProgressEntity(
                                downloadKey = downloadKey,
                                type = "TEXTS_ALL",
                                targetId = "all",
                                status = if (downloadedSurahs == totalSurahs) "COMPLETED" else "DOWNLOADING",
                                progress = totalProgress
                            )
                        )
                        onProgress(totalProgress)
                        lastReportedProgress = totalProgress
                    }
                } else {
                    throw Exception("Failed to download texts for surah $surahNumber")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error bulk downloading texts and tafsirs", e)
            quranDao.insertDownloadProgress(
                DownloadProgressEntity(
                    downloadKey = downloadKey,
                    type = "TEXTS_ALL",
                    targetId = "all",
                    status = "FAILED",
                    progress = ((downloadedSurahs.toFloat() / totalSurahs) * 100).toInt(),
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Downloads Tafsir & Translation text for a surah and updates Room cache DB.
     */
    suspend fun downloadTafsirAndTranslation(surahNumber: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 0. Periksa pustaka offline terlebih dahulu
            val totalAyahs = surahVerseCounts[surahNumber - 1]
            val existingTranslations = quranDao.getTranslationBySurahSync(surahNumber)
            val existingTafsirs = quranDao.getTafsirBySurah(surahNumber)
            
            if (existingTranslations.size == totalAyahs && existingTafsirs.size == totalAyahs) {
                Log.d(tag, "Tafsir & Terjemahan Surah $surahNumber sudah ada di pustaka offline.")
                return@withContext Result.success(Unit)
            }

            // 1. Fetch Indonesian Tafsir (from equran.id)
            val tafsirResponse = apiService.getKemenagTafsir(surahNumber)
            if (tafsirResponse.isSuccessful && tafsirResponse.body() != null) {
                val tafsirList = tafsirResponse.body()!!.data.tafsir
                val entities = tafsirList.map {
                    val key = "${surahNumber}:${it.ayat}"
                    
                    // Maintain existing versions of other tafsirs if already cached
                    val existing = quranDao.getTafsirByAyah(key)
                    TafsirEntity(
                        ayahKey = key,
                        surahNumber = surahNumber,
                        ayahNumber = it.ayat,
                        tafsirKemenag = it.teks,
                        tafsirJalalayn = existing?.tafsirJalalayn ?: "",
                        tafsirIbnKathirEn = existing?.tafsirIbnKathirEn ?: "",
                        tafsirIbnKathirAr = existing?.tafsirIbnKathirAr ?: ""
                    )
                }
                quranDao.insertTafsirs(entities)
            }

            // 2. Fetch Translation and Arabic text for each ayah in the surah
            val translationEntities = mutableListOf<TranslationEntity>()
            
            for (ayah in 1..totalAyahs) {
                val key = "${surahNumber}:${ayah}"
                
                var arabicText = ""
                var indoTranslation = ""
                
                // Fetch basic Arabic text
                val arabicResponse = apiService.getArabicTextAndDetails(key)
                if (arabicResponse.isSuccessful && arabicResponse.body() != null) {
                    arabicText = arabicResponse.body()!!.data.text
                }
                
                // Fetch Indonesian translation
                val transResponse = apiService.getIndonesianTranslation(key)
                if (transResponse.isSuccessful && transResponse.body() != null) {
                    indoTranslation = transResponse.body()!!.data.text
                }
                
                // Fetch Jalalayn Tafsir to enrich details if available
                var jalalaynText = ""
                try {
                    val jalalaynResponse = apiService.getJalalaynTafsir(key)
                    if (jalalaynResponse.isSuccessful && jalalaynResponse.body() != null) {
                        jalalaynText = jalalaynResponse.body()!!.data.text
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching Jalalayn Tafsir for $key", e)
                }

                // Fetch Arabic Ibn Kathir Tafsir to enrich details if available
                var ibnKathirArText = ""
                try {
                    val ibnKathirResponse = apiService.getArabicIbnKathirTafsir(key)
                    if (ibnKathirResponse.isSuccessful && ibnKathirResponse.body() != null) {
                        ibnKathirArText = ibnKathirResponse.body()!!.tafsir.text
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching Arabic Ibn Kathir Tafsir for $key", e)
                }

                if (jalalaynText.isNotEmpty() || ibnKathirArText.isNotEmpty()) {
                    val existingTafsir = quranDao.getTafsirByAyah(key)
                    val updatedTafsir = TafsirEntity(
                        ayahKey = key,
                        surahNumber = surahNumber,
                        ayahNumber = ayah,
                        tafsirKemenag = existingTafsir?.tafsirKemenag ?: "",
                        tafsirJalalayn = if (jalalaynText.isNotEmpty()) jalalaynText else (existingTafsir?.tafsirJalalayn ?: ""),
                        tafsirIbnKathirEn = existingTafsir?.tafsirIbnKathirEn ?: "",
                        tafsirIbnKathirAr = if (ibnKathirArText.isNotEmpty()) ibnKathirArText else (existingTafsir?.tafsirIbnKathirAr ?: "")
                    )
                    quranDao.insertTafsir(updatedTafsir)
                }

                translationEntities.add(
                    TranslationEntity(
                        ayahKey = key,
                        surahNumber = surahNumber,
                        ayahNumber = ayah,
                        textAr = arabicText,
                        textId = indoTranslation
                    )
                )
            }
            
            quranDao.insertTranslations(translationEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error caching texts for surah $surahNumber", e)
            Result.failure(e)
        }
    }
}
