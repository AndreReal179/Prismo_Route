package com.rotaguru

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barra flutuante. Usa TYPE_ACCESSIBILITY_OVERLAY, então não precisa da
 * permissão "Exibir sobre outros apps".
 *   toque curto  -> expande / recolhe (recolhido mostra só a melhor)
 *   toque longo  -> abre as configurações
 */
class Overlay(private val svc: AccessibilityService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bar: LinearLayout? = null
    private var expanded = true
    private var last: List<Result> = emptyList()
    private val hideRunnable = Runnable { remove() }

    private fun dp(v: Int) = (v * svc.resources.displayMetrics.density).toInt()

    fun show(results: List<Result>) {
        handler.removeCallbacks(hideRunnable)
        last = results
        render()
    }

    fun hideSoon() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 1200)
    }

    /** Barra cinza de diagnóstico: mostra o que o serviço está lendo. */
    fun showDebug(msg: String) {
        handler.removeCallbacks(hideRunnable)
        last = emptyList()
        val b = ensureBar()
        b.removeAllViews()
        b.addView(TextView(svc).apply {
            text = msg
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(0xE6333333.toInt())
                cornerRadius = dp(12).toFloat()
            }
        })
        handler.postDelayed(hideRunnable, 6000)
    }

    fun remove() {
        bar?.let { runCatching { wm.removeView(it) } }
        bar = null
    }

    private fun ensureBar(): LinearLayout {
        bar?.let { return it }
        val b = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(2))
            setOnClickListener { expanded = !expanded; render() }
            setOnLongClickListener {
                svc.startActivity(
                    Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(78)
        }
        wm.addView(b, lp)
        bar = b
        return b
    }

    private fun render() {
        if (last.isEmpty()) return
        val b = ensureBar()
        b.removeAllViews()
        val best = Evaluator.best(last)
        val rows = if (expanded) last else listOfNotNull(best ?: last.first())
        for (r in rows) b.addView(row(r, r === best && last.size > 1))
    }

    private fun row(r: Result, isBest: Boolean): View {
        val color = r.level?.color ?: 0xFF555555.toInt()
        val head = Evaluator.headline(r) + if (isBest) "   ★ MELHOR" else ""
        val det = Evaluator.detail(r)
        val sb = SpannableStringBuilder(head)
        if (det.isNotEmpty()) {
            val start = sb.length
            sb.append("\n").append(det)
            sb.setSpan(RelativeSizeSpan(0.78f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(svc).apply {
            text = sb
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(14).toFloat()
                if (isBest) setStroke(dp(3), 0xFFFFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
    }
}
