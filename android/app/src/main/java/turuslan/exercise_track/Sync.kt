package turuslan.exercise_track

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Sync(val scope: CoroutineScope) {
    val wss = mutableMapOf<String, WebSocketSession>()
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun openWs(deviceUid: String) {
        if (wss.contains(deviceUid)) return
        val address = mdns.devices[deviceUid] ?: return
        client.webSocket("ws://$address/ws") {
            val ws = this
            if (wss.contains(deviceUid)) return@webSocket
            wss[deviceUid] = ws
            // TODO: max
            // TODO: json
            ws.send(Frame.Text("""{"$deviceUid":0}"""))
            for (frame in ws.incoming) {
                // TODO: json
                val json = (frame as Frame.Text).readText()
                // TODO: dbMergeTimeMany
                // TODO: notifyTimeAdded
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
