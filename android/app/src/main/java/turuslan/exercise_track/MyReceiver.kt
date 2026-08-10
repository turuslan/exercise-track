package turuslan.exercise_track

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repository: MyRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == intentActionAddTime) {
            repository.tryAddTime()
            return
        }
    }
}
