package com.example.easetalk

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LanguageActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language) // keep your existing layout

        val btnEnglish = findViewById<Button>(R.id.btnEnglish)
        val btnHindi = findViewById<Button>(R.id.btnHindi)
        val btnKannada = findViewById<Button>(R.id.btnKannada)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnEnglish.setOnClickListener { selectedLanguage = "English"; Toast.makeText(this,"English selected", Toast.LENGTH_SHORT).show() }
        btnHindi.setOnClickListener { selectedLanguage = "Hindi"; Toast.makeText(this,"Hindi selected", Toast.LENGTH_SHORT).show() }
        btnKannada.setOnClickListener { selectedLanguage = "Kannada"; Toast.makeText(this,"Kannada selected", Toast.LENGTH_SHORT).show() }

        btnContinue.setOnClickListener {
            if (selectedLanguage == null) {
                Toast.makeText(this, "Please select a language for AI", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveBotLanguageAndContinue(selectedLanguage!!)
        }
    }

    private fun saveBotLanguageAndContinue(language: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val update = mapOf("botLanguage" to language)
        db.collection("users").document(user.uid)
            .set(update, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "AI language set to $language", Toast.LENGTH_SHORT).show()
                // Move to CallOptions (voice/video)
                startActivity(Intent(this, CallOptionsActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save bot language: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}