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
import android.text.style.ForegroundColorSpan
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
    private val prefs = Prefs(svc)
    private val BR = java.util.Locale("pt", "BR")
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
                topMargin = (r.offer.headBottom ?: 0) - dp(4)
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
     *   42 km · busca 3,9 · ★4,86      (busca e nota mudam de cor)
     *   gasolina R$ 22 · líquido R$ 48
     *   Peça R$ 71 ao passageiro        (só quando abaixo do R$/km mínimo)
     */
    private fun badge(r: Result, isBest: Boolean, compact: Boolean): View {
        val color = r.level?.color ?: 0xFF6B7280.toInt()
        val pad = if (compact) 8 else 12
        val small = if (compact) 11f else 13f

        fun tv(t: CharSequence, sp: Float, c: Int, bold: Boolean) = TextView(svc).apply {
            text = t
            textSize = sp
            setTextColor(c)
            includeFontPadding = false
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        val pill = tv(r.level?.label ?: "?", small, 0xFFFFFFFF.toInt(), true).apply {
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(8).toFloat()
            }
        }
        val perKm = tv(Evaluator.perKm(r), if (compact) 15f else 19f, 0xFFFFFFFF.toInt(), true)

        val top = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pill)
            addView(View(svc), LinearLayout.LayoutParams(dp(8), 1))
            addView(perKm)
            if (isBest) addView(tv(if (compact) "  ★" else "  ★ MELHOR", small, GOLD, true))
        }

        // linha 2: km da corrida · busca (colorida) · nota (colorida) · R$/h
        val sb = SpannableStringBuilder()
        fun add(t: String, c: Int) {
            if (sb.isNotEmpty()) sb.append("  ·  ")
            val st = sb.length
            sb.append(t)
            sb.setSpan(ForegroundColorSpan(c), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        r.tripUsed?.let {
            add((if (r.estimated) "~" else "") + String.format(BR, "%.0f km", it), MUTED)
        }
        r.offer.pickupKm?.let {
            add("busca " + String.format(BR, "%.1f km", it), Evaluator.pickupColor(it, prefs))
        }
        r.offer.rating?.let {
            add("★ " + String.format(BR, "%.2f", it), Evaluator.ratingColor(it))
        }
        if (!compact) r.rph?.let { add(String.format(BR, "R$ %.0f/h", it), MUTED) }

        val fuelTxt = Evaluator.fuelLine(r)
        val sugTxt = Evaluator.suggestLine(r)

        return LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(pad), dp(pad - 2), dp(pad), dp(pad - 2))
            background = GradientDrawable().apply {
                setColor(INK)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(if (isBest) 3 else 1), if (isBest) GOLD else color)
            }
            fun line(v: View) = addView(v, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) })

            addView(top)
            if (sb.isNotEmpty()) line(tv(sb, small, MUTED, false))
            if (fuelTxt.isNotEmpty()) line(tv(fuelTxt, small, Evaluator.netColor(r), true))
            if (sugTxt != null) line(tv(sugTxt, small, GOLD, true))
        }
    }
}
