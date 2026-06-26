package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.VOICE_CALL_NOTIFICATION_CHANNEL_ID

class VoiceCallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop()
            else -> {
                val assistantName = intent?.getStringExtra(EXTRA_ASSISTANT_NAME).orEmpty().ifBlank { "Lulu" }
                startForegroundCompat(assistantName)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun startForegroundCompat(assistantName: String) {
        isRunning = true
        val notification = buildNotification(assistantName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stop() {
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(assistantName: String): android.app.Notification {
        return NotificationCompat.Builder(this, VOICE_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("Voice call active")
            .setContentText("Talking with $assistantName")
            .setContentIntent(buildLaunchPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun buildLaunchPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val ACTION_START = "me.rerere.rikkahub.action.VOICE_CALL_START"
        private const val ACTION_STOP = "me.rerere.rikkahub.action.VOICE_CALL_STOP"
        private const val EXTRA_ASSISTANT_NAME = "assistant_name"
        private const val NOTIFICATION_ID = 2401

        var isRunning: Boolean = false
            private set

        fun start(context: Context, assistantName: String) {
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ASSISTANT_NAME, assistantName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
