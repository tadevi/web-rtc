package com.example.my_webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


open class CustomSdpObserver(val tag: String) : SdpObserver {
    override fun onSetFailure(error: String) {
        Log.v(tag, "onSetFailure $error")
    }

    override fun onSetSuccess() {
        Log.v(tag, "onSetSuccess")
    }

    override fun onCreateSuccess(description: SessionDescription) {
        Log.v(tag, "onCreateSuccess ${description.type.canonicalForm()}")
    }

    override fun onCreateFailure(error: String) {
        Log.v(tag, "onCreateFailure $error")
    }
}