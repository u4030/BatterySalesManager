package com.batterysales.services // Must match the folder structure

import com.batterysales.ui.components.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.notification?.let {
            NotificationHelper.showNotification(
                applicationContext,
                it.title ?: "Battery Sales",
                it.body ?: ""
            )
        } ?: run {
            // Handle data message
            val title = remoteMessage.data["title"] ?: "Battery Sales"
            val body = remoteMessage.data["body"] ?: ""
            if (body.isNotEmpty()) {
                NotificationHelper.showNotification(applicationContext, title, body)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Store token in Firestore if needed for targeted notifications
    }
}