package com.rotaguru

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rotaguru", Context.MODE_PRIVATE)

    /** R$/km a partir do qual a rota é "Médio" */
    var medio: Float
        get() = sp.getFloat("medio", 1.5f)
        set(v) { sp.edit().putFloat("medio", v).apply() }

    /** R$/km a partir do qual a rota é "Ótimo" */
    var otimo: Float
        get() = sp.getFloat("otimo", 2.0f)
        set(v) { sp.edit().putFloat("otimo", v).apply() }

    /** Soma a distância até o passageiro no cálculo (recomendado) */
    var includePickup: Boolean
        get() = sp.getBoolean("pickup", true)
        set(v) { sp.edit().putBoolean("pickup", v).apply() }

    /** Aceita sozinho as rotas classificadas como Ótimo */
    var autoAccept: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) { sp.edit().putBoolean("auto", v).apply() }

    /** Nota mínima do passageiro para o aceite automático */
    var minRating: Float
        get() = sp.getFloat("minRating", 4.7f)
        set(v) { sp.edit().putFloat("minRating", v).apply() }

    /** Preço da gasolina (R$ por litro) */
    var fuelPrice: Float
        get() = sp.getFloat("fuelPrice", 6.0f)
        set(v) { sp.edit().putFloat("fuelPrice", v).apply() }

    /** Consumo do carro (km por litro) */
    var kmPerLiter: Float
        get() = sp.getFloat("kmPerLiter", 10.0f)
        set(v) { sp.edit().putFloat("kmPerLiter", v).apply() }

    /** R$/km mínimo: abaixo disso o app sugere um valor para pedir ao passageiro */
    var minRpk: Float
        get() = sp.getFloat("minRpk", 1.7f)
        set(v) { sp.edit().putFloat("minRpk", v).apply() }

    /** Aceite automático: só aceita corridas com R$/km (total, busca + viagem) a partir deste valor */
    var autoMinRpk: Float
        get() = sp.getFloat("autoMinRpk", 2.0f)
        set(v) { sp.edit().putFloat("autoMinRpk", v).apply() }

    /** Velocidade média (km/h) usada p/ estimar o tempo quando o app de corridas não mostra */
    var avgSpeed: Float
        get() = sp.getFloat("avgSpeed", 35f)
        set(v) { sp.edit().putFloat("avgSpeed", v).apply() }

    /** Posição do botão flutuante (-1 = padrão) */
    var fabX: Int
        get() = sp.getInt("fabX", -1)
        set(v) { sp.edit().putInt("fabX", v).apply() }
    var fabY: Int
        get() = sp.getInt("fabY", -1)
        set(v) { sp.edit().putInt("fabY", v).apply() }

    /** Pacotes dos apps de corridas/rotas (descobertos ao ler a primeira oferta de cada um), separados por "|" */
    var ridePkg: String
        get() = sp.getString("ridePkg", "") ?: ""
        set(v) { sp.edit().putString("ridePkg", v).apply() }

    val ridePkgs: Set<String>
        get() = ridePkg.split("|").filter { it.isNotEmpty() }.toSet()

    fun addRidePkg(p: String) {
        val cur = ridePkgs
        if (p.isNotEmpty() && p !in cur) ridePkg = (cur + p).joinToString("|")
    }

    /** Busca até este valor (km) = verde */
    var pickupGood: Float
        get() = sp.getFloat("pickupGood", 2.0f)
        set(v) { sp.edit().putFloat("pickupGood", v).apply() }

    /** Busca a partir deste valor (km) = vermelho (entre os dois = laranja) */
    var pickupBad: Float
        get() = sp.getFloat("pickupBad", 4.0f)
        set(v) { sp.edit().putFloat("pickupBad", v).apply() }
}
