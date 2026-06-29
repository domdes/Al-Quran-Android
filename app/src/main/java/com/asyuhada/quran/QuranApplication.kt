package com.asyuhada.quran

import android.app.Application
import android.content.Context
import com.asyuhada.quran.data.api.QuranApiService
import com.asyuhada.quran.data.db.QuranDatabase
import com.asyuhada.quran.data.download.QuranDownloadManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class QuranApplication : Application() {

    lateinit var database: QuranDatabase
        private set

    lateinit var apiService: QuranApiService
        private set

    lateinit var downloadManager: QuranDownloadManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = QuranDatabase.getDatabase(this)
        val initialUrl = getPortalBaseUrl()
        updateApiServiceUrl(initialUrl)
    }

    fun getPortalBaseUrl(): String {
        // Force use production server as requested
        return "https://quran.asyuhada-jaya.org/"
    }

    fun updateApiServiceUrl(newUrl: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl(newUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(QuranApiService::class.java)
        downloadManager = QuranDownloadManager(this, apiService, database.quranDao())
    }
}
