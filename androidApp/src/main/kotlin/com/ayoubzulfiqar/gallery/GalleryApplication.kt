package com.ayoubzulfiqar.gallery

import android.app.Application
import com.ayoubzulfiqar.gallery.di.AndroidAppGraph

class GalleryApplication : Application() {
    /** Process-scoped graph: repositories, caches and the vault session survive activity recreation. */
    lateinit var graph: AndroidAppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AndroidAppGraph(this)
    }
}
