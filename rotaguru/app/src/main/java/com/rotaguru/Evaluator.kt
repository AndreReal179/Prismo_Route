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
    val rpkApp: Double?,       // "R$ 1,4/km" mostrado pelo app (já arredondado; inclui a busca)
    val pickupKm: Double?,     // "~2,0 km" até o passageiro
    val tripKm: Double?,       // km da corrida (só na tela de detalhe)
    val tripMin: Double?,
    val pickupMin: Double?,
    val rating: Double?,
    val acceptNode: AccessibilityNodeInfo? = null,
    val cardNode: AccessibilityNodeInfo? = null,
    val tapX: Int = 0,
    val tapY: Int = 0,
    val headTop: Int? = null,
    val headBottom: Int? = null   // y (tela) da base da linha "R$ x/km" do card, p/ posicionar o selo
)

class Result(
    val offer: Offer,
    val effRpk: Double?,       // R$ por km rodado (corrida + busca)
    val level: Level?,
    val tripUsed: Double?,
    val estimated: Boolean,    // true = km da corrida estimado a partir do R$/km do app
    val rph: Double?,          // R$ por hora (só se tempos conhecidos)
    val fuel: Double? = null,  // gasto estimado com gasolina (busca + corrida)
    val net: Double? = null,   // ganho líquido = preço - gasolina
    val suggest: Double? = null, // valor sugerido p/ pedir ao passageiro (null = preço já atende o mínimo)
    val totalKm: Double? = null,  // km total = busca + corrida
    val totalMin: Double? = null, // tempo total = busca + viagem (null se algum tempo for desconhecido)
    val destMin: Double? = null   // tempo da viagem até o destino final
)

object Evaluator {

    fun evaluate(o: Offer, p: Prefs): Result {
        val pickupKm = o.pickupKm ?: 0.0
        // O R$/km mostrado pelo app de corridas JÁ inclui a busca: preço / (R$/km) = km TOTAL.
        // Na tela de detalhe temos o km exato da corrida (mapa) e somamos a busca.
        val total: Double? = o.tripKm?.let { it + pickupKm }
            ?: o.rpkApp?.takeIf { it > 0 }?.let { o.price / it }
        val trip: Double? = o.tripKm ?: total?.let { (it - pickupKm).coerceAtLeast(0.0) }
        val estimated = o.tripKm == null
        val base: Double? = if (p.includePickup) total else trip
        val eff: Double? = base?.let { if (it > 0) o.price / it else null }
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

        // gasolina: km total (busca + corrida)
        val fuel: Double? = total?.let { it / p.kmPerLiter.coerceAtLeast(0.1f) * p.fuelPrice }
        val net = fuel?.let { o.price - it }

        // sugestão: km total (busca + corrida) x R$/km mínimo (ex.: 18 km x 1,70 = R$ 30,60)
        val suggest: Double? = total?.let {
            val need = Math.round(p.minRpk.toDouble() * it * 100) / 100.0
            if (need > 0 && o.price < need - 0.005) need else null
        }
        val totalMin = if (tm != null && pm != null) tm + pm else null
        return Result(o, eff, level, trip, estimated, rph, fuel, net, suggest, total, totalMin, tm)
    }

    /** "44 min total · 38 min destino" (só o que for conhecido); null se não há tempos. */
    fun timeText(r: Result): String? {
        val parts = mutableListOf<String>()
        r.totalMin?.let { parts.add(String.format(BR, "%.0f min total", it)) }
        r.destMin?.let { parts.add(String.format(BR, "%.0f min destino", it)) }
        return if (parts.isEmpty()) null else parts.joinToString("  ·  ")
    }

    /** km total com 1 casa (busca + corrida). */
    fun totalKmText(r: Result): String? = r.totalKm?.let {
        (if (r.estimated) "~" else "") + String.format(BR, "%.1f km total", it)
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

    const val GREEN = 0xFF4CAF50.toInt()
    const val AMBER = 0xFFFFB300.toInt()
    const val RED = 0xFFEF5350.toInt()

    /** Nota do passageiro: verde = ótima, laranja = ok, vermelho = baixa. */
    fun ratingColor(v: Double): Int = when {
        v >= 4.80 -> GREEN
        v >= 4.60 -> AMBER
        else -> RED
    }

    /** Distância de busca: perto = verde, média = laranja, longe = vermelho. */
    fun pickupColor(km: Double, p: Prefs): Int = when {
        km <= p.pickupGood -> GREEN
        km <= p.pickupBad -> AMBER
        else -> RED
    }

    fun netColor(r: Result): Int = when {
        r.net == null -> 0xFFB4B8C0.toInt()
        r.net <= 0 -> RED
        r.suggest != null -> AMBER
        else -> GREEN
    }

    fun fuelLine(r: Result): String =
        if (r.fuel == null || r.net == null) "" else
            "gasolina ${money(r.fuel)}  ·  líquido ${money(r.net)}"

    fun suggestLine(r: Result): String? = r.suggest?.let { "Peça ${money(it)} ao passageiro" }

    /** Versão curta para o selo em cima de cada card. */
    fun compact(r: Result): String {
        val parts = mutableListOf<String>()
        r.totalKm?.let { parts.add((if (r.estimated) "~" else "") + String.format(BR, "%.0f km", it)) }
        r.offer.pickupKm?.let { parts.add("busca " + String.format(BR, "%.1f", it)) }
        r.rph?.let { parts.add(String.format(BR, "R$ %.0f/h", it)) }
        r.offer.rating?.let { parts.add("★" + String.format(BR, "%.2f", it)) }
        return parts.joinToString("  ·  ")
    }

    fun perKm(r: Result): String = r.effRpk?.let { String.format(BR, "R$ %.2f/km", it) } ?: "—"

    fun detail(r: Result): String {
        val parts = mutableListOf<String>()
        r.totalKm?.let { parts.add((if (r.estimated) "total ~" else "total ") + km(it)) }
        r.offer.pickupKm?.let { parts.add("busca " + km(it)) }
        r.rph?.let { parts.add(String.format(BR, "R$ %.0f/h", it)) }
        r.offer.rating?.let { parts.add("★ " + String.format(BR, "%.2f", it)) }
        return parts.joinToString("  •  ")
    }
}
