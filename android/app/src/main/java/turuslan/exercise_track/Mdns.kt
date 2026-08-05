package turuslan.exercise_track

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias MdnsDevices = Map<DeviceUid, String>

class Mdns @Inject constructor(@ApplicationContext context: Context) {
    val mdnsType = "_exercise-track._tcp"
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val multicastLock =
        wifiManager.createMulticastLock("exercise-track").apply { setReferenceCounted(false) }!!
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val mutDevicesMap = mutableMapOf<DeviceUid, String>()
    private val mutDevices = MutableStateFlow(mutDevicesMap.toMap())
    val devices = mutDevices.asStateFlow()

    suspend fun discover(thisDeviceUid: DeviceUid) {
        fun add(deviceUid: DeviceUid, address: String) {
            mutDevicesMap[deviceUid] = address
            mutDevices.value = mutDevicesMap.toMap()
        }

        fun remove(deviceUid: DeviceUid) {
            mutDevicesMap.remove(deviceUid)
            mutDevices.value = mutDevicesMap.toMap()
        }

        coroutineScope {
            val scope = this
            val resolveReq = Channel<NsdServiceInfo>()
            val resolveRes = Channel<Unit>()
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
                    val host = service.host
                    if (!host.isLoopbackAddress) {
                        add(service.serviceName, "${host.toString().substring(1)}:${service.port}")
                    }
                    onResolve()
                }

                fun onResolve() {
                    scope.launch { resolveRes.send(Unit) }
                }
            }
            try {
                nsd.discoverServices(mdnsType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                for (service in resolveReq) {
                    if (service.serviceName == thisDeviceUid) continue
                    if (service.serviceName.startsWith(thisDeviceUid) && service.serviceName[thisDeviceUid.length] == ' ') continue
                    nsd.resolveService(service, resolveListener)
                    resolveRes.receive()
                }
            } finally {
                nsd.stopServiceDiscovery(discoveryListener)
            }
        }
    }

    init {
        multicastLock.acquire()
    }

    fun stop() {
        if (multicastLock.isHeld) {
            multicastLock.release()
        }
    }

    fun advertise(thisDeviceUid: DeviceUid, port: Int) {
        Log.i("MDNS", "advertise $thisDeviceUid $port")
        val info = NsdServiceInfo()
        info.serviceType = mdnsType
        info.serviceName = thisDeviceUid
        info.port = port
        nsd.registerService(
            info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(service: NsdServiceInfo?) {}
                override fun onRegistrationFailed(service: NsdServiceInfo?, e: Int) {}
                override fun onServiceUnregistered(service: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(service: NsdServiceInfo?, e: Int) {}
            })
    }
}
