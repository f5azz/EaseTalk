package com.example.easetalk

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class VoiceCallActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEndCall: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        // Initialize UI elements
        tvStatus = findViewById(R.id.tvVoiceStatus)
        progressBar = findViewById(R.id.progressConnecting)
        btnEndCall = findViewById(R.id.btnEndCall)

        tvStatus.text = "Preparing voice call..."
        progressBar.visibility = View.VISIBLE

        btnEndCall.setOnClickListener {
            endCall()
        }

        // Fetch user settings and start AI call
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val gender = doc.getString("botGender") ?: "Female"
                    val lang = doc.getString("botLanguage") ?: "English"

                    tvStatus.text = "Connecting with AI ($gender, $lang)..."
                    startAICall("voice")

                }.addOnFailureListener { e ->
                    tvStatus.text = "Error loading bot settings: ${e.message}"
                    progressBar.visibility = View.GONE
                }
        } else {
            tvStatus.text = "User not logged in"
            progressBar.visibility = View.GONE
        }
    }

    private fun startAICall(callType: String) {
        val user = auth.currentUser ?: return

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
            val url = "http://10.149.49.104:3000/start-ai-call" // change IP if needed

            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d("AI_CALL", "Started: $response")
                    tvStatus.text = "AI Voice Call Connected 🎙️"
                    progressBar.visibility = View.GONE
                    btnEndCall.visibility = View.VISIBLE
                },
                { error ->
                    val body = error.networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e("AI_CALL", "Error: ${error.message}, Response: $body")
                    tvStatus.text = "Failed to start AI call"
                    progressBar.visibility = View.GONE
                }
            ) {
                override fun getBodyContentType() = "application/json; charset=utf-8"
                override fun getBody() = jsonData.toByteArray(Charsets.UTF_8)
            }

            queue.add(request)
        }
    }

    private fun endCall() {
        tvStatus.text = "Ending call..."
        btnEndCall.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        // Here you can add Agora leaveChannel logic later
        tvStatus.postDelayed({
            tvStatus.text = "Call ended."
            progressBar.visibility = View.GONE
        }, 1000)
    }
}
