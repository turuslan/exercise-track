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
    val wss = mutableMapOf<DeviceUid, WebSocketSession?>()
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun openWs(deviceUid: DeviceUid) {
        if (wss.contains(deviceUid)) return
        val address = mdns.devices[deviceUid] ?: return
        wss[deviceUid] = null
        client.webSocket("ws://$address/ws") {
            val ws = this
            if (wss.contains(deviceUid)) return@webSocket
            wss[deviceUid] = ws
            val devices = dbMaxTimeAll()
            ws.send(Frame.Text(json.encodeToString(devices)))
            for (frame in ws.incoming) {
                val times = fromResponse(json.decodeFromString((frame as Frame.Text).readText()))
                val added = dbMergeTimeMany(times)
                eventTimeAdded.emit(added)
            }
            wss.remove(deviceUid)
        }
    }

    suspend fun syncAll() {
        for (device in dbDevices()) {
            scope.launch { openWs(device.uid) }
        }
    }
}
