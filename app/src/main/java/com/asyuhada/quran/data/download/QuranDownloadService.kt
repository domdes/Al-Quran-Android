package com.asyuhada.quran.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.asyuhada.quran.MainActivity
import com.asyuhada.quran.QuranApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuranDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    companion object {
        const val CHANNEL_ID = "QuranDownloadChannel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_DOWNLOAD_MUSHAF = "ACTION_DOWNLOAD_MUSHAF"
        const val ACTION_DOWNLOAD_AUDIO = "ACTION_DOWNLOAD_AUDIO"
        const val ACTION_DOWNLOAD_TEXTS = "ACTION_DOWNLOAD_TEXTS"
        
        const val EXTRA_TARGET_ID = "EXTRA_TARGET_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        
        val action = intent.action
        val targetId = intent.getStringExtra(EXTRA_TARGET_ID) ?: return START_NOT_STICKY

        val notification = createNotification(0, "Memulai unduhan...")
        startForeground(NOTIFICATION_ID, notification)

        val app = application as QuranApplication
        
        serviceScope.launch {
            try {
                if (action == ACTION_DOWNLOAD_MUSHAF) {
                    app.downloadManager.downloadAllMushafPages(targetId) { progress ->
                        updateNotification(progress, "Mengunduh Mushaf ($progress%)")
                    }
                } else if (action == ACTION_DOWNLOAD_AUDIO) {
                    app.downloadManager.downloadAllAudio(targetId) { progress ->
                        updateNotification(progress, "Mengunduh Audio ($progress%)")
                    }
                } else if (action == ACTION_DOWNLOAD_TEXTS) {
                    app.downloadManager.downloadAllTextsAndTafsirs { progress ->
                        updateNotification(progress, "Mengunduh Teks & Tafsir ($progress%)")
                    }
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                // Check if any other downloads are running from DB?
                // For simplicity, we just stop the service when the current job is done.
                // In a robust implementation, we would track active jobs.
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unduhan Al-Qur'an",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi progres unduhan Mushaf dan Audio Al-Qur'an"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Unduhan Al-Qur'an")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Int, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(progress, text))
    }
}
