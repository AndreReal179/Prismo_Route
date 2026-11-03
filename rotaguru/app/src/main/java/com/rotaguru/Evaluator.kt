package com.rotaguru

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

enum class Level(val label: String, val color: Int) {
    RUIM("RUIM", 0xFFC62828.toInt()),
    MEDIO("MÉDIO", 0xFFEF8F00.toInt()),
    OTIMO("ÓTIMO", 0xFF2E7D32.toInt())
}

class Offer(
    val price: Double,
    val rpkApp: Double?,       // "R$ 1,4/km" mostrado pelo app (já arredondado)
    val pickupKm: Double?,     // "~2,0 km" até o passageiro
    val tripKm: Double?,       // km da corrida (só na tela de detalhe)
    val tripMin: Double?,
    val pickupMin: Double?,
    val rating: Double?,
    val acceptNode: AccessibilityNodeInfo? = null,
    val cardNode: AccessibilityNodeInfo? = null,
    val tapX: Int = 0,
    val tapY: Int = 0,
    val headBottom: Int? = null   // y (tela) da base da linha "R$ x/km" do card, p/ posicionar o selo
)

class Result(
    val offer: Offer,
    val effRpk: Double?,       // R$ por km rodado (corrida + busca)
    val level: Level?,
    val tripUsed: Double?,
    val estimated: Boolean,    // true = km da corrida estimado a partir do R$/km do app
    val rph: Double?           // R$ por hora (só se tempos conhecidos)
)

object Evaluator {

    fun evaluate(o: Offer, p: Prefs): Result {
        val trip: Double? = o.tripKm ?: o.rpkApp?.takeIf { it > 0 }?.let { o.price / it }
        val estimated = o.tripKm == null
        val pickup = if (p.includePickup) (o.pickupKm ?: 0.0) else 0.0
        val eff: Double? = trip?.let {
            val total = it + pickup
            if (total > 0) o.price / total else null
        }
        val level: Level? = eff?.let {
            when {
                it >= p.otimo -> Level.OTIMO
                it >= p.medio -> Level.MEDIO
                else -> Level.RUIM
            }
        }
        val tm = o.tripMin
        val pm = o.pickupMin
        val rph = if (tm != null && pm != null && tm + pm > 0) o.price / ((tm + pm) / 60.0) else null
        return Result(o, eff, level, trip, estimated, rph)
    }

    fun best(results: List<Result>): Result? =
        results.filter { it.effRpk != null }.maxByOrNull { it.effRpk!! }

    private val BR = Locale("pt", "BR")

    fun money(v: Double): String = String.format(BR, "R$ %.2f", v).replace(",00", "")

    fun km(v: Double): String = String.format(BR, "%.1f km", v)

    fun headline(r: Result): String {
        val lvl = r.level?.label ?: "?"
        val eff = r.effRpk?.let { String.format(BR, "R$ %.2f/km", it) } ?: "—"
        return "$lvl  ${money(r.offer.price)}  •  $eff"
    }

    /** Versão curta para o selo em cima de cada card. */
    fun compact(r: Result): String {
        val parts = mutableListOf<String>()
        r.tripUsed?.let { parts.add((if (r.estimated) "~" else "") + String.format(BR, "%.0f km", it)) }
        r.offer.pickupKm?.let { parts.add("busca " + String.format(BR, "%.1f", it)) }
        r.rph?.let { parts.add(String.format(BR, "R$ %.0f/h", it)) }
        r.offer.rating?.let { parts.add("★" + String.format(BR, "%.2f", it)) }
        return parts.joinToString("  ·  ")
    }

    fun perKm(r: Result): String = r.effRpk?.let { String.format(BR, "R$ %.2f/km", it) } ?: "—"

    fun detail(r: Result): String {
        val parts = mutableListOf<String>()
        r.tripUsed?.let { parts.add((if (r.estimated) "corrida ~" else "corrida ") + km(it)) }
        r.offer.pickupKm?.let { parts.add("busca " + km(it)) }
        r.rph?.let { parts.add(String.format(BR, "R$ %.0f/h", it)) }
        r.offer.rating?.let { parts.add("★ " + String.format(BR, "%.2f", it)) }
        return parts.joinToString("  •  ")
    }
}
