package com.batterysales

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BatterySalesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // تهيئة Timber للسجلات إذا كنت تستخدمه
        Timber.plant(Timber.DebugTree())
    }
}
