package com.batterysales

import android.app.Application
import com.batterysales.utils.NetworkHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class BatterySalesApp : Application() {

    @Inject
    lateinit var networkHelper: NetworkHelper

    override fun onCreate() {
        super.onCreate()
        // تهيئة Timber للسجلات إذا كنت تستخدمه
        Timber.plant(Timber.DebugTree())
    }
}
 
