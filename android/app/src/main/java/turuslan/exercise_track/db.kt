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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import javax.inject.Inject

val networkEnabledKey = "network_enabled"
typealias NetworkEnabledValue = Boolean

fun makeUid(): DeviceUid {
    return "android_${(0x100000000 * Math.random()).toLong().toString(16).padStart(8, '0')}"
}

@Entity
data class DbKv(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(indices = [Index(value = ["uid"], unique = true)])
data class DbDevice(
    @PrimaryKey(autoGenerate = true) val lid: DeviceLid,
    val uid: DeviceUid,
    val name: String,
    val enabled: Boolean,
)

@Entity
data class DbTime(
    @PrimaryKey val time: Time,
    @ColumnInfo(name = "device_lid") val deviceLid: DeviceLid,
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
    suspend fun devicesByLid(lids: List<DeviceLid>): List<DbDevice>

    @Query("""SELECT EXISTS(SELECT 1 FROM DbTime WHERE device_lid = :deviceLid)""")
    suspend fun timeExistsDeviceLid(deviceLid: DeviceLid): Boolean

    @Query("""SELECT MAX(time) from DbTime WHERE device_lid = :deviceLid""")
    suspend fun timeMaxByDeviceLid(deviceLid: DeviceLid): Time?

    @Insert
    suspend fun timeInsert(time: DbTime)

    @Insert
    suspend fun timeInsert(time: List<DbTime>)

    @Query("""SELECT * FROM DbTime WHERE (time BETWEEN :min AND :max) AND (:deviceLid IS NULL OR device_lid = :deviceLid) ORDER BY time ASC""")
    suspend fun timeRangeDeviceLid(min: Time, max: Time, deviceLid: DeviceLid?): List<DbTime>
}

@Database(
    entities = [
        DbKv::class,
        DbDevice::class,
        DbTime::class,
    ],
    version = 1,
)
abstract class DbRoom : RoomDatabase() {
    abstract fun dao(): DbDao
}

class Db @Inject constructor(@ApplicationContext context: Context) {
    private val room = Room.databaseBuilder(context, DbRoom::class.java, "db").build()

    data class Ready(private val dao: DbDao, private val thisDevice: DbDevice) {
        fun thisDeviceUid(): DeviceUid {
            return thisDevice.uid
        }

        suspend fun devices(): List<DbDevice> {
            return dao.devices(thisDevice.uid).sortedBy { it.uid }
        }

        suspend fun upsertDevice(uid: DeviceUid, name: String) {
            val device =
                dao.deviceByUid(uid)?.copy(name = name, enabled = true) ?: DbDevice(
                    0,
                    uid,
                    name,
                    true
                )
            dao.deviceUpsert(device)
        }

        suspend fun removeDevice(uid: DeviceUid) {
            val device = dao.deviceByUid(uid) ?: return
            if (dao.timeExistsDeviceLid(device.lid)) {
                dao.deviceUpsert(device.copy(enabled = false))
            } else {
                dao.deviceRemove(device)
            }
        }

        suspend fun insertTime(time: Time): TimesByDevice {
            dao.timeInsert(DbTime(time, thisDevice.lid))
            return mapOf(Pair(thisDevice.uid, listOf(time)))
        }

        suspend fun maxTimeAll(): MaxTimeByDevice {
            return devices().associate { Pair(it.uid, dao.timeMaxByDeviceLid(it.lid)) }
        }

        suspend fun selectTimeRange(min: Time, max: Time?, deviceUid: DeviceUid?): TimesByDevice {
            val max = max ?: timeNow()
            val deviceLid = deviceUid?.let { dao.deviceByUid(it)?.lid ?: return mapOf() }
            val rows = dao.timeRangeDeviceLid(min, max, deviceLid)
            deviceUid?.let { if (rows.isEmpty()) return mapOf(Pair(it, listOf())) }
            val lidTimes = rows.groupBy({ it.deviceLid }, { it.time })
            val devices = dao.devicesByLid(lidTimes.keys.toList())
            val lidUid = devices.associate { Pair(it.lid, it.uid) }
            return lidTimes.filterKeys { lidUid.contains(it) }.mapKeys { lidUid[it.key]!! }
        }

        suspend fun selectTimeRangeMany(devices: TimeByDevice): TimesByDevice {
            val result = mutableMapOf<DeviceUid, List<Time>>()
            devices.forEach {
                result += selectTimeRange(it.value, null, it.key)
            }
            return result
        }

        suspend fun mergeTime(deviceUid: DeviceUid, times: List<Time>): List<Time> {
            if (times.isEmpty()) return listOf()
            val device = dao.deviceByUid(deviceUid) ?: return listOf()
            if (!device.enabled) return listOf()
            val existing = selectTimeRange(times.min(), times.max(), deviceUid)[deviceUid]!!
            val times = times.filter { !existing.contains(it) }
            if (times.isEmpty()) return listOf()
            dao.timeInsert(times.map { DbTime(it, device.lid) })
            return times
        }

        suspend fun mergeTimeMany(devices: TimesByDevice): TimesByDevice {
            return devices.mapValues { mergeTime(it.key, it.value) }.filterValues { !it.isEmpty() }
        }

        suspend fun networkEnabled(): NetworkEnabledValue? {
            return dao.kvGet(networkEnabledKey)?.let { json.decodeFromString(it) }
        }

        suspend fun setNetworkEnabled(enable: NetworkEnabledValue) {
            dao.kvSet(DbKv(networkEnabledKey, json.encodeToString(enable)))
        }
    }

    val ready = CoroutineScope(Dispatchers.IO).async {
        val dao = room.dao()
        val thisDeviceUidKey = "this_device_uid"
        var thisDeviceUid = dao.kvGet(thisDeviceUidKey)
        if (thisDeviceUid == null) {
            thisDeviceUid = makeUid()
            dao.kvSet(DbKv(thisDeviceUidKey, thisDeviceUid))
        }
        var thisDevice = dao.deviceByUid(thisDeviceUid)
        if (thisDevice == null) {
            dao.deviceUpsert(DbDevice(0, thisDeviceUid, "this device", true))
            thisDevice = dao.deviceByUid(thisDeviceUid)!!
        }
        return@async Ready(dao, thisDevice)
    }
}
