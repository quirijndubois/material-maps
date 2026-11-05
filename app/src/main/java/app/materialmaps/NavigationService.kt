package app.materialmaps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps navigation going with the screen off or another app in front: Android only keeps
 * delivering location to an app in the background while it runs a foreground service, which
 * comes with an ongoing notification. That notification shows the next turn.
 */
class NavigationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            endRequestsFlow.value++
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notification(this, intent?.getStringExtra(EXTRA_TITLE) ?: "Navigating", intent?.getStringExtra(EXTRA_TEXT))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_END = "app.materialmaps.END_NAVIGATION"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        private val endRequestsFlow = MutableStateFlow(0)

        /** Goes up each time navigation is ended from the notification. */
        val endRequests: StateFlow<Int> = endRequestsFlow

        fun start(context: Context, title: String, text: String?) {
            val intent = Intent(context, NavigationService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** Shows a new next turn without restarting the service. */
        fun update(context: Context, title: String, text: String?) {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(context, title, text))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationService::class.java))
        }

        private fun notification(context: Context, title: String, text: String?): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "The next turn while navigating"
                        setShowBadge(false)
                    }
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val end = PendingIntent.getService(
                context, 1,
                Intent(context, NavigationService::class.java).setAction(ACTION_END),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_navigation)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .addAction(0, "End", end)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
