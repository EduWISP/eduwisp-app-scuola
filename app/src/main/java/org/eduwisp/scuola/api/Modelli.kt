// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola.api

import org.json.JSONObject

data class Ruolo(val id: Int, val ruolo: String, val nome: String, val istituto: String)

data class VoceMenu(val id: String, val titolo: String)

data class Io(
    val nome: String,
    val amministratore: Boolean,
    val solaLettura: Boolean,
    val vediCome: String?,
    val ruoli: List<Ruolo>,
    val ruoloAttuale: Int?,
    val coordinatoreDi: List<String>,
    val menu: List<VoceMenu>,
) {
    companion object {
        fun da(j: JSONObject): Io {
            val u = j.getJSONObject("utente")
            val vc = j.optJSONObject("vedi_come")
            return Io(
                nome = u.getString("nome"),
                amministratore = u.getBoolean("amministratore"),
                solaLettura = u.getBoolean("sola_lettura"),
                vediCome = vc?.let { "${it.getString("persona")} · ${it.getString("ruolo")} · ${it.getString("istituto")}" },
                ruoli = j.getJSONArray("ruoli").oggetti().map {
                    Ruolo(it.getInt("id"), it.getString("ruolo"), it.getString("nome"), it.getString("istituto"))
                },
                ruoloAttuale = if (j.isNull("ruolo_attuale")) null else j.getInt("ruolo_attuale"),
                coordinatoreDi = j.getJSONArray("coordinatore_di").let { a -> (0 until a.length()).map(a::getString) },
                menu = j.getJSONArray("menu").oggetti().map { VoceMenu(it.getString("id"), it.getString("titolo")) },
            )
        }
    }
}

data class ClasseBreve(
    val id: Int,
    val nome: String,
    val indirizzo: String,
    val studenti: Int,
    val dispositivi: Int,
    val nonConformi: Int,
    val senzaContatto: Int,
    val coordinata: Boolean,
) {
    companion object {
        fun da(j: JSONObject) = ClasseBreve(
            j.getInt("id"), j.getString("nome"), j.optString("indirizzo"), j.getInt("studenti"),
            j.getInt("dispositivi"), j.getInt("non_conformi"), j.getInt("senza_contatto"), j.getBoolean("coordinata"),
        )
    }
}

data class Tablet(val nome: String, val conforme: Boolean?, val senzaContatto: Boolean) {
    companion object {
        fun da(j: JSONObject?): Tablet? = j?.let {
            Tablet(it.getString("nome"), if (it.isNull("conforme")) null else it.getBoolean("conforme"), it.getBoolean("senza_contatto"))
        }
    }
}

data class Studente(val id: Int, val nome: String, val tablet: Tablet?)

data class AppConsentita(val nome: String, val icona: String?)

data class Sblocco(val id: Int, val app: String, val finoAlle: String, val da: String)

data class AppSbloccabile(val id: Int, val nome: String)

data class Classe(
    val id: Int,
    val nome: String,
    val annoScolastico: String,
    val studenti: List<Studente>,
    val tabletCondivisi: List<Tablet>,
    val appConsentite: List<AppConsentita>,
    val sblocchi: List<Sblocco>,
    val appSbloccabili: List<AppSbloccabile>,
    val durateMinuti: List<Int>,
) {
    companion object {
        fun da(j: JSONObject) = Classe(
            id = j.getInt("id"),
            nome = j.getString("nome"),
            annoScolastico = j.getString("anno_scolastico"),
            studenti = j.getJSONArray("studenti").oggetti().map {
                Studente(it.getInt("id"), it.getString("nome"), Tablet.da(it.optJSONObject("tablet")))
            },
            tabletCondivisi = j.getJSONArray("tablet_condivisi").oggetti().mapNotNull { Tablet.da(it) },
            appConsentite = j.getJSONArray("app_consentite").oggetti().map { AppConsentita(it.getString("nome"), it.testo("icona")) },
            sblocchi = j.getJSONArray("sblocchi").oggetti().map {
                Sblocco(it.getInt("id"), it.getString("app"), it.getString("fino_alle"), it.optString("da"))
            },
            appSbloccabili = j.getJSONArray("app_sbloccabili").oggetti().map { AppSbloccabile(it.getInt("id"), it.getString("nome")) },
            durateMinuti = j.getJSONArray("durate_minuti").let { a -> (0 until a.length()).map(a::getInt) },
        )
    }
}

data class RichiestaBreve(
    val id: Int,
    val app: String,
    val studente: String,
    val classe: String,
    val chiestaIl: String,
    val stato: String,
    val pareri: Int,
) {
    companion object {
        fun da(j: JSONObject) = RichiestaBreve(
            j.getInt("id"), j.getString("app"), j.getString("studente"), j.optString("classe"),
            j.getString("chiesta_il"), j.getString("stato"), j.getInt("pareri"),
        )
    }
}

data class Parere(val da: String, val motivo: String)

data class Richiesta(
    val breve: RichiestaBreve,
    val identificativo: String,
    val motivazioneStudente: String,
    val pareriFamiglia: List<Parere>,
    val decisione: String?,
    val puoDecidere: Boolean,
) {
    companion object {
        fun da(j: JSONObject) = Richiesta(
            breve = RichiestaBreve.da(j),
            identificativo = j.getString("identificativo"),
            motivazioneStudente = j.optString("motivazione_studente"),
            pareriFamiglia = j.getJSONArray("pareri_famiglia").oggetti().map { Parere(it.getString("da"), it.getString("motivo")) },
            decisione = j.optJSONObject("decisione")?.let { d -> listOf(d.optString("da"), d.optString("motivo")).filter { it.isNotBlank() }.joinToString(": ") },
            puoDecidere = j.getBoolean("puo_decidere"),
        )
    }
}

data class Persona(val id: Int, val persona: String, val istituto: String) {
    companion object {
        fun da(j: JSONObject) = Persona(j.getInt("id"), j.getString("persona"), j.getString("istituto"))
    }
}
