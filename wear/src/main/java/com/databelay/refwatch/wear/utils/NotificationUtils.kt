package com.databelay.refwatch.wear.utils // Or your chosen utility package

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

// Define constants for the notification channel at the top level of this file
// or in a separate object/companion object if you prefer them grouped.
const val ONGOING_NOTIFICATION_CHANNEL_ID = "refwatch_ongoing_timer_channel"
const val ONGOING_NOTIFICATION_CHANNEL_NAME = "RefWatch Timer"
// The notification ID itself is often managed where the notification is built,
// but if it's always the same for this specific ongoing activity, it can be here too.
const val ONGOING_NOTIFICATION_ID_VM = 123

/**
 * Creates the notification channel for the ongoing timer notification if it doesn't already exist.
 * This function should ideally be called once when the application starts (e.g., in Application.onCreate()
 * or MainActivity.onCreate()), but can also be called before showing a notification to ensure
 * the channel exists, as NotificationManager handles redundant creations gracefully.
 *
 * @param context The application or activity context.
 */
fun createOngoingTimerNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if the channel already exists; creating an existing channel with same ID does no harm.
        if (notificationManager.getNotificationChannel(ONGOING_NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                ONGOING_NOTIFICATION_CHANNEL_ID,
                ONGOING_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance as it's for an ongoing task icon
            ).apply {
                description = "Shows when the RefWatch game timer is active."
                // You can set other channel properties here if needed, e.g.,
                // enableLights(true)
                // lightColor = Color.BLUE
                // enableVibration(false) // Often ongoing notifications don't vibrate
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
