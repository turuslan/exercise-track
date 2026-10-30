package turuslan.exercise_track

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val intentActionAlarm = "turuslan.exercise_track.ALARM"

// This repository does lots of work, because neither activity nor view-model live as long
// as required for foreground service and notification.
// Viewing status and adding new time via notification on lock screen is primary function,
// as well as synchronizing.
@Singleton
class MyRepository @Inject constructor(
    @param:ApplicationContext val context: Context,
    val db: Db,
    val mdns: Mdns,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var jobs: List<Job>

    private val mutEnableNetwork = MutableStateFlow(false)
    val enableNetwork = mutEnableNetwork.asStateFlow()

    private val mutDataDay = MutableStateFlow(DataDay(timeNow(), listOf()))
    val dataDay = mutDataDay.asStateFlow()
    val dataDayUi = dataDay.mapState(scope) { it.ui() }

    private val mutThisDeviceUid = MutableStateFlow<DeviceUid?>(null)
    val thisDeviceUid = mutThisDeviceUid.asStateFlow()

    private val devicesMutex = Mutex()
    private val mutAddedDevices = MutableStateFlow<List<DbDevice>>(listOf())
    val addedDevices = mutAddedDevices.asStateFlow()

    private val eventTimeAdded = MutableSharedFlow<TimesByDevice>()

    val notification = MyNotification(context, dataDayUi)

    val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var alarm: Long? = null

    init {
        updateDataDay()
        jobs = listOf(
            scope.launch {
                var job: Job? = null
                try {
                    enableNetwork.collect { enable ->
                        job?.cancelAndJoin()
                        job = null
                        if (enable ?: false) {
                            job = launch { runNetwork() }
                        }
                    }
                } finally {
                    job?.cancelAndJoin()
                }
            },
            scope.launch { mutThisDeviceUid.value = db.ready.await().thisDeviceUid() },
            scope.launch { mutEnableNetwork.value = db.ready.await().networkEnabled() ?: false },
            scope.launch {
                dataDay.mapState(scope) { Pair(it.min, it.max) }.collect { readTimes() }
            },
            scope.launch { eventTimeAdded.collect { readTimes() } },
            scope.launch { devicesMutex.withLock { readDevices() } },
            scope.launch { notification.run() },
        )
        context.startForegroundService(myServiceIntent(context))
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val now = timeNow()
                mutDataDay.value = dataDay.value.copy(now = now)
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    fun updateDataDay() {
        val now = timeNow()
        mutDataDay.value = dataDay.value.copy(now = now)
        val next = time_scale.nextGroup(now)
        if (alarm != next) {
            alarm = next
            val type = AlarmManager.RTC
            val intent = PendingIntent.getBroadcast(
                context,
                3,
                Intent(intentActionAlarm, null, context, MyReceiver::class.java),
                pendingIntentFlags
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(type, next, intent)
            } else {
                alarmManager.setExact(type, next, intent)
            }
        }
    }

    private suspend fun runNetwork() {
        coroutineScope {
            listOf(
                launch { multicastLock(context) },
                launch { mdns.discover(db.ready.await().thisDeviceUid()) },
                launch {
                    runServer(db, eventTimeAdded) { port ->
                        mdns.advertise(db.ready.await().thisDeviceUid(), port)
                    }
                },
                launch { runClient(db, addedDevices, mdns.devices, eventTimeAdded) },
            ).joinAll()
        }
    }

    private suspend fun readTimes() {
        updateDataDay()
        val times = db.ready.await()
            .selectTimeRange(dataDay.value.min, dataDay.value.max, null).values.flatten().sorted()
        mutDataDay.value = dataDay.value.copy(times = times)
    }

    private suspend fun readDevices() {
        mutAddedDevices.value = db.ready.await().devices()
    }

    fun tryAddTime() {
        scope.launch {
            dataDay.value.mayAdd()?.let { eventTimeAdded.emit(db.ready.await().insertTime(it)) }
        }
    }

    fun addDevice(uid: DeviceUid) {
        scope.launch {
            devicesMutex.withLock {
                db.ready.await().upsertDevice(uid, uid)
                readDevices()
            }
        }
    }

    fun removeDevice(uid: DeviceUid) {
        scope.launch {
            devicesMutex.withLock {
                db.ready.await().removeDevice(uid)
                readDevices()
            }
        }
    }

    fun setEnableNetwork(enable: Boolean) {
        mutEnableNetwork.value = enable
        scope.launch { db.ready.await().setNetworkEnabled(enable) }
    }

    fun exit() {
        mutEnableNetwork.value = false
        scope.cancel()
        CoroutineScope(Dispatchers.Default).launch {
            jobs.joinAll()
            exitProcess(0)
        }
    }
}
