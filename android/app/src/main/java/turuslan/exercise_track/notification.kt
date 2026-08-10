package turuslan.exercise_track

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val intentActionAddTime = "turuslan.exercise_track.ADD_TIME"

suspend fun runNotification(context: Context, dataDay: StateFlow<DataDay>) {
    val channelId = "notifications"
    val notificationId = 1
    val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE + PendingIntent.FLAG_UPDATE_CURRENT
    val intentOpen = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        pendingIntentFlags
    )
    val intentAddTime = PendingIntent.getBroadcast(
        context,
        2,
        Intent(intentActionAddTime, null, context, MyReceiver::class.java),
        pendingIntentFlags
    )

    data class Ui(val count: Int, val target: Int, val title: String, val text: String)

    val manager = NotificationManagerCompat.from(context)
    val channel = NotificationChannelCompat.Builder(
        channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT
    ).setName(channelId).setSound(null, null).build()
    manager.createNotificationChannel(channel)
    try {
        dataDay.map { data ->
            Ui(
                count = data.count,
                target = data.target,
                title = "${data.slash}${data.ago(", ")}",
                text = data.bar,
            )
        }.distinctUntilChanged().collect { ui ->
            val notification =
                NotificationCompat.Builder(context, channelId).setOngoing(true).setShowWhen(false)
                    .setContentIntent(intentOpen)
                    .setSmallIcon(R.mipmap.ic_launcher).apply {
                        val root = RemoteViews(context.packageName, R.layout.notification)
                        root.setTextViewText(R.id.textView, "${ui.title}\n${ui.text}")
                        root.setOnClickPendingIntent(R.id.button, intentAddTime)
                        setContent(root)
                    }.build()
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                manager.notify(notificationId, notification)
            }
        }
    } finally {
        manager.cancel(notificationId)
    }
}
