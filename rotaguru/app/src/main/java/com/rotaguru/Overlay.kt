package com.rotaguru

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Selos de avaliação. Usa TYPE_ACCESSIBILITY_OVERLAY (não precisa de "Exibir sobre outros apps").
 *
 *  Tela de lista   : um selo compacto em cima de CADA card de corrida (camada transparente que
 *                    deixa os toques passarem para o app).
 *  Tela de detalhe : um selo no topo. Toque curto expande/recolhe, toque longo abre as configurações.
 */
class Overlay(private val svc: AccessibilityService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bar: LinearLayout? = null      // modo detalhe
    private var layer: FrameLayout? = null     // modo lista (um selo por card)
    private var expanded = true
    private var last: List<Result> = emptyList()
    private val hideRunnable = Runnable { remove() }

    private val GOLD = 0xFFFFC107.toInt()
    private val INK = 0xF0141416.toInt()
    private val MUTED = 0xFFB4B8C0.toInt()

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

    fun remove() {
        bar?.let { runCatching { wm.removeView(it) } }
        layer?.let { runCatching { wm.removeView(it) } }
        bar = null
        layer = null
    }

    private fun render() {
        if (last.isEmpty()) return
        val best = Evaluator.best(last)
        val listMode = last.all { it.offer.headBottom != null }
        if (listMode) {
            bar?.let { runCatching { wm.removeView(it) }; bar = null }
            renderList(best)
        } else {
            layer?.let { runCatching { wm.removeView(it) }; layer = null }
            renderBar(best)
        }
    }

    // ---------------------------------------------------------------- lista
    private fun ensureLayer(): FrameLayout {
        layer?.let { return it }
        val f = FrameLayout(svc)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(f, lp)
        layer = f
        return f
    }

    private fun renderList(best: Result?) {
        val f = ensureLayer()
        f.removeAllViews()
        val multi = last.size > 1
        for (r in last) {
            val badge = badge(r, r === best && multi, compact = true)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                rightMargin = dp(56)                       // deixa livre o menu "⋮" do card
                topMargin = (r.offer.headBottom ?: 0) + dp(2)
            }
            f.addView(badge, lp)
        }
        // corrige diferença entre coordenadas da tela e da janela (barra de status etc.)
        f.post {
            val loc = IntArray(2)
            f.getLocationOnScreen(loc)
            for (i in 0 until f.childCount) f.getChildAt(i).translationY = -loc[1].toFloat()
        }
    }

    // -------------------------------------------------------------- detalhe
    private fun ensureBar(): LinearLayout {
        bar?.let { return it }
        val b = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(2))
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

    private fun renderBar(best: Result?) {
        val b = ensureBar()
        b.removeAllViews()
        val rows = if (expanded) last else listOfNotNull(best ?: last.first())
        for (r in rows) {
            val v = badge(r, r === best && last.size > 1, compact = false)
            v.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
            b.addView(v)
        }
    }

    // ----------------------------------------------------------------- selo
    /**
     * Selo escuro com contorno na cor do nível:
     *   [ÓTIMO]  R$ 1,32/km  ★
     *   42 km · busca 3,9 · ★4,86
     */
    private fun badge(r: Result, isBest: Boolean, compact: Boolean): View {
        val color = r.level?.color ?: 0xFF6B7280.toInt()
        val pad = if (compact) 8 else 12

        fun tv(t: String, sp: Float, c: Int, bold: Boolean) = TextView(svc).apply {
            text = t
            textSize = sp
            setTextColor(c)
            includeFontPadding = false
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        val pill = tv(r.level?.label ?: "?", if (compact) 11f else 13f, 0xFFFFFFFF.toInt(), true).apply {
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(8).toFloat()
            }
        }
        val gap = View(svc)
        val perKm = tv(Evaluator.perKm(r), if (compact) 15f else 19f, 0xFFFFFFFF.toInt(), true)

        val top = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pill)
            addView(gap, LinearLayout.LayoutParams(dp(8), 1))
            addView(perKm)
            if (isBest) {
                addView(
                    tv("  ★ MELHOR", if (compact) 11f else 13f, GOLD, true)
                )
            }
        }
        val sub = tv(
            if (compact) Evaluator.compact(r) else Evaluator.detail(r),
            if (compact) 11f else 13f, MUTED, false
        )

        return LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(pad), dp(pad - 2), dp(pad), dp(pad - 2))
            background = GradientDrawable().apply {
                setColor(INK)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(if (isBest) 2 else 1) + (if (isBest) 1 else 0), if (isBest) GOLD else color)
            }
            addView(top)
            if (sub.text.isNotEmpty()) {
                addView(sub, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) })
            }
        }
    }
}
