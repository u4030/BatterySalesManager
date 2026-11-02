package com.batterysales.di

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Singleton
import kotlin.concurrent.thread

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // =========================================================================
    // CHANGE THIS IP TO YOUR COMPUTER'S LOCAL WI-FI IP ADDRESS
    // For Android Emulator: use "10.0.2.2"
    // For Physical Phone via Wi-Fi: use your local IP, e.g. "172.29.52.107"
    // Set to null or empty "" to disable emulator and connect to Production Firebase
    // =========================================================================
    const val EMULATOR_HOST_IP = "172.30.2.65" // <-- Place your computer's local Wi-Fi IP address here

    private const val USE_EMULATOR = true

    private fun runEmulatorConnectivityCheck(host: String, port: Int = 8080) {
        thread {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 2500)
                    Log.d("PERF_CHECK", "🚀 [Emulator] Successfully connected to Local Emulator Suite at $host:$port!")
                }
            } catch (e: Exception) {
                Log.e(
                    "PERF_CHECK",
                    "❌ [Emulator] Failed to connect to Local Emulator Suite at $host:$port. Reason: ${e.message}. Ensure Firebase Emulator is running, PC Firewall allows port $port, and both PC & phone are on the same Wi-Fi network."
                )
            }
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        val auth = FirebaseAuth.getInstance()
        if (USE_EMULATOR && EMULATOR_HOST_IP.isNotBlank()) {
            try {
                auth.useEmulator(EMULATOR_HOST_IP, 9099)
            } catch (e: Exception) {
                // Ignore if emulator already set
            }
        }
        return auth
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        
        // Enable Offline Persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        firestore.firestoreSettings = settings

        if (USE_EMULATOR && EMULATOR_HOST_IP.isNotBlank()) {
            runEmulatorConnectivityCheck(EMULATOR_HOST_IP, 8080)
            try {
                firestore.useEmulator(EMULATOR_HOST_IP, 8080)
            } catch (e: Exception) {
                // Ignore if emulator already set
            }
        }
        
        return firestore
    }
}
