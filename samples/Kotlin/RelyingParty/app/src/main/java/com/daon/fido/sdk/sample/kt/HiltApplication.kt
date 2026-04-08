package com.daon.fido.sdk.sample.kt

import android.app.Application
import android.util.Log
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.xauth.IXUAF
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp

/** Application class for the sample app. */
@HiltAndroidApp
class HiltApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        retrieveFirebaseToken()
    }

    private fun retrieveFirebaseToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "Firebase token retrieved: $token")
                    Logger.logDebug(TAG, "Firebase token: $token")
                    IXUAF(this, null, null).setPushNotificationServiceToken(token)
                } else {
                    Logger.logError(TAG, "Failed to retrieve Firebase token: ${task.exception}")
                }
            }
        } catch (e: Exception) {
            Logger.logError(TAG, "Firebase not available: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HiltApplication"
    }
}
