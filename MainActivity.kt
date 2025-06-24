package com.jahseh.adsxdiamantes

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var puntosText: TextView
    private var puntos = 0
    private var anunciosVistos = 0
    private val maxAnuncios = 30
    private lateinit var rewardedAd: RewardedAd
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        puntosText = findViewById(R.id.puntosText)
        webView = findViewById(R.id.webview)
        val btnAnuncio = findViewById<Button>(R.id.btnVerAnuncio)
        val btnRuleta = findViewById<Button>(R.id.btnRuleta)

        mediaPlayer = MediaPlayer.create(this, R.raw.fondo)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

        MobileAds.initialize(this)
        loadRewardedAd()

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("file:///android_asset/index.html")

        btnAnuncio.setOnClickListener {
            if (anunciosVistos < maxAnuncios) {
                if (::rewardedAd.isInitialized) {
                    rewardedAd.show(this) { rewardItem: RewardItem ->
                        puntos += 15
                        anunciosVistos++
                        actualizarPuntos()
                    }
                }
            } else {
                val sonido = MediaPlayer.create(this, R.raw.alerta)
                sonido.start()
            }
        }

        btnRuleta.setOnClickListener {
            startActivity(Intent(this, RuletaActivity::class.java))
        }
    }

    private fun actualizarPuntos() {
        puntosText.text = "🎮 Puntos: $puntos"
        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child("usuario_demo")
            .setValue(mapOf("puntos" to puntos))
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-8378428271014065/6238227369", adRequest, object :
            RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }

            override fun onAdFailedToLoad(p0: LoadAdError) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}
