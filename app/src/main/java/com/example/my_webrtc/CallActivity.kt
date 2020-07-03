package com.example.my_webrtc

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import org.webrtc.*
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer


class CallActivity : AppCompatActivity() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private val state = hashMapOf<String, Boolean>()
    private lateinit var opposite: String
    private lateinit var calleeName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        calleeName = intent.getStringExtra("callee")!!
        start()
    }


    private fun start() {
        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()

        createPeerConnection()

        addLocalStreamToPeer()

        waitToConnect()
        if (calleeName.isNotEmpty())
            makeCall()

        waitToAnswer()
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

                    onAddIceCandidate(candidate)
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

    private fun onAddIceCandidate(candidate: IceCandidate?) {
        // notify candidate for callee
        if (candidate != null) {
            Firebase.firestore
                .collection("users")
                .document(opposite)
                .update("candidate", Gson().toJson(candidate))
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

                Firebase.firestore
                    .collection("users")
                    .document(calleeName)
                    .update(
                        hashMapOf<String, Any>(
                            "caller" to CurrentUserPreference.getUser(),
                            "type" to description.type.canonicalForm(),
                            "sdp" to description.description
                        ).toMutableMap()
                    )

                opposite = calleeName

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
        }, mediaConstraints)
    }

    private fun waitToAnswer() {
        Firebase.firestore
            .collection("users")
            .document(CurrentUserPreference.getUser())
            .addSnapshotListener { documentSnapshot, _ ->
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

    private fun waitToConnect() {
        Firebase.firestore
            .collection("users")
            .document(CurrentUserPreference.getUser())
            .addSnapshotListener { documentSnapshot, _ ->
                documentSnapshot?.let { document ->
                    if (state["candidate"] == null) {
                        if (document["candidate"] != null) {
                            val candidate = Gson().fromJson(
                                document["candidate"].toString(),
                                IceCandidate::class.java
                            )
                            peerConnection.addIceCandidate(candidate)
                        }
                    } else {
                        state["candidate"] = true
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        peerConnection.close()
    }
}