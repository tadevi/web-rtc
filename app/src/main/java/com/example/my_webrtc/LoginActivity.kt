package com.example.my_webrtc

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        btnLogin.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            Firebase.firestore
                .collection("users")
                .document(etUserName.text.toString())
                .set(hashMapOf("date_update" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Toast.makeText(this, "Login successfully", Toast.LENGTH_SHORT).show()
                    CurrentUserPreference.setUser(etUserName.text.toString())
                    startActivity(Intent(this, MainActivity::class.java))
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    progressBar.visibility = View.GONE
                }
        }
    }
}