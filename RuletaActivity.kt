package com.jahseh.adsxdiamantes

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class RuletaActivity : AppCompatActivity() {

    private lateinit var ruletaImg: ImageView
    private lateinit var txtPremio: TextView
    private var giros = 0
    private val maxGiros = 5
    private lateinit var sonidoGiro: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruleta)

        ruletaImg = findViewById(R.id.ruletaImg)
        txtPremio = findViewById(R.id.txtPremio)
        val btnGirar = findViewById<Button>(R.id.btnGirar)

        sonidoGiro = MediaPlayer.create(this, R.raw.giro)

        btnGirar.setOnClickListener {
            if (giros < maxGiros) {
                sonidoGiro.start()
                val premio = obtenerPremio()
                txtPremio.text = "🎁 Ganaste $premio puntos"
                giros++
            } else {
                txtPremio.text = "⏳ Espera 4 horas para volver a girar"
            }
        }
    }

    private fun obtenerPremio(): Int {
        val ruleta = listOf(5, 10, 15, 20)
        return if (Random.nextInt(100) < 20) 100 else ruleta.random()
    }

    override fun onDestroy() {
        super.onDestroy()
        sonidoGiro.release()
    }
}
