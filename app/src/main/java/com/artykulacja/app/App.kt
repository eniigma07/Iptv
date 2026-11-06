package com.artykulacja.app

import android.app.Application
import com.artykulacja.app.audio.Player
import com.artykulacja.app.audio.Recorder
import com.artykulacja.app.data.Course
import com.artykulacja.app.data.ModelStore
import com.artykulacja.app.data.Settings
import com.artykulacja.app.data.Store
import com.artykulacja.app.notify.Reminder
import com.artykulacja.app.speech.Speech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: Settings
    lateinit var store: Store
    lateinit var course: Course
    lateinit var models: ModelStore
    lateinit var speech: Speech
    val player = Player()
    val recorder = Recorder()

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        store = Store(this)
        course = Course.load(this)
        models = ModelStore(this)
        speech = Speech(this, models.dir, settings)
        Reminder.createChannel(this)
        if (models.isReady()) startEngine()
    }

    fun startEngine() {
        scope.launch { speech.init() }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}

val app: App get() = App.instance
