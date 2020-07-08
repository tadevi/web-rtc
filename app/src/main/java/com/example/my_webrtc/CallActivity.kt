package com.example.my_webrtc

import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer

class CallActivity : AppCompatActivity() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var opposite: String
    private lateinit var extraIceCandidate: ExtraIceCandidate
    private lateinit var extraSessionDescription: ExtraSessionDescription
    private lateinit var peerConnection: PeerConnection
    private val eglBase = EglBase.create()

    private lateinit var rtcClient: Client
    private var ice = false


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

                if (!this::rtcClient.isInitialized) {
                    rtcClient = Callee(opposite, peerConnection)
                }

                if (rtcClient is Caller) {
                    rtcClient.onEvent(
                        Caller.RECEIVE_SDP,
                        extraSessionDescription.getSessionDescription()
                    )
                }

            }

            intent.hasExtra("candidate") -> {
                extraIceCandidate = intent.getParcelableExtra("candidate")!!

                if (rtcClient is Caller) {
                    rtcClient.onEvent(Caller.RECEIVE_ICE, extraIceCandidate.getIceCandidate())
                } else {
                    rtcClient.onEvent(Callee.RECEIVE_ICE, extraIceCandidate.getIceCandidate())
                }
            }

            intent.hasExtra("callee") -> {
                // you are caller
                opposite = intent.getStringExtra("callee")!!
                btnReceive.isVisible = false

                rtcClient = Caller(opposite, peerConnection)
                rtcClient.onEvent(Caller.MAKE_CALL, null)
            }

            intent.hasExtra("hang_up") -> {
                finish()
            }

            else -> finish()
        }
    }

    private fun start() {
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        localView.init(eglBase.eglBaseContext, null)
        remoteView.init(eglBase.eglBaseContext, null)
        localView.setZOrderMediaOverlay(true)
        remoteView.setZOrderMediaOverlay(true)

        createPeerConnection()

        addLocalStreamToPeer()

        btnReceive.setOnClickListener {
            btnReceive.isVisible = false

            rtcClient.onEvent(
                Callee.RECEIVE_CALL,
                extraSessionDescription.getSessionDescription()
            )
        }

        btnReject.setOnClickListener {
//            SignalClient.emitHangupEvent(LoginUser.getUser(), opposite)
            finish()
        }
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
//                IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
//                IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
                IceServer.builder("turn:numb.viagenie.ca")
                    .setUsername("anhvt52@gmail.com").setPassword("123456").createIceServer()
            )
        )

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : CustomPeerConnectionObserver() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    super.onIceCandidate(candidate)

                    if (ice) return;

                    if (rtcClient is Callee) {
                        rtcClient.onEvent(Callee.SEND_ICE, candidate)
                    } else {
                        rtcClient.onEvent(Caller.SEND_ICE, candidate)
                    }
                    ice = true;
                }

                override fun onAddStream(stream: MediaStream) {
                    super.onAddStream(stream)

                    runOnUiThread {
                        stream.videoTracks[0].addSink(remoteView)
                        stream.audioTracks[0].setVolume(50.0)
                    }
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

    private fun addLocalStreamToPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("local")
        stream.addTrack(
            peerConnectionFactory.createAudioTrack(
                "audio",
                peerConnectionFactory.createAudioSource(MediaConstraints())
            )
        )
        stream.addTrack(getVideoTrack())

        peerConnection.addStream(stream)
    }

    private fun getVideoTrack(): VideoTrack {
        val videoCapture = createCameraCapturer(Camera1Enumerator(false))!!
        val surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = peerConnectionFactory.createVideoSource(videoCapture.isScreencast)
        videoCapture.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)

        val videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
        videoCapture.startCapture(1024, 720, 30)
        videoTrack.addSink(localView)
        localView.setMirror(true)

        return videoTrack
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        peerConnection.close()
        SignalClient.clear()
    }
}