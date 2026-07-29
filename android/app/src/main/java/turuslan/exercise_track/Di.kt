package turuslan.exercise_track

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

val flowTimeAdded = MutableSharedFlow<TimesByDevice>()

class Di(context: Context, scope: CoroutineScope) {
    val mdns = Mdns(context, scope)
    val sync = Sync(scope)

    init {
        scope.launch { mdns.flow.collect { sync.syncAll() } }
    }
}

var _di: Di? = null
val di get() = _di!!

val json = Json {}

typealias Time = Long
typealias TimeByDevice = Map<String, Time>
typealias TimesByDevice = Map<String, List<Time>>
