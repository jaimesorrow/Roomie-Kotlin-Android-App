package com.example.roomie

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RoomieMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (!BuildConfig.FIREBASE_CONFIGURED || FirebaseAuth.getInstance().currentUser?.isEmailVerified != true) return
        FirebaseFunctions.getInstance("us-west1").getHttpsCallable("registerDevice").call(mapOf("token" to token))
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message.data["recipientUid"] != uid) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val cid = message.data["conversationId"] ?: return
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("messages", "Roomie messages", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this, MainActivity::class.java).putExtra("conversationId", cid)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, cid.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "messages").setSmallIcon(com.example.roomie.R.drawable.ic_notification)
            .setContentTitle("Roomie").setContentText("You have a new message.").setContentIntent(pending).setAutoCancel(true).build()
        NotificationManagerCompat.from(this).notify(cid.hashCode(), notification)
    }
}
