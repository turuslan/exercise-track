package turuslan.exercise_track

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

fun toDiff(times: List<Time>): List<Time> {
    return times.mapIndexed { i, time ->
        if (i == 0) {
            time
        } else {
            time - times[i - 1]
        }
    }
}

fun toResponse(times: TimesByDevice): TimesResponse {
    val diff = false
    if (diff) {
        return TimesResponse(timesDiff = times.mapValues { toDiff(it.value) })
    }
    return TimesResponse(times = times)
}

class Sync(val scope: CoroutineScope) {
    val wss = mutableMapOf<DeviceUid, WebSocketSession?>()
    val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.websocket.WebSockets)
    }

    suspend fun openWs(deviceUid: DeviceUid) {
        if (wss.contains(deviceUid)) return
        val address = mdns.devices[deviceUid] ?: return
        wss[deviceUid] = null
        client.webSocket("ws://$address/ws") {
            val ws = this
            wss[deviceUid] = ws
            val sendQueue = MutableSharedFlow<String>()
            launch { sendQueue.collect { ws.send(it) } }
            suspend fun sendDevices() {
                val devices = dbMaxTimeAll().mapValues { it.value?.let { it + 1 } ?: 0 }
                ws.send(json.encodeToString(devices))
            }
            sendDevices()
            launch {
                eventDevicesChange.collect {
                    val devices = dbDevices()
                    if (!devices.any { it.uid == deviceUid }) {
                        ws.close()
                        return@collect
                    }
                    sendDevices()
                }
            }
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

suspend fun runServer(db: Db, eventTimeAdded: Flow<TimesByDevice>): Int {
    val server = embeddedServer(io.ktor.server.cio.CIO, port = 0) {
        install(io.ktor.server.websocket.WebSockets)
        routing {
            webSocket("/ws") {
                val ws = this
                var devices: TimeByDevice = mapOf()
                val firstSend = CompletableDeferred<TimesByDevice>()
                val sendQueue = MutableSharedFlow<TimesByDevice>()
                suspend fun send(times: TimesByDevice) {
                    val times = times.filter { !it.value.isEmpty() && devices.contains(it.key) }
                    if (times.isEmpty()) return
                    ws.send(json.encodeToString(toResponse(times)))
                }
                launch {
                    send(firstSend.await())
                    sendQueue.collect { send(it) }
                }
                launch { eventTimeAdded.collect { times -> sendQueue.emit(times) } }
                for (frame in ws.incoming) {
                    devices = json.decodeFromString((frame as Frame.Text).readText())
                    val times = db.ready.await().selectTimeRangeMany(devices)
                    if (!firstSend.isCompleted) {
                        firstSend.complete(times)
                    } else {
                        sendQueue.emit(times)
                    }
                }
            }
        }
    }.start(false)
    return server.engine.resolvedConnectors().first().port
}
