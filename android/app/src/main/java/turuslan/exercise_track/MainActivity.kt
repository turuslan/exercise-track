package turuslan.exercise_track

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import turuslan.exercise_track.ui.theme.ExerciseTrackTheme
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExerciseTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val count = remember { mutableStateOf<Int?>(0) }
                    UiDay(count, modifier = Modifier.padding(innerPadding))
                }
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
    Column(modifier = modifier.padding(8.dp)) {
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
