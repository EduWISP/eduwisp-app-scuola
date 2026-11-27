// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.eduwisp.scuola.api.EduApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Server, token (cifrato con il Keystore) e ruolo scelto. Nessun dato di studenti sul telefono. */
class Sessione(context: Context) {
    private val prefs = context.getSharedPreferences("sessione", Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString("server", SERVER_PREDEFINITO)!!
        set(v) = prefs.edit().putString("server", v.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)?.let(Cifratura::decifra)
        set(v) = prefs.edit().putString("token", v?.let(Cifratura::cifra)).apply()

    var ruolo: Int?
        get() = prefs.getInt("ruolo", -1).takeIf { it >= 0 }
        set(v) = prefs.edit().putInt("ruolo", v ?: -1).apply()

    /** Ultimo numero di richieste in attesa già notificato. */
    var ultimeInAttesa: Int
        get() = prefs.getInt("ultime_in_attesa", 0)
        set(v) = prefs.edit().putInt("ultime_in_attesa", v).apply()

    val connesso: Boolean get() = token != null

    fun api() = EduApi(server, { token }, { ruolo })

    fun esci() = prefs.edit().remove("token").remove("ruolo").remove("ultime_in_attesa").apply()

    companion object {
        const val SERVER_PREDEFINITO = "https://demo.eduwisp.org"
    }
}

private object Cifratura {
    private const val ALIAS = "eduwisp-scuola-token"
    private const val T = "AES/GCM/NoPadding"

    private fun chiave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return g.generateKey()
    }

    fun cifra(testo: String): String {
        val c = Cipher.getInstance(T).apply { init(Cipher.ENCRYPT_MODE, chiave()) }
        return Base64.encodeToString(c.iv + c.doFinal(testo.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decifra(dati: String): String? = try {
        val b = Base64.decode(dati, Base64.NO_WRAP)
        val c = Cipher.getInstance(T)
        c.init(Cipher.DECRYPT_MODE, chiave(), GCMParameterSpec(128, b, 0, 12))
        String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    } catch (e: java.security.GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
