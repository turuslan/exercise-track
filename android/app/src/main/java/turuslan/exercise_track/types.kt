package turuslan.exercise_track

typealias Time = Long
typealias DeviceUid = String
typealias TimeByDevice = Map<DeviceUid, Time>
typealias MaxTimeByDevice = Map<DeviceUid, Time?>
typealias TimesByDevice = Map<DeviceUid, List<Time>>
