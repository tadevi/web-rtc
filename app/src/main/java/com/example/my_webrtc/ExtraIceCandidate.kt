package com.example.my_webrtc

import android.os.Parcelable
import com.google.gson.Gson
import kotlinx.android.parcel.Parcelize
import org.webrtc.IceCandidate

@Parcelize
data class ExtraIceCandidate(
    val from: String,
    val candidate: String
) : Parcelable {
    companion object {
        fun from(from: String, candidate: IceCandidate): ExtraIceCandidate {
            return ExtraIceCandidate(
                from,
                Gson().toJson(candidate)
            )
        }
    }

    fun getIceCandidate(): IceCandidate {
        return Gson().fromJson(candidate, IceCandidate::class.java)
    }
}