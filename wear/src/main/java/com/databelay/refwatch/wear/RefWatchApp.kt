package com.databelay.refwatch.wear // Or your app's main package

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RefWatchApp : Application() {
    // You can override onCreate() if you need to do other app-level initializations
    override fun onCreate() {
        super.onCreate()
        // Hilt setup is automatic.
        // Other initializations like Timber, etc.
    }
}