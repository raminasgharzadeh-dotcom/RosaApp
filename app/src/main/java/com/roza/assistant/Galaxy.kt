package com.roza.assistant

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

// The background picture, slowly zooming and drifting so the galaxy seems alive.
class DriftingImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val animators = ArrayList<Animator>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) startDrift(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (width > 0 && height > 0 && animators.isEmpty()) startDrift(width, height)
    }

    override fun onDetachedFromWindow() {
        stopDrift()
        super.onDetachedFromWindow()
    }

    private fun startDrift(w: Int, h: Int) {
        stopDrift()
        // Respect the phone's "remove animations" setting.
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return

        // The picture is always at least 18 percent bigger than the screen, and it moves
        // by less than that, so the edges of the picture never show.
        scaleX = 1.18f
        scaleY = 1.18f
        setLayerType(LAYER_TYPE_HARDWARE, null)

        add(ObjectAnimator.ofFloat(this, View.SCALE_X, 1.18f, 1.32f), 45000)
        add(ObjectAnimator.ofFloat(this, View.SCALE_Y, 1.18f, 1.32f), 45000)
        add(ObjectAnimator.ofFloat(this, View.TRANSLATION_X, -w * 0.04f, w * 0.04f), 38000)
        add(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, h * 0.03f, -h * 0.03f), 52000)
    }

    private fun add(animator: ObjectAnimator, time: Long) {
        animator.duration = time
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animators.add(animator)
        animator.start()
    }

    private fun stopDrift() {
        for (animator in animators) animator.cancel()
        animators.clear()
    }
}

// A field of small twinkling stars drawn over the background.
class StarField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val count = 80
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFD9D5FF.toInt(), 0xFFBFD4FF.toInt())

    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val radius = FloatArray(count)
    private val brightness = FloatArray(count)
    private val speed = IntArray(count)
    private val offset = FloatArray(count)
    private val colorOf = IntArray(count)

    private var phase = 0f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val random = Random(7)
        val density = resources.displayMetrics.density
        for (i in 0 until count) {
            x[i] = random.nextFloat() * w
            y[i] = random.nextFloat() * h
            radius[i] = (0.7f + random.nextFloat() * 1.6f) * density
            brightness[i] = 0.5f + random.nextFloat() * 0.5f
            // Whole-number speeds make the twinkle loop without a visible jump.
            speed[i] = 1 + random.nextInt(3)
            offset[i] = random.nextFloat() * (2 * PI).toFloat()
            colorOf[i] = colors[random.nextInt(colors.size)]
        }
        start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (width > 0 && height > 0) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        stop()
        // Respect the phone's "remove animations" setting: the stars stay still.
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return
        val loop = ValueAnimator.ofFloat(0f, (2 * PI).toFloat())
        loop.duration = 9000
        loop.repeatCount = ValueAnimator.INFINITE
        loop.interpolator = LinearInterpolator()
        loop.addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
        animator = loop
        loop.start()
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 0 until count) {
            val twinkle = 0.5f + 0.5f * sin(phase * speed[i] + offset[i])
            val alpha = brightness[i] * (0.25f + 0.75f * twinkle)
            paint.color = colorOf[i]
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(x[i], y[i], radius[i], paint)
        }
    }
}
