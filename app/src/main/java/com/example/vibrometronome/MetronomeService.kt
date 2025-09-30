// MetronomeService.kt
package com.example.vibrometronome

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MetronomeService : Service() {
    private lateinit var vibrator: Vibrator
    private var scheduler: ScheduledExecutorService? = null
    private var vibrateTask: ScheduledFuture<*>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentBpm = 120

    companion object {
        const val CHANNEL_ID = "MetronomeChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_UPDATE_BPM = "UPDATE_BPM"
        const val EXTRA_BPM = "BPM"
    }

    override fun onCreate() {
        super.onCreate()
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Acquire wake lock to keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VibroMetronome::MetronomeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentBpm = intent.getIntExtra(EXTRA_BPM, 120)
                startForeground(NOTIFICATION_ID, createNotification())
                startVibrating()
            }
            ACTION_STOP -> {
                stopVibrating()
                stopForeground(true)
                stopSelf()
            }
            ACTION_UPDATE_BPM -> {
                currentBpm = intent.getIntExtra(EXTRA_BPM, 120)
                stopVibrating()
                startVibrating()
            }
        }
        return START_STICKY
    }

    private fun startVibrating() {
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max

        scheduler = Executors.newSingleThreadScheduledExecutor()
        val intervalMs = (60000 / currentBpm).toLong()

        vibrateTask = scheduler?.scheduleAtFixedRate({
            vibrate()
        }, 0, intervalMs, TimeUnit.MILLISECONDS)
    }

    private fun stopVibrating() {
        vibrateTask?.cancel(true)
        scheduler?.shutdown()
        scheduler = null
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metronome Running")
            .setContentText("$currentBpm BPM")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopVibrating()
    }
}
