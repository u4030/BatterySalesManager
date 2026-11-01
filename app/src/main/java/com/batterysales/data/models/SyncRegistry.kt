package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Registry to track the latest version of data types.
 * Path: summaries/sync_registry
 */
data class SyncRegistry(
    val id: String = "sync_registry",
    val inventoryVersion: Long = 0,
    val suppliersVersion: Long = 0,
    val financialVersion: Long = 0,
    val alertsVersion: Long = 0,
    val lastModified: Date = Date(),
    @get:PropertyName("last_alert_update")
    @set:PropertyName("last_alert_update")
    var lastAlertUpdate: Date? = null
)
