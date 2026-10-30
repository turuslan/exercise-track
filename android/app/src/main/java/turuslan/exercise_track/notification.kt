package turuslan.exercise_track

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.StateFlow

const val intentActionAddTime = "turuslan.exercise_track.ADD_TIME"

const val pendingIntentFlags =
    PendingIntent.FLAG_IMMUTABLE + PendingIntent.FLAG_UPDATE_CURRENT

class MyNotification(private val context: Context, private val dataDay: StateFlow<DataDay.Ui>) {
    private val channelId = "notifications"
    private val notificationId = 1
    private val intentOpen = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        pendingIntentFlags
    )
    private val intentAddTime = PendingIntent.getBroadcast(
        context,
        2,
        Intent(intentActionAddTime, null, context, MyReceiver::class.java),
        pendingIntentFlags
    )
    private val manager = NotificationManagerCompat.from(context)

    init {
        val channel = NotificationChannelCompat.Builder(
            channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName(channelId).setSound(null, null).build()
        manager.createNotificationChannel(channel)
    }

    private fun makeNotification(ui: DataDay.Ui): Notification {
        return NotificationCompat.Builder(context, channelId).setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(intentOpen)
            .setSmallIcon(R.mipmap.ic_launcher).apply {
                val root = RemoteViews(context.packageName, R.layout.notification)
                root.setChronometer(R.id.chronometer, ui.base, ui.format, ui.started)
                root.setOnClickPendingIntent(R.id.button, intentAddTime)
                setContent(root)
            }.build()
    }

    fun startForeground(service: Service) {
        service.startForeground(notificationId, makeNotification(dataDay.value))
    }

    suspend fun run() {
        try {
            dataDay.collect { ui ->
                if (ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    manager.notify(notificationId, makeNotification(ui))
                }
            }
        } finally {
            manager.cancel(notificationId)
        }
    }
}
