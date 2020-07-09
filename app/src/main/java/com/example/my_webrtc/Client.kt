package com.example.my_webrtc

import android.util.Log
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

interface Client {
    fun onEvent(event: Int, data: Any?)
}

class Caller(val callee: String, val peerConnection: PeerConnection) : Client {
    @Volatile
    private var sdp: SessionDescription? = null

    @Volatile
    private var sendCandidate: MutableList<IceCandidate> = mutableListOf()

    @Volatile
    private var receiveCandidate: MutableList<IceCandidate> = mutableListOf()

    @Volatile
    private var currentStep = MAKE_CALL

    companion object {
        const val MAKE_CALL = 0
        const val RECEIVE_SDP = 1
        const val SEND_ICE = 2
        const val RECEIVE_ICE = 3
    }

    override fun onEvent(event: Int, data: Any?) {
        when (event) {
            MAKE_CALL -> {
                peerConnection.createOffer(
                    object : CustomSdpObserver("createOffer") {
                        override fun onCreateSuccess(description: SessionDescription) {
                            super.onCreateSuccess(description)

                            peerConnection.setLocalDescription(
                                CustomSdpObserver("localDesc"),
                                description
                            )

                            SignalClient.emitSdp(callee, description)
                        }
                    },
                    MediaConstraints()
                )
                currentStep = MAKE_CALL
            }
            RECEIVE_SDP -> {
                sdp = data as SessionDescription

                if (currentStep == MAKE_CALL) {
                    currentStep = RECEIVE_SDP
                    peerConnection.setRemoteDescription(CustomSdpObserver("remoteDesc"), sdp)

                    if (sendCandidate.isNotEmpty()) {
                        onEvent(SEND_ICE, null)
                    }
                }
            }

            SEND_ICE -> {
                if (data != null) {
                    sendCandidate.add(data as IceCandidate)
                }

                if (currentStep == RECEIVE_SDP) {
                    currentStep = SEND_ICE
                    for (candidate in sendCandidate) {
                        SignalClient.emitCandidate(callee, candidate)
                    }

                    if (receiveCandidate.isNotEmpty()) {
                        onEvent(RECEIVE_ICE, null)
                    }
                    Log.e("Caller", "Send ${sendCandidate.size} candidate")
                }
            }

            RECEIVE_ICE -> {
                if (data != null) {
                    receiveCandidate.add(data as IceCandidate)
                }

                if (currentStep == SEND_ICE) {
                    currentStep = -1
                    for (candidate in receiveCandidate) {
                        peerConnection.addIceCandidate(candidate)
                    }
                }
                Log.e("Caller", "Receive ${receiveCandidate.size} candidate")
            }
        }
        Log.e("Caller", "Step $event")
    }
}

class Callee(val caller: String, val peerConnection: PeerConnection) : Client {
    companion object {
        const val RECEIVE_CALL = 0
        const val SEND_SDP = 1
        const val RECEIVE_ICE = 2
        const val SEND_ICE = 3
    }

    @Volatile
    private var sdp: SessionDescription? = null

    @Volatile
    private var receiveCandidate: MutableList<IceCandidate> = mutableListOf()

    @Volatile
    private var sendCandidate: MutableList<IceCandidate> = mutableListOf()

    @Volatile
    private var currentStep = RECEIVE_CALL

    override fun onEvent(event: Int, data: Any?) {
        when (event) {
            RECEIVE_CALL -> {
                peerConnection.setRemoteDescription(
                    CustomSdpObserver("remoteDesc"),
                    data as SessionDescription
                )

                peerConnection.createAnswer(
                    object : CustomSdpObserver("createAnswer") {
                        override fun onCreateSuccess(description: SessionDescription) {
                            super.onCreateSuccess(description)

                            peerConnection.setLocalDescription(
                                CustomSdpObserver("localDesc"),
                                description
                            )

                            onEvent(SEND_SDP, description)
                        }
                    },
                    MediaConstraints()
                )
            }

            SEND_SDP -> {
                sdp = data as SessionDescription
                if (currentStep == RECEIVE_CALL) {
                    SignalClient.emitSdp(caller, sdp!!)
                    currentStep = SEND_SDP
                }
            }

            RECEIVE_ICE -> {
                if (data != null) {
                    receiveCandidate.add(data as IceCandidate)
                }
                if (currentStep == SEND_SDP) {
                    currentStep = RECEIVE_ICE
                    for (candidate in receiveCandidate) {
                        peerConnection.addIceCandidate(candidate)
                    }

                    if (sendCandidate.isNotEmpty()) {
                        onEvent(SEND_ICE, null)
                    }
                    Log.e("Callee", "Receive ${receiveCandidate.size} candidate")
                }
            }

            SEND_ICE -> {
                if (data != null) {
                    sendCandidate.add(data as IceCandidate)
                }

                if (currentStep == RECEIVE_ICE) {
                    currentStep = -1
                    for (candidate in sendCandidate) {
                        SignalClient.emitCandidate(caller, candidate)
                    }
                }
                Log.e("Callee", "Send ${sendCandidate.size} candidate")
            }
        }
        Log.e("Callee", "Step $event")
    }
}