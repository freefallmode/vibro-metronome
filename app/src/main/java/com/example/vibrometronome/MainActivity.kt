// MainActivity.kt
package com.example.vibrometronome

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var bpmText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var isRunning = false
    private var currentBpm = 120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        createNotificationChannel()

        bpmText = findViewById(R.id.bpmText)
        seekBar = findViewById(R.id.bpmSeekBar)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        // Set up SeekBar (40-240 BPM range)
        seekBar.max = 200
        seekBar.progress = 80 // 120 BPM
        updateBpmDisplay(currentBpm)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBpm = progress + 40
                updateBpmDisplay(currentBpm)
                if (isRunning) {
                    updateMetronomeBpm(currentBpm)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        startButton.setOnClickListener {
            startMetronome()
        }

        stopButton.setOnClickListener {
            stopMetronome()
        }

        updateButtonStates()
    }

    private fun startMetronome() {
        isRunning = true
        val intent = Intent(this, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START
            putExtra(MetronomeService.EXTRA_BPM, currentBpm)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateButtonStates()
    }

    private fun stopMetronome() {
        isRunning = false
        val intent = Intent(this, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_STOP
        }
        startService(intent)
        updateButtonStates()
    }

    private fun updateMetronomeBpm(bpm: Int) {
        val intent = Intent(this, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_UPDATE_BPM
            putExtra(MetronomeService.EXTRA_BPM, bpm)
        }
        startService(intent)
    }

    private fun updateBpmDisplay(bpm: Int) {
        bpmText.text = "$bpm BPM"
    }

    private fun updateButtonStates() {
        startButton.isEnabled = !isRunning
        stopButton.isEnabled = isRunning
        seekBar.isEnabled = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MetronomeService.CHANNEL_ID,
                "Metronome Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps metronome running in background"
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isRunning) {
            stopMetronome()
        }
    }
}
