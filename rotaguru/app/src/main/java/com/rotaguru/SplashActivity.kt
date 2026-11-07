package com.rotaguru

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView

/** Tela de carregamento do Prismo Route: mostra a arte por 5 segundos e abre o app. */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val open = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val img = ImageView(this).apply {
            setImageResource(R.drawable.splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
        }
        @Suppress("DEPRECATION")
        img.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(img)
        img.animate().alpha(1f).setDuration(500).start()
        handler.postDelayed(open, DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(open)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // voltar durante o carregamento só fecha, sem abrir o app depois
        handler.removeCallbacks(open)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        const val DURATION_MS = 5_000L
    }
}
