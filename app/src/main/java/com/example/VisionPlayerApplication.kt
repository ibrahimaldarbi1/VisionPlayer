package com.example

import android.app.Application
import com.example.core.AppContainer

class VisionPlayerApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        appContainer =
            AppContainer(
                applicationContext = this
            )
    }
}
