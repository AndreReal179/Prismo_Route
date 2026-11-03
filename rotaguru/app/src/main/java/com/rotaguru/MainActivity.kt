package com.rotaguru

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var sample: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float = 15f, color: Int = 0xFFCCCCCC.toInt()) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            setPadding(0, dp(10), 0, dp(4))
        }

    private fun numberField(value: Float, onChange: (Float) -> Unit) = EditText(this).apply {
        setText(value.toString().replace('.', ','))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setTextColor(0xFFFFFFFF.toInt())
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

    private fun switch(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t; isChecked = checked; setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v); refreshSample() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(40))
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        col.addView(label("Rota Guru", 26f, 0xFFFFFFFF.toInt()))
        status = label("", 15f)
        col.addView(status)

        col.addView(Button(this).apply {
            text = "Ativar serviço de acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        col.addView(label("Classificação (R$ por km rodado)", 17f, 0xFFFFFFFF.toInt()))
        col.addView(label("RUIM abaixo de:  (padrão 1,5)  •  MÉDIO a partir daqui"))
        col.addView(numberField(prefs.medio) { prefs.medio = it })
        col.addView(label("ÓTIMO a partir de:  (padrão 2,0)"))
        col.addView(numberField(prefs.otimo) { prefs.otimo = it })
        col.addView(switch("Somar a distância até o passageiro", prefs.includePickup) { prefs.includePickup = it })

        col.addView(label("Aceite automático", 17f, 0xFFFFFFFF.toInt()))
        col.addView(switch("Aceitar sozinho as rotas ÓTIMAS", prefs.autoAccept) { prefs.autoAccept = it })
        col.addView(label("Nota mínima do passageiro para aceitar"))
        col.addView(numberField(prefs.minRating) { prefs.minRating = it })

        col.addView(label("Teste com os seus prints", 17f, 0xFFFFFFFF.toInt()))
        sample = label("", 15f, 0xFFFFFFFF.toInt())
        col.addView(sample)
        col.addView(label("Dica: na barra flutuante, toque = recolher/expandir, toque longo = abrir estas configurações.", 13f, 0xFF999999.toInt()))

        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(0xFF1B1B1B.toInt()) })
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = if (enabled) "✅ Serviço ativo — abra o app de corridas." else "⚠️ Serviço desativado — toque no botão abaixo."
        refreshSample()
    }

    private fun refreshSample() {
        if (!::sample.isInitialized) return
        val a = Evaluator.evaluate(Offer(39.0, 1.0, 2.1, 37.7, 38.0, 6.0, 4.97), prefs)
        val b = Evaluator.evaluate(Offer(25.5, 1.4, 3.1, null, null, null, 4.8), prefs)
        val best = Evaluator.best(listOf(a, b))
        sample.text = "Aline:\n" + Evaluator.headline(a) + "\n" + Evaluator.detail(a) +
            "\n\nManuel:\n" + Evaluator.headline(b) + "\n" + Evaluator.detail(b) +
            "\n\nMelhor: " + (if (best === a) "Aline" else "Manuel")
    }
}
