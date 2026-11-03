package com.rotaguru

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Botão flutuante (arrastável). Toque abre um menu com opções rápidas:
 * aceite automático, R$/km mínimo, nota mínima e velocidade média.
 * Usa TYPE_ACCESSIBILITY_OVERLAY (não precisa de "Exibir sobre outros apps").
 */
class Fab(private val svc: AccessibilityService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(svc)
    private val BR = Locale("pt", "BR")
    private val density = svc.resources.displayMetrics.density

    private var button: TextView? = null
    private var buttonLp: WindowManager.LayoutParams? = null
    private var menu: LinearLayout? = null

    private val GOLD = 0xFFFFC107.toInt()
    private val BASE = 0xFF0E1014.toInt()
    private val MUTED = 0xFFA9AEB8.toInt()
    private val GREEN = 0xFF4CAF50.toInt()

    private fun dp(v: Int) = (v * density).toInt()
    private fun screenW() = svc.resources.displayMetrics.widthPixels
    private fun screenH() = svc.resources.displayMetrics.heightPixels

    fun setVisible(v: Boolean) {
        if (v) showButton() else remove()
    }

    fun remove() {
        closeMenu()
        button?.let { runCatching { wm.removeView(it) } }
        button = null
        buttonLp = null
    }

    // ------------------------------------------------------------------ botão
    private fun showButton() {
        if (button != null) return
        val size = dp(46)
        val b = TextView(svc).apply {
            text = "R$"
            textSize = 13f
            setTextColor(GOLD)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE0E1014.toInt())
                setStroke(dp(2), GOLD)
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs.fabX >= 0) prefs.fabX else screenW() - size - dp(6)
            y = if (prefs.fabY >= 0) prefs.fabY else dp(300)
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        b.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = ev.rawX - downX
                    val my = ev.rawY - downY
                    if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true
                    if (moved) {
                        lp.x = (startX + mx).toInt().coerceIn(0, (screenW() - size).coerceAtLeast(0))
                        lp.y = (startY + my).toInt().coerceIn(0, (screenH() - size).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(b, lp) }
                        if (menu != null) positionMenu()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.fabX = lp.x
                        prefs.fabY = lp.y
                    } else toggleMenu()
                    true
                }
                else -> false
            }
        }
        runCatching { wm.addView(b, lp) }.onSuccess {
            button = b
            buttonLp = lp
        }
    }

    // ------------------------------------------------------------------- menu
    private fun toggleMenu() {
        if (menu != null) closeMenu() else openMenu()
    }

    private fun closeMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    private fun menuWidth() = dp(270)

    private fun positionMenu() {
        val m = menu ?: return
        val blp = buttonLp ?: return
        val lp = m.layoutParams as? WindowManager.LayoutParams ?: return
        val w = menuWidth()
        lp.x = if (blp.x + dp(23) > screenW() / 2) blp.x - w - dp(8) else blp.x + dp(54)
        lp.x = lp.x.coerceIn(0, (screenW() - w).coerceAtLeast(0))
        lp.y = blp.y.coerceIn(dp(24), (screenH() - dp(340)).coerceAtLeast(dp(24)))
        runCatching { wm.updateViewLayout(m, lp) }
    }

    private fun label(t: String, sp: Float, c: Int, bold: Boolean = false) = TextView(svc).apply {
        text = t
        textSize = sp
        setTextColor(c)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun pill(t: String, bg: Int, fg: Int) = TextView(svc).apply {
        text = t
        textSize = 13f
        setTextColor(fg)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(10).toFloat() }
    }

    private fun styleSwitch(t: TextView, on: Boolean) {
        t.text = if (on) "LIGADO" else "DESLIGADO"
        t.setTextColor(if (on) 0xFFFFFFFF.toInt() else MUTED)
        (t.background as GradientDrawable).setColor(if (on) GREEN else 0xFF2A2E36.toInt())
    }

    private fun row(vararg v: View): LinearLayout = LinearLayout(svc).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(7), 0, dp(7))
        v.forEach { addView(it) }
    }

    private fun stepper(
        title: String,
        get: () -> Float,
        set: (Float) -> Unit,
        step: Float,
        min: Float,
        max: Float,
        fmt: (Float) -> String
    ): LinearLayout {
        val value = label(fmt(get()), 14f, 0xFFFFFFFF.toInt(), true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(70)
        }
        fun change(delta: Float) {
            val n = (Math.round((get() + delta) * 100f) / 100f).coerceIn(min, max)
            set(n)
            value.text = fmt(n)
        }
        val minus = pill("−", 0xFF2A2E36.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener { change(-step) }
        }
        val plus = pill("+", 0xFF2A2E36.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener { change(step) }
        }
        val name = label(title, 12f, MUTED)
        return row(
            name.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) },
            minus, value, plus
        )
    }

    private fun openMenu() {
        if (menu != null) return
        val box = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(BASE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x55FFC107)
            }
        }

        // título + fechar
        val close = label("✕", 16f, MUTED, true).apply {
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { closeMenu() }
        }
        val title = label("Rota Guru", 15f, GOLD, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        box.addView(row(title, close))

        // aceite automático
        val sw = pill("", 0xFF2A2E36.toInt(), MUTED)
        styleSwitch(sw, prefs.autoAccept)
        sw.setOnClickListener {
            prefs.autoAccept = !prefs.autoAccept
            styleSwitch(sw, prefs.autoAccept)
        }
        val swLabel = label("Aceitar rotas automaticamente", 12f, 0xFFFFFFFF.toInt()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        box.addView(row(swLabel, sw))

        box.addView(stepper(
            "A partir de (R$/km)",
            { prefs.autoMinRpk }, { prefs.autoMinRpk = it },
            0.10f, 0.5f, 6f
        ) { String.format(BR, "R$ %.2f", it) })

        box.addView(stepper(
            "Nota mínima",
            { prefs.minRating }, { prefs.minRating = it },
            0.05f, 3f, 5f
        ) { String.format(BR, "%.2f", it) })

        box.addView(stepper(
            "Velocidade média (tempo ~)",
            { prefs.avgSpeed }, { prefs.avgSpeed = it },
            5f, 15f, 90f
        ) { String.format(BR, "%.0f km/h", it) })

        box.addView(label(
            "Aceita só se o R$/km (busca + viagem) e a nota atingirem o mínimo.",
            10f, MUTED
        ).apply { setPadding(0, dp(4), 0, dp(6)) })

        val more = pill("Mais configurações", 0xFF2A2E36.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                closeMenu()
                runCatching {
                    svc.startActivity(
                        Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        box.addView(more, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val lp = WindowManager.LayoutParams(
            menuWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        runCatching { wm.addView(box, lp) }.onSuccess {
            menu = box
            positionMenu()
        }
    }
}
