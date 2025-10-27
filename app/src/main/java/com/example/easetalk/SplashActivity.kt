package com.example.easetalk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class SplashActivity : AppCompatActivity() {
    private val TAG = "SplashActivity"
    private val SPLASH_MS = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Use a very simple layout; ensure it exists at res/layout/activity_splash.xml
            setContentView(R.layout.activity_splash)

            // Safe-loading the image: if resource id is wrong, catch the exception
            val logo = findViewById<ImageView>(R.id.logo)
            try {
                // prefer mipmap/ic_launcher so mismatch with drawable won't crash
                logo.setImageResource(R.mipmap.ic_launcher)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set custom logo resource, using no image. ${e.message}")
            }

            // Delay then open WelcomeActivity
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(this@SplashActivity, WelcomeActivity::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start WelcomeActivity", e)
                    Toast.makeText(this, "Error starting app", Toast.LENGTH_SHORT).show()
                } finally {
                    finish()
                }
            }, SPLASH_MS)

        } catch (e: Exception) {
            // Catch anything unexpected to avoid silent crash
            Log.e(TAG, "Fatal error in SplashActivity.onCreate", e)
            // Fallback: still try to start WelcomeActivity (best effort)
            try {
                startActivity(Intent(this@SplashActivity, WelcomeActivity::class.java))
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback start failed", ex)
            } finally {
                finish()
            }
        }
    }
}