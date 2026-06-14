package com.databelay.refwatch.wear // Or your app's main package

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// TODO: Settings: buzzer interval, ask for numbers on goal
// TODO: Date time selector on phone chooses wrong numbers
// TODO: replace the video with a functioning one where we use the game log

@HiltAndroidApp
class RefWatchApp : Application() {
    // You can override onCreate() if you need to do other app-level initializations
    override fun onCreate() {
        super.onCreate()
        // Hilt setup is automatic.
        // Other initializations like Timber, etc.
    }
}
