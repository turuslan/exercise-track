package turuslan.exercise_track

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import turuslan.exercise_track.ui.theme.ExerciseTrackTheme
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

const val DUPLICATE_TIME_MS = 5000

data class DataDay(
    val now: Time,
    val times: List<Time>,
) {
    val min get() = time_scale.getGroup(now)
    val max get() = time_scale.nextGroup(now)
    val target = 10
    val count get() = times.size
    val last get() = times.lastOrNull()
    val bar1 get() = "#".repeat(min(count, target))
    val bar2 get() = ".".repeat(max(target - count, 0))
    val bar3 get() = "+".repeat(max(count - target, 0))
    val slash get() = "$count/$target"
    val bar get() = "[${bar1}${bar2}${bar3}]"
    fun ago(prefix: String) = last?.let { "${prefix}${time_scale.ago(it, now)}" } ?: ""

    fun mayAdd(): Time? {
        val time = timeNow()
        val last = last ?: return time
        if (DUPLICATE_TIME_MS <= differenceInMilliseconds(time, last)) {
            return time
        }
        return null
    }

    fun shouldChange(time: Time): Boolean {
        return time in min..max && !times.contains(time)
    }
}

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
}

@AndroidEntryPoint
class MyReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repository: MyRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == intentActionAddTime) {
            repository.tryAddTime()
            return
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val padding = Modifier.padding(8.dp)
            ExerciseTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        UiDay(modifier = padding)
                        UiDevices(modifier = padding)
                    }
                }
            }
        }
    }
}

@Composable
fun UiDevices(modifier: Modifier = Modifier, vm: DevicesViewModel = hiltViewModel()) {
    val thisDeviceUid = vm.thisDeviceUid.collectAsState().value
    val addedDevices = vm.addedDevices.collectAsState().value
    val unknownDevices = vm.unknownDevices.collectAsState().value
    Column(modifier = modifier) {
        Text("this device uid: ${thisDeviceUid ?: "???"}")
        Text("added devices:")
        for (device in addedDevices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val onOff = if (device.online) {
                    "+"
                } else {
                    "-"
                }
                Text("$onOff ${device.uid}", modifier = Modifier.weight(1f))
                Button({ vm.removeDevice(device.uid) }) { Text("-") }
            }
        }
        Text("unknown devices:")
        for (deviceUid in unknownDevices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceUid, modifier = Modifier.weight(1f))
                Button({ vm.addDevice(deviceUid) }) { Text("+") }
            }
        }
    }
}

@Composable
fun UiDay(
    modifier: Modifier = Modifier, vm: DayViewModel = hiltViewModel()
) {
    val fontSize = 32.sp
    val data = vm.dataDay.collectAsState().value
    Column(modifier = modifier) {
        Button(
            onClick = { vm.tryAddTime() },
            shape = RoundedCornerShape(1.dp),
        ) {
            Text(
                "${data.slash}${data.ago(", ")}\n${data.bar}",
                fontFamily = FontFamily.Monospace,
                lineHeight = fontSize,
                fontSize = fontSize,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@HiltAndroidApp
class MyApplication : Application() {}
