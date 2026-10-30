package turuslan.exercise_track

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

const val DUPLICATE_TIME_MS = 5000

data class DataDay(
    val now: Time,
    val times: List<Time>,
) {
    val min get() = time_scale.getGroup(now)
    val max get() = time_scale.nextGroup(now)
    val target = 10
    val count get() = times.size
    val last get() = times.lastOrNull()
    val bar1 get() = "#".repeat(min(count, target))
    val bar2 get() = ".".repeat(max(target - count, 0))
    val bar3 get() = "+".repeat(max(count - target, 0))
    val slash get() = "$count/$target"
    val bar get() = "[${bar1}${bar2}${bar3}]"

    fun mayAdd(): Time? {
        val time = timeNow()
        val last = last ?: return time
        if (DUPLICATE_TIME_MS <= differenceInMilliseconds(time, last)) {
            return time
        }
        return null
    }

    fun shouldChange(time: Time): Boolean {
        return time in min..max && !times.contains(time)
    }

    data class Ui(val last: Long?, val text1: String, val format1: String, val text2: String) {
        val base
            get() = SystemClock.elapsedRealtime() + (last?.let { it - System.currentTimeMillis() }
                ?: 0)
        val format get() = "$text1$format1\n$text2"
        val started get() = last != null
    }

    fun ui(): Ui {
        return Ui(
            last,
            slash,
            last?.let { ", %s" } ?: "",
            bar,
        )
    }
}
