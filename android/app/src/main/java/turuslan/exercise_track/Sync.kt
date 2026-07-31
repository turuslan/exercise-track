package turuslan.exercise_track

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimesResponse(
    val times: TimesByDevice? = null,
    @SerialName("times_diff") val timesDiff: TimesByDevice? = null,
)

fun fromDiff(timesDiff: List<Long>): List<Long> {
    val times = mutableListOf<Long>()
    var sum = 0L
    for (diff in timesDiff) {
        sum += diff
        times.add(diff)
    }
    return times
}

fun fromResponse(res: TimesResponse): TimesByDevice {
    if (res.timesDiff != null) {
        return res.timesDiff.mapValues { fromDiff(it.value) }
    }
    return res.times!!
}

class Sync(val scope: CoroutineScope) {
    val wss = mutableMapOf<DeviceUid, WebSocketSession>()
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun openWs(deviceUid: DeviceUid) {
        if (wss.contains(deviceUid)) return
        val address = mdns.devices[deviceUid] ?: return
        client.webSocket("ws://$address/ws") {
            val ws = this
            if (wss.contains(deviceUid)) return@webSocket
            wss[deviceUid] = ws
            // TODO: max
            val devices = mapOf(Pair(deviceUid, 0))
            ws.send(Frame.Text(json.encodeToString(devices)))
            for (frame in ws.incoming) {
                val times = fromResponse(json.decodeFromString((frame as Frame.Text).readText()))
                // TODO: dbMergeTimeMany
                val added = times
                flowTimeAdded.emit(added)
            }
            wss.remove(deviceUid)
        }
    }

    fun syncAll() {
        // TODO: db devices
        val devices = listOf("_debug1001")

        for (deviceUid in devices) {
            scope.launch { openWs(deviceUid) }
        }
    }
}
