package com.hiennv.flutter_callkit_incoming

import android.content.Context
import android.media.RingtoneManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log

object CallkitRingtoneManager {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun playRingtone(context: Context) {
        Log.d("CallkitRingtoneManager", "Playing ringtone")

        stopRingtone() // Stop any previous ringtone before playing a new one

        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, ringtoneUri)
            isLooping = true  // ✅ Enable looping
            setOnPreparedListener { start() }
            setOnErrorListener { mp, what, extra ->
                Log.e("CallkitRingtoneManager", "Error playing sound: $what, $extra")
                stopRingtone()
                true
            }
            prepareAsync()
        }

        playVibrator(context)
    }

    fun stopRingtone() {
        Log.d("CallkitRingtoneManager", "Stopping ringtone")

        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        stopVibrator()
    }

    private fun playVibrator(context: Context) {
        Log.d("CallkitRingtoneManager", "Starting vibration")

        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator?.hasVibrator() == true) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0) // 1 sec ON, 1 sec OFF, repeat
            vibrator?.vibrate(effect)
        }
    }

    private fun stopVibrator() {
        Log.d("CallkitRingtoneManager", "Stopping vibration")
        vibrator?.cancel()
        vibrator = null
    }
}