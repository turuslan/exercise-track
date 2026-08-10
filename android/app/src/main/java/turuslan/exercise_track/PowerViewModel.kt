package turuslan.exercise_track

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PowerViewModel @Inject constructor(
    val repository: MyRepository,
    application: Application,
) : AndroidViewModel(application) {
    val enableNetwork = repository.enableNetwork

    fun setEnableNetwork(enable: Boolean) {
        repository.setEnableNetwork(enable)
    }

    fun exit() {
        repository.exit()
    }
}
