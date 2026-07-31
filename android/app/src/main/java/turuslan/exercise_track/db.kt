package turuslan.exercise_track

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

fun makeUid(): String {
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
    val uid: String,
    val name: String,
    val enabled: Boolean,
)

@Dao
interface DbDao {
    @Query("""SELECT value FROM DbKv WHERE `key` = :key""")
    suspend fun kvGet(key: String): String?

    @Upsert
    suspend fun kvSet(kv: DbKv)

    @Query("""SELECT * FROM DbDevice WHERE uid = :uid""")
    suspend fun deviceByUid(uid: String): DbDevice?

    @Query("""SELECT * FROM DbDevice WHERE uid != :thisDeviceUid AND enabled""")
    suspend fun devices(thisDeviceUid: String): List<DbDevice>

    @Upsert
    suspend fun deviceUpsert(device: DbDevice): DbDevice

    @Delete
    suspend fun deviceRemove(device: DbDevice)

    // TODO
    suspend fun timeExistsDeviceLid(lid: Int): Boolean = false
}

@Database(
    entities = [
        DbKv::class,
        DbDevice::class,
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
                    db.thisDevice =
                        dao.deviceUpsert(DbDevice(0, thisDeviceUid, "this device", true))
                }
            }
            return db
        }
    }
}

suspend fun dbThisDeviceUid(): String {
    di.db.ready()

    return di.db.thisDevice!!.uid
}

suspend fun dbDevices(): List<DbDevice> {
    di.db.ready()

    return di.dao.devices(di.db.thisDevice!!.uid).sortedBy { it.uid }
}

suspend fun dbUpsertDevice(uid: String, name: String) {
    di.db.ready()

    di.dao.deviceUpsert(DbDevice(0, uid, name, true))
}

suspend fun dbRemoveDevice(uid: String) {
    di.db.ready()

    val device = di.dao.deviceByUid(uid) ?: return
    if (di.dao.timeExistsDeviceLid(device.lid)) {
        di.dao.deviceUpsert(device.copy(enabled = false))
    } else {
        di.dao.deviceRemove(device)
    }
}
