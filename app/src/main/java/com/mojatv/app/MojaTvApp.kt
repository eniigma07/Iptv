package com.mojatv.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mojatv.app.data.Http
import com.mojatv.app.data.Repository

class MojaTvApp : Application(), ImageLoaderFactory {

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = Repository(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Http.client)
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: MojaTvApp
            private set

        val repo: Repository get() = instance.repository
    }
}
