package io.github.dwicki.tidytudo

import android.app.Application
import android.content.Context
import io.github.dwicki.tidytudo.service.Notifications

class TidytudoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.recorder.start()
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as TidytudoApp).container
