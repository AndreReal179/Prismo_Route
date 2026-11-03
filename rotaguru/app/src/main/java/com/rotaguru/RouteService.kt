package com.rotaguru

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class RouteService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var overlay: Overlay
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private var lastClick = 0L
    private var lastEvent = 0L
    private var ticking = false

    /**
     * Batimento: enquanto houve atividade recente (ou o selo está na tela) relê a tela a cada ~900 ms.
     * Resolve o selo que "sumia" quando a lista ficava parada e nenhum evento novo chegava.
     */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (now - lastEvent < 60_000 || overlay.isShowing) scan()
            handler.postDelayed(this, 900)
        }
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        overlay = Overlay(this)
        if (!ticking) {
            ticking = true
            handler.postDelayed(tick, 900)
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.packageName == packageName || !::overlay.isInitialized) return
        lastEvent = SystemClock.uptimeMillis()
        if (scheduled) return
        scheduled = true
        handler.postDelayed({ scheduled = false; scan() }, 200)
    }

    /** Janela ativa; se o sistema não entregar, procura entre as janelas de aplicativo. */
    private fun findRoot(): AccessibilityNodeInfo? {
        val r = rootInActiveWindow
        if (r != null) return if (r.packageName == packageName) null else r
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val rr = w.root ?: continue
            if (rr.packageName != packageName) return rr
        }
        return null
    }

    private fun scan() {
        val root = runCatching { findRoot() }.getOrNull() ?: return
        val offers = runCatching { Parser.parse(root) }.getOrDefault(emptyList())
        if (offers.isEmpty()) {
            overlay.hideSoon()
            return
        }
        val results = offers.map { Evaluator.evaluate(it, prefs) }
        overlay.show(results)
        maybeAutoAccept(results)
    }

    private fun maybeAutoAccept(results: List<Result>) {
        if (!prefs.autoAccept) return
        val now = SystemClock.uptimeMillis()
        if (now - lastClick < 3000) return

        val candidate = results
            .filter { it.level == Level.OTIMO }
            .filter { r -> r.offer.rating?.let { it >= prefs.minRating } ?: true }
            .maxByOrNull { it.effRpk ?: 0.0 } ?: return

        val o = candidate.offer
        val ok = when {
            o.acceptNode != null -> click(o.acceptNode)          // tela de detalhe
            o.cardNode != null -> click(o.cardNode)              // lista: abre o card
            else -> tap(o.tapX, o.tapY)
        }
        if (ok) lastClick = now
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var guard = 0
        while (cur != null && guard++ < 10) {
            if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
        }
        return false
    }

    private fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(g, null, null)
    }

    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.remove()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        ticking = false
        if (::overlay.isInitialized) overlay.remove()
        super.onDestroy()
    }
}
