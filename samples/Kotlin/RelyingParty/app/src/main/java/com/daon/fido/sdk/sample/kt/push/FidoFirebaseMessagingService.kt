package com.daon.fido.sdk.sample.kt.push

import android.content.Intent
import com.daon.fido.sdk.sample.kt.IntroActivity
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.xauth.IXUAF
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service for handling push notifications.
 *
 * This service receives data-only push messages from the server and displays a system notification.
 * When the user taps the notification, it opens IntroActivity which navigates to the
 * PushAuthenticationScreen.
 */
class FidoFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        Logger.logDebug(TAG, "FidoFirebaseMessagingService created")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Logger.logDebug(TAG, "onMessageReceived from: ${remoteMessage.from}")

        remoteMessage.notification?.let { notification ->
            Logger.logDebug(
                TAG,
                "Notification - title: ${notification.title}, body: ${notification.body}",
            )
        }

        if (remoteMessage.data.isNotEmpty()) {
            Logger.logDebug(TAG, "Data Payload: ${remoteMessage.data}")
            handleData(remoteMessage)
        }
    }

    private fun handleData(msg: RemoteMessage) {
        val description = msg.data["description"]
        val provider = msg.data["provider"]
        val pushType = msg.data["push.type"]
        val id = msg.data["id"]
        val timeToLive = msg.data["time_to_live"]

        Logger.logDebug(
            TAG,
            "Push Data - description: $description, provider: $provider, " +
                "push.type: $pushType, id: $id, time_to_live: $timeToLive",
        )

        if (id.isNullOrEmpty()) {
            Logger.logDebug(TAG, "No transaction ID in push payload, ignoring")
            return
        }

        val intent =
            Intent(this, IntroActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, id)
                action = ACTION_PUSH_AUTHENTICATION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val notificationUtils = NotificationUtils(applicationContext)
        notificationUtils.showNotificationMessage(
            title = provider ?: "Authentication Request",
            message = description ?: "Tap to authenticate",
            intent = intent,
        )
    }

    override fun onNewToken(token: String) {
        Logger.logDebug(TAG, "onNewToken: $token")
        IXUAF(applicationContext, null, null).setPushNotificationServiceToken(token)
    }

    companion object {
        private const val TAG = "FidoFirebaseMsgService"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val ACTION_PUSH_AUTHENTICATION = "com.daon.fido.sdk.sample.kt.PUSH_AUTHENTICATION"
    }
}
