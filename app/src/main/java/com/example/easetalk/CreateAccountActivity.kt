package com.example.easetalk

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etFirst = findViewById<EditText>(R.id.etFirstName)
        val etLast = findViewById<EditText>(R.id.etLastName)
        val etEmail = findViewById<EditText>(R.id.etEmailCreate)
        val etPass = findViewById<EditText>(R.id.etPasswordCreate)
        val btnCreate = findViewById<Button>(R.id.btnCreateAccount)

        btnCreate.setOnClickListener {
            val first = etFirst.text.toString().trim()
            val last = etLast.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString()

            if (first.isEmpty() || last.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create user in Firebase Auth
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            // Save basic profile to Firestore
                            val userMap = hashMapOf(
                                "firstName" to first,
                                "lastName" to last,
                                "email" to email,
                                "gender" to "",     // empty until user selects
                                "language" to ""    // empty until user selects
                            )
                            db.collection("users").document(uid).set(userMap)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                                    // After creation, sign out so user must login explicitly
                                    auth.signOut()
                                    // Redirect to Login screen
                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Firestore save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Toast.makeText(this, "Account created but UID null", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}