package com.example.my_webrtc

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

object SignalClient {
    private lateinit var userName: String
    private lateinit var from: String

    fun initUser(userName: String) {
        this.userName = userName
    }

    fun getFrom() = from

    fun addOnReceiveSessionDescription(callback: (String, SessionDescription) -> Unit) {
        Firebase.firestore
            .collection(userName)
            .document("sdp")
            .addSnapshotListener { documentSnapshot, _ ->
                documentSnapshot?.let { document ->
                    if (document["from"] != null && !document.metadata.isFromCache) {
                        from = document["from"].toString()
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
            .addSnapshotListener { documentSnapshot, _ ->
                documentSnapshot?.let { document ->
                    if (document["sdp"] != null && !document.metadata.isFromCache) {
                        callback(
                            from,
                            Gson().fromJson(
                                JSONObject(document.data!!).toString(),
                                IceCandidate::class.java
                            )
                        )
                    }
                }
            }
    }

//    fun addOnHangupEvent(callback: () -> Unit) {
//        Firebase.firestore
//            .collection(LoginUser.getUser())
//            .document("status")
//            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
//                documentSnapshot?.let { document ->
//                    if (document["is_active"] != null && !document.metadata.isFromCache) {
//                        if (document["is_active"] == "false") {
//                            callback()
//                        }
//                    }
//                }
//            }
//    }

//    fun emitHangupEvent(from: String, to: String, value: Boolean = false) {
//        Firebase.firestore
//            .collection(to)
//            .document("status")
//            .set(
//                hashMapOf(
//                    "is_active" to value
//                )
//            )
//    }

    fun emitSessionDescription(
        opposite: String,
        sessionDescription: SessionDescription
    ) {
        Firebase.firestore
            .collection(opposite)
            .document("sdp")
            .set(
                hashMapOf(
                    "from" to LoginUser.getUser(),
                    "type" to sessionDescription.type.canonicalForm(),
                    "sdp" to sessionDescription.description
                )
            )
    }

    fun emitIceCandidate(
        to: String,
        candidate: IceCandidate
    ) {
        Firebase.firestore
            .collection(to)
            .document("candidate")
            .set(
                candidate
            )

    }
}