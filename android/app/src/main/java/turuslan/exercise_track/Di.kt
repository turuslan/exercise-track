package turuslan.exercise_track

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

val flowTimeAdded = MutableSharedFlow<TimesByDevice>()
val flowMdns = MutableSharedFlow<Unit>()

class Di(context: Context, scope: CoroutineScope) {
    val db = Db.make(context,scope)
    val dao = db.dao()
    val mdns = Mdns(context, scope)
    val sync = Sync(scope)

    init {
        scope.launch { flowMdns.collect { sync.syncAll() } }
    }
}

var _di: Di? = null
val di get() = _di!!

val json = Json.Default
