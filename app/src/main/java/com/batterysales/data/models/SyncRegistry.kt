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
    @get:PropertyName("inventory_version") @set:PropertyName("inventory_version") var inventoryVersionSnake: Long = 0,
    @get:PropertyName("suppliersVersion") @set:PropertyName("suppliersVersion") var suppliersVersion: Long = 0,
    @get:PropertyName("suppliers_version") @set:PropertyName("suppliers_version") var suppliersVersionSnake: Long = 0,
    @get:PropertyName("financialVersion") @set:PropertyName("financialVersion") var financialVersion: Long = 0,
    @get:PropertyName("financial_version") @set:PropertyName("financial_version") var financialVersionSnake: Long = 0,
    @get:PropertyName("alertsVersion") @set:PropertyName("alertsVersion") var alertsVersion: Long = 0,
    @get:PropertyName("alerts_version") @set:PropertyName("alerts_version") var alertsVersionSnake: Long = 0,
    @get:PropertyName("lastModified") @set:PropertyName("lastModified") var lastModified: Date = Date(),
    @get:PropertyName("last_modified") @set:PropertyName("last_modified") var lastModifiedSnake: Date? = null,
    @get:PropertyName("lastAlertUpdate") @set:PropertyName("lastAlertUpdate") var lastAlertUpdateCamel: Date? = null,
    @get:PropertyName("last_alert_update") @set:PropertyName("last_alert_update") var lastAlertUpdate: Date? = null
) {
    val resolvedLastModified: Date
        get() = lastModifiedSnake ?: lastModified
}
