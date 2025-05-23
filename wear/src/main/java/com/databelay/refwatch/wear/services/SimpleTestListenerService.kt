// In wear/src/main/java/.../services/SimpleTestListenerService.kt
package com.databelay.refwatch.wear.services

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class SimpleTestListenerService : WearableListenerService() {
    private val TAG = "SimpleTestListener"
    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "!!!!!!!! SIMPLE TEST LISTENER SERVICE CREATED !!!!!!!!")
    }
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.e(TAG, "!!!!!!!! SIMPLE TEST LISTENER - onDataChanged: ${dataEvents.count} !!!!!!!")
        dataEvents.release()
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "!!!!!!!! SIMPLE TEST LISTENER SERVICE DESTROYED !!!!!!!!")
    }
}

// In wear/src/main/AndroidManifest.xml (add this alongside your other service)
// <service android:name=".services.SimpleTestListenerService" android:exported="true">
//     <intent-filter>
//         <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
//         <data android:scheme="wear" android:host="*" android:pathPattern=".*"/>
//     </intent-filter>
// </service>