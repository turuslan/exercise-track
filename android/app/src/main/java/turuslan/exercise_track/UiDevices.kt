package turuslan.exercise_track

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

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
