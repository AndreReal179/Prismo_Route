package com.rotaguru

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização automática pelo GitHub.
 *
 * Fluxo:
 *  1. Cada push na branch principal gera um Release no GitHub com o APK (ver .github/workflows/build.yml).
 *     A tag tem o formato  v2.5.37-b1037  → o número depois do "-b" é o versionCode.
 *  2. O app consulta  api.github.com/repos/<repo>/releases/latest  (ao abrir, quando o serviço liga e a
 *     cada ~3 h em segundo plano). Se o versionCode do release for maior que o instalado, mostra uma
 *     notificação "Nova versão disponível".
 *  3. Tocar na notificação abre o app, baixa o APK e instala por cima com o PackageInstaller
 *     (mesma assinatura rotaguru.jks, então não precisa desinstalar e não perde nada).
 */
object Updater {
    /** "dono/repositorio" — preenchido na compilação do GitHub Actions (github.repository). */
    val REPO: String get() = BuildConfig.GH_REPO

    private const val CHANNEL = "rotaguru_update"
    private const val NOTIF_ID = 1002
    private const val JOB_ID = 4201
    private const val CHECK_EVERY_MS = 3 * 60 * 60 * 1000L     // checagem em segundo plano
    private const val MIN_GAP_MS = 20 * 60 * 1000L              // não consulta o GitHub mais que isso

    const val ACTION_UPDATE = "com.rotaguru.action.ATUALIZAR"
    const val EXTRA_URL = "url"
    const val EXTRA_VERSION = "versao"
    const val EXTRA_CODE = "codigo"
    const val ACTION_INSTALL_STATUS = "com.rotaguru.action.INSTALL_STATUS"

    data class Release(val versionName: String, val versionCode: Int, val apkUrl: String, val notes: String)

    private val main = Handler(Looper.getMainLooper())

    val enabled get() = REPO.contains('/')

    @Suppress("DEPRECATION")
    fun currentCode(ctx: Context): Int {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }

    fun currentName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    // ------------------------------------------------------------------ consulta ao GitHub

    /** Última versão vista no GitHub e último erro (para mostrar na aba Atualizações). */
    @Volatile var lastSeen: Release? = null
    @Volatile var lastError: String? = null

    private val TAG_RX = Regex("v([0-9.]+)-b(\\d+)$")

    /**
     * Busca o último release (rede — chamar fora da thread principal).
     * Tenta a API do GitHub; se falhar (limite de consultas, etc.) usa o link público
     * github.com/<repo>/releases/latest. Lança exceção com a explicação se nada der certo.
     */
    fun fetchLatest(): Release {
        check(enabled) { "Repositório não configurado" }
        val apiErr = try {
            return fetchApi()
        } catch (t: Throwable) { t.message ?: t.javaClass.simpleName }
        try {
            return fetchPublic()
        } catch (t: Throwable) {
            throw IllegalStateException("GitHub: $apiErr / ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 15_000
        useCaches = false
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "PrismoRoute-Updater")
    }

    private fun fetchApi(): Release {
        val conn = open("https://api.github.com/repos/$REPO/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = conn.responseCode
            check(code == 200) { "API respondeu $code" }
            val js = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = js.optString("tag_name")
            val m = TAG_RX.find(tag) ?: error("tag fora do padrão: $tag")
            val assets = js.optJSONArray("assets") ?: error("release sem arquivos")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) { url = a.optString("browser_download_url"); break }
            }
            if (url.isNullOrEmpty()) error("release $tag sem APK")
            return Release(m.groupValues[1], m.groupValues[2].toInt(), url, js.optString("body").trim())
        } finally {
            conn.disconnect()
        }
    }

    /** Plano B: github.com/<repo>/releases/latest redireciona para .../releases/tag/<tag>. */
    private fun fetchPublic(): Release {
        val conn = open("https://github.com/$REPO/releases/latest")
        conn.instanceFollowRedirects = false
        try {
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location") ?: error("site respondeu $code")
            val tag = loc.substringAfterLast("/tag/", "").substringBefore('?')
            val m = TAG_RX.find(tag) ?: error("nenhum release publicado")
            val name = m.groupValues[1]
            val url = "https://github.com/$REPO/releases/download/$tag/PrismoRoute-$name.apk"
            return Release(name, m.groupValues[2].toInt(), url, "")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Confere se há versão nova. Se houver e [notify] for true, mostra a notificação.
     * [onResult] roda na thread principal: (versão nova ou null, erro?). Detalhes em [lastSeen]/[lastError].
     */
    fun checkAsync(ctx: Context, notify: Boolean, force: Boolean = false, onResult: ((Release?, Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (!enabled) { onResult?.invoke(null, false); return }
        val sp = app.getSharedPreferences("rotaguru_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("last_check", 0) < MIN_GAP_MS) { onResult?.invoke(null, false); return }
        Thread {
            var rel: Release? = null
            var err = false
            try {
                val r = fetchLatest()
                lastSeen = r
                lastError = null
                sp.edit().putLong("last_check", now).apply()
                if (r.versionCode > currentCode(app)) rel = r
            } catch (t: Throwable) {
                err = true
                lastError = t.message ?: t.javaClass.simpleName
            }
            if (rel != null && notify) showAvailable(app, rel)
            main.post { onResult?.invoke(rel, err) }
        }.start()
    }

    // ------------------------------------------------------------------ notificação

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Atualizações do Prismo Route", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa quando sai uma versão nova do Prismo Route"
        }
        nm(ctx).createNotificationChannel(ch)
    }

    fun updateIntent(ctx: Context, r: Release) = Intent(ctx, MainActivity::class.java)
        .setAction(ACTION_UPDATE)
        .putExtra(EXTRA_URL, r.apkUrl)
        .putExtra(EXTRA_VERSION, r.versionName)
        .putExtra(EXTRA_CODE, r.versionCode)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun showAvailable(ctx: Context, r: Release) {
        val app = ctx.applicationContext
        if (!Notif.canPost(app)) return
        runCatching {
            ensureChannel(app)
            val pi = PendingIntent.getActivity(
                app, 10, updateIntent(app, r),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = if (r.notes.isNotEmpty()) r.notes.lineSequence().first().take(120) else "Toque para baixar e instalar"
            val n = Notification.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Nova versão do Prismo Route: ${r.versionName}")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(
                    (if (r.notes.isNotEmpty()) r.notes.take(400) + "\n\n" else "") + "Toque para atualizar agora."
                ))
                .setColor(0xFFFFC107.toInt())
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(app, android.R.drawable.stat_sys_download_done),
                    "Atualizar", pi
                ).build())
                .build()
            nm(app).notify(NOTIF_ID, n)
        }
    }

    fun showMessage(ctx: Context, title: String, text: String) {
        val app = ctx.applicationContext
        if (!Notif.canPost(app)) return
        runCatching {
            ensureChannel(app)
            val open = PendingIntent.getActivity(
                app, 11, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_rota)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(0xFFFFC107.toInt())
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm(app).notify(NOTIF_ID, n)
        }
    }

    fun cancelNotif(ctx: Context) { runCatching { nm(ctx.applicationContext).cancel(NOTIF_ID) } }

    // ------------------------------------------------------------------ checagem em segundo plano

    /** Agenda a checagem periódica (sobrevive a reinício do celular). Pode chamar várias vezes. */
    fun schedule(ctx: Context) {
        if (!enabled) return
        runCatching {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(CHECK_EVERY_MS)
                .setPersisted(true)
                .build()
            js.schedule(job)
        }
    }

    // ------------------------------------------------------------------ download + instalação

    fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "update").apply { mkdirs() }, "PrismoRoute.apk")

    /** Baixa o APK (rede — fora da thread principal). [progress] recebe 0..100 (ou -1 se tamanho desconhecido). */
    fun download(ctx: Context, url: String, progress: (Int) -> Unit): File {
        val out = apkFile(ctx)
        val tmp = File(out.parentFile, "PrismoRoute.part")
        var link = url
        var conn: HttpURLConnection
        var hops = 0
        while (true) {   // o GitHub redireciona para outro domínio; seguimos na mão para garantir
            conn = (URL(link).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "PrismoRoute-Updater")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 6) {
                link = conn.getHeaderField("Location") ?: break
                conn.disconnect(); hops++; continue
            }
            if (code != 200) { conn.disconnect(); throw IllegalStateException("Erro $code ao baixar a atualização") }
            break
        }
        try {
            val total = conn.contentLengthLong
            var done = 0L
            var last = -2
            conn.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        val p = if (total > 0) (done * 100 / total).toInt() else -1
                        if (p != last) { last = p; progress(p) }
                    }
                }
            }
            if (total > 0 && done != total) throw IllegalStateException("Download incompleto")
        } finally {
            conn.disconnect()
        }
        out.delete()
        if (!tmp.renameTo(out)) throw IllegalStateException("Não consegui salvar o arquivo")
        return out
    }

    /** O Android exige que o usuário libere "Instalar apps desconhecidos" para o Prismo Route uma vez. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(act: Activity) {
        act.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
        )
    }

    /** Instala o APK por cima do app atual. A resposta do sistema chega em [InstallReceiver]. */
    fun install(ctx: Context, apk: File) {
        val app = ctx.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            // Android 12+: depois da 1ª atualização feita pelo próprio app, as próximas nem pedem confirmação
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apk.inputStream().use { input ->
                s.openWrite("PrismoRoute.apk", 0, apk.length()).use { o ->
                    input.copyTo(o, 64 * 1024)
                    s.fsync(o)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                app, id,
                Intent(app, InstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS).setPackage(app.packageName),
                flags
            )
            s.commit(pi.intentSender)
        }
    }
}

/** Resposta do instalador do Android. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android pede a confirmação "Deseja atualizar este app?" — abre a tela do sistema
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // normalmente o app é reiniciado antes de chegar aqui; o aviso final fica com UpdatedReceiver
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "instalação cancelada"
                MainActivity.onInstallFailed(msg)
                Updater.showMessage(ctx, "Atualização não instalada", msg)
                Toast.makeText(ctx.applicationContext, "Atualização não instalada: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Roda depois que o app foi atualizado: avisa a versão nova, limpa o APK baixado e reagenda a checagem. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                runCatching { Updater.apkFile(ctx).delete() }
                Updater.showMessage(ctx, "Prismo Route atualizado", "Versão ${Updater.currentName(ctx)} instalada com sucesso")
                Updater.schedule(ctx)
            }
            Intent.ACTION_BOOT_COMPLETED -> Updater.schedule(ctx)
        }
    }
}

/** Checagem periódica em segundo plano (JobScheduler). */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Updater.checkAsync(this, notify = true) { _, _ -> params?.let { jobFinished(it, false) } }
        return true
    }

    override fun onStopJob(params: JobParameters?) = true
}
