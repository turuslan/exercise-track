package turuslan.exercise_track

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class MyScope @Inject constructor() {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }
}

@Singleton
class MyRepository @Inject constructor(val scope: MyScope, val mdns: Mdns) {
    private val mutDataDay = MutableStateFlow(DataDay(timeNow(), listOf()))
    val dataDay = mutDataDay.asStateFlow()

    private val devicesMutex = Mutex()
    private val mutAddedDevices = MutableStateFlow<List<DbDevice>>(listOf())
    val addedDevices = mutAddedDevices.asStateFlow()

    private val eventTimeAdded = MutableSharedFlow<TimesByDevice>()

    init {
        scope.launch {
            val now = timeNow()
            mutDataDay.value = dataDay.value.copy(now = now)
            delay((time_scale.nextTick(now) - now).milliseconds)
        }
        scope.launch {
            dataDay.map { Pair(it.min, it.max) }.distinctUntilChanged().collect { readTimes() }
        }
        scope.launch {
            mdns.discover(dbThisDeviceUid())
        }
        scope.launch {
            eventTimeAdded.collect {
                readTimes()
            }
        }
        scope.launch {
            devicesMutex.withLock {
                readDevices()
            }
        }
        // TODO: devices + mdns.devices => sync.syncAll()
    }

    private suspend fun readTimes() {
        val times =
            dbSelectTimeRange(dataDay.value.min, dataDay.value.max, null).values.flatten().sorted()
        mutDataDay.value = dataDay.value.copy(times = times)
    }

    private suspend fun readDevices() {
        mutAddedDevices.value = dbDevices()
    }

    fun tryAddTime() {
        scope.launch { dataDay.value.mayAdd()?.let { eventTimeAdded.emit(dbInsertTime(it)) } }
    }

    fun addDevice(uid: DeviceUid) {
        scope.launch {
            devicesMutex.withLock {
                dbUpsertDevice(uid, uid)
                readDevices()
            }
        }
    }

    fun removeDevice(uid: DeviceUid) {
        scope.launch {
            devicesMutex.withLock {
                dbRemoveDevice(uid)
                readDevices()
            }
        }
    }
}
