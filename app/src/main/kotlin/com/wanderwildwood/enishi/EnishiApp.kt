package com.wanderwildwood.enishi

import android.app.Application
import com.wanderwildwood.enishi.data.HomeLink

/** Only so the sync, in whatever process start, asks Home first when it uses Home's account (HomeLink). */
class EnishiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HomeLink.install(this)
    }
}
