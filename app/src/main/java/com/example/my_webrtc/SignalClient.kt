package com.example.my_webrtc

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

object SignalClient {
    private lateinit var userName: String

    fun initUser(userName: String) {
        this.userName = userName
    }

    fun addOnReceiveSessionDescription(callback: (String, SessionDescription) -> Unit) {
        Firebase.firestore
            .collection(userName)
            .document("session_description")
            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                documentSnapshot?.let { document ->
                    if (document["from"] != null && !document.metadata.isFromCache) {
                        val from = document["from"].toString()
                        val type = document["type"].toString()
                        val sdp = document["sdp"].toString()

                        callback(
                            from,
                            SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                        )
                    }
                }
            }
    }

    fun addOnReceiveIceCandidate(callback: (String, IceCandidate) -> Unit) {
        Firebase.firestore
            .collection(userName)
            .document("candidate")
            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                documentSnapshot?.let { document ->
                    if (document["from"] != null && !document.metadata.isFromCache) {
                        val from = document["from"].toString()
                        val candidate =
                            Gson().fromJson(
                                document["candidate"].toString(),
                                IceCandidate::class.java
                            )

                        callback(from, candidate)
                    }
                }
            }
    }

    fun addOnHangupEvent(callback: () -> Unit) {
        Firebase.firestore
            .collection(LoginUser.getUser())
            .document("status")
            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                documentSnapshot?.let { document ->
                    if (document["is_active"] != null && !document.metadata.isFromCache) {
                        if (document["is_active"] == "false") {
                            callback()
                        }
                    }
                }
            }
    }

    fun emitHangupEvent(from: String, to: String, value: Boolean = false) {
        Firebase.firestore
            .collection(to)
            .document("status")
            .set(
                hashMapOf(
                    "is_active" to value
                )
            )
    }

    fun emitSessionDescription(
        from: String,
        to: String,
        sessionDescription: SessionDescription
    ) {
        Firebase.firestore
            .collection(to)
            .document("session_description")
            .set(
                hashMapOf(
                    "from" to from,
                    "type" to sessionDescription.type.canonicalForm(),
                    "sdp" to sessionDescription.description
                )
            )
    }

    fun emitIceCandidate(
        from: String,
        to: String,
        candidate: IceCandidate
    ) {
        Firebase.firestore
            .collection(to)
            .document("candidate")
            .set(
                hashMapOf(
                    "from" to from,
                    "candidate" to Gson().toJson(candidate)
                )
            )

    }
}