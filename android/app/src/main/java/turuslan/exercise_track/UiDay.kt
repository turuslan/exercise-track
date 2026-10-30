package turuslan.exercise_track

import android.view.LayoutInflater
import android.widget.Chronometer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun UiDay(
    modifier: Modifier = Modifier, vm: DayViewModel = hiltViewModel()
) {
    val data = vm.dataDayUi.collectAsState().value
    Column(modifier = modifier) {
        Button(
            onClick = { vm.tryAddTime() },
            shape = RoundedCornerShape(1.dp),
        ) {
            AndroidView({
                LayoutInflater.from(it).inflate(R.layout.ui_day_chronometer, null) as Chronometer
            }, modifier = Modifier.fillMaxWidth()) {
                it.stop()
                // must call `setFormat` then `setBase`
                it.format = data.format
                it.base = data.base
                if (data.started) {
                    it.start()
                }
            }
        }
    }
}
