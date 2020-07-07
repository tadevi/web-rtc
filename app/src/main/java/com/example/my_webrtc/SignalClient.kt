package com.example.my_webrtc

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

object SignalClient {
    private var state = hashMapOf<String, Boolean>()
    private var sdpCallback: ((SessionDescription) -> Unit)? = null
    private var candidateCallback: ((IceCandidate) -> Unit)? = null

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
        onDocumentChange(
            CurrentUserPreference.getUser()
        ) {
            if (it["sdp"] != null) {
                if (state["sdp"] == null) {
                    sdpCallback?.invoke(
                        SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(it["type"].toString()),
                            it["description"].toString()
                        )
                    )
                } else {
                    state["sdp"] = true
                }
            } else if (it["candidate"] != null) {
                if (state["candidate"] == null) {
                    candidateCallback?.invoke(
                        Gson().fromJson(
                            JSONObject(it["candidate"] as MutableMap<Any?, Any?>).toString(),
                            IceCandidate::class.java
                        )
                    )
                } else {
                    state["candidate"] = true
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
        sdpCallback = null
        candidateCallback = null
    }

    fun emitSdp(to: String, sdp: SessionDescription) {
        editDocument(to) {
            update(
                "sdp", sdp
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

    fun registerReceiveSdpCallback(callback: (SessionDescription) -> Unit) {
        sdpCallback = callback
    }

    fun registerReceiveCandidateCallback(callback: (IceCandidate) -> Unit) {
        candidateCallback = callback
    }
}