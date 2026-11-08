package com.focusguard.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * The app's accent colour. Blue when idle, red while focus mode is on, and it fades smoothly
 * between the two. Views opt in with a tag:
 *   accent_text  - text colour          accent_tint - icon / progress tint
 *   accent_bg    - background tint      accent_dark - background tint, darker shade
 *   aurora       - the glowing screen background
 *   accent_custom- custom views that read [Accent.color] in onDraw
 * Switches and radio buttons are tinted automatically.
 */
object Accent {

    const val BLUE = 0xFF2F8CFF.toInt()
    const val RED = 0xFFFF4D5E.toInt()
    private const val BASE = 0xFF0D0D0D.toInt()

    private val evaluator = ArgbEvaluator()

    var color: Int = BLUE
        private set
    var dark: Int = mix(BLUE, BASE, 0.78f)
        private set

    private var focusOn = false
    private var animator: ValueAnimator? = null
    private val listeners = LinkedHashSet<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun mix(a: Int, b: Int, t: Float): Int = evaluator.evaluate(t.coerceIn(0f, 1f), a, b) as Int

    private fun set(c: Int) {
        color = c
        dark = mix(c, BASE, 0.78f)
        for (l in ArrayList(listeners)) l()
    }

    /** Fades to red when [on] is true, back to blue otherwise. Does nothing if already there. */
    fun setFocus(on: Boolean, animate: Boolean = true) {
        if (on == focusOn && animator == null) {
            // already settled on this colour
            if (color == (if (on) RED else BLUE)) return
        }
        focusOn = on
        animator?.cancel()
        animator = null
        val to = if (on) RED else BLUE
        if (!animate) {
            set(to)
            return
        }
        val from = color
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1100L
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { set(mix(from, to, it.animatedValue as Float)) }
        a.start()
        animator = a
    }

    /** Colours every tagged view under [root] with the current accent. */
    fun apply(root: View) {
        val c = color
        val d = dark
        walk(root, c, d)
    }

    private fun walk(v: View, c: Int, d: Int) {
        when (v.tag as? String) {
            "accent_text" -> if (v is TextView) v.setTextColor(c)
            "accent_tint" -> when (v) {
                is ImageView -> v.imageTintList = ColorStateList.valueOf(c)
                is ProgressBar -> v.progressTintList = ColorStateList.valueOf(c)
                is TextView -> v.compoundDrawableTintList = ColorStateList.valueOf(c)
            }
            "accent_bg" -> v.backgroundTintList = ColorStateList.valueOf(c)
            "accent_dark" -> v.backgroundTintList = ColorStateList.valueOf(d)
            "aurora" -> {
                if (v.background !is AuroraDrawable) v.background = AuroraDrawable()
                v.background.invalidateSelf()
            }
            "accent_custom" -> v.invalidate()
        }
        if (v is MaterialSwitch) {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            v.trackTintList = ColorStateList(states, intArrayOf(c, 0xFF2E2E2E.toInt()))
            v.thumbTintList = ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), 0xFF9A9A9A.toInt()))
        } else if (v is RadioButton) {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            v.buttonTintList = ColorStateList(states, intArrayOf(c, 0xFF9A9A9A.toInt()))
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), c, d)
        }
    }
}

/** Dark screen background with a soft glow of the current accent colour. */
class AuroraDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val c = Accent.color
        val top = Accent.mix(c, 0xFF04070C.toInt(), 0.88f)
        val mid = Accent.mix(c, 0xFF0D0D0D.toInt(), 0.74f)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h, intArrayOf(top, mid, 0xFF0D0D0D.toInt()), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, paint)
        val glow = Color.argb(0x66, Color.red(c), Color.green(c), Color.blue(c))
        val none = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))
        paint.shader = RadialGradient(
            w / 2f, h * 0.62f, maxOf(w, h) * 0.75f, glow, none, Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
