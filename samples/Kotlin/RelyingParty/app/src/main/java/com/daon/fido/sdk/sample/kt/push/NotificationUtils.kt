package com.daon.fido.sdk.sample.kt.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.daon.fido.sdk.sample.kt.R

/**
 * Utility class for displaying push notifications.
 *
 * Handles notification channel creation (required for Android 8.0+) and building/displaying
 * notifications with proper styling.
 */
class NotificationUtils(private val context: Context) {

    private var notificationManager: NotificationManager? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultChannel =
                NotificationChannel(
                        DEFAULT_CHANNEL,
                        context.getString(R.string.notification_channel_name_default),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                    .apply {
                        lightColor = Color.GREEN
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }

            getNotificationManager().createNotificationChannel(defaultChannel)
        }
    }

    fun showNotificationMessage(title: String, message: String, intent: Intent) {
        if (message.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, DEFAULT_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        getNotificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun getNotificationManager(): NotificationManager {
        if (notificationManager == null) {
            notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return notificationManager!!
    }

    companion object {
        const val NOTIFICATION_ID = 100
        const val DEFAULT_CHANNEL = "fido_push_channel"
    }
}
