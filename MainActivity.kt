package com.jahseh.adsxdiamantes

import android.content.Intent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
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
    private lateinit var btnAnuncio: Button // Made btnAnuncio a class member
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var userUID: String

    companion object {
        const val PREFS_USER = "UserPrefs"
        const val KEY_USER_UID = "userUID"
        const val KEY_ANUNCIOS_VISTOS = "anunciosVistosCount"
        const val KEY_LAST_AD_RESET_DATE = "lastAdResetDate" // For daily reset
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userUID = getOrCreateUserUID()
        Log.d("MainActivity", "User UID: $userUID") // For debugging

        loadAnunciosVistosState() // Load anunciosVistos

        puntosText = findViewById(R.id.puntosText)
        loadInitialPoints() // Load points after UID is set and before UI might need it

        webView = findViewById(R.id.webview)
        btnAnuncio = findViewById(R.id.btnVerAnuncio) // Initialize class member
        val btnRuleta = findViewById<Button>(R.id.btnRuleta)

        btnAnuncio.isEnabled = false // Disable initially

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
                if (::rewardedAd.isInitialized) { // Check if ad object exists
                    rewardedAd.show(this) { rewardItem: RewardItem ->
                        puntos += 15
                        anunciosVistos++
                        saveAnunciosVistosState() // Save after incrementing
                        actualizarPuntos()
                        // Ad shown, disable button and try to load a new one
                        btnAnuncio.isEnabled = false
                        loadRewardedAd()
                    }
                } else {
                     // Ad not initialized, should ideally not happen if button is disabled
                     // until ad is loaded. Log this unexpected state.
                    Log.w("MainActivity", "Attempted to show ad, but rewardedAd is not initialized.")
                    btnAnuncio.isEnabled = false // Ensure it's disabled
                    loadRewardedAd() // Try to load an ad
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
        // puntosText.text = "🎮 Puntos: $puntos" // Replaced by actualizarPuntosText
        actualizarPuntosText()
        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(userUID) // Use the unique user ID
            .setValue(mapOf("puntos" to puntos))
    }

    private fun getOrCreateUserUID(): String {
        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
        var uid = prefs.getString(KEY_USER_UID, null)
        if (uid == null) {
            uid = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_UID, uid).apply()
        }
        return uid
    }
     private fun loadInitialPoints() {
        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(userUID)
            .child("puntos")
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    puntos = snapshot.getValue(Int::class.java) ?: 0
                    actualizarPuntosText() // Update UI with loaded points
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e("MainActivity", "Failed to load initial points: ${error.message}")
                    puntos = 0 // Default to 0 if error
                    actualizarPuntosText()
                }
            })
    }

    private fun actualizarPuntosText() {
        puntosText.text = getString(R.string.puntos_format, puntos)
    }

    private fun loadAnunciosVistosState() {
        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
        // Basic persistence: load the count.
        // Daily reset logic would go here. For now, it's cumulative.
        anunciosVistos = prefs.getInt(KEY_ANUNCIOS_VISTOS, 0)

        // --- Optional Daily Reset Logic (Example) ---
        // val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        // val lastResetDate = prefs.getString(KEY_LAST_AD_RESET_DATE, "")
        // if (today != lastResetDate) {
        //     anunciosVistos = 0
        //     prefs.edit().putInt(KEY_ANUNCIOS_VISTOS, anunciosVistos)
        //                  .putString(KEY_LAST_AD_RESET_DATE, today)
        //                  .apply()
        //     Log.d("MainActivity", "Anuncios vistos reset for new day.")
        // }
        // --- End Optional Daily Reset Logic ---
    }

    private fun saveAnunciosVistosState() {
        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ANUNCIOS_VISTOS, anunciosVistos).apply()
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-8378428271014065/6238227369", adRequest, object :
            RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                btnAnuncio.isEnabled = true // Enable button when ad is loaded
                Log.d("MainActivity", "Rewarded Ad loaded successfully.")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e("MainActivity", "Rewarded Ad failed to load: ${loadAdError.message}")
                btnAnuncio.isEnabled = false // Ensure button is disabled if ad fails to load
                // Optionally, you could try to load another ad here, or inform the user.
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}
