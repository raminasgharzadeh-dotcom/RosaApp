package com.roza.assistant

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val avatar = findViewById<ImageView>(R.id.avatar)
        val background = findViewById<ImageView>(R.id.background)
        val ring = findViewById<View>(R.id.ring)

        makeRound(avatar)
        loadImagesFromAssets(avatar, background)
        startBreathing(avatar, ring)
    }

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
