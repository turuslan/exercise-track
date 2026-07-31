package turuslan.exercise_track

import kotlinx.coroutines.flow.MutableSharedFlow

val eventMdnsChange = MutableSharedFlow<Unit>()

val eventTimeAdded = MutableSharedFlow<TimesByDevice>()

val eventDevicesChange = MutableSharedFlow<Unit>()
