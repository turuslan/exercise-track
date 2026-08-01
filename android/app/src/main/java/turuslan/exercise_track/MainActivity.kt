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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    val ago get() = last?.let { " ${time_scale.ago(it, now)}" } ?: ""

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
            val times = dbSelectTimeRange(data.value.min, data.value.max, null)
                .values.flatten().sorted()
            data.value = data.value.copy(times = times)
        }
    }
    LaunchedEffect(Unit) {
        eventTimeAdded.collect { devices ->
            val added = devices.values.flatten()
                .filter { data.value.shouldChange(it) }
            if (added.isEmpty()) return@collect
            val times = data.value.times.toMutableList()
            times.addAll(added)
            times.sort()
            data.value = data.value.copy(times = times)
        }
    }
    UiDay(data, modifier) {
        scope.launch { eventTimeAdded.emit(dbInsertTime(it)) }
    }
}

@Composable
fun UiDay(
    data: State<DataDay>,
    modifier: Modifier = Modifier,
    addTime: (Time) -> Unit = {}
) {
    val fontSize = 32.sp
    val data = data.value
    Column(modifier = modifier) {
        Button(
            onClick = { data.mayAdd()?.let { addTime(it) } },
            shape = RoundedCornerShape(1.dp),
        ) {
            Text(
                "${data.slash}${data.ago}\n${data.bar}",
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
