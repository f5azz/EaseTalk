package com.example.easetalk

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class GenderActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gender) // keep your existing layout

        val btnMale = findViewById<Button>(R.id.btnMale)
        val btnFemale = findViewById<Button>(R.id.btnFemale)

        btnMale.setOnClickListener { saveBotGenderAndContinue("Male") }
        btnFemale.setOnClickListener { saveBotGenderAndContinue("Female") }
    }

    private fun saveBotGenderAndContinue(gender: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Save as botGender (not user's gender)
        val update = mapOf("botGender" to gender)
        db.collection("users").document(user.uid)
            .set(update, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "AI voice gender set to $gender", Toast.LENGTH_SHORT).show()
                // continue to language selection
                startActivity(Intent(this, LanguageActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save bot gender: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}