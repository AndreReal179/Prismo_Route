package com.rotaguru

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lê a árvore de acessibilidade do app de corridas e extrai as ofertas.
 *
 * Tela de lista  : vários cards, cada um com "R$ x/km", "~y km", "R$ preço".
 * Tela de detalhe: botão "Aceitar por R$ X" + balões do mapa (tempo / km).
 */
object Parser {

    private class T(val text: String, val rect: Rect, val node: AccessibilityNodeInfo)

    private val RPK = Regex("""R\$\s*([\d.,]+)\s*/\s*km""")
    private val PICK = Regex("""~\s*([\d.,]+)\s*km""")
    private val PRICE = Regex("""R\$\s*([\d.,]+)""")
    private val ACCEPT = Regex("""Aceitar por\s*R\$\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val BUBBLE = Regex("""(\d+)\s*min\D{0,6}([\d.,]+)\s*km""", RegexOption.DOT_MATCHES_ALL)
    private val ONLY_KM = Regex("""^\s*([\d.,]+)\s*km\s*$""")
    private val RATING = Regex("""^\s*(\d[.,]\d{1,2})\s*(\(\d+\))?\s*$""")

    fun num(s: String): Double? {
        var t = s.trim().trimEnd('.', ',')
        if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
        return t.toDoubleOrNull()
    }

    private val ONLY_MIN = Regex("""^\s*(\d+)\s*min\.?\s*$""", RegexOption.IGNORE_CASE)

    /** hidden = true: inclui também nós "não visíveis" e lê text + contentDescription (balões do mapa). */
    private fun flatten(n: AccessibilityNodeInfo?, out: MutableList<T>, depth: Int = 0, hidden: Boolean = false) {
        if (n == null || depth > 40) return
        if (hidden || n.isVisibleToUser) {
            val texts = if (hidden) listOfNotNull(n.text, n.contentDescription) else listOfNotNull(n.text ?: n.contentDescription)
            val r = Rect()
            n.getBoundsInScreen(r)
            for (c in texts.map { it.toString().trim() }.filter { it.isNotBlank() }.distinct()) {
                out.add(T(c, r, n))
            }
        }
        for (i in 0 until n.childCount) flatten(n.getChild(i), out, depth + 1, hidden)
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var guard = 0
        while (cur != null && guard++ < 10) {
            if (cur.isClickable) return cur
            cur = cur.parent
        }
        return null
    }

    /** Primeiro "R$ n" que NÃO seja seguido de "/km". */
    private fun firstPrice(text: String): Double? {
        for (m in PRICE.findAll(text)) {
            val after = text.substring(m.range.last + 1).trimStart()
            if (after.startsWith("/km") || after.startsWith("/ km")) continue
            return num(m.groupValues[1])
        }
        return null
    }

    fun parse(root: AccessibilityNodeInfo): List<Offer> {
        val items = mutableListOf<T>()
        flatten(root, items)
        if (items.isEmpty()) return emptyList()

        // ---------- Tela de detalhe ----------
        val acc = items.firstOrNull { ACCEPT.containsMatchIn(it.text) }
        if (acc != null) {
            val price = num(ACCEPT.find(acc.text)!!.groupValues[1]) ?: return emptyList()
            val all = items.joinToString("\n") { it.text }
            val rpk = RPK.find(all)?.let { num(it.groupValues[1]) }
            var pickup = PICK.find(all)?.let { num(it.groupValues[1]) }

            // fonte ampla: inclui nós não visíveis e contentDescription (balões do mapa)
            val wide = mutableListOf<T>()
            flatten(root, wide, hidden = true)
            val src = if (wide.isEmpty()) items else wide
            val screenW = Rect().also { root.getBoundsInScreen(it) }.width().coerceAtLeast(1)

            // balões do mapa: "6 min 2,1 km" e "38 min 37,7 km"
            val bubbles = src.flatMap { t ->
                BUBBLE.findAll(t.text).mapNotNull { m ->
                    val min = m.groupValues[1].toDoubleOrNull()
                    val km = num(m.groupValues[2])
                    if (min != null && km != null) Pair(min, km) else null
                }.toList()
            }
            var tripKm: Double? = null
            var tripMin: Double? = null
            var pickupMin: Double? = null
            if (bubbles.isNotEmpty()) {
                val trip = bubbles.maxByOrNull { it.second }!!
                tripKm = trip.second
                tripMin = trip.first
                val other = bubbles.firstOrNull { it !== trip }
                if (other != null) {
                    pickupMin = other.first
                    // km exato da rota até o passageiro (mapa) tem prioridade sobre o "~x km" arredondado
                    pickup = other.second
                }
            }

            // balões separados em "N min" e "x km" (ou só o km, como o balão azul da busca)
            if (tripKm == null) {
                var kms = src.mapNotNull { ONLY_KM.find(it.text)?.let { m -> num(m.groupValues[1]) } }
                if (pickup != null) kms = kms.filter { Math.abs(it - pickup!!) > 0.05 }
                if (kms.isNotEmpty()) tripKm = kms.maxOrNull()
            }
            // "9 min." na coluna esquerda do card = tempo até o passageiro; o resto = viagem
            val mins = src.mapNotNull { t ->
                ONLY_MIN.find(t.text)?.let { m -> m.groupValues[1].toDoubleOrNull()?.let { v -> Pair(v, t.rect.left < screenW * 0.3) } }
            }
            if (pickupMin == null) pickupMin = mins.firstOrNull { it.second }?.first
            if (tripMin == null) tripMin = mins.filter { !it.second }.maxOfOrNull { it.first }
            val rating = items.firstNotNullOfOrNull { RATING.find(it.text)?.let { m -> num(m.groupValues[1]) } }
            return listOf(
                Offer(price, rpk, pickup, tripKm, tripMin, pickupMin, rating, acceptNode = acc.node)
            )
        }

        // ---------- Tela de lista ----------
        val heads = items.filter { RPK.containsMatchIn(it.text) }.sortedBy { it.rect.top }
        val offers = mutableListOf<Offer>()
        heads.forEachIndexed { i, h ->
            val top = h.rect.top - 2
            val bottom = heads.getOrNull(i + 1)?.rect?.top ?: Int.MAX_VALUE
            val region = items.filter { it.rect.top in top until bottom }
            val joined = region.joinToString("\n") { it.text }

            val price = firstPrice(joined) ?: return@forEachIndexed
            val rpk = RPK.find(joined)?.let { num(it.groupValues[1]) }
            val pickup = PICK.find(joined)?.let { num(it.groupValues[1]) }
            val rating = region.firstNotNullOfOrNull { RATING.find(it.text)?.let { m -> num(m.groupValues[1]) } }

            // ponto de toque de reserva: meio do card, a partir da coluna do texto
            val cardBottom = region.maxOfOrNull { it.rect.bottom } ?: (h.rect.bottom + 200)
            offers.add(
                Offer(
                    price, rpk, pickup, null, null, null, rating,
                    cardNode = clickableAncestor(h.node),
                    tapX = h.rect.left + 200,
                    tapY = (h.rect.top + cardBottom) / 2,
                    headTop = h.rect.top,
                    headBottom = h.rect.bottom
                )
            )
        }
        return offers
    }
}
