package com.example.easetalk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import org.json.JSONObject

class VideoCallActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var agoraEngine: RtcEngine? = null
    // ✅ Replace with your real Agora App ID
    private val appId = "2b5dd36c54654aedb40beb8acdb58c6f"
    private var token: String? = null
    private val channelName = "EaseTalkAI"

    private lateinit var tvStatus: TextView

    // permission request codes
    private val REQ_CAMERA = 1001
    private val REQ_AUDIO = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        tvStatus = findViewById(R.id.tvVideoStatus)
        tvStatus.text = "Preparing AI video call..."

        // Check permissions and proceed
        val cameraOk = checkPermission(Manifest.permission.CAMERA, REQ_CAMERA)
        val audioOk = checkPermission(Manifest.permission.RECORD_AUDIO, REQ_AUDIO)

        if (cameraOk && audioOk) {
            fetchUserSettingsAndStart()
        } else {
            // When permissions are requested, result will come to onRequestPermissionsResult
            tvStatus.text = "Requesting camera & microphone permissions..."
        }
    }

    // ---------- Step 1: Fetch User Settings ----------
    private fun fetchUserSettingsAndStart() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val botGender = doc.getString("botGender") ?: "Female"
                    val botLanguage = doc.getString("botLanguage") ?: "English"
                    tvStatus.text = "Connecting AI bot ($botGender, $botLanguage)..."

                    // Start AI session in backend
                    startAICall(user.uid, "video", botGender, botLanguage)
                }
                .addOnFailureListener { e ->
                    tvStatus.text = "Error loading bot settings: ${e.message}"
                    Log.e("AI_CALL", "Error loading bot settings", e)
                }
        } else {
            tvStatus.text = "User not logged in"
        }
    }

    // ---------- Step 2: Start AI Call ----------
    private fun startAICall(userId: String, callType: String, botGender: String, botLanguage: String) {
        val queue = Volley.newRequestQueue(this)
        // ✅ Replace with your backend IP/URL if needed
        val url = "http://10.149.49.104:3000/start-ai-call"

        val data = JSONObject(
            mapOf(
                "userId" to userId,
                "callType" to callType,
                "botGender" to botGender,
                "botLanguage" to botLanguage
            )
        )

        val request = object : StringRequest(
            Method.POST, url,
            { response ->
                // Backend started the AI session successfully, now fetch Agora token and setup SDK
                Log.d("AI_CALL", "Started: $response")
                // fetch token for the channel; once token arrives we'll setup Agora engine & join
                fetchAgoraToken("EaseTalkAI")
            },
            { error ->
                val body = error.networkResponse?.data?.toString(Charsets.UTF_8)
                Log.e("AI_CALL", "Error: ${error.message}, Response: $body")
                tvStatus.text = "Backend error: ${error.message}"
            }
        ) {
            override fun getBodyContentType() = "application/json; charset=utf-8"
            override fun getBody() = data.toString().toByteArray(Charsets.UTF_8)
        }

        queue.add(request)
    }

    // ---------- Step 3: Fetch Agora Token ----------
    private fun fetchAgoraToken(channelName: String) {
        val url = "http://10.149.49.104:3000/generate-agora-token"
        val json = JSONObject().apply {
            put("channelName", "EaseTalkAI")
            put("uid", 0)
        }

        val request = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObj = JSONObject(response)
                    token = jsonObj.getString("token")
                    Log.d("AGORA_TOKEN", "Token fetched: $token")
                    setupVideoSDKEngine()
                } catch (e: Exception) {
                    Log.e("TOKEN", "Parsing error: ${e.message}", e)
                    tvStatus.text = "Error parsing token"
                }
            },
            { error ->
                Log.e("TOKEN", "Token fetch failed: ${error.message}", error)
                tvStatus.text = "Token fetch failed"
            }
        ) {
            override fun getBodyContentType() = "application/json; charset=utf-8"
            override fun getBody() = json.toString().toByteArray(Charsets.UTF_8)
        }

        Volley.newRequestQueue(this).add(request)
    }

    // ---------- Step 4: Setup Agora SDK & Join ----------
    private fun setupVideoSDKEngine() {
        try {
            val handler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    runOnUiThread { tvStatus.text = "✅ Connected to AI bot on $channel" }
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    runOnUiThread {
                        val remoteContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)
                        // create remote view
                        val remoteView = SurfaceView(baseContext)
                        remoteView.setZOrderMediaOverlay(true)
                        remoteContainer.removeAllViews()
                        remoteContainer.addView(remoteView)
                        agoraEngine?.setupRemoteVideo(VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
                        Log.d("AGORA", "Remote user joined: $uid")
                    }
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    runOnUiThread {
                        tvStatus.text = "Bot disconnected."
                        findViewById<FrameLayout>(R.id.remote_video_view_container).removeAllViews()
                        Log.d("AGORA", "Remote user offline: $uid reason: $reason")
                    }
                }
            }

            // create engine
            agoraEngine = RtcEngine.create(baseContext, appId, handler)

            // enable video
            agoraEngine?.enableVideo()

            // Setup local video
            val localContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
            localContainer.removeAllViews()
            val localView = SurfaceView(baseContext)
            // local should be behind remote overlay typically
            localView.setZOrderOnTop(false)
            localContainer.addView(localView)
            agoraEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))

            // Join the channel if token present
            token?.let {
                agoraEngine?.joinChannel(it, channelName, "", 0)
                Log.d("AGORA", "Joining Agora channel: $channelName with token")
                tvStatus.text = "Joining channel..."
            } ?: run {
                tvStatus.text = "Missing Agora token"
                Log.e("AGORA", "Token is null, cannot join")
            }

        } catch (e: Exception) {
            Log.e("AGORA", "Setup failed: ${e.message}", e)
            tvStatus.text = "Video setup failed: ${e.message}"
        }
    }

    // ---------- Permission helper ----------
    private fun checkPermission(permission: String, requestCode: Int): Boolean {
        return if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            false
        } else {
            true
        }
    }

    // Handle permission results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // We request camera and audio separately; check both results and proceed when both are granted
        val cameraGranted = if (requestCode == REQ_CAMERA) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            // if not camera request, check current permission state
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }

        val audioGranted = if (requestCode == REQ_AUDIO) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }

        if (cameraGranted && audioGranted) {
            // both permissions available now
            tvStatus.text = "Permissions granted. Starting..."
            fetchUserSettingsAndStart()
        } else {
            tvStatus.text = "Camera and microphone permissions are required."
        }
    }

    // ---------- Clean Up ----------
    override fun onDestroy() {
        super.onDestroy()
        try {
            agoraEngine?.leaveChannel()
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e("AGORA", "Error during destroy: ${e.message}", e)
        } finally {
            agoraEngine = null
        }
    }
}
