package com.example.my_webrtc

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        btnLogin.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            Firebase.firestore
                .collection("rooms")
                .document(etUserName.text.toString())
                .set(hashMapOf("date_update" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Toast.makeText(this, "Login successfully", Toast.LENGTH_SHORT).show()
                    LoginUser.setUser(etUserName.text.toString())

                    initSignal()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    progressBar.visibility = View.GONE
                }
        }
    }

    private fun initSignal() {
        // response signal call
        SignalClient.registerReceiveSdpCallback { from, sessionDescription ->
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra(
                "session_description",
                ExtraSessionDescription.from(from, sessionDescription)
            )
            startActivity(intent)
        }

        // response signal connect
        SignalClient.registerReceiveCandidateCallback { from, iceCandidate ->
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra("candidate", ExtraIceCandidate.from(from, iceCandidate))

            startActivity(intent)
        }

//        SignalClient.addOnHangupEvent {
//            val intent = Intent(this, CallActivity::class.java)
//            intent.putExtra("hang_up", true)
//            startActivity(intent)
//        }
        SignalClient.init()

        startActivity(Intent(this, MainActivity::class.java))
    }
}