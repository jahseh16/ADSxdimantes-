package com.jahseh.adsxdiamantes

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

class RuletaActivity : AppCompatActivity() {

    private lateinit var ruletaImg: ImageView
    private lateinit var txtPremio: TextView
    private lateinit var btnGirar: Button // Made btnGirar a class member
    private var giros = 0
    private val maxGiros = 5
    private lateinit var sonidoGiro: MediaPlayer
    private lateinit var userUID: String

    // SharedPreferences constants
    private val RULETA_PREFS_NAME = "RuletaPrefs" // Renamed to avoid conflict if MainActivity uses same name for different things
    private val KEY_GIROS = "girosCount"
    private val KEY_TIMESTAMP_MAX_GIROS = "timestampMaxGiros"
    private val COOLDOWN_PERIOD_HOURS = 4
    private val COOLDOWN_PERIOD_MS = COOLDOWN_PERIOD_HOURS * 60 * 60 * 1000L // 4 hours in milliseconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruleta)

        ruletaImg = findViewById(R.id.ruletaImg)
        txtPremio = findViewById(R.id.txtPremio)
        btnGirar = findViewById(R.id.btnGirar) // Initialize btnGirar

        // Retrieve UID created by MainActivity
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) // Using MainActivity's PREFS_USER
        userUID = userPrefs.getString("userUID", "default_user_uid_error") ?: "default_user_uid_error"
        if (userUID == "default_user_uid_error") {
            android.util.Log.e("RuletaActivity", "Error: User UID not found or is default error value.")
            // Potentially handle this error more gracefully, e.g., disable Firebase features or show error to user
        }


        sonidoGiro = MediaPlayer.create(this, R.raw.giro)

        loadSpinState()
        checkCooldown()

        btnGirar.setOnClickListener {
            if (giros < maxGiros) {
                sonidoGiro.start()
                val premio = obtenerPremio()
                actualizarPuntosEnFirebase(premio)
                txtPremio.text = getString(R.string.ganaste_puntos_format, premio) // Show prize first
                giros++
                saveSpinState()
                if (giros >= maxGiros) {
                    saveTimestampMaxGiros()
                    updateUiForCooldown()
                } else {
                    // Update text to show next state (remaining spins) after a short delay
                    // to allow user to read the prize.
                    // For simplicity now, we'll update it immediately.
                    // A Handler().postDelayed could be used for a better UX.
                    updateUiForSpinAvailable()
                }
            } else {
                // This case is mostly defensive, as button should be disabled by checkCooldown
                updateUiForCooldown()
            }
        }
    }

    private fun loadSpinState() {
        val prefs = getSharedPreferences(RULETA_PREFS_NAME, Context.MODE_PRIVATE)
        giros = prefs.getInt(KEY_GIROS, 0)
    }

    private fun saveSpinState() {
        val prefs = getSharedPreferences(RULETA_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(KEY_GIROS, giros)
        editor.apply()
    }

    private fun saveTimestampMaxGiros() {
        val prefs = getSharedPreferences(RULETA_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putLong(KEY_TIMESTAMP_MAX_GIROS, System.currentTimeMillis())
        editor.apply()
    }

    private fun checkCooldown() {
        val prefs = getSharedPreferences(RULETA_PREFS_NAME, Context.MODE_PRIVATE)
        val timestampMaxGiros = prefs.getLong(KEY_TIMESTAMP_MAX_GIROS, 0L)

        if (giros >= maxGiros) {
            if (timestampMaxGiros > 0L) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - timestampMaxGiros >= COOLDOWN_PERIOD_MS) {
                    // Cooldown finished
                    giros = 0
                    saveSpinState()
                    // Clear the timestamp
                    val editor = prefs.edit()
                    editor.remove(KEY_TIMESTAMP_MAX_GIROS)
                    editor.apply()
                    updateUiForSpinAvailable()
                } else {
                    // Still in cooldown
                    updateUiForCooldown()
                }
            } else {
                 // Max giros reached but no timestamp, probably an old state.
                 // Treat as ready to spin to avoid getting stuck.
                giros = 0
                saveSpinState()
                updateUiForSpinAvailable()
            }
        } else {
            // Not at max giros, spinning is available
            updateUiForSpinAvailable()
        }
    }

    private fun updateUiForCooldown() {
        btnGirar.isEnabled = false
        // Calculate remaining time for a more informative message if desired
        val prefs = getSharedPreferences(RULETA_PREFS_NAME, Context.MODE_PRIVATE)
        val timestampMaxGiros = prefs.getLong(KEY_TIMESTAMP_MAX_GIROS, 0L)
        var remainingTimeMsg = getString(R.string.espera_horas_para_girar) // Default message
        if (timestampMaxGiros > 0L) {
            val remainingMs = COOLDOWN_PERIOD_MS - (System.currentTimeMillis() - timestampMaxGiros)
            if (remainingMs > 0) {
                val hours = (remainingMs / (1000 * 60 * 60))
                val minutes = (remainingMs / (1000 * 60)) % 60
                remainingTimeMsg = getString(R.string.espera_horas_minutos_format, hours.toInt(), minutes.toInt())
            } else {
                 // Should have been reset by checkCooldown, but as a fallback:
                remainingTimeMsg = getString(R.string.puedes_girar_de_nuevo)
                 checkCooldown() // Re-check state
            }
        }
        txtPremio.text = remainingTimeMsg
    }

    private fun updateUiForSpinAvailable() {
        btnGirar.isEnabled = true
        txtPremio.text = getString(R.string.gira_para_ganar_remaining_format, maxGiros - giros)
    }

    private fun obtenerPremio(): Int {
        val ruleta = listOf(5, 10, 15, 20)
        return if (Random.nextInt(100) < 20) 100 else ruleta.random()
    }

    private fun actualizarPuntosEnFirebase(puntosGanados: Int) {
        if (userUID == "default_user_uid_error" || userUID.isBlank()) {
            android.util.Log.e("RuletaActivity", "Cannot update points, User UID is invalid: $userUID")
            return // Don't proceed if UID is not valid
        }
        val dbRef = FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(userUID) // Use the unique user ID

        dbRef.child("puntos").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var puntosActuales = snapshot.getValue(Int::class.java) ?: 0
                puntosActuales += puntosGanados
                dbRef.child("puntos").setValue(puntosActuales)
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejar error, por ejemplo, con un Log
                android.util.Log.e("RuletaActivity", "Error al actualizar puntos en Firebase: ${error.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        sonidoGiro.release()
    }
}
