package com.example.easetalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.widget.Chronometer
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.OvershootInterpolator

class VoiceCallActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEndCall: MaterialButton
    private lateinit var btnSpeakerToggle: MaterialButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var callTimer: Chronometer

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var sttIntent: Intent

    private val ui = Handler(Looper.getMainLooper())

    private enum class State { IDLE, LISTENING, THINKING, SPEAKING }
    private var state = State.IDLE

    private var botGender = "Female" // from Firestore (Male/Female)
    private var currentSttLangTag: String? = null

    // Audio
    private lateinit var audioManager: AudioManager
    private var isSpeakerOn = false

    // Cache chosen voice per locale+gender so we don't rescan every time
    private val voiceCache = mutableMapOf<String, Voice?>()

    // Agora placeholders
    private fun agoraJoinChannel() {}
    private fun agoraLeaveChannel() {}
    private fun agoraMuteMic(mute: Boolean) {}

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkAiConnection()
        else Toast.makeText(this, "Mic permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        // Views
        toolbar = findViewById(R.id.topToolbar)
        callTimer = findViewById(R.id.callTimer)
        tvStatus = findViewById(R.id.tvVoiceStatus)
        progressBar = findViewById(R.id.progressConnecting)
        btnEndCall = findViewById(R.id.btnEndCall)
        btnSpeakerToggle = findViewById(R.id.btnSpeakerToggle)

        // Toolbar setup
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { endCall() }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        tvStatus.text = "Preparing AI voice call..."
        progressBar.visibility = android.view.View.VISIBLE
        btnEndCall.visibility = android.view.View.GONE

        setupTts()
        setupStt()
        setupSpeakerToggleUI(initialSpeakerOn = false)

        btnEndCall.setOnClickListener { endCall() }
        btnSpeakerToggle.setOnClickListener { toggleSpeaker() }

        fetchUserSettings()
    }

    // ---------- TTS ----------
    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                tts?.language = Locale("en", "IN")
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                logAvailableVoices() // optional
                Log.d("TTS", "Ready")
            } else {
                Log.e("TTS", "Init failed")
            }
        }
    }

    // Optional: log voices for debugging
    private fun logAvailableVoices() {
        val vs = tts?.voices ?: return
        Log.d("TTS", "Available voices (${vs.size}):")
        vs.forEach { v ->
            Log.d("TTS", "- ${v.name} | ${v.locale} | quality=${v.quality} | latency=${v.latency} | features=${v.features}")
        }
    }

    // ---------- STT ----------
    private fun setupStt() {
        sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // ---------- Speaker UI ----------
    private fun setupSpeakerToggleUI(initialSpeakerOn: Boolean) {
        isSpeakerOn = initialSpeakerOn

        // Default to earpiece when call starts
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false

        refreshSpeakerButton()
    }

    private fun refreshSpeakerButton() {
        val green = 0xFF2ECC71.toInt() // ON
        val gray = 0xFF9E9E9E.toInt()  // OFF
        val bgColor = if (isSpeakerOn) green else gray
        btnSpeakerToggle.backgroundTintList = ColorStateList.valueOf(bgColor)

        if (isSpeakerOn) {
            btnSpeakerToggle.text = "Speaker OFF"
            btnSpeakerToggle.icon = ContextCompat.getDrawable(this, R.drawable.ic_speaker_on_24)
        } else {
            btnSpeakerToggle.text = "Speaker ON"
            btnSpeakerToggle.icon = ContextCompat.getDrawable(this, R.drawable.ic_speaker_off_24)
        }
    }

    private fun animateSpeakerButtonPulse() {
        val scaleUpX = ObjectAnimator.ofFloat(btnSpeakerToggle, "scaleX", 1f, 1.08f)
        val scaleUpY = ObjectAnimator.ofFloat(btnSpeakerToggle, "scaleY", 1f, 1.08f)
        val scaleDownX = ObjectAnimator.ofFloat(btnSpeakerToggle, "scaleX", 1.08f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(btnSpeakerToggle, "scaleY", 1.08f, 1f)

        scaleUpX.duration = 120
        scaleUpY.duration = 120
        scaleDownX.duration = 160
        scaleDownY.duration = 160

        val set = AnimatorSet()
        set.play(scaleUpX).with(scaleUpY)
        set.play(scaleDownX).with(scaleDownY).after(scaleUpX)
        set.interpolator = OvershootInterpolator(1.6f)
        set.start()
    }

    private fun animateSpeakerIconFlip() {
        val rotateOut = ObjectAnimator.ofFloat(btnSpeakerToggle, "rotation", 0f, if (isSpeakerOn) 8f else -8f)
        rotateOut.duration = 120
        val rotateBack = ObjectAnimator.ofFloat(btnSpeakerToggle, "rotation", if (isSpeakerOn) 8f else -8f, 0f)
        rotateBack.duration = 160

        val set = AnimatorSet()
        set.playSequentially(rotateOut, rotateBack)
        set.start()
    }

    // Smooth cubic fade for STREAM_MUSIC volume (no UI freeze)
    private fun fadeTtsVolume(
        targetVolume: Float,
        durationMs: Long = 400,
        startDelayMs: Long = 0
    ) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetIndex = (targetVolume * maxVolume).toInt().coerceIn(0, maxVolume)
        val steps = 25
        val stepDelay = durationMs / steps
        val delta = (targetIndex - startVolume).toFloat() / steps

        Thread {
            Thread.sleep(startDelayMs)
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val eased = t * t * (3 - 2 * t) // smoothstep easing
                val newVolume = (startVolume + delta * (eased * steps)).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                Thread.sleep(stepDelay)
            }
        }.start()
    }

    private fun toggleSpeaker() {
        // Fade out, then switch route via UI Handler (no blocking)
        fadeTtsVolume(0.2f, 350)
        btnSpeakerToggle.isEnabled = false

        ui.postDelayed({
            isSpeakerOn = !isSpeakerOn

            if (isSpeakerOn) {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                Log.d("AUDIO_ROUTE", "🔊 Switched to Loudspeaker")
                Toast.makeText(this, "Loudspeaker Enabled", Toast.LENGTH_SHORT).show()
            } else {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                Log.d("AUDIO_ROUTE", "🔈 Switched to Earpiece")
                Toast.makeText(this, "Switched to Earpiece", Toast.LENGTH_SHORT).show()
            }

            // Update visuals + animate
            refreshSpeakerButton()
            animateSpeakerButtonPulse()
            animateSpeakerIconFlip()

            // Fade back in
            fadeTtsVolume(1.0f, 450, 50)
            btnSpeakerToggle.isEnabled = true
        }, 370) // slightly more than fade out
    }

    // ---------- Pull user pref (bot gender) ----------
    private fun fetchUserSettings() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    botGender = doc.getString("botGender") ?: "Female"
                    tvStatus.text = "🎙️ Connecting with AI..."
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        checkAiConnection()
                    }
                }
                .addOnFailureListener { e ->
                    tvStatus.text = "Error: ${e.message}"
                    progressBar.visibility = android.view.View.GONE
                }
        } else {
            tvStatus.text = "User not logged in"
            progressBar.visibility = android.view.View.GONE
        }
    }

    // ---------- Ensure backend/Ollama reachable ----------
    private fun checkAiConnection() {
        AiServerChecker.checkConnection(this) { connected ->
            if (connected) startAgent() else {
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "🔴 AI server unreachable. Please start Ollama."
            }
        }
    }

    // ---------- Start loop ----------
    private fun startAgent() {
        agoraJoinChannel()
        btnEndCall.visibility = android.view.View.VISIBLE
        progressBar.visibility = android.view.View.GONE
        state = State.LISTENING
        tvStatus.text = "🎤 You can start talking..."

        // Start the call timer
        callTimer.base = SystemClock.elapsedRealtime()
        callTimer.start()

        startListening()
    }

    private fun startListening() {
        if (state != State.LISTENING) return
        stopListening()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Log.w("STT", "Error: $error")
                    if (state == State.LISTENING) ui.postDelayed({ startListening() }, 300)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onUserUtterance(text)
                    else if (state == State.LISTENING) ui.postDelayed({ startListening() }, 300)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            if (currentSttLangTag != null) {
                sttIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSttLangTag)
            }
            startListening(sttIntent)
        }
    }

    private fun stopListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun updateSttLanguage(langCode: String) {
        val tag = when (langCode) {
            "hi" -> "hi-IN"
            "kn" -> "kn-IN"
            "ta" -> "ta-IN"
            "te" -> "te-IN"
            "ml" -> "ml-IN"
            "mr" -> "mr-IN"
            "bn" -> "bn-IN"
            "ar" -> "ar-SA"
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            "de" -> "de-DE"
            "ja" -> "ja-JP"
            else -> "en-IN"
        }
        currentSttLangTag = tag
        sttIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
    }

    private fun onUserUtterance(text: String) {
        if (state != State.LISTENING) return
        state = State.THINKING
        tvStatus.text = "You: $text"

        LanguageIdentification.getClient().identifyLanguage(text)
            .addOnSuccessListener { code ->
                val lang = if (code == "und") "en" else code
                updateSttLanguage(lang)
                sendToBackend(text, lang)
            }
            .addOnFailureListener {
                sendToBackend(text, "en")
            }
    }

    // ---------- Backend call ----------
    private fun sendToBackend(text: String, lang: String) {
        state = State.THINKING
        tvStatus.text = "🤖 Thinking..."
        Log.d("AI_CALL", "→ text='$text' lang=$lang")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reply = LlmClient.generateReply(text, lang)
                val cleaned = reply.replace(Regex("\\s+"), " ")
                    .replace("“", "\"").replace("”", "\"")
                    .trim()
                Log.d("AI_RESPONSE_RAW", "← '$cleaned'")
                withContext(Dispatchers.Main) {
                    respond(if (cleaned.isNotEmpty()) cleaned else "Sorry, I couldn’t process that.", lang)
                }
            } catch (e: Exception) {
                Log.e("AI", "generateReply failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    respond("Sorry, I didn’t catch that. Please try again.", lang)
                }
            }
        }
    }

    // ---------- Speak + loop ----------
    private fun respond(reply: String, lang: String) {
        state = State.SPEAKING
        agoraMuteMic(true)
        stopListening()
        speakAI(reply, lang) {
            agoraMuteMic(false)
            if (state != State.IDLE) {
                state = State.LISTENING
                startListening()
            }
        }
    }

    private fun speakAI(text: String, lang: String, onDone: () -> Unit) {
        if (!isTtsReady) {
            Log.w("TTS", "Not ready; skipping speech")
            onDone()
            return
        }

        val loc = toLocale(lang)
        val avail = tts?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
        tts?.language = if (avail >= TextToSpeech.LANG_AVAILABLE) loc else Locale("en", "IN")

        // Ensure correct audio route before speaking
        if (isSpeakerOn) {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            Log.d("AUDIO_ROUTE", "🔊 Routing TTS to loudspeaker")
        } else {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
            Log.d("AUDIO_ROUTE", "🔈 Routing TTS to earpiece")
        }

        // Choose a voice per gender
        val gender = botGender // "Male" / "Female"
        val cacheKey = "${loc.language}-${loc.country}-${gender.lowercase(Locale.ROOT)}"
        val chosen = voiceCache.getOrPut(cacheKey) { pickVoiceForGender(loc, gender) }

        chosen?.let {
            try {
                tts?.voice = it
                Log.d("TTS", "Using voice: ${it.name} (${it.locale}) for gender=$gender")
            } catch (e: Exception) {
                Log.w("TTS", "Failed to set voice: ${e.message}")
            }
        }

        // Pitch/rate for distinction
        if (gender.equals("male", true)) {
            tts?.setPitch(0.85f)
            tts?.setSpeechRate(0.98f)
        } else {
            tts?.setPitch(1.12f)
            tts?.setSpeechRate(1.05f)
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { Log.d("TTS", "🗣️ Speaking started...") }
            override fun onError(utteranceId: String?) { Log.e("TTS", "❌ Error"); ui.post(onDone) }
            override fun onDone(utteranceId: String?) { Log.d("TTS", "✅ Speech finished"); ui.post(onDone) }
        })

        tvStatus.text = "AI: $text"
        // Gentle fade-in at each utterance start
        fadeTtsVolume(1.0f, 250)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.nanoTime()}")
    }

    /**
     * Pick a voice for the given locale + gender.
     */
    private fun pickVoiceForGender(targetLocale: Locale, gender: String): Voice? {
        val engine = tts ?: return null
        val voices = engine.voices ?: return null
        if (voices.isEmpty()) return null

        val sameLang = voices.filter { it.locale.language == targetLocale.language }
        val exactLocale = sameLang.filter { it.locale.country == targetLocale.country }

        fun qualityScore(v: Voice): Int = when (v.quality) {
            Voice.QUALITY_VERY_HIGH -> 3
            Voice.QUALITY_HIGH -> 2
            Voice.QUALITY_NORMAL -> 1
            else -> 0
        }

        fun matchesGender(v: Voice, g: String): Boolean {
            val name = v.name.lowercase(Locale.ROOT)
            val feat = v.features?.joinToString(",")?.lowercase(Locale.ROOT) ?: ""
            val gLower = g.lowercase(Locale.ROOT)
            return name.contains(gLower) || feat.contains(gLower) ||
                    (gLower == "male" && (name.contains("male") || name.endsWith("-m"))) ||
                    (gLower == "female" && (name.contains("female") || name.endsWith("-f")))
        }

        fun sorted(list: List<Voice>): List<Voice> =
            list.sortedWith(compareByDescending<Voice> { qualityScore(it) }.thenBy { it.name })

        val genderLower = gender.lowercase(Locale.ROOT)

        val exactList = sorted(exactLocale)
        val exactHint = exactList.firstOrNull { matchesGender(it, genderLower) }
        if (exactHint != null) return exactHint

        val langList = sorted(sameLang)
        val langHint = langList.firstOrNull { matchesGender(it, genderLower) }
        if (langHint != null) return langHint

        return if (genderLower == "male") {
            (exactList.ifEmpty { langList }).firstOrNull()
        } else {
            val pool = exactList.ifEmpty { langList }
            when {
                pool.size >= 2 -> pool[1]
                pool.isNotEmpty() -> pool.last()
                else -> null
            }
        }
    }

    private fun toLocale(code: String): Locale = when (code) {
        "hi" -> Locale("hi", "IN")
        "kn" -> Locale("kn", "IN")
        "ta" -> Locale("ta", "IN")
        "te" -> Locale("te", "IN")
        "ml" -> Locale("ml", "IN")
        "mr" -> Locale("mr", "IN")
        "bn" -> Locale("bn", "IN")
        "ar" -> Locale("ar", "SA")
        "fr" -> Locale.FRENCH
        "es" -> Locale("es", "ES")
        "de" -> Locale.GERMAN
        "ja" -> Locale.JAPANESE
        else -> Locale("en", "IN")
    }

    // ---------- End ----------
    private fun endCall() {
        state = State.IDLE
        tvStatus.text = "Ending call..."
        btnEndCall.visibility = android.view.View.GONE
        progressBar.visibility = android.view.View.VISIBLE
        stopListening()
        tts?.stop()
        agoraLeaveChannel()

        // Stop timer
        callTimer.stop()

        // Reset audio route
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        isSpeakerOn = false
        refreshSpeakerButton()

        ui.postDelayed({
            tvStatus.text = "Call ended."
            progressBar.visibility = android.view.View.GONE
            speakAI("Goodbye! See you soon.", "en") {}
            finish()
        }, 800)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        tts?.stop()
        tts?.shutdown()
        callTimer.stop()
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }
}
