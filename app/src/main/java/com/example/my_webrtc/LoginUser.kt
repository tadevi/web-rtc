package com.example.my_webrtc

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object LoginUser {
    private lateinit var sharePrefs: SharedPreferences
    fun init(context: Context) {
        sharePrefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)
    }

    fun setUser(name: String) {
        sharePrefs.edit {
            putString("name", name)
        }
    }

    fun getUser(): String {
        return sharePrefs.getString("name", "")!!
    }
}