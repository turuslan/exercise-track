package turuslan.exercise_track

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

var _diReady = false
var _db: Db? = null
val db get() = _db!!
val dao get() = db.dao()

fun di(context: Context, scope: CoroutineScope) {
    if (_diReady) return

    _db = Db.make(context, scope)
    val sync = Sync(scope)

    scope.launch { eventMdnsChange.collect { sync.syncAll() } }
    scope.launch { eventDevicesChange.collect { sync.syncAll() } }
    scope.launch { mdns.advertise(sync.server()) }
    _diReady = true
}

val json = Json.Default
