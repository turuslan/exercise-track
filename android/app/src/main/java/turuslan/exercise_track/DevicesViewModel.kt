package turuslan.exercise_track

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(application: Application, val repository: MyRepository) :
    AndroidViewModel(application) {
    data class AddedDevice(val uid: DeviceUid, val online: Boolean)

    val thisDeviceUid = repository.thisDeviceUid

    val addedDevices = combineState(
        viewModelScope,
        repository.addedDevices,
        repository.mdns.devices
    ) { added, mdns -> added.map { AddedDevice(it.uid, mdns.contains(it.uid)) } }
    val unknownDevices = combineState(
        viewModelScope,
        repository.addedDevices,
        repository.mdns.devices
    ) { added, mdns -> mdns.keys.filter { uid -> !added.any { it.uid == uid } }.sorted() }

    fun addDevice(uid: DeviceUid) {
        repository.addDevice(uid)
    }

    fun removeDevice(uid: DeviceUid) {
        repository.removeDevice(uid)
    }
}
