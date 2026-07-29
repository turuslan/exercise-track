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
import kotlinx.coroutines.launch
import turuslan.exercise_track.ui.theme.ExerciseTrackTheme
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_di == null) _di = Di(applicationContext, lifecycleScope)
        setContent {
            val padding = Modifier.padding(8.dp)
            ExerciseTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        val times = remember { mutableStateListOf<Time>() }
                        UiDay(times, modifier = padding)
                        UiDevices(modifier = padding)
                    }
                }
            }
        }
    }
}

@Composable
fun UiDevices(modifier: Modifier = Modifier) {
    data class Device(val uid: String)

    val thisDeviceUid = remember { mutableStateOf<String?>(null) }
    val dbDevices = remember { mutableStateListOf<Device>() }
    Column(modifier = modifier) {
        Text("this device uid: ${thisDeviceUid.value ?: "???"}")
        Text("added devices:")
        for (device in dbDevices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val onOff = if (mdns.devices.contains(device.uid)) {
                    "+"
                } else {
                    "-"
                }
                Text("$onOff ${device.uid}", modifier = Modifier.weight(1f))
                Button({
                    dbDevices.remove(device)
                }) { Text("-") }
            }
        }
        Text("unknown devices:")
        for (deviceUid in mdns.devices.keys.sorted()) {
            if (dbDevices.find { d -> d.uid == deviceUid } != null) continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceUid, modifier = Modifier.weight(1f))
                Button({
                    dbDevices.add(Device(deviceUid))
                    dbDevices.sortBy { it.uid }
                }) { Text("+") }
            }
        }
    }
}

@Composable
fun UiDay(times: SnapshotStateList<Time>, modifier: Modifier = Modifier) {
    val target = 10
    val fontSize = 32.sp
    val count = times.size
    val bar1 = "#".repeat(min(count, target))
    val bar2 = ".".repeat(max(target - count, 0))
    val bar3 = "+".repeat(max(count - target, 0))
    val ago = "??:?? ago"
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        flowTimeAdded.collect { devices ->
            devices.forEach { (_, deviceTimes) ->
                deviceTimes.forEach {
                    if (!times.contains(it)) {
                        times.add(it)
                    }
                }
            }
        }
    }
    Column(modifier = modifier) {
        Button(
            onClick = {
                scope.launch {
                    flowTimeAdded.emit(mapOf(Pair("_android", listOf(times.max() + 1L))))
                }
            }, shape = RoundedCornerShape(1.dp)
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
    ExerciseTrackTheme {
        UiDay(times)
    }
}
