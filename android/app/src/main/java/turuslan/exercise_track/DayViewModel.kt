package turuslan.exercise_track

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DayViewModel @Inject constructor(
    val repository: MyRepository,
    application: Application,
) : AndroidViewModel(application) {
    val dataDay = repository.dataDay

    fun tryAddTime() {
        repository.tryAddTime()
    }
}
