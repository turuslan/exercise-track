package turuslan.exercise_track

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

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
