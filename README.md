<p align="center"><img src="https://raw.githubusercontent.com/EduWISP/.github/main/branding/eduwisp-mark.svg" alt="EduWISP" height="72"></p>

# eduwisp-app-scuola

**App per il personale della scuola** · *School staff app*

App Android per docenti, coordinatori e dirigenti ([ADR-0022](https://eduwisp.org/docs/decisioni/0022-app-mobili/)). Il **menu cambia con il ruolo** e arriva dal server, con gli stessi permessi del portale:

| Ruolo | Cosa vede e fa |
|---|---|
| Docente | le proprie classi, studenti e stato dei tablet, app consentite; **sblocca un'app** per la lezione e la richiude; decide sulle **richieste di app** delle sue classi con motivazione |
| Coordinatore | in più, la voce **Coordinamento**: le classi coordinate e le loro richieste |
| Dirigente | l'**istituto** (riepilogo e tutte le classi) e tutte le richieste, senza decidere |
| Amministratore | il **«vedi come»** in sola lettura e registrato |

> 🧪 **Stato: prototipo v0.** Provata sull'emulatore Android 15 con la [demo](https://demo.eduwisp.org): utente `demo`, password `demo` (sola lettura). Test chiuso su Google Play con il gruppo [eduwisp-tester](https://groups.google.com/g/eduwisp-tester).

🇬🇧 *Android app for teachers, class coordinators and head teachers (ADR-0022): role-based menu from the server, classes, tablets, lesson app unlocks, app-request decisions. **Status: prototype v0**; try it with the demo (user `demo`, password `demo`, read-only).*

## Scelte / Design choices

- Kotlin e Jetpack Compose; **nessun servizio Google** (niente Firebase né Play Services, [ADR-0009](https://eduwisp.org/docs/decisioni/0009-solo-software-libero/)).
- Accesso con utente e password dell'istituto; il token del telefono è cifrato con il **Keystore** di Android; solo HTTPS; niente backup nel cloud.
- Nessuna copia dei dati di studenti sul telefono: l'app chiede al server ciò che mostra.
- Le richieste di app in attesa si controllano **ogni 15 minuti** (WorkManager); la notifica dice solo quante sono, senza nomi, perché compare sulla schermata di blocco.

## Compilare / Build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Serve l'API [`eduwisp-api`](https://github.com/EduWISP/eduwisp-api) sul server dell'istituto (rilascio 0.8.0 o successivo di `ansible-eduwisp`).

## Privacy

Dati trattati e regole: [PRIVACY.md](PRIVACY.md). Il rispetto della normativa su privacy e minori è un vincolo bloccante ([ADR-0008](https://eduwisp.org/docs/decisioni/0008-conformita-normativa/)).

## Licenza / License

**AGPL-3.0-or-later**, con termini aggiuntivi di attribuzione e tutela del nome (sezione 7): vedi [LICENSE](LICENSE) e [NOTICE.md](NOTICE.md). Chi usa o modifica EduWISP deve sempre indicare «Basato su EduWISP» e non può usare il nome e il logo per versioni modificate. Licenza scelta secondo le [linee guida AgID](https://eduwisp.org/docs/decisioni/0006-licenza/) per il software usato via rete.

*AGPL-3.0-or-later with additional attribution and trademark terms (section 7): see LICENSE and NOTICE.md.*

I contributi richiedono il [DCO](https://github.com/EduWISP/.github/blob/main/CONTRIBUTING.md#dco) (`git commit -s`).
