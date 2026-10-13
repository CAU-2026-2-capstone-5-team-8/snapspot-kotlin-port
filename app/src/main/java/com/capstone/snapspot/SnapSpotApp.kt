package com.capstone.snapspot

import android.app.Application
import com.capstone.snapspot.data.notify.createAlertChannel
import com.capstone.snapspot.ui.map.MapSupport
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SnapSpotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapSupport.init(this)
        createAlertChannel(this)
    }
}
