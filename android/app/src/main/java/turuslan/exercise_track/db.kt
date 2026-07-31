package turuslan.exercise_track

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

fun makeUid(): DeviceUid {
    return "_${(0x100000000 * Math.random()).toLong()}"
}

@Entity
data class DbKv(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(indices = [Index(value = ["uid"], unique = true)])
data class DbDevice(
    @PrimaryKey(autoGenerate = true) val lid: Int,
    val uid: DeviceUid,
    val name: String,
    val enabled: Boolean,
)

@Entity
data class DbTime(
    @PrimaryKey val time: Time,
    @ColumnInfo(name = "device_lid") val deviceLid: Int,
)

@Dao
interface DbDao {
    @Query("""SELECT value FROM DbKv WHERE `key` = :key""")
    suspend fun kvGet(key: String): String?

    @Upsert
    suspend fun kvSet(kv: DbKv)

    @Query("""SELECT * FROM DbDevice WHERE uid = :uid""")
    suspend fun deviceByUid(uid: DeviceUid): DbDevice?

    @Query("""SELECT * FROM DbDevice WHERE uid != :thisDeviceUid AND enabled ORDER BY uid ASC""")
    suspend fun devices(thisDeviceUid: DeviceUid): List<DbDevice>

    @Upsert
    suspend fun deviceUpsert(device: DbDevice)

    @Delete
    suspend fun deviceRemove(device: DbDevice)

    @Query("""SELECT * FROM DbDevice WHERE lid IN (:lids)""")
    suspend fun devicesByLid(lids: List<Int>): List<DbDevice>

    @Query("""SELECT EXISTS(SELECT 1 FROM DbTime WHERE device_lid = :deviceLid)""")
    suspend fun timeExistsDeviceLid(deviceLid: Int): Boolean

    @Query("""SELECT MAX(time) from DbTime WHERE device_lid = :deviceLid""")
    suspend fun timeMaxByDeviceLid(deviceLid: Int): Time?

    @Insert
    suspend fun timeInsert(time: DbTime)

    @Insert
    suspend fun timeInsert(time: List<DbTime>)

    @Query("""SELECT * FROM DbTime WHERE (time BETWEEN :min AND :max) AND (:deviceLid IS NULL OR device_lid = :deviceLid) ORDER BY time ASC""")
    suspend fun timeRangeDeviceLid(min: Time, max: Time, deviceLid: Int?): List<DbTime>
}

@Database(
    entities = [
        DbKv::class,
        DbDevice::class,
        DbTime::class,
    ],
    version = 1,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): DbDao

    var thisDevice: DbDevice? = null
    var _ready: Deferred<Unit>? = null

    suspend fun ready() {
        _ready!!.await()
    }

    companion object {
        fun make(context: Context, scope: CoroutineScope): Db {
            val db = Room.databaseBuilder(context, Db::class.java, "db").build()
            db._ready = scope.async {
                val dao = db.dao()
                val thisDeviceUidKey = "this_device_uid"
                var thisDeviceUid = dao.kvGet(thisDeviceUidKey)
                if (thisDeviceUid == null) {
                    thisDeviceUid = makeUid()
                    dao.kvSet(DbKv(thisDeviceUidKey, thisDeviceUid))
                }
                db.thisDevice = dao.deviceByUid(thisDeviceUid)
                if (db.thisDevice == null) {
                    dao.deviceUpsert(DbDevice(0, thisDeviceUid, "this device", true))
                    db.thisDevice = dao.deviceByUid(thisDeviceUid)!!
                }
            }
            return db
        }
    }
}

suspend fun dbThisDeviceUid(): DeviceUid {
    di.db.ready()

    return di.db.thisDevice!!.uid
}

suspend fun dbDevices(): List<DbDevice> {
    di.db.ready()

    return di.dao.devices(di.db.thisDevice!!.uid).sortedBy { it.uid }
}

suspend fun dbUpsertDevice(uid: DeviceUid, name: String) {
    di.db.ready()

    di.dao.deviceUpsert(DbDevice(0, uid, name, true))
}

suspend fun dbRemoveDevice(uid: DeviceUid) {
    di.db.ready()

    val device = di.dao.deviceByUid(uid) ?: return
    if (di.dao.timeExistsDeviceLid(device.lid)) {
        di.dao.deviceUpsert(device.copy(enabled = false))
    } else {
        di.dao.deviceRemove(device)
    }
}

suspend fun dbInsertTime(time: Time): TimesByDevice {
    di.db.ready()

    di.dao.timeInsert(DbTime(time, di.db.thisDevice!!.lid))
    return mapOf(Pair(di.db.thisDevice!!.uid, listOf(time)))
}

suspend fun dbMaxTimeAll(): TimeByDevice {
    di.db.ready()

    return dbDevices().associate { Pair(it.uid, di.dao.timeMaxByDeviceLid(it.lid) ?: 0) }
}

suspend fun dbSelectTimeRange(min: Time, max: Time?, deviceUid: DeviceUid?): TimesByDevice {
    di.db.ready()

    val max = max ?: timeNow()
    val deviceLid = deviceUid?.let { di.dao.deviceByUid(it)?.lid ?: return mapOf() }
    val rows = di.dao.timeRangeDeviceLid(min, max, deviceLid)
    deviceUid?.let { if (rows.isEmpty()) return mapOf(Pair(it, listOf())) }
    val lidTimes = rows.groupBy({ it.deviceLid }, { it.time })
    val devices = di.dao.devicesByLid(lidTimes.keys.toList())
    val lidUid = devices.associate { Pair(it.lid, it.uid) }
    return lidTimes.filterKeys { lidUid.contains(it) }.mapKeys { lidUid[it.key]!! }
}

suspend fun dbSelectTimeRangeMany(devices: TimeByDevice): TimesByDevice {
    di.db.ready()

    val result = mutableMapOf<DeviceUid, List<Time>>()
    devices.forEach {
        result += dbSelectTimeRange(it.value, null, it.key)
    }
    return result
}

suspend fun dbMergeTime(deviceUid: DeviceUid, times: List<Time>): List<Time> {
    di.db.ready()

    if (times.isEmpty()) return listOf()
    val device = di.dao.deviceByUid(deviceUid) ?: return listOf()
    if (!device.enabled) return listOf()
    val existing = dbSelectTimeRange(times.min(), times.max(), deviceUid)[deviceUid]!!
    val times = times.filter { existing.contains(it) }
    if (times.isEmpty()) return listOf()
    di.dao.timeInsert(times.map { DbTime(it, device.lid) })
    return times
}

suspend fun dbMergeTimeMany(devices: TimesByDevice): TimesByDevice {
    di.db.ready()

    return devices.mapValues { dbMergeTime(it.key, it.value) }
}
