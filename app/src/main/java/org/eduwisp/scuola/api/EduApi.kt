// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola.api

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Esito di una chiamata all'API (ADR-0022). */
sealed interface Esito<out T> {
    data class Ok<T>(val valore: T) : Esito<T>
    data class Errore(val codice: Int, val messaggio: String) : Esito<Nothing>
    data class Rete(val motivo: String) : Esito<Nothing>
}

/** Scambio HTTP: in produzione HttpURLConnection, nei test un finto. */
fun interface Trasporto {
    @Throws(IOException::class)
    fun scambia(metodo: String, url: String, token: String?, corpo: String?): Pair<Int, String>
}

class HttpTrasporto(private val timeoutMs: Int = 20_000) : Trasporto {
    override fun scambia(metodo: String, url: String, token: String?, corpo: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metodo
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (corpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            }
            val codice = c.responseCode
            val testo = (if (codice >= 400) c.errorStream else c.inputStream)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: ""
            return codice to testo
        } finally {
            c.disconnect()
        }
    }
}

/** Client dell'API `/api/app/v1/` del server della scuola. */
class EduApi(
    server: String,
    private val token: () -> String?,
    private val ruolo: () -> Int? = { null },
    private val trasporto: Trasporto = HttpTrasporto(),
) {
    private val base = server.trimEnd('/') + "/api/app/v1/"

    private fun url(percorso: String, parametri: Map<String, String?> = emptyMap()): String {
        val tutti = parametri + ("membership" to ruolo()?.toString())
        val query = tutti.filterValues { !it.isNullOrEmpty() }.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        return base + percorso + if (query.isEmpty()) "" else "?$query"
    }

    private fun <T> chiama(metodo: String, url: String, corpo: JSONObject?, leggi: (String) -> T): Esito<T> {
        val (codice, testo) = try {
            trasporto.scambia(metodo, url, token(), corpo?.toString())
        } catch (e: IOException) {
            return Esito.Rete(e.message ?: e.javaClass.simpleName)
        }
        return try {
            if (codice in 200..299) {
                Esito.Ok(leggi(testo))
            } else {
                val messaggio = testo.takeIf { it.isNotBlank() }?.let {
                    JSONObject(it).optString("errore").ifBlank { JSONObject(it).optString("detail") }
                }.orEmpty()
                Esito.Errore(codice, messaggio.ifBlank { "Errore $codice" })
            }
        } catch (e: JSONException) {
            Esito.Rete("risposta non valida (HTTP $codice)")
        }
    }

    fun accedi(utente: String, password: String, telefono: String): Esito<String> = chiama(
        "POST",
        base + "accedi",
        JSONObject().put("username", utente).put("password", password).put("telefono", telefono),
    ) { JSONObject(it).getString("token") }

    fun esci(): Esito<Unit> = chiama("POST", url("esci"), JSONObject()) { }

    fun io(): Esito<Io> = chiama("GET", url("io"), null) { Io.da(JSONObject(it)) }

    fun classi(soloCoordinate: Boolean = false): Esito<List<ClasseBreve>> =
        chiama("GET", url("classi", mapOf("solo" to if (soloCoordinate) "coordinate" else null)), null) {
            JSONArray(it).oggetti().map(ClasseBreve::da)
        }

    fun classe(id: Int): Esito<Classe> = chiama("GET", url("classi/$id"), null) { Classe.da(JSONObject(it)) }

    fun sblocca(classe: Int, app: Int, minuti: Int): Esito<Unit> =
        chiama("POST", url("classi/$classe/sblocchi"), JSONObject().put("app", app).put("minuti", minuti)) { }

    fun terminaSblocco(id: Int): Esito<Unit> = chiama("POST", url("sblocchi/$id/termina"), JSONObject()) { }

    fun richieste(soloCoordinate: Boolean = false): Esito<List<RichiestaBreve>> =
        chiama("GET", url("richieste", mapOf("solo" to if (soloCoordinate) "coordinate" else null)), null) {
            JSONArray(it).oggetti().map(RichiestaBreve::da)
        }

    fun richiesta(id: Int): Esito<Richiesta> = chiama("GET", url("richieste/$id"), null) { Richiesta.da(JSONObject(it)) }

    fun decidi(id: Int, approva: Boolean, motivo: String): Esito<Richiesta> = chiama(
        "POST",
        url("richieste/$id/decisione"),
        JSONObject().put("approva", approva).put("motivo", motivo),
    ) { Richiesta.da(JSONObject(it)) }

    fun riepilogo(): Esito<Int> = chiama("GET", url("riepilogo"), null) { JSONObject(it).getInt("richieste_in_attesa") }

    fun vediComeElenco(ruolo: String, cerca: String = ""): Esito<List<Persona>> =
        chiama("GET", url("vedi-come", mapOf("ruolo" to ruolo, "cerca" to cerca)), null) {
            JSONArray(it).oggetti().map(Persona::da)
        }

    fun vediComeAttiva(appartenenza: Int): Esito<Unit> =
        chiama("POST", base + "vedi-come", JSONObject().put("ruolo", appartenenza)) { }

    fun vediComeEsci(): Esito<Unit> = chiama("DELETE", base + "vedi-come", null) { }
}

fun JSONArray.oggetti(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun JSONObject.testo(nome: String): String? = if (isNull(nome)) null else optString(nome).takeIf { it.isNotEmpty() }
