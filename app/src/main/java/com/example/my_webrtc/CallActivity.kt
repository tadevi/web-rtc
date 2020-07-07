package com.example.my_webrtc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_call.*
import org.webrtc.*
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer


class CallActivity : AppCompatActivity() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var opposite: String
    private lateinit var extraIceCandidate: ExtraIceCandidate
    private lateinit var extraSessionDescription: ExtraSessionDescription

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        start()
        fromIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { fromIntent(it) }
    }

    private fun fromIntent(intent: Intent) {
        when {
            intent.hasExtra("session_description") -> {
                extraSessionDescription = intent.getParcelableExtra("session_description")!!
                opposite = extraSessionDescription.from
            }

            intent.hasExtra("candidate") -> {
                extraIceCandidate = intent.getParcelableExtra("candidate")!!
                Toast.makeText(this, "Receive candidate from firebase", Toast.LENGTH_LONG).show()
                onAddCandidate(extraIceCandidate)
            }

            intent.hasExtra("callee") -> {
                opposite = intent.getStringExtra("callee")!!
                makeCall()

                btnReceive.isVisible = false
            }

            intent.hasExtra("hang_up") -> {
                finish()
            }

            else -> finish()
        }
    }

    private fun onAddCandidate(candidate: ExtraIceCandidate) {
        peerConnection.addIceCandidate(candidate.getIceCandidate())
    }

    private fun doAnswer(extraSessionDescription: ExtraSessionDescription) {
        when (SessionDescription.Type.fromCanonicalForm(extraSessionDescription.type)) {
            SessionDescription.Type.ANSWER -> {
                // you are caller
                peerConnection.setRemoteDescription(
                    CustomSdpObserver("receiveAnswer"),
                    extraSessionDescription.getSessionDescription()
                )

            }
            SessionDescription.Type.OFFER -> {
                // you are callee
                peerConnection.setRemoteDescription(
                    CustomSdpObserver("receiveOffer"),
                    extraSessionDescription.getSessionDescription()
                )

                peerConnection.createAnswer(object : CustomSdpObserver("createAnswer") {
                    override fun onCreateSuccess(description: SessionDescription) {
                        super.onCreateSuccess(description)

                        SignalClient.emitSessionDescription(
                            opposite,
                            description
                        )
                    }
                }, MediaConstraints())
            }
            else -> finish()
        }
        peerConnection.setRemoteDescription(
            CustomSdpObserver("receiveOffer"),
            extraSessionDescription.getSessionDescription()
        )

        peerConnection.createAnswer(object : CustomSdpObserver("sendAnswer") {
            override fun onCreateSuccess(description: SessionDescription) {
                super.onCreateSuccess(description)

                peerConnection.setLocalDescription(CustomSdpObserver("localDesc"), description)

                SignalClient.emitSessionDescription(
                    opposite,
                    description
                )
            }
        }, MediaConstraints())
    }

    private fun start() {
        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()

        createPeerConnection()

        addLocalStreamToPeer()

        btnReceive.setOnClickListener {
            btnReceive.isVisible = false
            doAnswer(extraSessionDescription)
        }

        btnReject.setOnClickListener {
//            SignalClient.emitHangupEvent(LoginUser.getUser(), opposite)
            finish()
        }
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                IceServer.builder("turn:numb.viagenie.ca")
                    .setUsername("anhvt52@gmail.com").setPassword("123456").createIceServer()
            )
        )

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : CustomPeerConnectionObserver() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    super.onIceCandidate(candidate)

                    runOnUiThread {
                        Toast.makeText(
                            this@CallActivity,
                            "Emit candidate to $opposite ",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    SignalClient.emitIceCandidate(opposite, candidate)
                }

                override fun onAddStream(stream: MediaStream) {
                    super.onAddStream(stream)

                    showToast("Received remote stream")
                }
            })!!
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun makeCall() {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection.createOffer(object : CustomSdpObserver("makeCall") {
            override fun onCreateSuccess(description: SessionDescription) {
                super.onCreateSuccess(description)

                peerConnection.setLocalDescription(
                    CustomSdpObserver("localDescription"),
                    description
                )

                SignalClient.emitSessionDescription(opposite, description)
            }
        }, mediaConstraints)
    }

    private fun addLocalStreamToPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("local")
        stream.addTrack(
            peerConnectionFactory.createAudioTrack(
                "audio",
                peerConnectionFactory.createAudioSource(MediaConstraints())
            )
        )

        peerConnection.addStream(stream)
    }


    override fun onDestroy() {
        super.onDestroy()
        peerConnection.close()
    }
}