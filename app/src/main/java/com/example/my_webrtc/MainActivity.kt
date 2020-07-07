package com.example.my_webrtc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCall.setOnClickListener {
            startActivity(
                Intent(this, CallActivity::class.java).putExtra(
                    "callee",
                    etCalle.text.toString()
                )
            )
        }
    }
}