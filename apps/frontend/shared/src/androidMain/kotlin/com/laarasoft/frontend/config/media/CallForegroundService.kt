package com.laarasoft.frontend.config.media

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Keeps a call alive while the app is in the background. Android only lets an
// app use the microphone from the background inside a foreground service, and
// screen capture (MediaProjection) needs one of type mediaProjection running
// *before* the projection is created.
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!updateForeground()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // Returns false when there is nothing the service may run for.
    private fun updateForeground(): Boolean {
        var types = 0
        if (hasMicPermission(this)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (sharingScreen) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (types == 0) return false

        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
        ready?.complete(Unit)
        return true
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ongoing meeting", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(if (sharingScreen) "Presenting your screen" else "In a meeting")
            .setContentText("Tap to return to the meeting")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "leet_call"
        private const val NOTIFICATION_ID = 1001

        @Volatile private var instance: CallForegroundService? = null
        @Volatile private var sharingScreen = false
        @Volatile private var ready: CompletableDeferred<Unit>? = null

        private fun hasMicPermission(context: Context) =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        // Started when the call starts (the app is in the foreground then,
        // which Android requires). Without mic access there is nothing to keep
        // alive until the user shares their screen.
        fun startCall(context: Context) {
            if (!hasMicPermission(context)) return
            ContextCompat.startForegroundService(context, Intent(context, CallForegroundService::class.java))
        }

        fun endCall(context: Context) {
            sharingScreen = false
            context.stopService(Intent(context, CallForegroundService::class.java))
        }

        // Must finish before MediaProjectionManager.getMediaProjection is called.
        suspend fun startScreenShare(context: Context) {
            sharingScreen = true
            val running = instance
            if (running != null) {
                withContext(Dispatchers.Main) { running.updateForeground() }
                return
            }
            val started = CompletableDeferred<Unit>()
            ready = started
            ContextCompat.startForegroundService(context, Intent(context, CallForegroundService::class.java))
            withTimeoutOrNull(5_000) { started.await() }
            ready = null
        }

        fun stopScreenShare(context: Context) {
            sharingScreen = false
            val running = instance ?: return
            ContextCompat.getMainExecutor(context).execute {
                if (!running.updateForeground()) running.stopSelf()
            }
        }
    }
}
