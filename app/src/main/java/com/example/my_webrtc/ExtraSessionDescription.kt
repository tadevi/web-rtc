package com.example.my_webrtc

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.webrtc.SessionDescription

@Parcelize
data class ExtraSessionDescription(
    val from: String,
    val type: String,
    val description: String
) : Parcelable {
    companion object {
        fun from(from: String, sessionDescription: SessionDescription): ExtraSessionDescription {
            return ExtraSessionDescription(
                from,
                sessionDescription.type.canonicalForm(),
                sessionDescription.description
            )
        }
    }

    fun getSessionDescription(): SessionDescription {
        return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), description)
    }
}