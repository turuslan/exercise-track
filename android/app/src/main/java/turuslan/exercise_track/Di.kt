package turuslan.exercise_track

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class Di(context: Context, scope: CoroutineScope) {
    val db = Db.make(context, scope)
    val dao = db.dao()
    val mdns = Mdns(context, scope)
    val sync = Sync(scope)

    init {
        scope.launch { eventMdnsChange.collect { sync.syncAll() } }
        scope.launch { eventDevicesChange.collect { sync.syncAll() } }
        scope.launch { mdns.advertise(sync.server()) }
    }
}

var _di: Di? = null
val di get() = _di!!

val json = Json.Default
