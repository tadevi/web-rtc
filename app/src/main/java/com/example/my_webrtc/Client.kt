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
    private var sendCandidate: IceCandidate? = null

    @Volatile
    private var receiveCandidate: IceCandidate? = null

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

                    if (sendCandidate != null) {
                        onEvent(SEND_ICE, sendCandidate)
                    }
                }
            }

            SEND_ICE -> {
                sendCandidate = data as IceCandidate

                if (currentStep == RECEIVE_SDP) {
                    currentStep = SEND_ICE
                    SignalClient.emitCandidate(callee, sendCandidate!!)

                    if (receiveCandidate != null) {
                        onEvent(RECEIVE_ICE, receiveCandidate)
                    }
                }
            }

            RECEIVE_ICE -> {
                receiveCandidate = data as IceCandidate

                if (currentStep == SEND_ICE) {
                    currentStep = -1

                    peerConnection.addIceCandidate(receiveCandidate)
                }
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
    private var receiveCandidate: IceCandidate? = null

    @Volatile
    private var sendCandidate: IceCandidate? = null

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
                receiveCandidate = data as IceCandidate

                if (currentStep == SEND_SDP) {
                    currentStep = RECEIVE_ICE

                    peerConnection.addIceCandidate(receiveCandidate)

                    if (sendCandidate != null) {
                        onEvent(SEND_ICE, sendCandidate)
                    }
                }
            }

            SEND_ICE -> {
                sendCandidate = data as IceCandidate

                if (currentStep == RECEIVE_ICE) {
                    currentStep = -1

                    SignalClient.emitCandidate(caller, sendCandidate!!)
                }
            }
        }
        Log.e("Callee", "Step $event")
    }
}