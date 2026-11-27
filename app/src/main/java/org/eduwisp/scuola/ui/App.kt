// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eduwisp.scuola.R
import org.eduwisp.scuola.Sessione
import org.eduwisp.scuola.api.Classe
import org.eduwisp.scuola.api.ClasseBreve
import org.eduwisp.scuola.api.Esito
import org.eduwisp.scuola.api.Io
import org.eduwisp.scuola.api.Persona
import org.eduwisp.scuola.api.Richiesta
import org.eduwisp.scuola.api.RichiestaBreve
import org.eduwisp.scuola.api.Tablet

private val Blu = Color(0xFF163E73)

/** Messaggio da mostrare per un esito non riuscito. */
fun messaggio(e: Esito<*>): String? = when (e) {
    is Esito.Ok -> null
    is Esito.Errore -> e.messaggio
    is Esito.Rete -> "Server non raggiungibile: ${e.motivo}"
}

private suspend fun <T> io(blocco: () -> T): T = withContext(Dispatchers.IO) { blocco() }

@Composable
fun AppScuola(sessione: Sessione) {
    var connesso by remember { mutableStateOf(sessione.connesso) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (!connesso) {
                Accesso(sessione) { connesso = true }
            } else {
                Principale(sessione) {
                    sessione.esci()
                    connesso = false
                }
            }
        }
    }
}

@Composable
private fun Accesso(sessione: Sessione, onFatto: () -> Unit) {
    var server by remember { mutableStateOf(sessione.server) }
    var utente by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errore by remember { mutableStateOf<String?>(null) }
    var attesa by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Blu)
        Text(stringResource(R.string.accesso_spiegazione))
        OutlinedTextField(server, { server = it }, label = { Text(stringResource(R.string.server)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(utente, { utente = it }, label = { Text(stringResource(R.string.utente)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        if (server.trimEnd('/') == Sessione.SERVER_PREDEFINITO) Text(stringResource(R.string.accesso_demo))
        errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !attesa && utente.isNotBlank() && password.isNotBlank() && server.startsWith("https://"), onClick = {
            attesa = true
            errore = null
            scope.launch {
                sessione.server = server
                val e = io { sessione.api().accedi(utente.trim(), password, Build.MODEL) }
                attesa = false
                if (e is Esito.Ok) {
                    sessione.token = e.valore
                    onFatto()
                } else {
                    errore = messaggio(e)
                }
            }
        }) { Text(stringResource(R.string.accedi)) }
    }
}

private sealed interface Pagina {
    data class Sezione(val id: String) : Pagina
    data class DettaglioClasse(val id: Int) : Pagina
    data class DettaglioRichiesta(val id: Int) : Pagina
    data object VediCome : Pagina
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Principale(sessione: Sessione, onEsci: () -> Unit) {
    val scope = rememberCoroutineScope()
    var io by remember { mutableStateOf<Io?>(null) }
    var errore by remember { mutableStateOf<String?>(null) }
    var pagina by remember { mutableStateOf<Pagina?>(null) }
    var aggiorna by remember { mutableIntStateOf(0) }
    var menuRuoli by remember { mutableStateOf(false) }

    LaunchedEffect(aggiorna) {
        when (val e = io { sessione.api().io() }) {
            is Esito.Ok -> {
                io = e.valore
                errore = null
                if (pagina == null || pagina is Pagina.Sezione && e.valore.menu.none { it.id == (pagina as Pagina.Sezione).id }) {
                    pagina = e.valore.menu.firstOrNull()?.let { Pagina.Sezione(it.id) }
                        ?: if (e.valore.amministratore) Pagina.VediCome else null
                }
            }
            is Esito.Errore -> if (e.codice == 401) onEsci() else errore = e.messaggio
            is Esito.Rete -> errore = messaggio(e)
        }
    }
    val dati = io
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (dati != null && dati.ruoli.size > 1) {
                        TextButton(onClick = { menuRuoli = true }) {
                            Text(dati.ruoli.firstOrNull { it.id == dati.ruoloAttuale }?.nome ?: stringResource(R.string.ruolo))
                        }
                        DropdownMenu(menuRuoli, { menuRuoli = false }) {
                            dati.ruoli.forEach { r ->
                                DropdownMenuItem(text = { Text("${r.nome} · ${r.istituto}") }, onClick = {
                                    menuRuoli = false
                                    sessione.ruolo = r.id
                                    pagina = null
                                    aggiorna++
                                })
                            }
                        }
                    }
                    if (dati?.amministratore == true && dati.vediCome == null) {
                        TextButton(onClick = { pagina = Pagina.VediCome }) { Text(stringResource(R.string.vedi_come)) }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            io { sessione.api().esci() }
                            onEsci()
                        }
                    }) { Text(stringResource(R.string.esci)) }
                },
            )
        },
        bottomBar = {
            val voci = dati?.menu.orEmpty()
            if (voci.size > 1) {
                NavigationBar {
                    voci.forEach { v ->
                        NavigationBarItem(
                            selected = (pagina as? Pagina.Sezione)?.id == v.id,
                            onClick = { pagina = Pagina.Sezione(v.id) },
                            icon = { Text(v.titolo.take(1)) },
                            label = { Text(v.titolo) },
                        )
                    }
                }
            }
        },
    ) { margini ->
        Column(Modifier.padding(margini).fillMaxSize()) {
            if (dati?.solaLettura == true) {
                Banner(
                    dati.vediCome?.let { stringResource(R.string.banner_vedi_come, it) } ?: stringResource(R.string.banner_sola_lettura),
                    azione = if (dati.vediCome != null) stringResource(R.string.esci_dalla_vista) else null,
                ) {
                    scope.launch {
                        io { sessione.api().vediComeEsci() }
                        pagina = null
                        aggiorna++
                    }
                }
            }
            errore?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            when (val p = pagina) {
                null -> if (dati != null) Text(stringResource(R.string.nessun_ruolo), Modifier.padding(16.dp)) else Caricamento()
                is Pagina.Sezione -> when (p.id) {
                    "classi", "istituto" -> ElencoClassi(sessione, istituto = p.id == "istituto") { pagina = Pagina.DettaglioClasse(it) }
                    "coordinamento" -> Coordinamento(sessione, dati?.coordinatoreDi.orEmpty(),
                        onClasse = { pagina = Pagina.DettaglioClasse(it) }, onRichiesta = { pagina = Pagina.DettaglioRichiesta(it) })
                    "richieste" -> ElencoRichieste(sessione) { pagina = Pagina.DettaglioRichiesta(it) }
                    else -> Text(p.id, Modifier.padding(16.dp))
                }
                is Pagina.DettaglioClasse -> DettaglioClasse(sessione, p.id, solaLettura = dati?.solaLettura == true) {
                    pagina = dati?.menu?.firstOrNull()?.let { Pagina.Sezione(it.id) }
                }
                is Pagina.DettaglioRichiesta -> DettaglioRichiesta(sessione, p.id, solaLettura = dati?.solaLettura == true) {
                    pagina = Pagina.Sezione("richieste")
                }
                Pagina.VediCome -> VediCome(sessione) {
                    pagina = null
                    aggiorna++
                }
            }
        }
    }
}

@Composable
private fun Banner(testo: String, azione: String?, onAzione: () -> Unit) {
    Surface(color = Color(0xFF7C2D12), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(testo, Modifier.weight(1f))
            if (azione != null) TextButton(onClick = onAzione) { Text(azione, color = Color.White) }
        }
    }
}

@Composable
private fun Caricamento() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
}

@Composable
private fun <T> Carica(chiave: Any, carica: () -> Esito<T>, contenuto: @Composable (T, () -> Unit) -> Unit) {
    var stato by remember(chiave) { mutableStateOf<Esito<T>?>(null) }
    var giro by remember(chiave) { mutableIntStateOf(0) }
    LaunchedEffect(chiave, giro) { stato = io(carica) }
    when (val s = stato) {
        null -> Caricamento()
        is Esito.Ok -> contenuto(s.valore) { giro++ }
        else -> Text(messaggio(s).orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ElencoClassi(sessione: Sessione, istituto: Boolean, onClasse: (Int) -> Unit) {
    Carica("classi$istituto", { sessione.api().classi() }) { classi, _ ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (istituto) {
                item {
                    Riquadri(
                        listOf(
                            stringResource(R.string.classi) to classi.size,
                            stringResource(R.string.studenti) to classi.sumOf { it.studenti },
                            stringResource(R.string.tablet) to classi.sumOf { it.dispositivi },
                            stringResource(R.string.non_conformi) to classi.sumOf { it.nonConformi },
                        ),
                    )
                }
            }
            items(classi, key = { it.id }) { RigaClasse(it) { onClasse(it.id) } }
            if (classi.isEmpty()) item { Text(stringResource(R.string.nessuna_classe), Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun Riquadri(valori: List<Pair<String, Int>>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        valori.forEach { (nome, n) ->
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(8.dp)) {
                    Text("$n", style = MaterialTheme.typography.titleLarge, color = Blu, fontWeight = FontWeight.Bold)
                    Text(nome, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RigaClasse(c: ClasseBreve, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(c.nome + if (c.coordinata) " · " + stringResource(R.string.coordinata) else "", fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.riga_classe, c.studenti, c.dispositivi, c.nonConformi, c.senzaContatto), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun DettaglioClasse(sessione: Sessione, id: Int, solaLettura: Boolean, onIndietro: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dialogo by remember { mutableStateOf(false) }
    var avviso by remember { mutableStateOf<String?>(null) }
    Carica("classe$id", { sessione.api().classe(id) }) { c: Classe, ricarica ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                TextButton(onClick = onIndietro) { Text(stringResource(R.string.indietro)) }
                Text(c.nome, style = MaterialTheme.typography.headlineSmall, color = Blu)
                Text(stringResource(R.string.anno, c.annoScolastico), style = MaterialTheme.typography.bodySmall)
                avviso?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.app), fontWeight = FontWeight.SemiBold)
                Text(c.appConsentite.joinToString(", ") { it.nome }.ifEmpty { stringResource(R.string.nessuna) })
                c.sblocchi.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sbloccata_fino, s.app, s.finoAlle.substring(11, 16)), Modifier.weight(1f))
                        if (c.durateMinuti.isNotEmpty() && !solaLettura) {
                            TextButton(onClick = {
                                scope.launch {
                                    avviso = messaggio(io { sessione.api().terminaSblocco(s.id) }) ?: "${s.app}: ok"
                                    ricarica()
                                }
                            }) { Text(stringResource(R.string.blocca_ora)) }
                        }
                    }
                }
                if (c.appSbloccabili.isNotEmpty() && !solaLettura) {
                    OutlinedButton(onClick = { dialogo = true }) { Text(stringResource(R.string.sblocca_per_lezione)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.studenti_n, c.studenti.size), fontWeight = FontWeight.SemiBold)
            }
            items(c.studenti, key = { it.id }) { s ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(s.nome)
                    Text(statoTablet(s.tablet), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
            if (c.tabletCondivisi.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.tablet_condivisi, c.tabletCondivisi.size), fontWeight = FontWeight.SemiBold)
                    c.tabletCondivisi.forEach { Text(statoTablet(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (dialogo) {
            DialogoSblocco(c, onAnnulla = { dialogo = false }) { app, minuti ->
                dialogo = false
                scope.launch {
                    avviso = messaggio(io { sessione.api().sblocca(c.id, app, minuti) }) ?: "OK"
                    ricarica()
                }
            }
        }
    }
}

@Composable
private fun statoTablet(t: Tablet?): String = when {
    t == null -> stringResource(R.string.nessun_tablet)
    t.conforme == false -> stringResource(R.string.tablet_non_conforme, t.nome)
    t.senzaContatto -> stringResource(R.string.tablet_senza_contatto, t.nome)
    else -> stringResource(R.string.tablet_ok, t.nome)
}

@Composable
private fun DialogoSblocco(c: Classe, onAnnulla: () -> Unit, onSblocca: (Int, Int) -> Unit) {
    var app by remember { mutableStateOf(c.appSbloccabili.first().id) }
    var minuti by remember { mutableStateOf(c.durateMinuti.firstOrNull { it == 60 } ?: c.durateMinuti.first()) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text(stringResource(R.string.sblocca_per_lezione)) },
        text = {
            Column {
                c.appSbloccabili.forEach { a ->
                    FilterChip(selected = a.id == app, onClick = { app = a.id }, label = { Text(a.nome) })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.durateMinuti.forEach { m ->
                        FilterChip(selected = m == minuti, onClick = { minuti = m }, label = { Text(if (m < 60) "$m min" else "${m / 60} h") })
                    }
                }
                Text(stringResource(R.string.sblocco_spiegazione), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSblocca(app, minuti) }) { Text(stringResource(R.string.sblocca)) } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text(stringResource(R.string.annulla)) } },
    )
}

@Composable
private fun Coordinamento(sessione: Sessione, classi: List<String>, onClasse: (Int) -> Unit, onRichiesta: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.coordinatore_di, classi.joinToString(", ")), Modifier.padding(vertical = 8.dp))
        Carica("coordinate", { sessione.api().classi(soloCoordinate = true) }) { elenco, _ ->
            Column { elenco.forEach { RigaClasse(it) { onClasse(it.id) } } }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.richieste_delle_classi), fontWeight = FontWeight.SemiBold)
        Carica("richieste-coordinate", { sessione.api().richieste(soloCoordinate = true) }) { elenco, _ ->
            Column {
                elenco.forEach { RigaRichiesta(it) { onRichiesta(it.id) } }
                if (elenco.isEmpty()) Text(stringResource(R.string.nessuna_richiesta))
            }
        }
    }
}

@Composable
private fun ElencoRichieste(sessione: Sessione, onRichiesta: (Int) -> Unit) {
    Carica("richieste", { sessione.api().richieste() }) { elenco, _ ->
        val ordinate = elenco.sortedBy { if (it.stato == "pending") 0 else 1 }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(ordinate, key = { it.id }) { RigaRichiesta(it) { onRichiesta(it.id) } }
            if (elenco.isEmpty()) item { Text(stringResource(R.string.nessuna_richiesta), Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun nomeStato(stato: String) = stringResource(
    when (stato) {
        "pending" -> R.string.in_attesa
        "approved" -> R.string.approvata
        else -> R.string.respinta
    },
)

@Composable
private fun RigaRichiesta(r: RichiestaBreve, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text("${r.app} · ${nomeStato(r.stato)}", fontWeight = FontWeight.SemiBold)
        Text("${r.studente} · ${r.classe}" + if (r.pareri > 0) " · " + stringResource(R.string.pareri_n, r.pareri) else "",
            style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun DettaglioRichiesta(sessione: Sessione, id: Int, solaLettura: Boolean, onIndietro: () -> Unit) {
    val scope = rememberCoroutineScope()
    var motivo by remember { mutableStateOf("") }
    var avviso by remember { mutableStateOf<String?>(null) }
    Carica("richiesta$id", { sessione.api().richiesta(id) }) { r: Richiesta, ricarica ->
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onIndietro) { Text(stringResource(R.string.indietro)) }
            Text(r.breve.app, style = MaterialTheme.typography.headlineSmall, color = Blu)
            Text("${r.identificativo} · ${r.breve.studente} · ${r.breve.classe}", style = MaterialTheme.typography.bodySmall)
            Text(nomeStato(r.breve.stato), fontWeight = FontWeight.SemiBold)
            if (r.motivazioneStudente.isNotBlank()) Text(stringResource(R.string.motivazione_studente, r.motivazioneStudente))
            Text(stringResource(R.string.pareri_famiglia), fontWeight = FontWeight.SemiBold)
            if (r.pareriFamiglia.isEmpty()) Text(stringResource(R.string.nessun_parere))
            r.pareriFamiglia.forEach { Text("${it.da}: ${it.motivo}") }
            r.decisione?.let { Text(stringResource(R.string.decisione, it)) }
            avviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (r.puoDecidere && !solaLettura) {
                OutlinedTextField(motivo, { motivo = it }, label = { Text(stringResource(R.string.motivazione_obbligatoria)) },
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to R.string.approva, false to R.string.respingi).forEach { (approva, testo) ->
                        Button(enabled = motivo.isNotBlank(), onClick = {
                            scope.launch {
                                avviso = messaggio(io { sessione.api().decidi(r.breve.id, approva, motivo.trim()) })
                                if (avviso == null) ricarica()
                            }
                        }) { Text(stringResource(testo)) }
                    }
                }
                Text(stringResource(R.string.approva_spiegazione), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VediCome(sessione: Sessione, onAttivata: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ruolo by remember { mutableStateOf("school_admin") }
    var cerca by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.vedi_come), style = MaterialTheme.typography.headlineSmall, color = Blu)
        Text(stringResource(R.string.vedi_come_spiegazione), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("school_admin" to R.string.dirigente, "teacher" to R.string.docente).forEach { (id, testo) ->
                FilterChip(selected = ruolo == id, onClick = { ruolo = id }, label = { Text(stringResource(testo)) })
            }
        }
        OutlinedTextField(cerca, { cerca = it }, label = { Text(stringResource(R.string.cerca)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Carica("vedi$ruolo$cerca", { sessione.api().vediComeElenco(ruolo, cerca) }) { persone: List<Persona>, _ ->
            LazyColumn {
                items(persone, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            if (io { sessione.api().vediComeAttiva(p.id) } is Esito.Ok) onAttivata()
                        }
                    }.padding(vertical = 10.dp)) {
                        Text(p.persona)
                        Text(p.istituto, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
