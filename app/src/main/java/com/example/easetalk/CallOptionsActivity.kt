package com.example.easetalk

import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class CallOptionsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var btnVoice: Button
    private lateinit var btnVideo: Button
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_options)

        // Firebase instances
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Views
        btnVoice = findViewById(R.id.btnVoiceCall)
        btnVideo = findViewById(R.id.btnVideoCall)
        tvInfo = findViewById(R.id.tvBotInfo)

        // Load user bot settings
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val gender = doc.getString("botGender") ?: "Not set"
                    val language = doc.getString("botLanguage") ?: "Not set"
                    tvInfo.text = "AI: $gender · $language"
                }
                .addOnFailureListener {
                    tvInfo.text = "AI info not available"
                }
        }

        // Button click listeners
        btnVoice.setOnClickListener {
            startActivity(Intent(this, VoiceCallActivity::class.java))
        }
        btnVideo.setOnClickListener {
            startActivity(Intent(this, VideoCallActivity::class.java))
        }
    }
}
