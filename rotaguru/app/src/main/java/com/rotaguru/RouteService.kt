package com.rotaguru

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RouteService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var overlay: Overlay
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private var lastClick = 0L

    override fun onServiceConnected() {
        prefs = Prefs(this)
        overlay = Overlay(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.packageName == packageName) return
        if (scheduled) return
        scheduled = true
        handler.postDelayed({ scheduled = false; scan() }, 250)
    }

    private fun scan() {
        val root = rootInActiveWindow
        if (root == null || root.packageName == packageName) {
            return
        }
        val offers = Parser.parse(root)
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
        overlay.remove()
    }

    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.remove()
        super.onDestroy()
    }
}
