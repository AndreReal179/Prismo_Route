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
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var activateBtn: TextView
    private lateinit var notifWarn: LinearLayout
    private lateinit var sampleBox: LinearLayout

    private enum class Page { HOME, IFOOD, INDRIVE, REPORT, CONFIG }
    private var page = Page.HOME
    private var rotasOpen = false
    private var sampleKind = 0            // 0 = sem simulação, 1 = iFood, 2 = InDrive
    private var homeShown = false
    private var drawerOpen = false
    private val report by lazy { ReportScreen(this) }
    private lateinit var body: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View

    private val BG = 0xFF0E1014.toInt()
    private val CARD = 0xFF171A21.toInt()
    private val LINE = 0xFF262B35.toInt()
    private val INPUT = 0xFF0E1014.toInt()
    private val TXT = 0xFFFFFFFF.toInt()
    private val MUTED = 0xFF9AA0AC.toInt()
    private val GREEN = 0xFF4CAF50.toInt()
    private val AMBER = 0xFFFFB300.toInt()
    private val GOLD = 0xFFFFC107.toInt()
    private val RED = 0xFFEF5350.toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
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

    private fun title(t: String, sub: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tx(t, 17f, TXT, true))
        if (sub != null) addView(tx(sub, 12.5f, MUTED), lp(2))
    }

    private fun styleButton(b: TextView, primary: Boolean) {
        b.setTextColor(if (primary) BG else TXT)
        b.background = rounded(if (primary) GREEN else 0xFF222733.toInt(), 12, if (primary) null else LINE)
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
        val num = tx(n.toString(), 13f, BG, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GOLD) }
        }
        addView(num, LinearLayout.LayoutParams(dp(26), dp(26)).apply { topMargin = dp(2) })
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
            addView(Switch(this@MainActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, v -> onChange(v); refreshSample() }
            })
        }

    /** Cartão com cabeçalho que abre/fecha o conteúdo. */
    private fun collapsible(head: String, sub: String, open: Boolean, content: LinearLayout): LinearLayout {
        val c = card()
        val arrow = tx(if (open) "▾" else "▸", 18f, MUTED)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title(head, sub), LinearLayout.LayoutParams(0, WRAP, 1f))
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
            setBackgroundColor(CARD)
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
        if (!handleUpdateIntent(intent)) Updater.checkAsync(this, notify = true)
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

    private fun drawerRow(text: String, selected: Boolean, indent: Int, trailing: CharSequence?, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12 + indent), dp(13), dp(12), dp(13))
            if (selected) background = rounded(0xFF222733.toInt(), 12)
            addView(tx(text, if (indent > 0) 15f else 16f, if (selected) GOLD else TXT, selected || indent == 0),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            if (trailing != null) addView(tx(trailing, 13f, MUTED))
            setOnClickListener { onClick() }
        }

    private fun buildDrawer() {
        drawer.removeAllViews()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(14))
            addView(tx("Prismo Route", 22f, GOLD, true))
            addView(tx("Avaliador de corridas", 12.5f, MUTED), lp(2))
            setOnClickListener { navigate(Page.HOME) }
        }
        drawer.addView(head, lp())
        drawer.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, dp(1)))

        val inRotas = page == Page.IFOOD || page == Page.INDRIVE
        drawer.addView(
            drawerRow("Rotas", inRotas && !rotasOpen, 0, if (rotasOpen) "▾" else "▸") {
                rotasOpen = !rotasOpen
                buildDrawer()
            }, lp(10)
        )
        if (rotasOpen) {
            drawer.addView(drawerRow("iFood", page == Page.IFOOD, 18, null) { navigate(Page.IFOOD) }, lp(2))
            drawer.addView(drawerRow("InDrive", page == Page.INDRIVE, 18, null) { navigate(Page.INDRIVE) }, lp(2))
        }
        drawer.addView(
            drawerRow("Relatório", page == Page.REPORT, 0, null) { navigate(Page.REPORT) }, lp(4)
        )
        drawer.addView(
            drawerRow("Configurações", page == Page.CONFIG, 0, null) { navigate(Page.CONFIG) }, lp(4)
        )
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
            page != Page.HOME -> navigate(Page.HOME)
            else -> super.onBackPressed()
        }
    }

    private fun showPage() {
        body.removeAllViews()
        sampleKind = 0
        homeShown = false
        when (page) {
            Page.HOME -> { titleView.text = "Prismo Route"; buildHome() }
            Page.IFOOD -> { titleView.text = "iFood"; buildIfood() }
            Page.INDRIVE -> { titleView.text = "InDrive"; buildIndrive() }
            Page.REPORT -> { titleView.text = "Relatório"; report.build(body) }
            Page.CONFIG -> { titleView.text = "Configurações"; buildConfig() }
        }
    }

    // ------------------------------------------------------------------ início
    private fun buildHome() {
        val col = body
        col.addView(tx("Avaliador de corridas em tempo real", 14f, MUTED), lp(0))

        val status = card()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this)
        top.addView(statusDot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(12) })
        val st = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = tx("", 17f, TXT, true)
        statusSub = tx("", 13f, MUTED)
        st.addView(statusTitle)
        st.addView(statusSub, lp(2))
        top.addView(st, LinearLayout.LayoutParams(0, WRAP, 1f))
        status.addView(top)
        // botão único: Ligar (desligado/travado) ou Desligar (ligado)
        activateBtn = button("", true) { onPowerClick() }
        status.addView(activateBtn, lp(14))

        // aviso quando as notificações estão bloqueadas (sem elas o botão Desligar não aparece na barra)
        notifWarn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = rounded(0x1FFFB300, 12, 0x55FFB300)
            addView(tx("Notificações bloqueadas", 13.5f, AMBER, true))
            addView(tx("Permita as notificações para o botão \"Desligar\" ficar na barra.", 12.5f, MUTED), lp(2))
            addView(button("Permitir notificações", false) { askNotifications(true) }, lp(10))
        }
        status.addView(notifWarn, lp(12))
        col.addView(status, lp(14))

        val bank = card().apply {
            addView(title("Vai abrir o app do banco?"))
            addView(tx("Puxe a barra de notificações e toque em \"Desligar\" no aviso do Prismo Route " +
                "(ou use o botão acima / o menu do botão flutuante). Com o Prismo Route desligado, " +
                "os apps de banco abrem normalmente. Para voltar a rodar, abra o app e toque em \"Ligar\".",
                13f, MUTED), lp(6))
        }
        col.addView(bank, lp(12))

        val guide = stack(
            step(1, "Abra o Prismo Route", "Você já está aqui. Deixe o app instalado."),
            step(2, "Ligue o Prismo Route",
                "Toque em \"Ligar Prismo Route\". Na primeira vez abre a acessibilidade: escolha Prismo Route e ligue a chave."),
            step(3, "Ajuste seus valores",
                "Abra o menu ☰ → Rotas → iFood ou InDrive e preencha gasolina, consumo e os R$/km."),
            step(4, "Abra o app de corridas",
                "O painel aparece em cada corrida. Na tela de detalhe: toque curto recolhe, toque longo volta para cá."),
            step(5, "Terminou? Desligue",
                "Toque em \"Desligar\" na barra de notificações antes de usar o app do banco.")
        )
        col.addView(collapsible("Como começar", "5 passos rápidos", !Power.isEnabled(this), guide), lp(12))

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
            },
            tx("O painel não aparece ou some?", 14.5f, TXT, true),
            tx("• Desligue e ligue de novo a chave do serviço.\n" +
                "• Tire o app da economia de bateria (botão abaixo) e deixe como \"Sem restrições\".\n" +
                "• Mantenha o app de corridas aberto na lista ou na tela de detalhe da corrida.", 13f, MUTED),
            button("Ajustes de bateria", false) {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            },
            tx("Ligar com 1 toque (sem abrir a acessibilidade)", 14.5f, TXT, true),
            tx("Libere uma única vez pelo computador, com a depuração USB ligada:\n\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                "Depois disso o botão \"Ligar Prismo Route\" liga o serviço direto.", 13f, MUTED)
        )
        col.addView(
            collapsible("Não consigo ativar", "Chave cinza ou \"Configuração restrita\"", false, restricted),
            lp(12)
        )
        homeShown = true
        updateStatus()
    }

    // ------------------------------------------------------------------ iFood
    private fun buildIfood() {
        val cls = card()
        cls.addView(title("Classificação", "Em R$ por km da rota inteira (coleta + entregas)"))
        cls.addView(
            pair(
                field("RUIM abaixo de", prefs.ifoodMedio) { prefs.ifoodMedio = it },
                field("ÓTIMO a partir de", prefs.ifoodOtimo) { prefs.ifoodOtimo = it }
            ), lp(14)
        )
        body.addView(cls, lp(8))

        val ped = card()
        ped.addView(title("Valor por pedido",
            "O app conta as entregas da rota e divide o valor bruto. Abaixo do mínimo = RUIM."))
        ped.addView(field("Valor mínimo por pedido (R$, 0 = sem mínimo)", prefs.ifoodMinPerOrder) { prefs.ifoodMinPerOrder = it }, lp(14))
        body.addView(ped, lp(12))
        body.addView(fuelCard(false), lp(12))

        val sim = card()
        sim.addView(title("Simulação", "Veja como ficam duas rotas com seus valores"))
        sampleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sim.addView(sampleBox, lp(12))
        body.addView(sim, lp(12))
        sampleKind = 1
        refreshSample()
    }

    // ------------------------------------------------------------------ InDrive
    private fun buildIndrive() {
        val cls = card()
        cls.addView(title("Classificação", "Em R$ por km rodado (busca + corrida)"))
        cls.addView(
            pair(
                field("RUIM abaixo de", prefs.medio) { prefs.medio = it },
                field("ÓTIMO a partir de", prefs.otimo) { prefs.otimo = it }
            ), lp(14)
        )
        cls.addView(
            switchRow("Somar a busca", "Conta a distância até o passageiro na nota", prefs.includePickup) {
                prefs.includePickup = it
            }, lp(14)
        )
        body.addView(cls, lp(8))
        body.addView(fuelCard(true), lp(12))

        val pick = card()
        pick.addView(title("Cores da busca", "Distância até o passageiro"))
        pick.addView(
            pair(
                field("Verde até (km)", prefs.pickupGood) { prefs.pickupGood = it },
                field("Vermelho a partir de (km)", prefs.pickupBad) { prefs.pickupBad = it }
            ), lp(14)
        )
        body.addView(pick, lp(12))

        val auto = card()
        auto.addView(title("Aceite automático"))
        auto.addView(
            switchRow("Aceitar corridas automaticamente", "Só aceita se atingir os três mínimos abaixo", prefs.autoAccept) {
                prefs.autoAccept = it
            }, lp(12)
        )
        auto.addView(field("Valor mínimo bruto (R$, 0 = sem mínimo)", prefs.autoMinPrice) { prefs.autoMinPrice = it }, lp(12))
        auto.addView(field("Valor R$/km mínimo", prefs.autoMinRpk) { prefs.autoMinRpk = it }, lp(12))
        auto.addView(field("Nota mínima do passageiro", prefs.minRating) { prefs.minRating = it }, lp(12))
        body.addView(auto, lp(12))

        val sim = card()
        sim.addView(title("Simulação", "Veja como ficam duas corridas com seus valores"))
        sampleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sim.addView(sampleBox, lp(12))
        body.addView(sim, lp(12))
        sampleKind = 2
        refreshSample()
    }

    /** Gasolina e lucro: preço e consumo valem para todos os apps. */
    private fun fuelCard(withMinRpk: Boolean): LinearLayout {
        val fuel = card()
        fuel.addView(title("Gasolina e lucro", "Gasto = km total ÷ consumo × preço do litro (vale para todos os apps)"))
        fuel.addView(
            pair(
                field("Gasolina (R$/litro)", prefs.fuelPrice) { prefs.fuelPrice = it },
                field("Consumo (km/litro)", prefs.kmPerLiter) { prefs.kmPerLiter = it }
            ), lp(14)
        )
        if (withMinRpk) {
            fuel.addView(field("R$/km mínimo (abaixo disso sugere valor)", prefs.minRpk) { prefs.minRpk = it }, lp(12))
        }
        return fuel
    }

    // ------------------------------------------------------------------ configurações
    private fun buildConfig() {
        val calc = card()
        calc.addView(title("Cálculo", "Usado quando o app de corridas não mostra o tempo"))
        calc.addView(field("Velocidade média (km/h)", prefs.avgSpeed) { prefs.avgSpeed = it.coerceIn(5f, 120f) }, lp(12))
        body.addView(calc, lp(8))

        val c = card()
        c.addView(title("Atualizações", "Versão instalada: ${Updater.currentName(this)}"))
        if (!Updater.enabled) {
            c.addView(tx("Esta cópia foi compilada fora do GitHub, então não sabe onde procurar versões novas.", 13.5f, MUTED), lp(10))
            body.addView(c, lp(8))
            return
        }
        c.addView(tx("O app confere sozinho a cada poucas horas e avisa com uma notificação quando sai versão nova. Tocou, ele baixa e instala por cima, sem perder suas configurações.", 13.5f, MUTED), lp(10))
        val status = tx("", 13.5f, MUTED)
        lateinit var btn: TextView
        btn = button("Verificar agora", true) {
            btn.isEnabled = false
            status.setTextColor(MUTED)
            status.text = "Procurando…"
            Updater.checkAsync(this, notify = false, force = true) { rel, err ->
                btn.isEnabled = true
                when {
                    rel != null -> {
                        status.setTextColor(GREEN)
                        status.text = "Versão ${rel.versionName} disponível!"
                        AlertDialog.Builder(this)
                            .setTitle("Nova versão ${rel.versionName}")
                            .setMessage(if (rel.notes.isNotEmpty()) rel.notes.take(800) else "Quer atualizar agora?")
                            .setPositiveButton("Atualizar") { _, _ -> startUpdate(rel.apkUrl, rel.versionName) }
                            .setNegativeButton("Depois", null)
                            .show()
                    }
                    err -> { status.setTextColor(AMBER); status.text = "Sem conexão com o GitHub. Tente de novo." }
                    else -> { status.setTextColor(MUTED); status.text = "Você já está na versão mais nova." }
                }
            }
        }
        c.addView(btn, lp(14))
        c.addView(status, lp(10))
        body.addView(c, lp(8))
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
        h.postDelayed({ if (homeShown) updateStatus() }, 500)
        h.postDelayed({ if (homeShown) updateStatus() }, 1500)
    }

    private fun updateStatus() {
        val enabled = Power.isEnabled(this)
        val on = RouteService.running
        val stuck = enabled && !on
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (stuck) RED else if (on) GREEN else AMBER)
        }
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
