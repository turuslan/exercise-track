package turuslan.exercise_track

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun UiPower(modifier: Modifier = Modifier, vm: PowerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                vm.enableNetwork.collectAsState().value,
                { vm.setEnableNetwork(it) },
            )
            Text("Network")
        }
        Button(
            onClick = {
                val activity = context.findActivity()
                activity?.finishAndRemoveTask()
                activity?.stopService(myServiceIntent(context))
                vm.exit()
            },
            shape = RoundedCornerShape(1.dp),
        ) { Text("Exit") }
    }
}
