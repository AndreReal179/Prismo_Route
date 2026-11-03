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

    /** Mostra na tela o que o app está lendo quando não acha nenhuma corrida */
    var debug: Boolean
        get() = sp.getBoolean("debug", true)
        set(v) { sp.edit().putBoolean("debug", v).apply() }

    /** Nota mínima do passageiro para o aceite automático */
    var minRating: Float
        get() = sp.getFloat("minRating", 4.7f)
        set(v) { sp.edit().putFloat("minRating", v).apply() }
}
