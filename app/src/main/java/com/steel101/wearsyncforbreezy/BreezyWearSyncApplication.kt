package com.steel101.wearsyncforbreezy

import android.app.Application

import java.security.Security

class BreezyWearSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            // For libadb-android pairing (TLS 1.3)
            val conscryptProvider = Class.forName("org.conscrypt.Conscrypt")
                .getMethod("newProvider")
                .invoke(null) as java.security.Provider
            Security.insertProviderAt(conscryptProvider, 1)
        } catch (_: Exception) {}

        System.setProperty("io.netty.noUnsafe", "true")
        System.setProperty("io.netty.noPreferDirect", "true")
        System.setProperty("io.netty.transport.noNative", "true")
        System.setProperty("io.netty.leakDetection.level", "DISABLED")
    }
}
