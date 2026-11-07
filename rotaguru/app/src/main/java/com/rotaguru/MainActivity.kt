package com.rotaguru

import android.app.Activity
import android.app.AlertDialog
import android.widget.ProgressBar
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var statusDot: View
    private lateinit var statusIcon: android.widget.ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var activateBtn: TextView
    private lateinit var notifWarn: LinearLayout
    private lateinit var sampleBox: LinearLayout

    private enum class Page { HOME, IFOOD, INDRIVE, REPORT, UPDATES, NEWS, CONFIG }
    private var page = Page.REPORT          // o Relatório é a tela inicial
    private var rotasOpen = false
    private var sampleKind = 0            // 0 = sem simulação, 1 = iFood, 2 = InDrive
    private var homeShown = false
    private var drawerOpen = false
    private val report by lazy { ReportScreen(this) }
    private lateinit var body: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View

    private val BG = 0xFF07060D.toInt()
    private val CARD = 0xFF110E1B.toInt()
    private val LINE = 0xFF2A2240.toInt()
    private val INPUT = 0xFF07060D.toInt()
    private val TXT = 0xFFFFFFFF.toInt()
    private val MUTED = 0xFFA29DBD.toInt()
    private val GREEN = 0xFF4CAF50.toInt()
    private val AMBER = 0xFFFFB300.toInt()
    private val GOLD = 0xFFB57BFF.toInt()      // destaque (roxo da marca)
    private val BLUE = 0xFF3BA4FF.toInt()      // azul da marca
    private val RED = 0xFFEF5350.toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    /** Degradê roxo → azul da tela de carregamento. */
    private fun brandGradient(radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF8B3DFF.toInt(), 0xFF3B82F6.toInt())
    ).apply { cornerRadius = dp(radius).toFloat() }

    /** Pinta o texto com o degradê da marca. */
    private fun gradientText(t: TextView) {
        t.post {
            val w = t.paint.measureText(t.text.toString())
            t.paint.shader = android.graphics.LinearGradient(0f, 0f, w, 0f,
                intArrayOf(0xFFC084FC.toInt(), 0xFF8B5CF6.toInt(), 0xFF38BDF8.toInt()), null,
                android.graphics.Shader.TileMode.CLAMP)
            t.invalidate()
        }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun tx(t: CharSequence, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(CARD, 18, LINE)
    }

    /** Título de cartão: selo com ícone (opcional) + título + subtítulo. */
    private fun title(t: String, sub: String? = null, icon: String? = null, accent: Int = GOLD): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (icon != null) addView(badge(icon, accent, 38), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) })
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tx(t, 16.5f, TXT, true))
            if (sub != null) col.addView(tx(sub, 12.5f, MUTED), lp(2))
            addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }

    /** Selo quadrado arredondado com ícone, no tom da cor [accent]. */
    private fun badge(icon: String, accent: Int, size: Int) = tx(icon, size * 0.42f, accent, true).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = rounded((accent and 0x00FFFFFF) or 0x22000000, size / 3, (accent and 0x00FFFFFF) or 0x3D000000)
    }

    /** Cabeçalho de página: faixa com degradê suave roxo → azul, ícone grande e subtítulo. */
    private fun pageHeader(icon: String, t: String, sub: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x3D8B3DFF, 0x173B82F6)).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), 0x478B5CF6)
        }
        addView(tx(icon, 22f, TXT).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = brandGradient(15)
        }, LinearLayout.LayoutParams(dp(50), dp(50)).apply { rightMargin = dp(14) })
        val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tx(t, 19f, TXT, true))
        col.addView(tx(sub, 12.5f, 0xFFC9C3E3.toInt()), lp(3))
        addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    /** Rótulo pequeno em maiúsculas para separar blocos dentro de um cartão. */
    private fun caps(t: String) = tx(t.uppercase(), 10.5f, MUTED, true).apply { letterSpacing = 0.1f }

    private fun divider() = View(this).apply { setBackgroundColor(LINE) }

    /** Grade de opções (3 por linha); a escolhida fica com o degradê da marca. */
    private fun optionGrid(items: List<String>, sel: Int, onPick: (Int) -> Unit): LinearLayout {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val views = ArrayList<TextView>()
        var row: LinearLayout? = null
        items.forEachIndexed { i, label ->
            if (i % 3 == 0) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row = r
                grid.addView(r, lp(if (i == 0) 0 else 8))
            }
            val chip = tx(label, 13f, MUTED, true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(6), dp(11), dp(6), dp(11))
            }
            views.add(chip)
            row!!.addView(chip, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i % 3 != 0) leftMargin = dp(8) })
        }
        val rem = items.size % 3
        if (rem != 0) repeat(3 - rem) {
            row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(8) })
        }
        fun paint(s: Int) {
            views.forEachIndexed { i, v ->
                val on = i == s
                v.setTextColor(if (on) TXT else MUTED)
                v.background = if (on) brandGradient(11) else rounded(INPUT, 11, LINE)
            }
        }
        paint(sel)
        views.forEachIndexed { i, v -> v.setOnClickListener { paint(i); onPick(i) } }
        return grid
    }

    private fun styleButton(b: TextView, primary: Boolean) {
        b.setTextColor(TXT)
        b.background = if (primary) brandGradient(12) else rounded(0xFF1C1630.toInt(), 12, LINE)
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(14), dp(13), dp(14), dp(13))
        styleButton(this, primary)
        setOnClickListener { onClick() }
    }

    private fun step(n: Int, head: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val num = tx(n.toString(), 12.5f, TXT, true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF8B3DFF.toInt(), 0xFF3B82F6.toInt())).apply { shape = GradientDrawable.OVAL }
        }
        addView(num, LinearLayout.LayoutParams(dp(26), dp(26)).apply { topMargin = dp(1) })
        val col = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(tx(head, 14.5f, TXT, true))
            addView(tx(body, 13f, MUTED), lp(2))
        }
        addView(col, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(12) })
    }

    private fun field(label: String, value: Float, onChange: (Float) -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tx(label, 12.5f, MUTED))
            val et = EditText(this@MainActivity).apply {
                setText(value.toString().replace('.', ','))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(TXT)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                background = rounded(INPUT, 10, LINE)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setSelectAllOnFocus(true)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        s?.toString()?.replace(',', '.')?.toFloatOrNull()?.let {
                            onChange(it); refreshSample()
                        }
                    }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            addView(et, lp(6))
        }

    private fun pair(a: View, b: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(6) })
    }

    private fun switchRow(head: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(tx(head, 14.5f, TXT, true))
                addView(tx(sub, 12.5f, MUTED), lp(2))
            }
            addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            addView(Switch(this@MainActivity).apply {
                isChecked = checked
                thumbTintList = android.content.res.ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), 0xFFB8B3CC.toInt()))
                trackTintList = android.content.res.ColorStateList(states, intArrayOf(0xFF8B5CF6.toInt(), 0xFF3A3352.toInt()))
                setOnCheckedChangeListener { _, v -> onChange(v); refreshSample() }
            })
        }

    /** Cartão com cabeçalho que abre/fecha o conteúdo. */
    private fun collapsible(head: String, sub: String, open: Boolean, content: LinearLayout, icon: String? = null): LinearLayout {
        val c = card()
        val arrow = tx(if (open) "▾" else "▸", 18f, MUTED)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title(head, sub, icon), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(arrow)
        }
        content.visibility = if (open) View.VISIBLE else View.GONE
        header.setOnClickListener {
            val show = content.visibility != View.VISIBLE
            content.visibility = if (show) View.VISIBLE else View.GONE
            arrow.text = if (show) "▾" else "▸"
        }
        c.addView(header)
        c.addView(content, lp(14))
        return c
    }

    private fun stack(vararg rows: View, gap: Int = 14) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        rows.forEachIndexed { i, v -> addView(v, lp(if (i == 0) 0 else gap)) }
    }

    // ------------------------------------------------------------------ estrutura (menu lateral)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(34), dp(16), dp(8))
            setBackgroundColor(BG)
        }
        val burger = tx("☰", 24f, TXT, true).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { setDrawer(true) }
        }
        titleView = tx("Prismo Route", 20f, TXT, true)
        bar.addView(burger)
        bar.addView(titleView, LinearLayout.LayoutParams(0, WRAP, 1f))
        content.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(40))
        }
        content.addView(
            ScrollView(this).apply { isFillViewport = true; addView(body) },
            LinearLayout.LayoutParams(MATCH, 0, 1f)
        )
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        scrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { setDrawer(false) }
        }
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1A1230.toInt(), 0xFF0E0B18.toInt(), 0xFF0A0812.toInt()))
            setPadding(dp(14), dp(44), dp(14), dp(16))
            visibility = View.GONE
            translationX = -dp(290).toFloat()
            isClickable = true
        }
        root.addView(drawer, FrameLayout.LayoutParams(dp(280), MATCH))

        setContentView(root)
        showPage()
        askNotifications(false)

        // atualização automática pelo GitHub
        Updater.schedule(this)
        if (!handleUpdateIntent(intent)) Updater.checkAsync(this, notify = true) { rel, _ -> if (rel != null) newRelease = rel }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    // ------------------------------------------------------------------ atualização
    private var updDialog: AlertDialog? = null
    private var updText: TextView? = null
    private var updBar: ProgressBar? = null
    private var pendingApk: java.io.File? = null      // baixado, esperando liberar "instalar apps desconhecidos"
    private var updBusy = false

    /** Veio do toque na notificação "Nova versão"? Então baixa e instala. */
    private fun handleUpdateIntent(i: Intent?): Boolean {
        if (i?.action != Updater.ACTION_UPDATE) return false
        val url = i.getStringExtra(Updater.EXTRA_URL) ?: return false
        val ver = i.getStringExtra(Updater.EXTRA_VERSION) ?: ""
        val code = i.getIntExtra(Updater.EXTRA_CODE, 0)
        setIntent(Intent(this, MainActivity::class.java))   // não repete ao girar a tela
        if (code in 1..Updater.currentCode(this)) {
            Toast.makeText(this, "O Prismo Route já está na versão mais nova", Toast.LENGTH_SHORT).show()
            Updater.cancelNotif(this)
            return true
        }
        startUpdate(url, ver)
        return true
    }

    private fun startUpdate(url: String, ver: String) {
        if (updBusy) return
        updBusy = true
        Updater.cancelNotif(this)
        showUpdDialog("Baixando a versão $ver…")
        val ui = Handler(Looper.getMainLooper())
        Thread {
            try {
                val apk = Updater.download(this, url) { p ->
                    ui.post {
                        updBar?.isIndeterminate = p < 0
                        if (p >= 0) updBar?.progress = p
                        updText?.text = if (p >= 0) "Baixando a versão $ver… $p%" else "Baixando a versão $ver…"
                    }
                }
                ui.post { installApk(apk) }
            } catch (t: Throwable) {
                ui.post {
                    updBusy = false
                    closeUpdDialog()
                    AlertDialog.Builder(this)
                        .setTitle("Não deu para baixar")
                        .setMessage((t.message ?: "Erro de conexão") + "\n\nConfira a internet e tente de novo.")
                        .setPositiveButton("Tentar de novo") { _, _ -> startUpdate(url, ver) }
                        .setNegativeButton("Agora não", null)
                        .show()
                }
            }
        }.start()
    }

    private fun installApk(apk: java.io.File) {
        if (!Updater.canInstall(this)) {
            pendingApk = apk
            closeUpdDialog()
            AlertDialog.Builder(this)
                .setTitle("Liberar a atualização")
                .setMessage("Só na primeira vez: o Android pede para você permitir que o Prismo Route instale atualizações.\n\nNa próxima tela, ative \"Permitir desta fonte\" e volte para o app.")
                .setPositiveButton("Abrir") { _, _ -> Updater.openInstallPermission(this) }
                .setNegativeButton("Agora não") { _, _ -> pendingApk = null; updBusy = false }
                .setCancelable(false)
                .show()
            return
        }
        pendingApk = null
        showUpdDialog("Instalando…")
        updBar?.isIndeterminate = true
        try {
            if (RouteService.running) RouteService.instance?.let { runCatching { it.saveState() } }
            Updater.install(this, apk)
            // o Android mostra "Deseja atualizar este app?" (ou instala direto) e reinicia o Prismo Route
        } catch (t: Throwable) {
            onInstallFailedUi(t.message ?: "erro desconhecido")
        }
    }

    private fun onInstallFailedUi(msg: String) {
        updBusy = false
        closeUpdDialog()
        AlertDialog.Builder(this)
            .setTitle("Atualização não instalada")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showUpdDialog(msg: String) {
        if (updDialog?.isShowing == true) { updText?.text = msg; return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(8))
        }
        updText = TextView(this).apply { text = msg; textSize = 15f }
        updBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        box.addView(updText)
        box.addView(updBar, lp(14))
        updDialog = AlertDialog.Builder(this)
            .setTitle("Atualizando o Prismo Route")
            .setView(box)
            .setCancelable(false)
            .show()
    }

    private fun closeUpdDialog() {
        runCatching { updDialog?.dismiss() }
        updDialog = null; updText = null; updBar = null
    }

    override fun onDestroy() {
        if (current === this) current = null
        closeUpdDialog()
        super.onDestroy()
    }

    companion object {
        @Volatile private var current: MainActivity? = null

        /** Chamado pelo InstallReceiver quando o Android recusa/cancela a instalação. */
        fun onInstallFailed(msg: String) {
            val a = current ?: return
            a.runOnUiThread { if (a.updBusy) a.onInstallFailedUi(msg) }
        }
    }

    private fun setDrawer(open: Boolean) {
        drawerOpen = open
        if (open) {
            buildDrawer()
            scrim.visibility = View.VISIBLE
            drawer.visibility = View.VISIBLE
            scrim.animate().alpha(1f).setDuration(180).start()
            drawer.animate().translationX(0f).setDuration(200).start()
        } else {
            scrim.animate().alpha(0f).setDuration(160).withEndAction { scrim.visibility = View.GONE }.start()
            drawer.animate().translationX(-dp(290).toFloat()).setDuration(180)
                .withEndAction { drawer.visibility = View.GONE }.start()
        }
    }

    private fun drawerRow(
        icon: String, text: String, selected: Boolean, indent: Int,
        trailing: CharSequence? = null, trailingColor: Int = MUTED, onClick: () -> Unit
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12 + indent), dp(12), dp(12), dp(12))
            if (selected) background = rounded(0x268B5CF6, 12, 0x3D8B5CF6)
            addView(tx(icon, if (indent > 0) 14f else 16f, TXT).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(26), WRAP).apply { rightMargin = dp(10) })
            addView(tx(text, if (indent > 0) 15f else 16f, if (selected) GOLD else TXT, selected || indent == 0),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            if (trailing != null) addView(tx(trailing, 12.5f, trailingColor, true))
            setOnClickListener { onClick() }
        }

    private var verPulse: android.animation.Animator? = null

    /**
     * "Avaliador de corridas" + a versão numa pílula. Quando há versão nova, a pílula ganha o
     * degradê da marca, mostra "v atual → v nova" e fica pulsando de leve; toque abre Atualizações.
     */
    private fun versionBlock(): View {
        val cur = Updater.currentName(this)
        val pend = newRelease ?: Updater.pending(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val wrap = { top: Int -> LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(top) } }
        if (pend == null) {
            col.addView(tx("v$cur", 11.5f, 0xFFC9C3E3.toInt(), true).apply {
                setPadding(dp(9), dp(3), dp(9), dp(4))
                background = rounded(0xFF1C1630.toInt(), 10, LINE)
            }, wrap(4))
            return col
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) }
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(12), dp(5))
            background = brandGradient(12)
            addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(7) })
            addView(tx("v$cur  →  v${pend.versionName}", 11.5f, TXT, true))
            setOnClickListener { navigate(Page.UPDATES) }
        }
        col.addView(pill, wrap(4))
        col.addView(tx("✨ Nova versão disponível · toque para atualizar", 11f, 0xFFC4A1FF.toInt()).apply {
            setOnClickListener { navigate(Page.UPDATES) }
        }, wrap(5))
        // pulsar: a pílula "respira" e a bolinha pisca
        pill.pivotX = 0f
        val breathe = android.animation.ObjectAnimator.ofPropertyValuesHolder(pill,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.72f)
        ).apply {
            duration = 850
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
        }
        val blink = android.animation.ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.2f).apply {
            duration = 500
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
        }
        verPulse = android.animation.AnimatorSet().apply { playTogether(breathe, blink); start() }
        return col
    }

    private fun buildDrawer() {
        verPulse?.cancel()
        verPulse = null
        drawer.removeAllViews()
        val name = tx("Prismo Route", 22f, TXT, true)
        gradientText(name)
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(versionBlock(), lp(2))
        }
        name.setOnClickListener { navigate(Page.REPORT) }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(12), 0, dp(4), dp(16))
            addView(names, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(powerButton(), LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        drawer.addView(head, lp())
        drawer.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)))

        val inRotas = page == Page.IFOOD || page == Page.INDRIVE
        drawer.addView(
            drawerRow("🧭", "Rotas", inRotas && !rotasOpen, 0, if (rotasOpen) "▾" else "▸") {
                rotasOpen = !rotasOpen
                buildDrawer()
            }, lp(10)
        )
        if (rotasOpen) {
            drawer.addView(drawerRow("🍔", "iFood", page == Page.IFOOD, 18) { navigate(Page.IFOOD) }, lp(2))
            drawer.addView(drawerRow("🚗", "InDrive", page == Page.INDRIVE, 18) { navigate(Page.INDRIVE) }, lp(2))
        }
        drawer.addView(drawerRow("📊", "Relatório", page == Page.REPORT, 0) { navigate(Page.REPORT) }, lp(4))
        val hasNew = newRelease != null || Updater.pending(this) != null
        drawer.addView(
            drawerRow("⬆️", "Atualizações", page == Page.UPDATES || page == Page.NEWS, 0,
                if (hasNew) "● Nova" else null, 0xFFC4A1FF.toInt()) { navigate(Page.UPDATES) }, lp(4)
        )
        drawer.addView(drawerRow("⚙️", "Configurações", page == Page.CONFIG, 0) { navigate(Page.CONFIG) }, lp(4))
        drawer.addView(drawerRow("❓", "Ajuda", page == Page.HOME, 0) { navigate(Page.HOME) }, lp(4))
    }

    /**
     * Botão liga/desliga ao lado do nome no menu: verde = ligado, vermelho = desligado.
     * Toque liga ou desliga o serviço na hora.
     */
    private fun powerButton(): View {
        val on = RouteService.running
        val color = if (on) GREEN else RED
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((color and 0x00FFFFFF) or 0x26000000)
                setStroke(dp(1), (color and 0x00FFFFFF) or 0x8C000000.toInt())
            }
            addView(android.widget.ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_power)
                imageTintList = android.content.res.ColorStateList.valueOf(color)
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            contentDescription = if (on) "Desligar Prismo Route" else "Ligar Prismo Route"
            setOnClickListener { onPowerClick() }
        }
    }

    private fun navigate(p: Page) {
        page = p
        if (p == Page.IFOOD || p == Page.INDRIVE) rotasOpen = true
        setDrawer(false)
        showPage()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> setDrawer(false)
            page == Page.NEWS -> navigate(Page.UPDATES)
            page != Page.REPORT -> navigate(Page.REPORT)
            else -> super.onBackPressed()
        }
    }

    private fun showPage() {
        body.removeAllViews()
        sampleKind = 0
        homeShown = false
        when (page) {
            Page.HOME -> { titleView.text = "Ajuda"; buildHome() }
            Page.IFOOD -> { titleView.text = "iFood"; buildIfood() }
            Page.INDRIVE -> { titleView.text = "InDrive"; buildIndrive() }
            Page.REPORT -> { titleView.text = "Relatório"; report.build(body) }
            Page.UPDATES -> { titleView.text = "Atualizações"; buildUpdates() }
            Page.NEWS -> { titleView.text = "Novidades"; buildNews() }
            Page.CONFIG -> { titleView.text = "Configurações"; buildConfig() }
        }
    }

    // ------------------------------------------------------------------ início
    private fun buildHome() {
        val col = body
        col.addView(pageHeader("❓", "Ajuda", "Como usar o Prismo Route · versão ${Updater.currentName(this)}"), lp(4))

        // status do serviço
        val status = card()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusIcon = android.widget.ImageView(this).apply { setImageResource(R.drawable.ic_power) }
        statusDot = FrameLayout(this).apply {
            addView(statusIcon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        top.addView(statusDot, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(14) })
        val st = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = tx("", 17f, TXT, true)
        statusSub = tx("", 13f, MUTED)
        st.addView(statusTitle)
        st.addView(statusSub, lp(2))
        top.addView(st, LinearLayout.LayoutParams(0, WRAP, 1f))
        status.addView(top)
        // botão único: Ligar (desligado/travado) ou Desligar (ligado)
        activateBtn = button("", true) { onPowerClick() }
        status.addView(activateBtn, lp(16))

        // aviso quando as notificações estão bloqueadas (sem elas o botão Desligar não aparece na barra)
        notifWarn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = rounded(0x1FFFB300, 12, 0x55FFB300)
            addView(tx("Notificações bloqueadas", 13.5f, AMBER, true))
            addView(tx("Permita as notificações para o botão \"Desligar\" ficar na barra e para receber os avisos de atualização.", 12.5f, MUTED), lp(2))
            addView(button("Permitir notificações", false) { askNotifications(true) }, lp(10))
        }
        status.addView(notifWarn, lp(12))
        col.addView(status, lp(12))

        val bank = card().apply {
            addView(title("Vai abrir o app do banco?", "Desligue antes, senão o banco não abre", "🏦", AMBER))
            addView(tx("Toque em \"Desligar\" no aviso da barra de notificações, no botão vermelho/verde do menu ☰ " +
                "ou no menu do botão flutuante. Com o Prismo Route desligado os apps de banco abrem normalmente. " +
                "Para voltar a rodar, ligue de novo pelo mesmo botão.",
                13f, MUTED), lp(12))
        }
        col.addView(bank, lp(12))

        val guide = stack(
            step(1, "Ligue o Prismo Route",
                "Toque em \"Ligar Prismo Route\" acima. Na primeira vez abre a acessibilidade: escolha Prismo Route e ligue a chave."),
            step(2, "Ajuste seus valores",
                "Menu ☰ → Rotas → iFood ou InDrive: gasolina, consumo e os R$/km. O quadro \"No mês\" mostra valores de referência."),
            step(3, "Escolha o alerta",
                "Menu ☰ → Configurações: escolha o som e a vibração que tocam a cada rota nova."),
            step(4, "Abra o app de corridas",
                "O selo aparece em cada corrida/rota. Na tela de detalhe: toque curto recolhe, toque longo abre o app."),
            step(5, "Acompanhe no Relatório",
                "Tudo o que foi analisado fica no Relatório (tela inicial), com gráficos por dia, semana e mês."),
            step(6, "Terminou? Desligue",
                "Desligue antes de usar o app do banco.")
        )
        col.addView(collapsible("Como começar", "6 passos rápidos", !Power.isEnabled(this), guide, "🚀"), lp(12))

        val restricted = stack(
            tx("No Android 13 ou mais novo, apps instalados fora da Play Store vêm com a chave bloqueada " +
                "(aparece \"Configuração restrita\"). Para liberar:", 13f, MUTED),
            step(1, "Tente ativar uma vez",
                "Toque em \"Ligar Prismo Route\" e na chave do Prismo Route. O aviso de configuração restrita aparece."),
            step(2, "Abra as informações do app",
                "Toque no botão abaixo (ou segure o ícone do app → Informações do app)."),
            step(3, "Permita as configurações restritas",
                "Toque nos 3 pontinhos ⋮ no canto superior direito e escolha \"Permitir configurações restritas\". Confirme com senha ou digital."),
            step(4, "Volte e ative",
                "Acessibilidade → Apps instalados → Prismo Route → ligar."),
            button("Abrir informações do app", false) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        )
        col.addView(collapsible("Não consigo ativar", "Chave cinza ou \"Configuração restrita\"", false, restricted, "🔒"), lp(12))

        val trouble = stack(
            tx("• Desligue e ligue de novo a chave do serviço.\n" +
                "• Tire o app da economia de bateria (botão abaixo) e deixe como \"Sem restrições\".\n" +
                "• Mantenha o app de corridas aberto na lista ou na tela de detalhe da corrida.", 13f, MUTED),
            button("Ajustes de bateria", false) {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        )
        col.addView(collapsible("O selo não aparece ou some", "Bateria e permissões", false, trouble, "🛠️"), lp(12))

        val adb = stack(
            tx("Libere uma única vez pelo computador, com a depuração USB ligada:", 13f, MUTED),
            tx("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", 12.5f, 0xFFC4A1FF.toInt()).apply {
                typeface = Typeface.MONOSPACE
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(INPUT, 10, LINE)
                setTextIsSelectable(true)
            },
            tx("Depois disso o botão liga o serviço direto, sem abrir a acessibilidade.", 13f, MUTED)
        )
        col.addView(collapsible("Ligar com 1 toque", "Opcional · pelo computador", false, adb, "⚡"), lp(12))

        homeShown = true
        updateStatus()
    }

    // ------------------------------------------------------------------ iFood
    private fun buildIfood() {
        body.addView(pageHeader("🍔", "iFood", "Rotas disponíveis · coleta + entregas"), lp(4))

        val ifRecs = MonthRef.monthRecs(this, route = true)
        val cls = card()
        cls.addView(title("Classificação", "R$ por km da rota inteira (coleta + entregas)", "🎯"))
        cls.addView(
            pair(
                field("RUIM abaixo de", prefs.ifoodMedio) { prefs.ifoodMedio = it },
                field("ÓTIMO a partir de", prefs.ifoodOtimo) { prefs.ifoodOtimo = it }
            ), lp(16)
        )
        cls.addView(classRef(MonthRef.of(ifRecs.mapNotNull { it.rpk }), "rotas",
            { prefs.ifoodMedio = it }, { prefs.ifoodOtimo = it }), lp(14))
        body.addView(cls, lp(12))

        val ped = card()
        ped.addView(title("Valor por pedido", "Valor bruto ÷ nº de entregas. Abaixo do mínimo = RUIM.", "📦", BLUE))
        ped.addView(field("Valor mínimo por pedido (R$, 0 = sem mínimo)", prefs.ifoodMinPerOrder) { prefs.ifoodMinPerOrder = it }, lp(16))
        ped.addView(orderRef(MonthRef.of(ifRecs.mapNotNull { it.per })), lp(14))
        body.addView(ped, lp(12))
        body.addView(fuelCard(false), lp(12))
    }

    // ------------------------------------------------------------------ InDrive
    private fun buildIndrive() {
        body.addView(pageHeader("🚗", "InDrive", "Corridas · R$ por km rodado (busca + corrida)"), lp(4))

        val cls = card()
        cls.addView(title("Classificação", "R$ por km rodado (busca + corrida)", "🎯"))
        cls.addView(
            pair(
                field("RUIM abaixo de", prefs.medio) { prefs.medio = it },
                field("ÓTIMO a partir de", prefs.otimo) { prefs.otimo = it }
            ), lp(16)
        )
        cls.addView(classRef(MonthRef.of(MonthRef.monthRecs(this, route = false).mapNotNull { it.rpk }), "corridas",
            { prefs.medio = it }, { prefs.otimo = it }), lp(14))
        cls.addView(divider(), LinearLayout.LayoutParams(MATCH, 1).apply { topMargin = dp(16) })
        cls.addView(
            switchRow("Somar a busca", "Conta a distância até o passageiro na nota", prefs.includePickup) {
                prefs.includePickup = it
            }, lp(14)
        )
        body.addView(cls, lp(12))
        body.addView(fuelCard(true), lp(12))

        val pick = card()
        pick.addView(title("Cores da busca", "Distância até o passageiro", "📍", BLUE))
        pick.addView(
            pair(
                field("Verde até (km)", prefs.pickupGood) { prefs.pickupGood = it },
                field("Vermelho a partir de (km)", prefs.pickupBad) { prefs.pickupBad = it }
            ), lp(16)
        )
        body.addView(pick, lp(12))

        val auto = card()
        auto.addView(title("Aceite automático", "Aceita sozinho só quando bate os 3 mínimos", "⚡", AMBER))
        auto.addView(
            switchRow("Aceitar corridas automaticamente", "Também dá para ligar pelo botão flutuante", prefs.autoAccept) {
                prefs.autoAccept = it
            }, lp(14)
        )
        auto.addView(field("Valor mínimo bruto (R$, 0 = sem mínimo)", prefs.autoMinPrice) { prefs.autoMinPrice = it }, lp(14))
        auto.addView(
            pair(
                field("R$/km mínimo", prefs.autoMinRpk) { prefs.autoMinRpk = it },
                field("Nota mínima", prefs.minRating) { prefs.minRating = it }
            ), lp(12)
        )
        body.addView(auto, lp(12))
    }

    // ------------------------------------------------------------------ referência do mês (vinda do Relatório)
    private fun money(v: Double) = String.format(java.util.Locale("pt", "BR"), "R$ %.2f", v)

    private fun monthName(): String {
        val m = arrayOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro")
        return m[java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)]
    }

    /** Quadro discreto "No mês": base para os campos RUIM / ÓTIMO, com atalho "Usar". */
    private fun refBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(0xFF0C0A15.toInt(), 12, LINE)
    }

    private fun refHeader(box: LinearLayout, n: Int, unit: String) {
        box.addView(tx("📊  NO MÊS · ${monthName().uppercase()} · $n $unit", 10.5f, MUTED, true).apply { letterSpacing = 0.06f })
    }

    private fun refLine(label: String, value: String, detail: String, color: Int, onUse: (() -> Unit)?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val dot = View(this@MainActivity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            }
            addView(dot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(9) })
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tx(android.text.SpannableStringBuilder().apply {
                append(label + "  ")
                val st = length
                append(value)
                setSpan(android.text.style.StyleSpan(Typeface.BOLD), st, length, 0)
                setSpan(android.text.style.ForegroundColorSpan(TXT), st, length, 0)
            }, 13f, MUTED))
            col.addView(tx(detail, 11.5f, MUTED), lp(1))
            addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (onUse != null) addView(tx("Usar", 12.5f, GOLD, true).apply {
                setPadding(dp(12), dp(6), dp(4), dp(6))
                setOnClickListener {
                    onUse()
                    Toast.makeText(this@MainActivity, "$label $value aplicado", Toast.LENGTH_SHORT).show()
                    showPage()
                }
            })
        }

    private fun pctOf(t: MonthRef.Tier, unit: String) =
        "${t.pct}% das $unit" + (t.avg?.let { " · em média ${money(it)}" } ?: "")

    /** Referência para RUIM abaixo de / ÓTIMO a partir de (R$/km). */
    private fun classRef(r: MonthRef.Ref?, unit: String, setRuim: (Float) -> Unit, setOtimo: (Float) -> Unit): View {
        val box = refBox()
        if (r == null) {
            refHeader(box, 0, unit)
            box.addView(tx("Ainda sem $unit este mês. Os valores aparecem aqui conforme o Relatório for registrando.", 12.5f, MUTED), lp(6))
            return box
        }
        refHeader(box, r.n, unit)
        box.addView(refLine("RUIM abaixo de", money(r.mid) + "/km", pctOf(r.low, unit), 0xFFEF5350.toInt()) {
            setRuim(r.mid.toFloat())
        }, lp(10))
        box.addView(refLine("Médio", money(r.mid) + " a " + money(r.high), pctOf(r.medium, unit), AMBER, null), lp(8))
        box.addView(refLine("ÓTIMO a partir de", money(r.high) + "/km", pctOf(r.top, unit), GREEN) {
            setOtimo(r.high.toFloat())
        }, lp(8))
        if (r.n < 10) box.addView(tx("Poucos dados ainda: os valores ficam mais precisos com o uso.", 11f, MUTED), lp(8))
        return box
    }

    /** Referência do valor por pedido: a faixa com mais pedidos no mês. */
    private fun orderRef(r: MonthRef.Ref?): View {
        val box = refBox()
        if (r == null) {
            refHeader(box, 0, "rotas")
            box.addView(tx("Ainda sem rotas do iFood este mês.", 12.5f, MUTED), lp(6))
            return box
        }
        refHeader(box, r.n, "rotas")
        val i = r.topIdx
        val t = r.tier(i)
        val (name, range, color) = when (i) {
            0 -> Triple("Baixo", "abaixo de ${money(r.mid)}", 0xFFEF5350.toInt())
            1 -> Triple("Médio", "${money(r.mid)} a ${money(r.high)}", AMBER)
            else -> Triple("Alto", "${money(r.high)} ou mais", GREEN)
        }
        val big = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(tx("${t.pct}%", 24f, color, true))
            addView(tx("  dos pedidos ficam em", 12.5f, MUTED).apply { setPadding(0, 0, 0, dp(4)) })
        }
        box.addView(big, lp(8))
        box.addView(tx(t.avg?.let { "≈ ${money(it)} por pedido" } ?: "—", 17f, TXT, true), lp(0))
        box.addView(tx("Faixa $name: $range", 11.5f, MUTED), lp(2))
        box.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, 1).apply { topMargin = dp(10) })
        box.addView(refLine("Baixo", "abaixo de ${money(r.mid)}", pctOf(r.low, "rotas"), 0xFFEF5350.toInt(), null), lp(10))
        box.addView(refLine("Médio", "${money(r.mid)} a ${money(r.high)}", pctOf(r.medium, "rotas"), AMBER, null), lp(8))
        box.addView(refLine("Alto", "${money(r.high)} ou mais", pctOf(r.top, "rotas"), GREEN, null), lp(8))
        return box
    }

    /** Gasolina e lucro: preço e consumo valem para todos os apps. */
    private fun fuelCard(withMinRpk: Boolean): LinearLayout {
        val fuel = card()
        fuel.addView(title("Gasolina e lucro", "Gasto = km ÷ consumo × preço do litro · vale para todos os apps", "⛽", GREEN))
        fuel.addView(
            pair(
                field("Gasolina (R$/litro)", prefs.fuelPrice) { prefs.fuelPrice = it },
                field("Consumo (km/litro)", prefs.kmPerLiter) { prefs.kmPerLiter = it }
            ), lp(16)
        )
        if (withMinRpk) {
            fuel.addView(field("R$/km mínimo (abaixo disso sugere valor)", prefs.minRpk) { prefs.minRpk = it }, lp(12))
        }
        return fuel
    }

    // ------------------------------------------------------------------ configurações
    private fun buildConfig() {
        body.addView(pageHeader("⚙️", "Configurações", "Alertas, cálculo e informações do app"), lp(4))

        // alerta de rota nova
        val al = card()
        al.addView(title("Alerta de rota", "Som e vibração a cada rota nova", "🔔"))
        al.addView(
            switchRow("Avisar a cada rota nova", "Toca mesmo no Não perturbe e no silencioso", prefs.alertOn) {
                prefs.alertOn = it
            }, lp(16)
        )
        al.addView(divider(), LinearLayout.LayoutParams(MATCH, 1).apply { topMargin = dp(16) })
        al.addView(caps("Som"), lp(16))
        al.addView(optionGrid(RouteAlert.SOUNDS, prefs.alertSound) { i ->
            prefs.alertSound = i
            RouteAlert.play(this, i, 0)          // toca para você ouvir
        }, lp(10))
        al.addView(caps("Vibração"), lp(18))
        al.addView(optionGrid(RouteAlert.VIBES, prefs.alertVibe) { i ->
            prefs.alertVibe = i
            RouteAlert.play(this, 0, i)          // vibra para você sentir
        }, lp(10))
        al.addView(button("▶  Testar alerta", false) {
            RouteAlert.play(this, prefs.alertSound, prefs.alertVibe)
        }, lp(18))
        al.addView(tx("O som usa o volume de ALARME do celular. Se não ouvir, aumente o volume de alarme.", 11.5f, MUTED), lp(10))
        body.addView(al, lp(12))

        // cálculo
        val calc = card()
        calc.addView(title("Cálculo", "Usado quando o app de corridas não mostra o tempo", "🧮", BLUE))
        calc.addView(field("Velocidade média (km/h)", prefs.avgSpeed) { prefs.avgSpeed = it.coerceIn(5f, 120f) }, lp(16))
        body.addView(calc, lp(12))

        // sobre
        val about = card()
        about.addView(title("Sobre", "Prismo Route · Avaliador de corridas", "ℹ️", 0xFFC4A1FF.toInt()))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun infoRow(k: String, v: String) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tx(k, 13.5f, MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(tx(v, 13.5f, TXT, true))
        }
        info.addView(infoRow("Versão", Updater.currentName(this)))
        info.addView(infoRow("Serviço", if (RouteService.running) "Ligado" else "Desligado"), lp(8))
        info.addView(infoRow("Rotas no relatório", ReportStore.load(this).size.toString()), lp(8))
        about.addView(info, lp(16))
        about.addView(pair(
            button("✨ Novidades", true) { navigate(Page.NEWS) },
            button("Atualizações", false) { navigate(Page.UPDATES) }
        ), lp(16))
        body.addView(about, lp(12))
    }

    // ------------------------------------------------------------------ atualizações
    private var newRelease: Updater.Release? = null      // versão nova encontrada (marca "Nova" no menu)

    private fun buildUpdates() {
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale("pt", "BR"))

        // versão instalada (destaque)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x478B3DFF, 0x1F3B82F6)).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0x528B5CF6)
            }
        }
        hero.addView(caps("Versão instalada"))
        val ver = tx("v" + Updater.currentName(this), 34f, TXT, true)
        gradientText(ver)
        hero.addView(ver, lp(4))
        hero.addView(tx("Prismo Route · Avaliador de corridas", 13.5f, TXT), lp(2))
        val installed = runCatching { packageManager.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull()
        if (installed != null) hero.addView(tx("Instalada em ${fmt.format(java.util.Date(installed))}", 12.5f, 0xFFC9C3E3.toInt()), lp(4))
        hero.addView(button("✨  Novidades", true) { navigate(Page.NEWS) }, lp(16))
        body.addView(hero, lp(4))

        if (!Updater.enabled) {
            val c = card()
            c.addView(tx("Esta cópia não sabe onde procurar versões novas.", 13.5f, MUTED))
            body.addView(c, lp(12))
            return
        }

        // verificação
        val chk = card()
        chk.addView(title("Procurar atualização", "O app também confere sozinho e avisa por notificação", "🔄", BLUE))
        val status = tx("", 13.5f, MUTED)
        val lastLine = tx("", 12f, MUTED)
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun paintLast() {
            val t = getSharedPreferences("rotaguru_update", MODE_PRIVATE).getLong("last_check", 0)
            lastLine.text = if (t > 0) "Última verificação: ${fmt.format(java.util.Date(t))}" else ""
        }
        fun showRelease(rel: Updater.Release) {
            result.removeAllViews()
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(0x268B5CF6, 14, 0x668B5CF6)
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            box.addView(caps("Disponível"))
            val nv = tx("v${rel.versionName}", 22f, TXT, true)
            gradientText(nv)
            box.addView(nv, lp(2))
            if (rel.notes.isNotEmpty()) box.addView(tx(rel.notes.take(800), 13.5f, TXT), lp(8))
            box.addView(button("Atualizar agora", true) { startUpdate(rel.apkUrl, rel.versionName) }, lp(14))
            result.addView(box)
        }
        lateinit var btn: TextView
        fun check() {
            btn.isEnabled = false
            btn.alpha = 0.6f
            status.setTextColor(MUTED)
            status.text = "Procurando…"
            Updater.checkAsync(this, notify = true, force = true) { rel, err ->
                if (page != Page.UPDATES) return@checkAsync
                btn.isEnabled = true
                btn.alpha = 1f
                paintLast()
                when {
                    rel != null -> {
                        newRelease = rel
                        status.setTextColor(0xFFC4A1FF.toInt())
                        status.text = "✨ Tem atualização disponível!"
                        showRelease(rel)
                    }
                    err -> {
                        status.setTextColor(AMBER)
                        status.text = "Não consegui consultar o GitHub. Confira a internet e tente de novo.\n(${Updater.lastError ?: "erro"})"
                    }
                    else -> {
                        newRelease = null
                        result.removeAllViews()
                        status.setTextColor(GREEN)
                        val gh = Updater.lastSeen?.versionName
                        status.text = "✓ Você já está na versão mais nova." +
                            (if (gh != null) "\nÚltima versão: $gh" else "")
                    }
                }
            }
        }
        btn = button("Verificar atualização", false) { check() }
        chk.addView(btn, lp(16))
        chk.addView(status, lp(12))
        chk.addView(lastLine, lp(4))
        chk.addView(result, lp(12))
        body.addView(chk, lp(12))
        paintLast()
        (newRelease ?: Updater.pending(this))?.let {
            showRelease(it); status.setTextColor(0xFFC4A1FF.toInt()); status.text = "✨ Tem atualização disponível!"
        }
        check()   // já confere ao abrir a aba
    }

    // ------------------------------------------------------------------ novidades
    /** Tudo o que o Prismo Route faz — escrito à mão para a versão 3.0. */
    private val NEWS = listOf(
        Triple("🎨", "Visual novo Prismo Route", listOf(
            "Novo nome: Prismo Route · Avaliador de corridas",
            "Ícone novo e tela de carregamento de 5 segundos",
            "Todo o app nas cores da marca: roxo e azul em fundo escuro",
            "Relatório agora é a tela inicial",
            "Menu lateral com ícones, versão do app e botão liga/desliga",
            "Nova aba Ajuda com o passo a passo e a solução de problemas"
        )),
        Triple("🔔", "Alerta de rota nova", listOf(
            "Toca um som e vibra a cada rota/corrida nova identificada",
            "4 sons para escolher: Pulso, Moeda, Sino e Radar",
            "5 tipos de vibração: Curta, Dupla, Longa, Batida e Tripla",
            "Funciona mesmo no Não perturbe e no modo silencioso (volume de alarme)",
            "A mesma oferta não toca de novo enquanto continua na tela",
            "Escolha e teste em Configurações → Alerta de rota"
        )),
        Triple("🎯", "Avaliação em tempo real", listOf(
            "Lê as ofertas do iFood (Rotas disponíveis) e do InDrive direto da tela",
            "Calcula o R$ por km de cada rota/corrida, contando a busca",
            "Classifica em RUIM, MÉDIO ou ÓTIMO com os seus limites",
            "iFood: valor por pedido (valor ÷ entregas) com mínimo configurável",
            "Gasto de gasolina e lucro líquido de cada oferta",
            "InDrive: sugere quanto pedir (\"PEÇA R$\") quando o valor está baixo",
            "Nota do passageiro, tempo da corrida e cores da distância da busca",
            "Destaca a melhor oferta da lista com ★"
        )),
        Triple("🟣", "Botão flutuante", listOf(
            "Fica sobre o app de corridas e mostra a melhor oferta da tela",
            "Painel rápido: aceite automático e seus 3 mínimos",
            "Maior valor do dia, atualizado sozinho a cada oferta melhor",
            "Atalhos para abrir o app e desligar"
        )),
        Triple("⚡", "Aceite automático", listOf(
            "Aceita sozinho só as ofertas que batem valor mínimo, R$/km mínimo e nota mínima",
            "Liga e desliga pelo botão flutuante ou pela aba InDrive"
        )),
        Triple("📊", "Relatório", listOf(
            "Guarda cada rota analisada (mês atual + 2 anteriores)",
            "Filtros Dia, Semana e Mês e por app: Todos, iFood ou InDrive",
            "Cartões de resumo: rotas analisadas, valor médio, R$/km médio e valor por pedido",
            "Comparação com o período anterior (▲ sobe / ▼ desce)",
            "Gráficos de valor total, R$/km e valor por pedido em faixas Baixo, Médio e Alto",
            "Cada faixa mostra a porcentagem e o valor médio (ex.: 44% ≈ R$ 10,00)",
            "Faixas automáticas que se ajustam sozinhas a cada nova rota",
            "Histórico dos meses guardados"
        )),
        Triple("🧭", "Referência do mês (iFood e InDrive)", listOf(
            "Quadro \"No mês\" embaixo dos limites RUIM e ÓTIMO, com os valores reais do mês",
            "Valor por pedido mais comum do mês (a faixa com mais pedidos)",
            "Botão \"Usar\" que aplica o valor sugerido com um toque",
            "Atualiza sozinho conforme o Relatório registra novas rotas"
        )),
        Triple("⏻", "Liga e desliga", listOf(
            "Botão no menu: verde = ligado, vermelho = desligado",
            "Notificação fixa com o botão Desligar enquanto está ligado",
            "Desligado, os apps de banco abrem normalmente"
        )),
        Triple("⬆️", "Atualizações automáticas", listOf(
            "Confere sozinho no GitHub: ao abrir, a cada 30 min com o serviço ligado e a cada 1 h em segundo plano",
            "Notificação quando sai versão nova; um toque baixa e instala por cima",
            "Aviso pulsando na versão do menu lateral quando há atualização",
            "Aba Atualizações com a versão instalada, data e verificação manual",
            "Não perde configurações nem o relatório ao atualizar"
        ))
    )

    private fun buildNews() {
        body.addView(pageHeader("✨", "Novidades", "Prismo Route ${Updater.currentName(this)} · tudo o que o app faz"), lp(4))

        // o que veio no commit desta versão (quando existir)
        val latest = card().apply { visibility = View.GONE }
        latest.addView(title("Nesta atualização", null, "🆕", BLUE))
        val notes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        latest.addView(notes, lp(12))
        body.addView(latest, lp(12))
        Updater.installedNotesAsync(this) { n ->
            if (page != Page.NEWS || n.isNullOrBlank()) return@installedNotesAsync
            n.lines().map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
                .filter { it.isNotEmpty() }
                .forEachIndexed { i, line -> notes.addView(bullet(line), lp(if (i == 0) 0 else 6)) }
            latest.visibility = View.VISIBLE
        }

        val accents = intArrayOf(GOLD, BLUE, GREEN, 0xFFC4A1FF.toInt(), AMBER, BLUE, GREEN, RED, GOLD)
        NEWS.forEachIndexed { idx, (icon, head, items) ->
            val c = card()
            c.addView(title(head, "${items.size} itens", icon, accents[idx % accents.size]))
            items.forEachIndexed { i, line -> c.addView(bullet(line), lp(if (i == 0) 14 else 7)) }
            body.addView(c, lp(12))
        }
        body.addView(tx("Prismo Route · feito para quem roda todo dia.", 12f, MUTED).apply {
            gravity = Gravity.CENTER
        }, lp(18))
    }

    /** Linha com marcador redondo no degradê da marca. */
    private fun bullet(t: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(View(this@MainActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFB57BFF.toInt(), 0xFF3BA4FF.toInt())).apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(7), dp(7)).apply { topMargin = dp(7); rightMargin = dp(11) })
        addView(tx(t, 13.5f, 0xFFE4E0F2.toInt()), LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    // ------------------------------------------------------------------ status
    override fun onResume() {
        super.onResume()
        current = this
        // voltou da tela "Permitir desta fonte": continua a instalação
        pendingApk?.let { apk -> if (Updater.canInstall(this)) installApk(apk) }
        if (RouteService.running) Notif.show(this)   // garante o aviso fixo na barra
        if (homeShown) updateStatus()
        if (page == Page.REPORT) report.refresh()
        if (drawerOpen) buildDrawer()                // ícone liga/desliga sempre com o estado real
        if (page == Page.IFOOD || page == Page.INDRIVE) showPage()   // referência do mês sempre atualizada
        refreshSample()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (RouteService.running) Notif.show(this)
        if (homeShown) updateStatus()
    }

    /** Android 13+: pede permissão de notificação (o botão Desligar mora na notificação). */
    private fun askNotifications(openSettingsIfDenied: Boolean) {
        if (Notif.canPost(this) && notificationsOn()) return
        if (!openSettingsIfDenied) {
            if (Build.VERSION.SDK_INT >= 33 && !Notif.canPost(this))
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !Notif.canPost(this) &&
            shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun notificationsOn() =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    private fun onPowerClick() {
        if (RouteService.running) {
            Power.turnOff(this)
            Toast.makeText(this, "Prismo Route desligado. Já pode abrir o app do banco.", Toast.LENGTH_LONG).show()
        } else {
            askNotifications(false)
            if (Power.turnOn(this)) Toast.makeText(this, "Ligando o Prismo Route…", Toast.LENGTH_SHORT).show()
        }
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ if (homeShown) updateStatus(); if (drawerOpen) buildDrawer() }, 500)
        h.postDelayed({ if (homeShown) updateStatus(); if (drawerOpen) buildDrawer() }, 1500)
    }

    private fun updateStatus() {
        val enabled = Power.isEnabled(this)
        val on = RouteService.running
        val stuck = enabled && !on
        val c = if (stuck) AMBER else if (on) GREEN else RED
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((c and 0x00FFFFFF) or 0x26000000)
            setStroke(dp(1), (c and 0x00FFFFFF) or 0x8C000000.toInt())
        }
        statusIcon.imageTintList = android.content.res.ColorStateList.valueOf(c)
        statusTitle.text = if (stuck) "Serviço parado" else if (on) "Prismo Route ligado" else "Prismo Route desligado"
        statusSub.text = if (stuck) "Toque em \"Ligar Prismo Route\" para reiniciar o serviço."
        else if (on) "Lendo as corridas. Antes de abrir o banco, toque em Desligar."
        else "Apps de banco funcionam normalmente. Ligue quando for rodar."
        if (on) {
            activateBtn.text = "Desligar Prismo Route"
            activateBtn.setTextColor(0xFFFF6B6B.toInt())
            activateBtn.background = rounded(0x1FEF5350, 12, 0x66EF5350)
        } else {
            activateBtn.text = "Ligar Prismo Route"
            styleButton(activateBtn, true)
        }
        notifWarn.visibility = if (Notif.canPost(this) && notificationsOn()) View.GONE else View.VISIBLE
    }

    private fun sampleRow(name: String, r: Result) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(BG, 12, r.level?.color ?: LINE)
        }
        box.addView(tx(name, 12.5f, MUTED))
        box.addView(tx(Evaluator.headline(r), 15f, TXT, true), lp(2))
        box.addView(tx(Evaluator.detail(r), 12.5f, MUTED), lp(2))
        val fuelTxt = Evaluator.fuelLine(r)
        if (fuelTxt.isNotEmpty()) box.addView(tx(fuelTxt, 12.5f, Evaluator.netColor(r), true), lp(2))
        if (r.offer.entregas >= 2 || r.belowMinOrder) {
            val each = r.perOrder ?: r.offer.price
            val n = r.offer.entregas
            val base = if (n >= 2) "$n pedidos · ${Evaluator.money(each)} cada" else "1 pedido · ${Evaluator.money(each)}"
            box.addView(
                tx(if (r.belowMinOrder) "$base — abaixo do mínimo (RUIM)" else base, 12.5f,
                    if (r.belowMinOrder) Evaluator.RED else GOLD, true), lp(2)
            )
        }
        Evaluator.suggestLine(r)?.let { if (!r.offer.route) box.addView(tx(it, 12.5f, GOLD, true), lp(2)) }
        sampleBox.addView(box, lp(if (sampleBox.childCount == 0) 0 else 8))
    }

    private fun refreshSample() {
        if (sampleKind == 0 || !::sampleBox.isInitialized) return
        sampleBox.removeAllViews()
        if (sampleKind == 1) {
            sampleRow("Rota com 3 entregas", Evaluator.evaluate(
                Offer(39.42, null, null, 9.83, 119.0, 0.0, null, route = true, coletas = 1, entregas = 3), prefs))
            sampleRow("Rota com 5 entregas", Evaluator.evaluate(
                Offer(30.0, null, null, 8.0, 50.0, 0.0, null, route = true, coletas = 1, entregas = 5), prefs))
            sampleRow("Rota com 1 entrega", Evaluator.evaluate(
                Offer(11.08, null, null, 2.96, 29.0, 0.0, null, route = true, coletas = 1, entregas = 1), prefs))
        } else {
            sampleRow("Aline", Evaluator.evaluate(Offer(39.0, 1.0, 2.1, 37.7, 38.0, 6.0, 4.97), prefs))
            sampleRow("Manuel", Evaluator.evaluate(Offer(25.5, 1.4, 3.1, null, null, null, 4.8), prefs))
        }
    }
}
