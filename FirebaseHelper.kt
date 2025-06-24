package com.jahseh.adsxdiamantes

import com.google.firebase.database.FirebaseDatabase

object FirebaseHelper {
    fun guardarUsuario(nombre: String, id: String, puntos: Int) {
        val datos = mapOf("nombre" to nombre, "id" to id, "puntos" to puntos)
        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(nombre)
            .setValue(datos)
    }
}
