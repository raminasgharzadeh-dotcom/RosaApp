package com.roza.assistant

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val history = ArrayList<Pair<String, String>>()
    private var busy = false

    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var status: TextView

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
        status = findViewById<TextView>(R.id.status)

        makeRound(avatar)
        loadImagesFromAssets(avatar, background)
        startBreathing(avatar, ring)

        sendButton.setOnClickListener { onSend() }
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
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        sendButton.isEnabled = !value
        status.visibility = if (value) View.VISIBLE else View.GONE
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
                val reply = client.chat(models, history, question)
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
