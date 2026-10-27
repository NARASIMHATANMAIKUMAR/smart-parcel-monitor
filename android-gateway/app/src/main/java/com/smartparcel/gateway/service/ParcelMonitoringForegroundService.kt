package com.smartparcel.gateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.smartparcel.gateway.MainActivity
import com.smartparcel.gateway.R
import com.smartparcel.gateway.SmartParcelApplication
import com.smartparcel.gateway.engine.GatewayEngine

/**
 * Foreground Service guaranteeing sustained background monitoring, Bluetooth streaming,
 * and GPS proxy updates during screen-off states. Coordinates directly with GatewayEngine.
 */
class ParcelMonitoringForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "parcel_monitoring_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.smartparcel.gateway.ACTION_START"
        const val ACTION_STOP = "com.smartparcel.gateway.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, ParcelMonitoringForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ParcelMonitoringForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var gatewayEngine: GatewayEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as? SmartParcelApplication
        gatewayEngine = app?.gatewayEngine

        gatewayEngine?.onNotificationUpdate = { statusText ->
            updateStatus(statusText)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartParcel::MonitoringWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // Max 24 hours
                val notification = buildNotification("Monitoring active in transit (SECURE)")
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun updateStatus(statusText: String) {
        val notification = buildNotification(statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Parcel Gateway Active")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_box)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Parcel In-Transit Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Bluetooth serial and GPS proxy active while phone screen is off."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        gatewayEngine?.onNotificationUpdate = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

