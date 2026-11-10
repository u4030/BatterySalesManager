package com.batterysales.data.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Registry to track the latest version of data types.
 * Path: summaries/sync_registry
 */
class SyncRegistry {
    @PropertyName("id") var id: String = "sync_registry"
    @PropertyName("inventoryVersion") var inventoryVersion: Long = 0
    @PropertyName("inventory_version") var inventoryVersionSnake: Long = 0
    @PropertyName("suppliersVersion") var suppliersVersion: Long = 0
    @PropertyName("suppliers_version") var suppliersVersionSnake: Long = 0
    @PropertyName("financialVersion") var financialVersion: Long = 0
    @PropertyName("financial_version") var financialVersionSnake: Long = 0
    @PropertyName("alertsVersion") var alertsVersion: Long = 0
    @PropertyName("alerts_version") var alertsVersionSnake: Long = 0
    @PropertyName("lastModified") var lastModified: Date = Date()
    @PropertyName("last_modified") var lastModifiedSnake: Date? = null
    @PropertyName("lastAlertUpdate") var lastAlertUpdateCamel: Date? = null
    @PropertyName("last_alert_update") var lastAlertUpdate: Date? = null

    constructor()
    constructor(
        id: String = "sync_registry",
        inventoryVersion: Long = 0,
        inventoryVersionSnake: Long = 0,
        suppliersVersion: Long = 0,
        suppliersVersionSnake: Long = 0,
        financialVersion: Long = 0,
        financialVersionSnake: Long = 0,
        alertsVersion: Long = 0,
        alertsVersionSnake: Long = 0,
        lastModified: Date = Date(),
        lastModifiedSnake: Date? = null,
        lastAlertUpdateCamel: Date? = null,
        lastAlertUpdate: Date? = null
    ) {
        this.id = id
        this.inventoryVersion = inventoryVersion
        this.inventoryVersionSnake = inventoryVersionSnake
        this.suppliersVersion = suppliersVersion
        this.suppliersVersionSnake = suppliersVersionSnake
        this.financialVersion = financialVersion
        this.financialVersionSnake = financialVersionSnake
        this.alertsVersion = alertsVersion
        this.alertsVersionSnake = alertsVersionSnake
        this.lastModified = lastModified
        this.lastModifiedSnake = lastModifiedSnake
        this.lastAlertUpdateCamel = lastAlertUpdateCamel
        this.lastAlertUpdate = lastAlertUpdate
    }

    @get:Exclude
    val resolvedLastModified: Date
        get() = lastModifiedSnake ?: lastModified
}
