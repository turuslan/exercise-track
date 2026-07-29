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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
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
                        val count = remember { mutableStateOf<Int?>(0) }
                        UiDay(count, modifier = padding)
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
fun UiDay(count: MutableState<Int?>, modifier: Modifier = Modifier) {
    val target = 10
    val fontSize = 32.sp
    val countInt = count.value ?: 0
    val bar1 = "#".repeat(min(countInt, target))
    val bar2 = ".".repeat(max(target - countInt, 0))
    val bar3 = "+".repeat(max(countInt - target, 0))
    val ago = "??:?? ago"
    Column(modifier = modifier) {
        Button(onClick = { count.value = countInt + 1 }, shape = RoundedCornerShape(1.dp)) {
            Text(
                "${count.value ?: "??"}/$target $ago\n[$bar1$bar2$bar3]",
                fontFamily = FontFamily.Monospace,
                lineHeight = fontSize,
                fontSize = fontSize,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    val count = remember { mutableStateOf<Int?>(3) }
    ExerciseTrackTheme {
        UiDay(count)
    }
}
