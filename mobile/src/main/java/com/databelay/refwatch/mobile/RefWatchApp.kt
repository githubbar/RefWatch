package com.databelay.refwatch.mobile // Or your app's main package

import android.app.Application
import android.util.Log
//import com.google.firebase.FirebaseApp
//import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RefWatchApp : Application()

/*     {
    // You can override onCreate() if you need to do other app-level initializations
    // In MyPhoneApplication.kt (phone app)
    override fun onCreate() {
        super.onCreate()
   Log.e("MyPhoneApp", "!!!!!!!!!! RefWatchApp onCreate - BEFORE Firebase Init !!!!!!!!!!")
        try {
            FirebaseApp.initializeApp(this)
            Log.e("MyPhoneApp", "!!!!!!!!!! FirebaseApp.initializeApp(this) CALLED SUCCESSFULLY !!!!!!!!!!")
            Log.e("MyPhoneApp","Default Firebase App Name: ${FirebaseApp.getInstance().name}")

            // ---- TEMPORARY TEST ----
            try {
                val authTest = FirebaseAuth.getInstance()
                Log.e("MyPhoneApp", "FirebaseAuth.getInstance() in App.onCreate SUCCEEDED! User: ${authTest.currentUser?.uid}")
            } catch (authEx: Exception) {
                Log.e("MyPhoneApp", "FirebaseAuth.getInstance() in App.onCreate FAILED!", authEx)
            }
            // ---- END TEMPORARY TEST ----

        } catch (e: Exception) {
            Log.e("MyPhoneApp", "!!!!!!!!!! FirebaseApp.initializeApp FAILED !!!!!!!!!!", e)
        }
    }
}

*/
