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

    companion object {
        /** true enquanto o serviço está conectado (a tela inicial usa p/ avisar quando travou) */
        @Volatile var running = false
        /** serviço conectado agora (usado pelo botão Desligar) */
        @Volatile var instance: RouteService? = null
    }

    private lateinit var prefs: Prefs
    private lateinit var overlay: Overlay
    private lateinit var fab: Fab
    private lateinit var store: ReportStore
    private var lastPkg = ""
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
            if (!running) return
            try {
                val now = SystemClock.uptimeMillis()
                if (now - lastEvent < 60_000 || overlay.isShowing) scan()
            } catch (_: Throwable) {
            }
            // botão flutuante: aparece no app de corridas (ou em qualquer app até descobrirmos qual é)
            try {
                // app em primeiro plano agora (não depende só de eventos)
                val ap = rootInActiveWindow?.packageName?.toString().orEmpty()
                if (ap.isNotEmpty() && ap != packageName && !ignoredPkg(ap)) lastPkg = ap
                val rides = prefs.ridePkgs
                fab.setVisible(rides.isEmpty() || lastPkg in rides)
            } catch (_: Throwable) {
            }
            // relatório: grava as ofertas que já saíram da tela
            try { store.flush() } catch (_: Throwable) {}
            handler.postDelayed(this, 900)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        instance = this
        prefs = Prefs(this)
        overlay = Overlay(this)
        fab = Fab(this)
        store = ReportStore(this)
        overlay.onHidden = { fab.update(null) }
        handler.removeCallbacks(tick)
        ticking = false
        if (!ticking) {
            ticking = true
            handler.postDelayed(tick, 900)
        }
        Notif.show(this)    // notificação fixa com o botão Desligar
        Updater.schedule(this)                       // checagem periódica de versão nova
        Updater.checkAsync(this, notify = true)
    }

    /**
     * Desliga o Prismo Route de verdade: tira o botão/painel da tela, remove a notificação e sai da lista de
     * acessibilidade do sistema (disableSelf). Com isso os apps de banco voltam a abrir.
     */
    fun shutdown() {
        if (::store.isInitialized) runCatching { store.flush(true) }   // salva o relatório antes de desligar
        handler.removeCallbacks(tick)
        ticking = false
        if (::overlay.isInitialized) runCatching { overlay.remove() }
        if (::fab.isInitialized) runCatching { fab.remove() }
        running = false
        instance = null
        Notif.cancel(this)
        runCatching { disableSelf() }
    }

    /** Salva o relatório pendente (antes de o app ser atualizado/reiniciado). */
    fun saveState() {
        if (::store.isInitialized) runCatching { store.flush(true) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.packageName == packageName || !::overlay.isInitialized) return
        val pkg = e.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && !ignoredPkg(pkg)) lastPkg = pkg
        lastEvent = SystemClock.uptimeMillis()
        if (scheduled) return
        scheduled = true
        handler.postDelayed({
            scheduled = false
            try { scan() } catch (_: Throwable) {}
        }, 200)
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

    private fun ignoredPkg(p: String) =
        p.contains("systemui") || p.contains("inputmethod") || p.contains("keyboard")

    private fun scan() {
        if (!running) return
        val root = runCatching { findRoot() }.getOrNull()
        if (root == null) {            // app minimizado / sem janela: some com os cards
            overlay.hideSoon()
            return
        }
        val offers = runCatching { Parser.parse(root) }.getOrDefault(emptyList())
        if (offers.isEmpty()) {
            overlay.hideSoon()
            return
        }
        root.packageName?.toString()?.let { prefs.addRidePkg(it) }
        val results = offers.map { Evaluator.evaluate(it, prefs) }
        runCatching { store.observe(results) }   // relatório
        fab.update(Evaluator.best(results))
        runCatching { overlay.show(results) }
        runCatching { maybeAutoAccept(results) }
    }

    private fun maybeAutoAccept(results: List<Result>) {
        if (!prefs.autoAccept) return
        val now = SystemClock.uptimeMillis()
        if (now - lastClick < 3000) return

        val candidate = results
            .filter { !it.offer.route }   // "Rotas disponíveis": nunca toca em "Gerar Rota" sozinho
            .filter { it.offer.price >= prefs.autoMinPrice }          // valor bruto mínimo
            .filter { (it.effRpk ?: 0.0) >= prefs.autoMinRpk }
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        if (instance === this) instance = null
        Notif.cancel(this)
        handler.removeCallbacks(tick)
        ticking = false
        if (::store.isInitialized) runCatching { store.flush(true) }
        if (::overlay.isInitialized) overlay.remove()
        if (::fab.isInitialized) fab.remove()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.remove()
    }

    override fun onDestroy() {
        if (::store.isInitialized) runCatching { store.flush(true) }
        handler.removeCallbacks(tick)
        ticking = false
        if (::overlay.isInitialized) overlay.remove()
        if (::fab.isInitialized) fab.remove()
        running = false
        if (instance === this) instance = null
        Notif.cancel(this)
        super.onDestroy()
    }
}
