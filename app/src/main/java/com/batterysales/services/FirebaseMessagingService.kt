package com.batterysales.services // Must match the folder structure

import com.google.firebase.messaging.FirebaseMessagingService

class FirebaseMessagingService : FirebaseMessagingService() {
    // Basic service kept to avoid manifest errors if defined there,
    // but actual logic is handled locally by AppNotificationManager.
}
