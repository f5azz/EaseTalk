package com.example.easetalk

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class VideoCallActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        val tvStatus = findViewById<TextView>(R.id.tvVideoStatus)
        tvStatus.text = "Preparing video call..."

        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                val gender = doc.getString("botGender") ?: "Not set"
                val lang = doc.getString("botLanguage") ?: "Not set"
                tvStatus.text = "Video call with AI ($gender, $lang)\nConnecting..."

                // Start AI call after fetching settings
                startAICall("video")

            }.addOnFailureListener { e ->
                tvStatus.text = "Error loading bot settings: ${e.message}"
            }
        } else {
            tvStatus.text = "User not logged in"
        }
    }

    private fun startAICall(callType: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            val botGender = doc.getString("botGender") ?: "Female"
            val botLanguage = doc.getString("botLanguage") ?: "English"

            val data = mapOf(
                "userId" to user.uid,
                "callType" to callType,
                "botGender" to botGender,
                "botLanguage" to botLanguage
            )

            val jsonData = JSONObject(data).toString()
            val queue = Volley.newRequestQueue(this)
            val url = "http://10.149.49.104:3000/start-ai-call"

            val request = object : StringRequest(
                Method.POST, url,
                { response -> Log.d("AI_CALL", "Started: $response") },
                { error ->
                    val body = error.networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e("AI_CALL", "Error: ${error.message}, Response: $body")
                }
            ) {
                override fun getBodyContentType() = "application/json; charset=utf-8"
                override fun getBody() = jsonData.toByteArray(Charsets.UTF_8)
            }


            queue.add(request)
        }
    }
}
