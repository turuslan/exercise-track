package turuslan.exercise_track

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Di(context: Context, scope: CoroutineScope) {
    val mdns = Mdns(context, scope)
    val sync = Sync(scope)

    init {
        scope.launch { mdns.flow.collect { sync.syncAll() } }
    }
}

var _di: Di? = null
val di get() = _di!!
