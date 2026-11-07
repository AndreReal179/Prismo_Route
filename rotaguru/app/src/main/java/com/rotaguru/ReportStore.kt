package com.rotaguru

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

/** Uma rota analisada, como fica guardada no relatório. */
class Rec(
    val t: Long,            // quando foi vista (epoch ms)
    val route: Boolean,     // true = iFood (rota com coletas/entregas); false = InDrive
    val price: Double,      // valor bruto
    val km: Double?,        // km total (busca + corrida)
    val rpk: Double?,       // R$ por km rodado
    val orders: Int,        // nº de pedidos (entregas); 0 se não for rota
    val per: Double?,       // valor bruto por pedido (só rotas do iFood)
    val net: Double?,       // ganho líquido (preço - gasolina)
    val rating: Double?     // nota do passageiro
)

/**
 * Armazenamento do relatório.
 *
 * - Cada mês vira um arquivo (reports/2026-11.jsonl), uma rota por linha.
 * - Guarda o mês atual + os 2 anteriores (3 meses). Quando começa um 4º mês, o arquivo mais
 *   antigo é APAGADO de vez (não ocupa mais espaço).
 * - Uma oferta fica na tela por vários segundos e é relida várias vezes; aqui ela é registrada
 *   uma única vez (quando some da tela), usando a leitura mais exata que apareceu.
 */
class ReportStore(private val ctx: Context) {

    private class Pending(var r: Result, val firstUp: Long, val firstWall: Long, var lastUp: Long)

    private val pending = HashMap<String, Pending>()
    private val done = HashMap<String, Long>()

    companion object {
        const val KEEP_MONTHS = 3
        private const val DWELL_MS = 1200L          // tempo mínimo na tela para contar como "analisada"
        private const val GONE_MS = 3000L           // sumiu da tela há 3 s = registra
        private const val DEDUPE_MS = 5 * 60_000L   // mesma oferta dentro de 5 min não conta de novo
        private val LOCK = Any()
        private val NAME = Regex("""(\d{4})-(\d{2})\.jsonl""")

        private fun dir(ctx: Context): File = File(ctx.filesDir, "reports").also { it.mkdirs() }

        fun monthIdx(t: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = t }
            return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        }

        private fun fileFor(ctx: Context, t: Long): File {
            val c = Calendar.getInstance().apply { timeInMillis = t }
            return File(dir(ctx), String.format(Locale.US, "%04d-%02d.jsonl", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1))
        }

        /** Apaga os meses que passaram do limite (mês atual + 2 anteriores). */
        fun purge(ctx: Context) {
            synchronized(LOCK) {
                val cur = monthIdx(System.currentTimeMillis())
                dir(ctx).listFiles()?.forEach { f ->
                    val m = NAME.matchEntire(f.name) ?: return@forEach
                    val idx = m.groupValues[1].toInt() * 12 + m.groupValues[2].toInt() - 1
                    if (idx < cur - (KEEP_MONTHS - 1)) f.delete()
                }
            }
        }

        private fun JSONObject.dbl(k: String): Double? = optDouble(k).takeIf { !it.isNaN() }

        /** Lê todas as rotas guardadas (já descartando os meses vencidos), da mais antiga para a mais nova. */
        fun load(ctx: Context): List<Rec> {
            purge(ctx)
            val out = ArrayList<Rec>()
            synchronized(LOCK) {
                dir(ctx).listFiles()?.filter { NAME.matches(it.name) }?.forEach { f ->
                    f.forEachLine { line ->
                        runCatching {
                            val j = JSONObject(line)
                            out.add(
                                Rec(
                                    t = j.getLong("t"),
                                    route = j.optInt("r") == 1,
                                    price = j.getDouble("p"),
                                    km = j.dbl("k"),
                                    rpk = j.dbl("e"),
                                    orders = j.optInt("o"),
                                    per = j.dbl("d"),
                                    net = j.dbl("n"),
                                    rating = j.dbl("a")
                                )
                            )
                        }
                    }
                }
            }
            out.sortBy { it.t }
            return out
        }
    }

    private fun keyOf(r: Result): String {
        val o = r.offer
        val cents = Math.round(o.price * 100)
        // Rota (iFood): lista e popup mostram o mesmo valor. Corrida (InDrive): valor + nota do passageiro.
        return if (o.route) "R|$cents" else "D|$cents|${Math.round((o.rating ?: 0.0) * 100)}"
    }

    /** Chamado a cada leitura da tela com as ofertas visíveis. */
    fun observe(results: List<Result>) {
        val now = SystemClock.uptimeMillis()
        val wall = System.currentTimeMillis()
        for (r in results) {
            if (r.offer.price <= 0) continue
            val key = keyOf(r)
            val d = done[key]
            if (d != null && now - d < DEDUPE_MS) {   // já registrada: só renova enquanto estiver na tela
                done[key] = now
                continue
            }
            val p = pending[key]
            if (p == null) {
                pending[key] = Pending(r, now, wall, now)
            } else {
                p.lastUp = now
                if (!r.estimated || p.r.estimated) p.r = r   // leitura exata (detalhe) vale mais que a estimada (lista)
            }
        }
    }

    /** Registra as ofertas que já saíram da tela (force = registra todas, ex.: serviço parando). */
    fun flush(force: Boolean = false) {
        if (pending.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        var wrote = false
        val it = pending.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val p = e.value
            if (force || now - p.lastUp > GONE_MS) {
                it.remove()
                if (p.lastUp - p.firstUp >= DWELL_MS) {
                    commit(p)
                    done[e.key] = now
                    wrote = true
                }
            }
        }
        if (done.size > 300) done.entries.removeAll { now - it.value > DEDUPE_MS }
        if (wrote) purge(ctx)
    }

    private fun commit(p: Pending) {
        val r = p.r
        val o = r.offer
        val j = JSONObject()
        j.put("t", p.firstWall)
        j.put("r", if (o.route) 1 else 0)
        j.put("p", o.price)
        r.totalKm?.takeIf { it.isFinite() }?.let { j.put("k", it) }
        r.effRpk?.takeIf { it.isFinite() }?.let { j.put("e", it) }
        if (o.route) j.put("o", o.entregas)
        r.perOrder?.takeIf { it.isFinite() }?.let { j.put("d", it) }
        r.net?.takeIf { it.isFinite() }?.let { j.put("n", it) }
        o.rating?.takeIf { it.isFinite() }?.let { j.put("a", it) }
        synchronized(LOCK) {
            runCatching { fileFor(ctx, p.firstWall).appendText(j.toString() + "\n") }
        }
    }
}
