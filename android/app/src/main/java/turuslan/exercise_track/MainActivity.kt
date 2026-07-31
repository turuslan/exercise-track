package turuslan.exercise_track

import android.os.Bundle
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
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import turuslan.exercise_track.ui.theme.ExerciseTrackTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

const val DUPLICATE_TIME_MS = 5000

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_di == null) _di = Di(applicationContext, lifecycleScope)
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
    val now = remember { mutableLongStateOf(timeNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = timeNow()
            delay((time_scale.nextTick(now.longValue) - now.longValue).milliseconds)
        }
    }
    val min = remember { derivedStateOf { time_scale.getGroup(now.longValue) } }
    val max = remember { derivedStateOf { time_scale.nextGroup(now.longValue) } }
    // TODO: db
    val times = remember { mutableStateListOf<Time>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        eventTimeAdded.collect { devices ->
            devices.values.forEach {
                for (time in it) {
                    if (times.contains(time)) continue
                    if (time !in min.value..max.value) continue
                    times.add(time)
                }
                times.sort()
            }
        }
    }
    UiDay(times, now, modifier) {
        scope.launch {
            // TODO: db
            eventTimeAdded.emit(mapOf(Pair("_android", listOf(it))))
        }
    }
}

@Composable
fun UiDay(
    times: SnapshotStateList<Time>,
    now: MutableLongState,
    modifier: Modifier = Modifier,
    addTime: (Time) -> Unit = {}
) {
    val target = 10
    val fontSize = 32.sp
    val count = times.size
    val last = times.lastOrNull()
    val bar1 = "#".repeat(min(count, target))
    val bar2 = ".".repeat(max(target - count, 0))
    val bar3 = "+".repeat(max(count - target, 0))
    val ago = last?.let { time_scale.ago(it, now.longValue) } ?: ""
    Column(modifier = modifier) {
        Button(
            onClick = {
                val time = timeNow()
                if (last?.let { DUPLICATE_TIME_MS <= differenceInMilliseconds(time, it) } ?: true) {
                    addTime(time)
                }
            },
            shape = RoundedCornerShape(1.dp),
        ) {
            Text(
                "$count/$target $ago\n[$bar1$bar2$bar3]",
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
fun GreetingPreview() {
    val times = remember { mutableStateListOf(100L, 200L, 300L) }
    val now = remember { mutableLongStateOf(0) }
    ExerciseTrackTheme {
        UiDay(times, now)
    }
}
