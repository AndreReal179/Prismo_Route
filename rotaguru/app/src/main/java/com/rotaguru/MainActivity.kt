package com.rotaguru

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.pm.PackageManager
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
    private lateinit var sampleBox: LinearLayout

    private val BG = 0xFF0E1014.toInt()
    private val CARD = 0xFF171A21.toInt()
    private val LINE = 0xFF262B35.toInt()
    private val INPUT = 0xFF0E1014.toInt()
    private val TXT = 0xFFFFFFFF.toInt()
    private val MUTED = 0xFF9AA0AC.toInt()
    private val GREEN = 0xFF4CAF50.toInt()
    private val AMBER = 0xFFFFB300.toInt()
    private val GOLD = 0xFFFFC107.toInt()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(36), dp(16), dp(40))
            setBackgroundColor(BG)
        }

        // ---------- cabeçalho
        col.addView(tx("Rota Guru", 30f, TXT, true))
        col.addView(tx("Avaliador de corridas em tempo real", 14f, MUTED), lp(2))

        // ---------- status do serviço
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
        activateBtn = button("Ativar acessibilidade", true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        status.addView(activateBtn, lp(14))
        // "Ativar": religa o serviço quando o app fechou/travou
        status.addView(button("Ativar", true) { restartService() }, lp(8))
        col.addView(status, lp(18))

        // ---------- como começar
        val guide = stack(
            step(1, "Abra o Rota Guru", "Você já está aqui. Deixe o app instalado."),
            step(2, "Ative a acessibilidade",
                "Toque em \"Ativar acessibilidade\", abra \"Apps instalados\" (ou \"Serviços baixados\"), escolha Rota Guru e ligue a chave."),
            step(3, "Ajuste seus valores",
                "Preencha gasolina, consumo e os R$/km mais abaixo nesta tela."),
            step(4, "Abra o app de corridas",
                "O painel aparece em cada corrida. Na tela de detalhe: toque curto recolhe, toque longo volta para cá.")
        )
        col.addView(collapsible("Como começar", "4 passos rápidos", true, guide), lp(12))

        // ---------- configuração restrita
        val restricted = stack(
            tx("No Android 13 ou mais novo, apps instalados fora da Play Store vêm com a chave bloqueada " +
                "(aparece \"Configuração restrita\"). Para liberar:", 13f, MUTED),
            step(1, "Tente ativar uma vez",
                "Toque em \"Ativar acessibilidade\" e na chave do Rota Guru. O aviso de configuração restrita aparece."),
            step(2, "Abra as informações do app",
                "Toque no botão abaixo (ou segure o ícone do app → Informações do app)."),
            step(3, "Permita as configurações restritas",
                "Toque nos 3 pontinhos ⋮ no canto superior direito e escolha \"Permitir configurações restritas\". Confirme com senha ou digital."),
            step(4, "Volte e ative",
                "Acessibilidade → Apps instalados → Rota Guru → ligar."),
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
            }
        )
        col.addView(
            collapsible("Não consigo ativar", "Chave cinza ou \"Configuração restrita\"", false, restricted),
            lp(12)
        )

        // ---------- classificação
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
        col.addView(cls, lp(12))

        // ---------- gasolina e lucro
        val fuel = card()
        fuel.addView(title("Gasolina e lucro", "Gasto = km total ÷ consumo × preço do litro"))
        fuel.addView(
            pair(
                field("Gasolina (R$/litro)", prefs.fuelPrice) { prefs.fuelPrice = it },
                field("Consumo (km/litro)", prefs.kmPerLiter) { prefs.kmPerLiter = it }
            ), lp(14)
        )
        fuel.addView(field("R$/km mínimo (abaixo disso sugere valor)", prefs.minRpk) { prefs.minRpk = it }, lp(12))
        col.addView(fuel, lp(12))

        // ---------- cores da busca
        val pick = card()
        pick.addView(title("Cores da busca", "Distância até o passageiro"))
        pick.addView(
            pair(
                field("Verde até (km)", prefs.pickupGood) { prefs.pickupGood = it },
                field("Vermelho a partir de (km)", prefs.pickupBad) { prefs.pickupBad = it }
            ), lp(14)
        )
        col.addView(pick, lp(12))

        // ---------- aceite automático
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
        col.addView(auto, lp(12))

        // ---------- simulação
        val sim = card()
        sim.addView(title("Simulação", "Veja como ficam duas corridas com seus valores"))
        sampleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sim.addView(sampleBox, lp(12))
        col.addView(sim, lp(12))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) GREEN else AMBER)
        }
        val stuck = enabled && !RouteService.running
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (stuck) 0xFFEF5350.toInt() else if (enabled) GREEN else AMBER)
        }
        statusTitle.text = if (stuck) "Serviço parado" else if (enabled) "Serviço ativo" else "Serviço desativado"
        statusSub.text = if (stuck) "Toque em \"Ativar\" para reiniciar o serviço."
        else if (enabled) "Abra o app de corridas e veja o painel nas corridas."
        else "Ative a acessibilidade para o painel aparecer."
        activateBtn.text = if (enabled) "Gerenciar acessibilidade" else "Ativar acessibilidade"
        styleButton(activateBtn, !enabled)
        refreshSample()
    }

    /**
     * Reinicia o serviço de acessibilidade. Com a permissão WRITE_SECURE_SETTINGS (concedida uma única vez
     * por ADB) o app desliga e liga a si mesmo; sem ela, explica e abre a tela de acessibilidade.
     */
    private fun restartService() {
        val comp = ComponentName(this, RouteService::class.java).flattenToString()
        val granted = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val ok = runCatching {
                val cur = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val others = cur.split(':').filter { it.isNotEmpty() && it != comp }
                Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching {
                        Settings.Secure.putString(
                            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            (others + comp).joinToString(":")
                        )
                        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({ onResume() }, 800)
                }, 600)
            }.isSuccess
            Toast.makeText(this, if (ok) "Reiniciando o serviço…" else "Não foi possível reiniciar", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Ativar com 1 toque")
            .setMessage(
                "Para este botão reiniciar o serviço sozinho, libere uma vez pelo computador " +
                    "(celular com depuração USB):\n\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                    "Enquanto isso, ligue/desligue a chave do Rota Guru na tela de acessibilidade."
            )
            .setPositiveButton("Abrir acessibilidade") { _, _ ->
                val opened = runCatching {
                    startActivity(
                        Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                            .putExtra("android.intent.extra.COMPONENT_NAME", comp)
                    )
                }.isSuccess
                if (!opened) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Fechar", null)
            .show()
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
        Evaluator.suggestLine(r)?.let { box.addView(tx(it, 12.5f, GOLD, true), lp(2)) }
        sampleBox.addView(box, lp(if (sampleBox.childCount == 0) 0 else 8))
    }

    private fun refreshSample() {
        if (!::sampleBox.isInitialized) return
        sampleBox.removeAllViews()
        sampleRow("Aline", Evaluator.evaluate(Offer(39.0, 1.0, 2.1, 37.7, 38.0, 6.0, 4.97), prefs))
        sampleRow("Manuel", Evaluator.evaluate(Offer(25.5, 1.4, 3.1, null, null, null, 4.8), prefs))
    }
}
