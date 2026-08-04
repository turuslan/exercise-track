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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import turuslan.exercise_track.ui.theme.ExerciseTrackTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

const val DUPLICATE_TIME_MS = 5000

val dataDay = mutableStateOf(DataDay(timeNow(), listOf()))

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

    suspend fun tryAdd() {
        mayAdd()?.let { eventTimeAdded.emit(dbInsertTime(it)) }
    }
}

val intentActionKey = "intentAction"

val intentActionValueOpen = 1
val intentActionValueAddTime = 2

@Composable
fun UiNotification(data: State<DataDay>) {
    val context = LocalContext.current
    val channelId = "notifications"
    val notificationId = 1
    fun makeIntent(action: Int): PendingIntent {
        val intent = if (action == intentActionValueOpen) {
            Intent(context, MainActivity::class.java)
        } else {
            Intent(
                context, MyReceiver::class.java
            )
        }
        Intent()
        intent.`package` = context.packageName
        intent.putExtra(intentActionKey, action)
        val flags = PendingIntent.FLAG_IMMUTABLE + PendingIntent.FLAG_UPDATE_CURRENT
        if (action == intentActionValueOpen) {
            return PendingIntent.getActivity(context, action, intent, flags)
        }
        return PendingIntent.getBroadcast(context, action, intent, flags)
    }

    data class Ui(val count: Int, val target: Int, val title: String, val text: String)
    LaunchedEffect(Unit) {
        val manager = NotificationManagerCompat.from(context)
        val channel = NotificationChannelCompat.Builder(
            channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName(channelId).setSound(null, null).build()
        manager.createNotificationChannel(channel)
        snapshotFlow { data.value }.map { data ->
            Ui(
                count = data.count,
                target = data.target,
                title = "${data.slash}${data.ago(", ")}",
                text = data.bar,
            )
        }.distinctUntilChanged().collect { ui ->
            val notification =
                NotificationCompat.Builder(context, channelId).setOngoing(true).setShowWhen(false)
                    .setContentIntent(makeIntent(intentActionValueOpen))
                    .setSmallIcon(R.mipmap.ic_launcher).apply {
                        val root = RemoteViews(context.packageName, R.layout.notification)
                        root.setTextViewText(R.id.textView, "${ui.title}\n${ui.text}")
                        root.setOnClickPendingIntent(
                            R.id.button, makeIntent(intentActionValueAddTime)
                        )
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
}

class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getIntExtra(intentActionKey, 0)
        if (action == 0) return
        if (action == intentActionValueOpen) return
        if (action == intentActionValueAddTime) {
            CoroutineScope(Dispatchers.Main).launch { dataDay.value.tryAdd() }
            return
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        di(applicationContext, lifecycleScope)
        setContent {
            val padding = Modifier.padding(8.dp)
            ExerciseTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        UiDayWrap(modifier = padding)
                        UiDevices(modifier = padding)
                    }
                }
            }
        }
    }
}

@Composable
fun UiDevices(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val thisDeviceUid = remember { mutableStateOf<DeviceUid?>(null) }
    val dbDevices = remember { mutableStateOf(listOf<DbDevice>()) }
    LaunchedEffect(Unit) {
        thisDeviceUid.value = dbThisDeviceUid()
        dbDevices.value = dbDevices()
        eventDevicesChange.collect { dbDevices.value = dbDevices() }
    }
    Column(modifier = modifier) {
        Text("this device uid: ${thisDeviceUid.value ?: "???"}")
        Text("added devices:")
        for (device in dbDevices.value) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val onOff = if (mdns.devices.contains(device.uid)) {
                    "+"
                } else {
                    "-"
                }
                Text("$onOff ${device.uid}", modifier = Modifier.weight(1f))
                Button({
                    scope.launch {
                        dbRemoveDevice(device.uid)
                        eventDevicesChange.emit(Unit)
                    }
                }) { Text("-") }
            }
        }
        Text("unknown devices:")
        for (deviceUid in mdns.devices.keys.sorted()) {
            if (dbDevices.value.find { d -> d.uid == deviceUid } != null) continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceUid, modifier = Modifier.weight(1f))
                Button({
                    scope.launch {
                        dbUpsertDevice(deviceUid, deviceUid)
                        eventDevicesChange.emit(Unit)
                    }
                }) { Text("+") }
            }
        }
    }
}

@Composable
fun UiDayWrap(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val data = remember { mutableStateOf(DataDay(timeNow(), listOf())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = timeNow()
            data.value = data.value.copy(now = now)
            delay((time_scale.nextTick(now) - now).milliseconds)
        }
    }
    LaunchedEffect(data.value.min, data.value.max) {
        scope.launch {
            val times =
                dbSelectTimeRange(data.value.min, data.value.max, null).values.flatten().sorted()
            data.value = data.value.copy(times = times)
        }
    }
    LaunchedEffect(Unit) {
        eventTimeAdded.collect { devices ->
            val added = devices.values.flatten().filter { data.value.shouldChange(it) }
            if (added.isEmpty()) return@collect
            val times = data.value.times.toMutableList()
            times.addAll(added)
            times.sort()
            data.value = data.value.copy(times = times)
        }
    }
    UiDay(data, modifier, { scope.launch { data.value.tryAdd() } })
    UiNotification(data)
}

@Composable
fun UiDay(
    data: State<DataDay>, modifier: Modifier = Modifier, addTime: () -> Unit = {}
) {
    val fontSize = 32.sp
    val data = data.value
    Column(modifier = modifier) {
        Button(
            onClick = addTime,
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

@Preview(showBackground = true, widthDp = 340, heightDp = 600)
@Composable
fun Preview() {
    val m = 60000L
    val data = remember { mutableStateOf(DataDay(22 * m, listOf(10 * m, 20 * m))) }
    ExerciseTrackTheme {
        UiDay(data)
    }
}

@HiltAndroidApp
class MyApplication : Application() {}
