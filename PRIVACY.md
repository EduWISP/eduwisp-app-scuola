# Privacy — eduwisp-app-scuola

Documento vivo. Riferimenti: [ADR-0022](https://eduwisp.org/docs/decisioni/0022-app-mobili/), [quadro normativo](https://eduwisp.org/docs/sicurezza-privacy/quadro-normativo/).

- **Sul telefono**: indirizzo del server, token di accesso (cifrato con il Keystore), ruolo scelto, numero dell'ultimo controllo delle richieste. **Nessun dato di studenti** salvato: l'app lo chiede al server ogni volta.
- **Niente backup** nel cloud né trasferimento ad altri dispositivi; solo HTTPS.
- **Notifiche**: solo il numero di richieste in attesa, senza nomi.
- **Nessun servizio di terzi**: niente statistiche, pubblicità, Firebase o Play Services.
- Disconnettendosi, il token viene revocato sul server.

---

🇬🇧 *Stores only server address, encrypted access token and chosen role on the phone; no student data, no cloud backup, HTTPS only, notifications without names, no third-party services.*
