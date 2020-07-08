package com.example.my_webrtc

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap

object SignalClient {
    private var state: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    private var sdpCallback: ((String, SessionDescription) -> Unit)? = null
    private var candidateCallback: ((String, IceCandidate) -> Unit)? = null

    private fun onDocumentChange(name: String, callback: (DocumentSnapshot) -> Unit) {
        Firebase
            .firestore
            .collection("rooms")
            .document(name)
            .addSnapshotListener { documentSnapshot, _ ->
                documentSnapshot?.let {
                    callback(it)
                }
            }
    }

    fun init() {
        onDocumentChange(LoginUser.getUser()) {
            if (state["sdp"] == null) {
                if (it["sdp"] != null) {
                    state["sdp"] = true
                    val from = it["from"].toString()
                    val sdp = it["sdp"] as HashMap<Any, Any?>
                    sdpCallback?.invoke(
                        from,
                        SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(sdp["type"].toString()),
                            sdp["description"].toString()
                        )
                    )
                }
            }
        }

        onDocumentChange(LoginUser.getUser()) {
            if (state["candidate"] == null) {
                if (it["candidate"] != null) {
                    state["candidate"] = true
                    candidateCallback?.invoke(
                        it["from"].toString(),
                        Gson().fromJson(
                            JSONObject(it["candidate"] as MutableMap<Any?, Any?>).toString(),
                            IceCandidate::class.java
                        )
                    )
                }
            }
        }
    }

    private fun editDocument(name: String, block: DocumentReference.() -> Unit) {
        Firebase
            .firestore
            .collection("rooms")
            .document(name)
            .block()
    }

    fun clear() {
        state.clear()
    }

    fun emitSdp(to: String, sdp: SessionDescription) {
        editDocument(to) {
            update(
                hashMapOf(
                    "from" to LoginUser.getUser(),
                    "sdp" to sdp
                )
            )
        }
    }

    fun emitCandidate(to: String, candidate: IceCandidate) {
        editDocument(to) {
            update(
                "candidate", candidate
            )
        }
    }

    fun registerReceiveSdpCallback(callback: (String, SessionDescription) -> Unit) {
        sdpCallback = callback
    }

    fun registerReceiveCandidateCallback(callback: (String, IceCandidate) -> Unit) {
        candidateCallback = callback
    }
}