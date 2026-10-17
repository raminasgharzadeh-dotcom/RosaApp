package com.roza.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val history = ArrayList<Pair<String, String>>()
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var listening = false
    private var rosaOn = true
    private var awake = false
    private var failures = 0
    private var updatingSwitch = false
    private var language = "fa-IR"
    private var recognizer: SpeechRecognizer? = null

    // The wake phrase: "سلام رزا" in Persian, or "hello rosa" in English.
    private val wakeRegex = Regex(
        "(سلام\\s*(رزا|روزا|رضا|رزه)|(hello|hey|hi)\\s*(rosa|roza|rosie))",
        RegexOption.IGNORE_CASE
    )

    private val sleepRunnable = Runnable {
        awake = false
        if (rosaOn && !busy) status.text = getString(R.string.wait_wake)
    }

    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button
    private lateinit var langButton: Button
    private lateinit var powerSwitch: SwitchCompat
    private lateinit var status: TextView

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (rosaOn) scheduleListen(0) else startListening()
            } else {
                if (rosaOn) setRosaOn(false)
                status.text = getString(R.string.err_mic_permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val avatar = findViewById<ImageView>(R.id.avatar)
        val background = findViewById<ImageView>(R.id.background)
        val ring = findViewById<View>(R.id.ring)
        messages = findViewById<LinearLayout>(R.id.messages)
        scroll = findViewById<ScrollView>(R.id.scroll)
        input = findViewById<EditText>(R.id.input)
        sendButton = findViewById<Button>(R.id.sendButton)
        micButton = findViewById<Button>(R.id.micButton)
        langButton = findViewById<Button>(R.id.langButton)
        powerSwitch = findViewById<SwitchCompat>(R.id.powerSwitch)
        status = findViewById<TextView>(R.id.status)

        makeRound(avatar)
        loadImagesFromAssets(avatar, background)
        startBreathing(avatar, ring)

        rosaOn = prefs().getBoolean("on", true)
        showSwitch(rosaOn)

        sendButton.setOnClickListener { onSend() }
        micButton.setOnClickListener { onMicClick() }
        powerSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingSwitch) setRosaOn(checked)
        }
        langButton.setOnClickListener {
            language = if (language == "fa-IR") "en-US" else "fa-IR"
            langButton.text = getString(if (language == "fa-IR") R.string.lang_fa else R.string.lang_en)
            if (listening) {
                stopRecognizer()
                if (rosaOn) scheduleListen(200)
            }
        }
    }

    // The microphone is only used while the app is on screen.
    override fun onResume() {
        super.onResume()
        if (rosaOn) {
            ensureMicThenListen()
        } else {
            status.text = getString(R.string.off_status)
        }
    }

    // When the app leaves the screen, shut the recognizer down completely.
    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        awake = false
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        super.onDestroy()
    }

    // ---------- on / off ----------

    private fun showSwitch(on: Boolean) {
        updatingSwitch = true
        powerSwitch.isChecked = on
        updatingSwitch = false
    }

    private fun setRosaOn(on: Boolean) {
        rosaOn = on
        prefs().edit().putBoolean("on", on).apply()
        awake = false
        failures = 0
        handler.removeCallbacksAndMessages(null)
        showSwitch(on)
        if (on) {
            ensureMicThenListen()
        } else {
            stopRecognizer()
            status.text = getString(R.string.off_status)
        }
    }

    // ---------- voice input ----------

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun ensureMicThenListen() {
        if (hasMicPermission()) {
            scheduleListen(0)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun scheduleListen(delay: Long) {
        handler.postDelayed({
            if (rosaOn && !busy && !listening) startListening()
        }, delay)
    }

    private fun onMicClick() {
        if (busy) return
        if (rosaOn) {
            // Rosa is already listening: treat the next sentence as a command.
            awake = true
            status.text = getString(R.string.say_command)
            handler.removeCallbacks(sleepRunnable)
            handler.postDelayed(sleepRunnable, 15000)
            if (!listening) scheduleListen(0)
            return
        }
        if (listening) {
            recognizer?.stopListening()
            return
        }
        if (hasMicPermission()) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun setListening(value: Boolean) {
        listening = value
        micButton.text = if (value) "⏹" else "🎤"
    }

    private fun stopRecognizer() {
        recognizer?.cancel()
        setListening(false)
    }

    private fun releaseRecognizer() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        setListening(false)
    }

    private fun firstResult(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim() ?: ""
    }

    private fun normalize(text: String): String {
        return text
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("[\\u064B-\\u065F]"), "")
            .trim()
    }

    private fun startListening() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = getString(R.string.err_no_recognizer)
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = engine

        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (rosaOn) return
                val text = firstResult(partialResults)
                if (text.isNotEmpty()) {
                    input.setText(text)
                    input.setSelection(input.text.length)
                }
            }

            override fun onResults(results: Bundle?) {
                setListening(false)
                failures = 0
                val text = firstResult(results)
                if (rosaOn) {
                    handleLoopResult(text)
                } else if (text.isEmpty()) {
                    status.text = getString(R.string.err_heard_nothing)
                } else {
                    status.text = ""
                    input.setText(text)
                    onSend()
                }
            }

            override fun onError(error: Int) {
                setListening(false)
                recognizer?.destroy()
                recognizer = null
                if (rosaOn) {
                    handleLoopError(error)
                } else {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.err_heard_nothing)
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER -> getString(R.string.err_speech_network)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.err_mic_permission)
                        else -> getString(R.string.err_speech_generic)
                    }
                    status.text = message + " (" + error + ")"
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Ask the recognizer to wait longer before deciding a sentence is finished.
        // Google's recognizer may ignore these values.
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            2000L
        )

        setListening(true)
        status.text = getString(
            if (!rosaOn) R.string.listening
            else if (awake) R.string.say_command
            else R.string.wait_wake
        )
        engine.startListening(intent)
    }

    // What to do with a sentence heard while Rosa is switched on.
    private fun handleLoopResult(text: String) {
        if (busy) return
        if (text.isEmpty()) {
            scheduleListen(300)
            return
        }
        val normalized = normalize(text)
        val match = wakeRegex.find(normalized)
        if (match != null) {
            val rest = normalized.substring(match.range.last + 1)
                .trim()
                .trimStart('،', ',', '.', ' ')
            if (rest.length > 2) {
                awake = false
                sendText(rest)
            } else {
                awake = true
                addBubble(getString(R.string.wake_reply), false)
                status.text = getString(R.string.say_command)
                handler.removeCallbacks(sleepRunnable)
                handler.postDelayed(sleepRunnable, 15000)
                scheduleListen(200)
            }
        } else if (awake) {
            awake = false
            handler.removeCallbacks(sleepRunnable)
            sendText(text)
        } else {
            // Not for Rosa: ignore it and keep waiting for the wake phrase.
            scheduleListen(200)
        }
    }

    private fun handleLoopError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleListen(300)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleListen(1500)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                setRosaOn(false)
                status.text = getString(R.string.err_mic_permission)
            }
            else -> {
                failures++
                if (failures >= 5) {
                    setRosaOn(false)
                    status.text = getString(R.string.err_speech_giveup) + " (" + error + ")"
                } else {
                    status.text = getString(R.string.err_speech_generic) + " (" + error + ")"
                    scheduleListen(2500)
                }
            }
        }
    }

    private fun sendText(text: String) {
        input.setText(text)
        onSend()
    }

    // ---------- chat ----------

    private fun prefs(): SharedPreferences =
        getSharedPreferences("rosa", Context.MODE_PRIVATE)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun cachedModels(): List<String> {
        val raw = prefs().getString("models", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun saveModels(models: List<String>) {
        prefs().edit().putString("models", models.joinToString("\n")).apply()
    }

    private fun clearModels() {
        prefs().edit().remove("models").apply()
    }

    private fun askForKey(onSaved: () -> Unit) {
        val field = EditText(this)
        field.hint = "sk-or-..."
        field.setSingleLine(true)
        field.setPadding(dp(20), dp(12), dp(20), dp(12))
        AlertDialog.Builder(this)
            .setTitle(R.string.key_title)
            .setMessage(R.string.key_message)
            .setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val key = field.text.toString().trim()
                if (key.isNotEmpty()) {
                    prefs().edit().putString("key", key).apply()
                    onSaved()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                if (rosaOn) scheduleListen(500)
            }
            .show()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        sendButton.isEnabled = !value
        micButton.isEnabled = !value
        status.text = if (value) getString(R.string.thinking) else ""
    }

    private fun addBubble(text: String, fromUser: Boolean) {
        val bubble = TextView(this)
        bubble.text = text
        bubble.textSize = 16f
        bubble.setTextColor(Color.WHITE)
        bubble.setBackgroundResource(if (fromUser) R.drawable.bubble_user else R.drawable.bubble_rosa)
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10))
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, dp(6), 0, dp(6))
        params.gravity = if (fromUser) Gravity.START else Gravity.END
        messages.addView(bubble, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onSend() {
        if (busy) return
        val question = input.text.toString().trim()
        if (question.isEmpty()) return

        val key = prefs().getString("key", "") ?: ""
        if (key.isEmpty()) {
            askForKey { onSend() }
            return
        }

        // In English mode, ask the model to answer in English.
        val modelQuestion =
            if (language == "en-US") question + "\n(Please answer in English.)" else question

        if (listening) {
            recognizer?.cancel()
            setListening(false)
        }
        awake = false
        input.setText("")
        addBubble(question, true)
        setBusy(true)

        Thread {
            var wrongKey = false
            var ok = false
            val answer: String = try {
                val client = OpenRouterClient(key)
                var models = cachedModels()
                if (models.isEmpty()) {
                    models = client.listFreeModels()
                    saveModels(models)
                }
                val reply = client.chat(models, history, modelQuestion)
                ok = true
                reply
            } catch (e: ApiException) {
                clearModels()
                when {
                    e.status == 401 -> {
                        wrongKey = true
                        getString(R.string.err_key)
                    }
                    e.status == 429 -> getString(R.string.err_limit)
                    else -> getString(R.string.err_generic)
                }
            } catch (e: Exception) {
                getString(R.string.err_network)
            }

            runOnUiThread {
                if (wrongKey) {
                    prefs().edit().remove("key").apply()
                }
                if (ok) {
                    history.add(Pair("user", question))
                    history.add(Pair("assistant", answer))
                }
                addBubble(answer, false)
                setBusy(false)
                if (rosaOn) scheduleListen(400)
            }
        }.start()
    }

    // ---------- look ----------

    // Makes the avatar image a circle.
    private fun makeRound(view: View) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setOval(0, 0, v.width, v.height)
            }
        }
        view.clipToOutline = true
    }

    // Finds the images in the assets folder. A portrait image becomes the
    // background, a landscape or square image becomes the avatar.
    private fun loadImagesFromAssets(avatar: ImageView, background: ImageView) {
        val names: Array<String> = try {
            assets.list("") ?: emptyArray<String>()
        } catch (e: Exception) {
            emptyArray<String>()
        }
        for (name in names) {
            val lower = name.lowercase()
            val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp")
            if (!isImage) continue

            val size = readSize(name) ?: continue
            val isPortrait = size.second > size.first
            val bitmap = decodeScaled(name, size, if (isPortrait) 1400 else 700) ?: continue
            if (isPortrait) {
                background.setImageBitmap(bitmap)
            } else {
                avatar.setImageBitmap(bitmap)
            }
        }
    }

    private fun readSize(name: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        return try {
            assets.open(name).use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeScaled(name: String, size: Pair<Int, Int>, maxSide: Int): Bitmap? {
        var sample = 1
        while (maxOf(size.first, size.second) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        return try {
            assets.open(name).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
    }

    // A slow breathing animation for the avatar and its glowing ring.
    private fun startBreathing(avatar: View, ring: View) {
        val animators = listOf(
            ObjectAnimator.ofFloat(avatar, View.SCALE_X, 1f, 1.04f),
            ObjectAnimator.ofFloat(avatar, View.SCALE_Y, 1f, 1.04f),
            ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.1f),
            ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.1f),
            ObjectAnimator.ofFloat(ring, View.ALPHA, 0.9f, 0.3f)
        )
        for (animator in animators) {
            animator.duration = 2200
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.REVERSE
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.start()
        }
    }
}
