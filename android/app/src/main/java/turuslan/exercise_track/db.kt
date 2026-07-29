package turuslan.exercise_track

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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

@Dao
interface DbDao {
    @Query("""SELECT value FROM DbKv WHERE `key` = :key""")
    suspend fun kvGet(key: String): String?

    @Upsert
    suspend fun kvSet(kv: DbKv)
}

@Database(
    entities = [
        DbKv::class,
    ],
    version = 1,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): DbDao

    var thisDeviceUid: String? = null
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
                db.thisDeviceUid = dao.kvGet(thisDeviceUidKey)
                if (db.thisDeviceUid == null) {
                    db.thisDeviceUid = makeUid()
                    dao.kvSet(DbKv(thisDeviceUidKey, db.thisDeviceUid!!))
                }
            }
            return db
        }
    }
}

suspend fun dbThisDeviceUid(): String {
    di.db.ready()

    return di.db.thisDeviceUid!!
}
