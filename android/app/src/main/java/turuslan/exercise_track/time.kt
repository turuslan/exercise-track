package turuslan.exercise_track

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

typealias Time = Long
typealias TimeByDevice = Map<String, Time>
typealias TimesByDevice = Map<String, List<Time>>

val timeZone = ZoneId.systemDefault()!!

fun timeNow(): Time {
    return Instant.now().toEpochMilli()
}

fun timeIso(time: Time): String {
    return Instant.ofEpochMilli(time).toString()
}

fun mapInstant(time: Time, f: (Instant) -> Instant): Time {
    return f(Instant.ofEpochMilli(time)).toEpochMilli()
}

fun startOfUnit(time: Time, unit: ChronoUnit): Time {
    assert(unit.duration <= ChronoUnit.DAYS.duration)
    return mapInstant(time) { it.atZone(timeZone).truncatedTo(ChronoUnit.DAYS).toInstant() }
}

fun startOfSecond(time: Time): Time {
    return startOfUnit(time, ChronoUnit.SECONDS)
}

fun startOfMinute(time: Time): Time {
    return startOfUnit(time, ChronoUnit.MINUTES)
}

fun startOfDay(time: Time): Time {
    return startOfUnit(time, ChronoUnit.DAYS)
}

fun differenceInUnits(time2: Time, time1: Time, unit: ChronoUnit): Long {
    return Instant.ofEpochMilli(time1).until(Instant.ofEpochMilli(time2), unit)
}

fun differenceInSeconds(time2: Time, time1: Time): Long {
    return differenceInUnits(time2, time1, ChronoUnit.SECONDS)
}

fun differenceInMinutes(time2: Time, time1: Time): Long {
    return differenceInUnits(time2, time1, ChronoUnit.MINUTES)
}

fun addUnits(time: Time, n: Long, unit: ChronoUnit): Time {
    return mapInstant(time) { it.plus(n, unit) }
}

fun addSeconds(time: Time, n: Long): Time {
    return addUnits(time, n, ChronoUnit.SECONDS)
}

fun addMinutes(time: Time, n: Long): Time {
    return addUnits(time, n, ChronoUnit.MINUTES)
}

fun addDays(time: Time, n: Long): Time {
    return addUnits(time, n, ChronoUnit.DAYS)
}

interface TimeScale {
    fun getGroup(time: Time): Time
    fun nextGroup(time: Time): Time
    fun nextTick(time: Time): Time
    fun ago(time1: Time, time2: Time): String
}

val time_scale_debug = object : TimeScale {
    override fun getGroup(time: Time): Time {
        return startOfMinute(time)
    }

    override fun nextGroup(time: Time): Time {
        return addMinutes(getGroup(time), 1)
    }

    override fun nextTick(time: Time): Time {
        return addSeconds(startOfSecond(time), 1)
    }

    override fun ago(
        time1: Time,
        time2: Time,
    ): String {
        return "${differenceInSeconds(time2, time1)}s ago"
    }
}
val time_scale_real = object : TimeScale {
    override fun getGroup(time: Time): Time {
        return startOfDay(time)
    }

    override fun nextGroup(time: Time): Time {
        return addDays(getGroup(time), 1)
    }

    override fun nextTick(time: Time): Time {
        return addSeconds(startOfSecond(time), 1)
    }

    override fun ago(
        time1: Time,
        time2: Time,
    ): String {
        val minutes = differenceInMinutes(time2, time1)
        val hours = minutes / 60
        return "${hours.toString().padStart(2, '0')}:${
            (minutes % 60).toString().padStart(2, '0')
        } ago"
    }
}
val time_scale = time_scale_real
