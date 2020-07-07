package com.example.my_webrtc

import android.app.Application
import org.webrtc.PeerConnectionFactory

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )
        LoginUser.init(this)
    }
}