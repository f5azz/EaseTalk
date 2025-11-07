package com.example.easetalk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import java.util.*

class VoiceCallActivity : AppCompatActivity(), TextToSpeech.OnUtteranceCompletedListener {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEndCall: Button

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var selectedLocale: Locale = Locale.US
    private var speechLocale: Locale = Locale.US
    private val REQ_CODE_SPEECH = 2000
    private var isListening = false
    private var botLanguage = "English"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        tvStatus = findViewById(R.id.tvVoiceStatus)
        progressBar = findViewById(R.id.progressConnecting)
        btnEndCall = findViewById(R.id.btnEndCall)

        tvStatus.text = "Preparing AI voice call..."
        progressBar.visibility = View.VISIBLE
        btnEndCall.visibility = View.GONE

        // Initialize TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                Log.d("TTS", "TextToSpeech initialized.")
            } else {
                Log.e("TTS", "TTS initialization failed.")
            }
        }

        btnEndCall.setOnClickListener {
            endCall()
        }

        // Start the AI call automatically
        fetchUserSettingsAndStartCall()
    }

    // ---------- Fetch User Settings ----------
    private fun fetchUserSettingsAndStartCall() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val gender = doc.getString("botGender") ?: "Female"
                    botLanguage = doc.getString("botLanguage") ?: "English"

                    selectedLocale = getLocaleForLanguage(botLanguage)
                    speechLocale = getLocaleForLanguage(botLanguage)
                    updateTtsLanguage(selectedLocale)

                    tvStatus.text = "Connecting with AI ($gender, $botLanguage)..."
                    startAICall("voice")
                }
                .addOnFailureListener { e ->
                    tvStatus.text = "Error loading bot settings: ${e.message}"
                    progressBar.visibility = View.GONE
                }
        } else {
            tvStatus.text = "User not logged in"
            progressBar.visibility = View.GONE
        }
    }

    // ---------- Locale Mapping ----------
    private fun getLocaleForLanguage(language: String): Locale {
        return when (language.lowercase(Locale.ROOT)) {
            "hindi" -> Locale("hi", "IN")
            "kannada" -> Locale("kn", "IN")
            "tamil" -> Locale("ta", "IN")
            "malayalam" -> Locale("ml", "IN")
            "telugu" -> Locale("te", "IN")
            "french" -> Locale.FRENCH
            "spanish" -> Locale("es", "ES")
            "arabic" -> Locale("ar", "SA")
            "german" -> Locale.GERMAN
            "japanese" -> Locale.JAPANESE
            else -> Locale.US
        }
    }

    // ---------- Set TTS Language ----------
    private fun updateTtsLanguage(locale: Locale) {
        if (isTtsReady) {
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TTS", "Language ${locale.displayLanguage} not supported, using English.")
                tts?.language = Locale.US
            } else {
                Log.d("TTS", "TTS language set to ${locale.displayLanguage}")
            }
        }
    }

    // ---------- Start AI Call ----------
    private fun startAICall(callType: String) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            val botGender = doc.getString("botGender") ?: "Female"
            botLanguage = doc.getString("botLanguage") ?: "English"

            val data = mapOf(
                "userId" to user.uid,
                "callType" to callType,
                "botGender" to botGender,
                "botLanguage" to botLanguage
            )

            val jsonData = JSONObject(data).toString()
            val queue = Volley.newRequestQueue(this)
            val url = "http://10.207.79.104:3000/start-ai-call"

            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d("AI_CALL", "Started: $response")
                    tvStatus.text = "🎙️ Connected to AI in $botLanguage"
                    progressBar.visibility = View.GONE
                    btnEndCall.visibility = View.VISIBLE
                    speakAI(getGreetingMessage(botLanguage))
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

    // ---------- Greeting ----------
    private fun getGreetingMessage(language: String): String {
        return when (language.lowercase(Locale.ROOT)) {
            "hindi" -> "नमस्ते! मैं आपकी एआई बोलने वाली साथी हूँ। आप कैसे हैं?"
            "kannada" -> "ನಮಸ್ಕಾರ! ನಾನು ನಿಮ್ಮ AI ಮಾತನಾಡುವ ಸಂಗಾತಿ. ನೀವು ಹೇಗಿದ್ದೀರಿ?"
            "tamil" -> "வணக்கம்! நான் உங்கள் AI பேச்சு தோழி. எப்படி இருக்கிறீர்கள்?"
            "malayalam" -> "നമസ്കാരം! ഞാൻ നിങ്ങളുടെ AI സംസാരിക്കുന്ന പങ്കാളിയാണ്. എങ്ങനെയുണ്ട്?"
            "telugu" -> "నమస్తే! నేను మీ AI మాట్లాడే భాగస్వామిని. మీరు ఎలా ఉన్నారు?"
            "french" -> "Bonjour! Je suis ton partenaire de conversation IA. Comment ça va?"
            "spanish" -> "¡Hola! Soy tu compañera de conversación de IA. ¿Cómo estás?"
            "arabic" -> "مرحبًا! أنا شريكتك في التحدث بالذكاء الاصطناعي. كيف حالك؟"
            else -> "Hi there! I'm your AI speaking partner. How are you today?"
        }
    }

    // ---------- Multilingual Speech Recognition ----------
    private fun startSpeechRecognition() {
        if (isListening) return
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "${speechLocale.language}-${speechLocale.country}"
        )
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening in ${speechLocale.displayLanguage}...")
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH)
        } catch (e: Exception) {
            Log.e("SPEECH", "Speech recognition not supported: ${e.message}")
            speakAI("Speech recognition is not supported for this language.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CODE_SPEECH && resultCode == Activity.RESULT_OK) {
            isListening = false
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val userSpeech = result?.get(0).orEmpty()
            Log.d("SPEECH", "You said: $userSpeech")
            tvStatus.text = "You: $userSpeech"
            sendToAI(userSpeech)
        } else {
            isListening = false
            tvStatus.text = "Didn't catch that."
            speakAI(getRetryMessage(botLanguage))
        }
    }

    private fun getRetryMessage(language: String): String {
        return when (language.lowercase(Locale.ROOT)) {
            "hindi" -> "माफ कीजिये, मैंने ठीक से सुना नहीं। क्या आप दोबारा बोल सकते हैं?"
            "kannada" -> "ಕ್ಷಮಿಸಿ, ನಾನು ಸರಿಯಾಗಿ ಕೇಳಲಿಲ್ಲ. ನೀವು ಮತ್ತೆ ಹೇಳುತ್ತೀರಾ?"
            "tamil" -> "மன்னிக்கவும், நான் சரியாக கேட்கவில்லை. தயவு செய்து மீண்டும் சொல்லுங்கள்."
            "malayalam" -> "ക്ഷമിക്കണം, ഞാൻ ശരിയായി കേട്ടില്ല. ദയവായി വീണ്ടും പറയാമോ?"
            else -> "Sorry, I didn't catch that. Could you repeat?"
        }
    }

    // ---------- Send to AI ----------
    private fun sendToAI(text: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            val botGender = doc.getString("botGender") ?: "Female"
            val botLanguage = doc.getString("botLanguage") ?: "English"

            val json = JSONObject().apply {
                put("text", text)
                put("botGender", botGender)
                put("botLanguage", botLanguage)
            }

            val url = "http://10.207.79.104:3000/ai-response"
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    try {
                        val reply = JSONObject(response).getString("reply")
                        Log.d("AI_REPLY", "AI replied: $reply")
                        tvStatus.text = "AI: $reply"
                        speakAI(reply)
                    } catch (e: Exception) {
                        Log.e("AI_REPLY", "Parsing error: ${e.message}")
                    }
                },
                { error ->
                    Log.e("AI_REPLY", "Error: ${error.message}")
                }
            ) {
                override fun getBodyContentType() = "application/json; charset=utf-8"
                override fun getBody() = json.toString().toByteArray(Charsets.UTF_8)
            }

            Volley.newRequestQueue(this).add(request)
        }
    }

    // ---------- AI Speaking ----------
    private fun speakAI(reply: String) {
        if (isTtsReady) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AI_REPLY")
            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, params, "AI_REPLY")

            // Restart listening after AI speaks
            tts?.setOnUtteranceProgressListener(object :
                android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { startSpeechRecognition() }
                }
                override fun onError(utteranceId: String?) {}
            })
        } else {
            Log.e("TTS", "TTS not ready yet")
        }
    }

    // ---------- End Call ----------
    private fun endCall() {
        tvStatus.text = "Ending call..."
        btnEndCall.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        tvStatus.postDelayed({
            tvStatus.text = "Call ended."
            progressBar.visibility = View.GONE
            speakAI(getGoodbyeMessage(botLanguage))
        }, 1000)
    }

    private fun getGoodbyeMessage(language: String): String {
        return when (language.lowercase(Locale.ROOT)) {
            "hindi" -> "अलविदा! फिर मिलते हैं।"
            "kannada" -> "ವಿದಾಯ! ಮತ್ತೆ ಭೇಟಿ ಆಗೋಣ."
            "tamil" -> "பிரியாவிடை! மறுபடியும் சந்திப்போம்."
            "malayalam" -> "വിട! വീണ്ടും കാണാം."
            else -> "Goodbye! See you soon."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TTS", "Error shutting down TTS: ${e.message}")
        }
    }

    override fun onUtteranceCompleted(utteranceId: String?) {}
}
