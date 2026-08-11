package turuslan.exercise_track

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyService : Service() {
    @Inject
    lateinit var repository: MyRepository

    override fun onCreate() {
        super.onCreate()
        repository.notification.startForeground(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}

fun myServiceIntent(context: Context): Intent {
    return Intent(context, MyService::class.java)
}
