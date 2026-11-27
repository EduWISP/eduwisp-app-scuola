# Pubblicazione su Google Play

Scheda (testi, icona, immagine in evidenza, schermate): [`fastlane/metadata/android/`](../../fastlane/metadata/android/), in italiano (`it-IT`) e inglese (`en-US`). Le schermate vengono dalla demo, con soli dati fittizi.

## Pacchetto

Il pacchetto (AAB) si firma con la **chiave di caricamento** del progetto (Play App Signing: l'app finale la firma Google). Compilazione: `./gradlew bundleRelease`, poi `jarsigner`.

## Dati per la revisione

- Accesso: server `https://demo.eduwisp.org`, utente `demo`, password `demo` (sola lettura, dati inventati).
- Informativa privacy: <https://eduwisp.org/docs/sicurezza-privacy/app-scuola/> (inglese: <https://eduwisp.org/docs/en/security-privacy/app-school/>).
- Categoria: Istruzione. Destinatari: adulti (personale della scuola), **non** bambini.
- Pubblicità: no. Acquisti: no.

## Sicurezza dei dati (risposte proposte)

| Domanda | Risposta |
|---|---|
| L'app raccoglie o condivide dati? | Raccoglie: sì (verso il server dell'istituto). Condivide con terzi: **no** |
| Dati raccolti | **Informazioni personali → ID utente** (nome utente dell'istituto), per **funzionalità dell'app** e **gestione dell'account**; obbligatorio |
| | **Informazioni sul dispositivo o altri ID** → no (il modello del telefono serve solo a riconoscere l'accesso: indicarlo come «ID utente/account» se richiesto) |
| Dati elaborati solo sul dispositivo | ruolo scelto, numero di richieste in attesa |
| Crittografia in transito | **sì** (solo HTTPS) |
| Eliminazione dei dati | sì: uscendo dall'app il token viene revocato; l'account lo gestisce l'istituto |
| Posizione, contatti, foto, file, audio, cronologia, identificativi pubblicitari | **no** |

L'app mostra dati di altre persone (studenti) letti dal server dell'istituto, senza salvarli sul telefono: non sono dati «raccolti» dall'utente dell'app.

## Test chiuso

Tester: gruppo [eduwisp-tester@googlegroups.com](https://groups.google.com/g/eduwisp-tester). Per un account sviluppatore personale nuovo Google chiede almeno 12 tester per 14 giorni prima della produzione.
