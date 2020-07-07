package com.example.my_webrtc

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer

class MainActivity : AppCompatActivity() {
    private val iceServers =
        listOf("stun:stun1.l.google.com:19302", "stun:stun2.l.google.com:19302")
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private val eglBase: EglBase = EglBase.create()
    private val state = hashMapOf<String, Boolean>()
    private lateinit var opposite: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        start()
    }

    private fun requestPermission() {

    }

    private fun start() {
        localView.init(eglBase.eglBaseContext, null)
        remoteView.init(eglBase.eglBaseContext, null)
        localView.setZOrderMediaOverlay(true)
        remoteView.setZOrderMediaOverlay(true)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        createPeerConnection()

        waitToConnect()

        btnCall.setOnClickListener {
            makeCall()
        }

        waitToAnswer()
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers.map {
            IceServer.builder(it).createIceServer()
        })

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : CustomPeerConnectionObserver() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    super.onIceCandidate(candidate)

                    onAddIceCandidate(candidate)
                }

                override fun onAddStream(stream: MediaStream) {
                    super.onAddStream(stream)

                    showToast("Received remote stream")

                    runOnUiThread {
                        stream.videoTracks[0].addSink(remoteView)
                    }
                }
            })!!
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun onAddIceCandidate(candidate: IceCandidate?) {
        // notify candidate for callee
        if (candidate != null) {
            Firebase.firestore
                .collection("users")
                .document(opposite)
                .update("candidate", candidate)
        }
    }

    private fun makeCall() {
        peerConnection.createOffer(object : CustomSdpObserver("makeCall") {
            override fun onCreateSuccess(description: SessionDescription) {
                super.onCreateSuccess(description)

                peerConnection.setLocalDescription(
                    CustomSdpObserver("localDescription"),
                    description
                )

                Firebase.firestore
                    .collection("users")
                    .document(etCalle.text.toString())
                    .update(
                        hashMapOf<String, Any>(
                            "caller" to CurrentUserPreference.getUser(),
                            "type" to description.type.canonicalForm(),
                            "sdp" to description.description
                        ).toMutableMap()
                    )

                opposite = etCalle.text.toString()

                Firebase.firestore
                    .collection("users")
                    .document(CurrentUserPreference.getUser())
                    .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                        documentSnapshot?.let { document ->
                            if (state["call_sdp"] == null) {
                                if (document["sdp"] != null) {
                                    // callee answer the offer
                                    val remoteSession = SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm(document["type"].toString()),
                                        document["sdp"].toString()
                                    )

                                    peerConnection.setRemoteDescription(
                                        CustomSdpObserver("remoteDescription"),
                                        remoteSession
                                    )
                                }
                            } else {
                                state["call_sdp"] = true
                            }
                        }
                    }

            }
        }, MediaConstraints())
    }

    private fun waitToAnswer() {
        Firebase.firestore
            .collection("users")
            .document(CurrentUserPreference.getUser())
            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                documentSnapshot?.let { document ->
                    if (state["answer_sdp"] == null) {
                        if (document["sdp"] != null) {
                            // get offer from caller if current user is callee
                            val remoteSession = SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(document["type"].toString()),
                                document["sdp"].toString()
                            )

                            opposite = document["caller"].toString()

                            peerConnection.setRemoteDescription(
                                CustomSdpObserver("remoteDescription"),
                                remoteSession
                            )

                            peerConnection.createAnswer(object : CustomSdpObserver("createAnswer") {
                                override fun onCreateSuccess(description: SessionDescription) {
                                    super.onCreateSuccess(description)

                                    peerConnection.setLocalDescription(
                                        CustomSdpObserver("localDescription"),
                                        description
                                    )
                                    // update answer to db
                                    Firebase.firestore
                                        .collection("users")
                                        .document(document["caller"].toString())
                                        .update(
                                            hashMapOf<String, Any>(
                                                "callee" to CurrentUserPreference.getUser(),
                                                "type" to description.type.canonicalForm(),
                                                "sdp" to description.description
                                            ).toMutableMap()
                                        )

                                }
                            }, MediaConstraints())
                        }
                    } else {
                        state["answer_sdp"] = true
                    }
                }
            }
        addLocalStreamToPeer()
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

    private fun waitToConnect() {
        Firebase.firestore
            .collection("users")
            .document(CurrentUserPreference.getUser())
            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                documentSnapshot?.let { document ->
                    if (state["candidate"] == null) {
                        if (document["candidate"] != null) {
                            val candidate = document.get("candidate")
                            peerConnection.addIceCandidate(
                                Gson().fromJson(
                                    JSONObject(candidate as MutableMap<Any?, Any?>).toString(),
                                    IceCandidate::class.java
                                )
                            )
                        }
                    } else {
                        state["candidate"] = true
                    }
                }
            }
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
    }
//    private val iceServers =
//        listOf("stun:stun1.l.google.com:19302", "stun:stun2.l.google.com:19302")
//    private lateinit var peerConnectionFactory: PeerConnectionFactory
//    private lateinit var peerConnection: PeerConnection
//    private val eglBase: EglBase = EglBase.create()
//    private val state = hashMapOf<String, Boolean>()
//    private lateinit var opposite: String
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        start()
//    }
//
//    private fun requestPermission() {
//
//    }
//
//    private fun start() {
//        localView.init(eglBase.eglBaseContext, null)
//        remoteView.init(eglBase.eglBaseContext, null)
//        localView.setZOrderMediaOverlay(true)
//        remoteView.setZOrderMediaOverlay(true)
//
//        peerConnectionFactory = PeerConnectionFactory.builder()
//            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
//            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
//            .createPeerConnectionFactory()
//
//        createPeerConnection()
//
//        waitToConnect()
//
//        btnCall.setOnClickListener {
//            makeCall()
//        }
//
//        waitToAnswer()
//    }
//
//    private fun createPeerConnection() {
//        val rtcConfig = PeerConnection.RTCConfiguration(iceServers.map {
//            IceServer.builder(it).createIceServer()
//        })
//
//        peerConnection = peerConnectionFactory.createPeerConnection(
//            rtcConfig,
//            object : CustomPeerConnectionObserver() {
//                override fun onIceCandidate(candidate: IceCandidate) {
//                    super.onIceCandidate(candidate)
//
//                    onAddIceCandidate(candidate)
//                }
//
//                override fun onAddStream(stream: MediaStream) {
//                    super.onAddStream(stream)
//
//                }
//            })!!
//    }
//
//    private fun onStreamReceive(mediaStream: MediaStream) {
//
//    }
//
//    private fun showToast(msg: String) {
//        runOnUiThread {
//            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT)
//                .show()
//        }
//    }
//
//    private fun onAddIceCandidate(candidate: IceCandidate?) {
//        // notify candidate for callee
//        if (candidate != null) {
//            Firebase.firestore
//                .collection("users")
//                .document(opposite)
//                .update("candidate", Gson().toJson(candidate))
//        }
//    }
//
//    private fun makeCall() {
//        peerConnection.createOffer(object : CustomSdpObserver("makeCall") {
//            override fun onCreateSuccess(description: SessionDescription) {
//                super.onCreateSuccess(description)
//
//                peerConnection.setLocalDescription(
//                    CustomSdpObserver("localDescription"),
//                    description
//                )
//
//                Firebase.firestore
//                    .collection("users")
//                    .document(etCalle.text.toString())
//                    .update(
//                        hashMapOf<String, Any>(
//                            "caller" to CurrentUserPreference.getUser(),
//                            "type" to description.type.canonicalForm(),
//                            "sdp" to description.description
//                        ).toMutableMap()
//                    )
//
//                opposite = etCalle.text.toString()
//
//                Firebase.firestore
//                    .collection("users")
//                    .document(CurrentUserPreference.getUser())
//                    .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
//                        documentSnapshot?.let { document ->
//                            if (state["call_sdp"] == null) {
//                                if (document["sdp"] != null) {
//                                    // callee answer the offer
//                                    val remoteSession = SessionDescription(
//                                        SessionDescription.Type.fromCanonicalForm(document["type"].toString()),
//                                        document["sdp"].toString()
//                                    )
//
//                                    peerConnection.setRemoteDescription(
//                                        CustomSdpObserver("remoteDescription"),
//                                        remoteSession
//                                    )
//                                }
//                            } else {
//                                state["call_sdp"] = true
//                            }
//                        }
//                    }
//
//            }
//        }, MediaConstraints())
//    }
//
//    private fun waitToAnswer() {
//        Firebase.firestore
//            .collection("users")
//            .document(CurrentUserPreference.getUser())
//            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
//                documentSnapshot?.let { document ->
//                    if (state["answer_sdp"] == null) {
//                        if (document["sdp"] != null) {
//                            // get offer from caller if current user is callee
//                            val remoteSession = SessionDescription(
//                                SessionDescription.Type.fromCanonicalForm(document["type"].toString()),
//                                document["sdp"].toString()
//                            )
//
//                            opposite = document["caller"].toString()
//
//                            peerConnection.setRemoteDescription(
//                                CustomSdpObserver("remoteDescription"),
//                                remoteSession
//                            )
//
//                            peerConnection.createAnswer(object : CustomSdpObserver("createAnswer") {
//                                override fun onCreateSuccess(description: SessionDescription) {
//                                    super.onCreateSuccess(description)
//
//                                    peerConnection.setLocalDescription(
//                                        CustomSdpObserver("localDescription"),
//                                        description
//                                    )
//                                    // update answer to db
//                                    Firebase.firestore
//                                        .collection("users")
//                                        .document(document["caller"].toString())
//                                        .update(
//                                            hashMapOf<String, Any>(
//                                                "callee" to CurrentUserPreference.getUser(),
//                                                "type" to description.type.canonicalForm(),
//                                                "sdp" to description.description
//                                            ).toMutableMap()
//                                        )
//
//                                }
//                            }, MediaConstraints())
//                        }
//                    } else {
//                        state["answer_sdp"] = true
//                    }
//                }
//            }
//        addLocalStreamToPeer()
//    }
//
//    private fun addLocalStreamToPeer() {
//        val stream = peerConnectionFactory.createLocalMediaStream("local")
//        stream.addTrack(
//            peerConnectionFactory.createAudioTrack(
//                "audio",
//                peerConnectionFactory.createAudioSource(MediaConstraints())
//            )
//        )
//        stream.addTrack(getVideoTrack())
//
//        peerConnection.addStream(stream)
//    }
//
//    private fun waitToConnect() {
//        Firebase.firestore
//            .collection("users")
//            .document(CurrentUserPreference.getUser())
//            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
//                documentSnapshot?.let { document ->
//                    if (state["candidate"] == null) {
//                        if (document["candidate"] != null) {
//                            val candidate = Gson().fromJson(
//                                document["candidate"].toString(),
//                                IceCandidate::class.java
//                            )
//                            peerConnection.addIceCandidate(candidate)
//                        }
//                    } else {
//                        state["candidate"] = true
//                    }
//                }
//            }
//    }
//
//    private fun getVideoTrack(): VideoTrack {
//        val videoCapture = createCameraCapturer(Camera1Enumerator(false))!!
//        val surfaceTextureHelper =
//            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
//        val videoSource = peerConnectionFactory.createVideoSource(videoCapture.isScreencast)
//        videoCapture.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
//
//        val videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
//        videoCapture.startCapture(1024, 720, 30)
//        videoTrack.addSink(localView)
//        localView.setMirror(true)
//
//        return videoTrack
//    }
//
//    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
//        val deviceNames = enumerator.deviceNames
//
//        for (deviceName in deviceNames) {
//            if (enumerator.isFrontFacing(deviceName)) {
//                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
//                if (videoCapturer != null) {
//                    return videoCapturer
//                }
//            }
//        }
//
//        for (deviceName in deviceNames) {
//            if (!enumerator.isFrontFacing(deviceName)) {
//                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
//                if (videoCapturer != null) {
//                    return videoCapturer
//                }
//            }
//        }
//        return null
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        peerConnection.close()
//    }
}