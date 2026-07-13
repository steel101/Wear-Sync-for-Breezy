package com.steel101.wearsyncforbreezy

import android.app.Application

class BreezyWearSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        System.setProperty("io.netty.noUnsafe", "true")
        System.setProperty("io.netty.noPreferDirect", "true")
        System.setProperty("io.netty.transport.noNative", "true")
        System.setProperty("io.netty.leakDetection.level", "DISABLED")
    }
}
