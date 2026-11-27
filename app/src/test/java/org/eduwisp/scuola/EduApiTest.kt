// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola

import org.eduwisp.scuola.api.EduApi
import org.eduwisp.scuola.api.Esito
import org.eduwisp.scuola.sync.nuoveDaNotificare
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Il client legge le risposte dell'API eduwisp-api (ADR-0022). */
class EduApiTest {
    private val chiamate = mutableListOf<Triple<String, String, String?>>()
    private var risposta: Pair<Int, String> = 200 to "{}"
    private var token: String? = "1.segreto"
    private val api = EduApi("https://demo.eduwisp.org/", { token }, { 7 }) { metodo, url, t, corpo ->
        chiamate += Triple("$metodo $url", t ?: "", corpo)
        risposta
    }

    @Test
    fun `accesso senza token e con il nome del telefono`() {
        risposta = 201 to """{"token":"3.abc","scade":"2026-12-27T10:00:00+00:00"}"""
        token = null
        assertEquals(Esito.Ok("3.abc"), api.accedi("docente", "segreta", "Pixel 8"))
        val (url, bearer, corpo) = chiamate.single()
        assertEquals("POST https://demo.eduwisp.org/api/app/v1/accedi", url)
        assertEquals("", bearer)
        assertEquals("Pixel 8", JSONObject(corpo!!).getString("telefono"))
    }

    @Test
    fun `menu e ruoli dal server`() {
        risposta = 200 to """{"utente":{"nome":"Anna Neri","amministratore":false,"sola_lettura":false},
            "vedi_come":null,"ruoli":[{"id":7,"ruolo":"teacher","nome":"Docente","istituto":"Liceo"}],
            "ruolo_attuale":7,"coordinatore_di":["1ª A"],
            "menu":[{"id":"classi","titolo":"Le mie classi"},{"id":"coordinamento","titolo":"Coordinamento"}]}"""
        val io = (api.io() as Esito.Ok).valore
        assertEquals(listOf("classi", "coordinamento"), io.menu.map { it.id })
        assertEquals(listOf("1ª A"), io.coordinatoreDi)
        assertNull(io.vediCome)
        assertTrue(chiamate.single().first.endsWith("/api/app/v1/io?membership=7"))
        assertEquals("1.segreto", chiamate.single().second)
    }

    @Test
    fun `dettaglio della classe`() {
        risposta = 200 to """{"id":3,"nome":"1ª A","anno_scolastico":"2026/2027",
            "studenti":[{"id":1,"nome":"Rossi Anna","tablet":{"nome":"TAB-1","conforme":true,"ultimo_contatto":null,"senza_contatto":true}},
                        {"id":2,"nome":"Bianchi Luca","tablet":null}],
            "tablet_condivisi":[],"app_consentite":[{"nome":"Teams","icona":null}],
            "sblocchi":[{"id":9,"app":"Wikipedia","fino_alle":"2027-03-02T11:00:00+01:00","da":"Prof"}],
            "app_sbloccabili":[{"id":4,"nome":"Enciclopedia"}],"durate_minuti":[30,60,120,240]}"""
        val c = (api.classe(3) as Esito.Ok).valore
        assertEquals("Rossi Anna", c.studenti[0].nome)
        assertTrue(c.studenti[0].tablet!!.senzaContatto)
        assertNull(c.studenti[1].tablet)
        assertEquals(listOf(30, 60, 120, 240), c.durateMinuti)
        assertNull(c.appConsentite[0].icona)
    }

    @Test
    fun `errori del server e della rete`() {
        risposta = 400 to """{"errore":"La motivazione è obbligatoria."}"""
        assertEquals(Esito.Errore(400, "La motivazione è obbligatoria."), api.decidi(1, true, ""))
        risposta = 401 to """{"detail":"Credenziale non valida o scaduta."}"""
        assertEquals(401, (api.io() as Esito.Errore).codice)
        val offline = EduApi("https://x", { null }) { _, _, _, _ -> throw IOException("rete assente") }
        assertTrue(offline.riepilogo() is Esito.Rete)
    }

    @Test
    fun `si notifica solo quando le richieste aumentano`() {
        assertTrue(nuoveDaNotificare(3, 1))
        assertFalse(nuoveDaNotificare(1, 3))
        assertFalse(nuoveDaNotificare(2, 2))
    }
}
