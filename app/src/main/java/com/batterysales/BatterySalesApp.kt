package com.batterysales

import android.app.Application
import com.batterysales.utils.NetworkHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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

        // تهيئة وتثبيت مصنع App Check
        initAppCheck()
    }

    private fun initAppCheck() {
        try {
            FirebaseApp.initializeApp(this)
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Timber.d("AppCheck: DebugAppCheckProviderFactory installed.")
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Timber.d("AppCheck: PlayIntegrityAppCheckProviderFactory installed.")
            }
        } catch (e: Exception) {
            Timber.e(e, "AppCheck initialization failed: ${e.message}")
        }
    }
}
 
