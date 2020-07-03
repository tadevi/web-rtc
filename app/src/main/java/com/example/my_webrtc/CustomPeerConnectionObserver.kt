package com.example.my_webrtc

import android.util.Log
import org.webrtc.*


open class CustomPeerConnectionObserver : PeerConnection.Observer {
    private val tag = "PeerConnection"
    override fun onIceCandidate(candidate: IceCandidate) {
        Log.e(tag, "onIceCandidate")
    }

    override fun onDataChannel(channel: DataChannel) {
        Log.e(tag, "onDataChannel $channel")
    }

    override fun onIceConnectionReceivingChange(recieve: Boolean) {
        Log.e(tag, "onIceConnectionReceivingChange $recieve")
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
        Log.e(tag, "onIceConnectionChange $state")
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
        Log.e(tag, "onIceGatheringChange $state")
    }

    override fun onAddStream(stream: MediaStream) {
        Log.e(tag, "onAddStream $stream")
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState) {
        Log.e(tag, "onSignalingChange $state")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
        Log.e(tag, "onIceCandidatesRemoved $candidates")
    }

    override fun onRemoveStream(stream: MediaStream) {
        Log.e(tag, "onRemoveStreeam $stream")
    }

    override fun onRenegotiationNeeded() {
        Log.e(tag, "onRenegotiationNeeded")
    }

    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
        Log.e(tag, "onAddTrack $receiver $streams")
    }
}