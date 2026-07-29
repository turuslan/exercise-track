package turuslan.exercise_track

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch


class Mdns(context: Context, val scope: CoroutineScope) {
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
    val flow = MutableSharedFlow<Unit>()

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
        scope.launch { flow.emit(Unit) }
    }

    fun remove(deviceUid: String) {
        devices.remove(deviceUid)
        scope.launch { flow.emit(Unit) }
    }
}

val mdns get() = di.mdns
