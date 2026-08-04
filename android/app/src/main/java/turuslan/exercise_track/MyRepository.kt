package turuslan.exercise_track

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class MyRepository @Inject constructor() {
    // TODO
    private val scope = CoroutineScope(Dispatchers.Default)

    private val mutDataDay = MutableStateFlow(DataDay(timeNow(), listOf()))
    val dataDay = mutDataDay.asStateFlow()

    init {
        suspend fun readTimes() {
            val times =
                dbSelectTimeRange(dataDay.value.min, dataDay.value.max, null).values.flatten()
                    .sorted()
            mutDataDay.value = dataDay.value.copy(times = times)
        }
        scope.launch {
            val now = timeNow()
            mutDataDay.value = dataDay.value.copy(now = now)
            delay((time_scale.nextTick(now) - now).milliseconds)
        }
        scope.launch {
            dataDay.map { Pair(it.min, it.max) }.distinctUntilChanged().collect { readTimes() }
        }
        scope.launch {
            eventTimeAdded.collect { devices ->
                if (devices.values.any { times -> times.any { dataDay.value.shouldChange(it) } }) {
                    readTimes()
                }
            }
        }
    }

    fun tryAddTime() {
        scope.launch { mutDataDay.value.tryAdd() }
    }
}
