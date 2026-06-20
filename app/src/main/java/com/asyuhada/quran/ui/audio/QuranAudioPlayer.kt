package com.asyuhada.quran.ui.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class QuranAudioPlayer(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    
    var onAyahCompleted: (() -> Unit)? = null
    var onPlaybackStateChanged: ((isPlaying: Boolean, isLoading: Boolean) -> Unit)? = null

    init {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isPlayingNow = exoPlayer?.isPlaying == true
                    val isLoading = playbackState == Player.STATE_BUFFERING
                    onPlaybackStateChanged?.invoke(isPlayingNow, isLoading)
                    
                    if (playbackState == Player.STATE_ENDED) {
                        onAyahCompleted?.invoke()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val isLoading = exoPlayer?.playbackState == Player.STATE_BUFFERING
                    onPlaybackStateChanged?.invoke(isPlaying, isLoading)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("QuranAudioPlayer", "ExoPlayer error: ${error.message}", error)
                    onPlaybackStateChanged?.invoke(false, false)
                }
            })
        }
    }

    fun playAyah(surah: Int, ayah: Int, reciter: String) {
        val padSurah = String.format("%03d", surah)
        val padAyah = String.format("%03d", ayah)
        
        // Check local file cache first
        val localFile = File(context.filesDir, "audio/$reciter/$surah/$padAyah.mp3")
        val mediaUri = if (localFile.exists() && localFile.length() > 0) {
            Uri.fromFile(localFile)
        } else {
            // Fallback to online streaming
            Uri.parse("https://everyayah.com/data/$reciter/$padSurah$padAyah.mp3")
        }

        exoPlayer?.let { player ->
            player.stop()
            val mediaItem = MediaItem.fromUri(mediaUri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }
}
