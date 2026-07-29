package turuslan.exercise_track

import android.content.Context
import kotlinx.coroutines.CoroutineScope

class Di(context: Context,scope: CoroutineScope) {
    val mdns = Mdns(context,scope)
}

var _di: Di? = null
val di get() = _di!!
