package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Registry to track the latest version of data types.
 * Path: summaries/sync_registry
 */
data class SyncRegistry(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "sync_registry",
    @get:PropertyName("inventoryVersion") @set:PropertyName("inventoryVersion") var inventoryVersion: Long = 0,
    @get:PropertyName("suppliersVersion") @set:PropertyName("suppliersVersion") var suppliersVersion: Long = 0,
    @get:PropertyName("financialVersion") @set:PropertyName("financialVersion") var financialVersion: Long = 0,
    @get:PropertyName("alertsVersion") @set:PropertyName("alertsVersion") var alertsVersion: Long = 0,
    @get:PropertyName("lastModified") @set:PropertyName("lastModified") var lastModified: Date = Date(),
    @get:PropertyName("last_alert_update") @set:PropertyName("last_alert_update") var lastAlertUpdate: Date? = null
)
