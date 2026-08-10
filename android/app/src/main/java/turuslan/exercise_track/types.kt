package turuslan.exercise_track

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

typealias Time = Long
typealias DeviceUid = String
typealias DeviceLid = Int
typealias TimeByDevice = Map<DeviceUid, Time>
typealias MaxTimeByDevice = Map<DeviceUid, Time?>
typealias TimesByDevice = Map<DeviceUid, List<Time>>

fun makeUid(): DeviceUid {
    return "android_${(0x100000000 * Math.random()).toLong().toString(16).padStart(8, '0')}"
}

fun <T, R> StateFlow<T>.mapState(scope: CoroutineScope, transform: (T) -> R): StateFlow<R> {
    return map(transform).stateIn(scope, SharingStarted.Eagerly, transform(value))
}

fun <T1, T2, R> combineState(
    scope: CoroutineScope,
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    transform: (T1, T2) -> R
): StateFlow<R> {
    return combine(flow1, flow2, transform).stateIn(
        scope,
        SharingStarted.Eagerly,
        transform(flow1.value, flow2.value)
    )
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

val json = Json.Default
