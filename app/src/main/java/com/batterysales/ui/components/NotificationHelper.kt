package com.batterysales.ui.components

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.RingtoneManager
import android.os.Build
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import com.batterysales.MainActivity
import com.batterysales.R

object NotificationHelper {
    private const val CHANNEL_ID = "battery_sales_notifications"
    private const val CHANNEL_NAME = "Battery Sales Notifications"

    fun showNotification(context: Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val fontSizeScale = prefs.getFloat("font_size_scale", 1.0f)
        val isBold = prefs.getBoolean("is_bold", false)

        val styledTitle = SpannableString(title).apply {
            setSpan(RelativeSizeSpan(fontSizeScale), 0, title.length, 0)
            if (isBold) {
                setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
            }
        }

        val styledMessage = SpannableString(message).apply {
            setSpan(RelativeSizeSpan(fontSizeScale), 0, message.length, 0)
            if (isBold) {
                setSpan(StyleSpan(Typeface.BOLD), 0, message.length, 0)
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for stock updates and low stock alerts"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.al_asriya)
            .setContentTitle(styledTitle)
            .setContentText(styledMessage)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
