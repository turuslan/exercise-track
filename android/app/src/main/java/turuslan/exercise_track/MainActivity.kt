package turuslan.exercise_track

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import turuslan.exercise_track.ui.theme.ExerciseTrackTheme
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_mdns == null) _mdns = Mdns(applicationContext, lifecycleScope)
        setContent {
            val padding = Modifier.padding(8.dp)
            ExerciseTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        val count = remember { mutableStateOf<Int?>(0) }
                        UiDay(count, modifier = padding)
                        UiDevices(modifier = padding)
                    }
                }
            }
        }
    }
}

class Mdns(context: Context, scope: CoroutineScope) {
    val mdnsType = "_exercise-track._tcp"
    val devices = mutableStateMapOf<String, String>()
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val multicastLock =
        wifiManager.createMulticastLock("exercise-track").apply { setReferenceCounted(false) }!!
    val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onServiceFound(service: NsdServiceInfo) {
            scope.launch { resolveReq.send(service) }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            remove(service.serviceName)
        }

        override fun onStartDiscoveryFailed(type: String, e: Int) {}
        override fun onStopDiscoveryFailed(type: String, e: Int) {}
    }
    val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(service: NsdServiceInfo, e: Int) {
            remove(service.serviceName)
            onResolve()
        }

        override fun onServiceResolved(service: NsdServiceInfo) {
            add(service.serviceName, "${service.host.toString().substring(1)}:${service.port}")
            onResolve()
        }

        fun onResolve() {
            scope.launch { resolveRes.send(Unit) }
        }
    }
    val resolveReq = Channel<NsdServiceInfo>()
    val resolveRes = Channel<Unit>()

    suspend fun resolve(service: NsdServiceInfo) {
        nsd.resolveService(service, resolveListener)
        resolveRes.receive()
    }

    init {
        multicastLock.acquire()
        nsd.discoverServices(mdnsType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        scope.launch {
            for (service in resolveReq) {
                resolve(service)
            }
        }
    }

    fun stop() {
        if (multicastLock.isHeld) {
            multicastLock.release()
        }
        nsd.stopServiceDiscovery(discoveryListener)
    }

    fun add(deviceUid: String, address: String) {
        devices[deviceUid] = address
    }

    fun remove(deviceUid: String) {
        devices.remove(deviceUid)
    }
}

var _mdns: Mdns? = null
val mdns get() = _mdns!!

@Composable
fun UiDevices(modifier: Modifier = Modifier) {
    data class Device(val uid: String)

    val thisDeviceUid = remember { mutableStateOf<String?>(null) }
    val dbDevices = remember { mutableStateListOf<Device>() }
    Column(modifier = modifier) {
        Text("this device uid: ${thisDeviceUid.value ?: "???"}")
        Text("added devices:")
        for (device in dbDevices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val onOff = if (mdns.devices.contains(device.uid)) {
                    "+"
                } else {
                    "-"
                }
                Text("$onOff ${device.uid}", modifier = Modifier.weight(1f))
                Button({
                    dbDevices.remove(device)
                }) { Text("-") }
            }
        }
        Text("unknown devices:")
        for (deviceUid in mdns.devices.keys.sorted()) {
            if (dbDevices.find { d -> d.uid == deviceUid } != null) continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceUid, modifier = Modifier.weight(1f))
                Button({
                    dbDevices.add(Device(deviceUid))
                    dbDevices.sortBy { it.uid }
                }) { Text("+") }
            }
        }
    }
}

@Composable
fun UiDay(count: MutableState<Int?>, modifier: Modifier = Modifier) {
    val target = 10
    val fontSize = 32.sp
    val countInt = count.value ?: 0
    val bar1 = "#".repeat(min(countInt, target))
    val bar2 = ".".repeat(max(target - countInt, 0))
    val bar3 = "+".repeat(max(countInt - target, 0))
    val ago = "??:?? ago"
    Column(modifier = modifier) {
        Button(onClick = { count.value = countInt + 1 }, shape = RoundedCornerShape(1.dp)) {
            Text(
                "${count.value ?: "??"}/$target $ago\n[$bar1$bar2$bar3]",
                fontFamily = FontFamily.Monospace,
                lineHeight = fontSize,
                fontSize = fontSize,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    val count = remember { mutableStateOf<Int?>(3) }
    ExerciseTrackTheme {
        UiDay(count)
    }
}
