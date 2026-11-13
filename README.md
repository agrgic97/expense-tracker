# Ausgaben-Tracker

Webapp zum Erfassen von Ausgaben mit Statistiken für Wochen, Monate und Jahre.

**Stack:** Vue 3 + TypeScript + Vite + Chart.js · Spring Boot 3.5 (Java 21) + Spring Security + JPA + Flyway · PostgreSQL 17 · Docker Compose

## Features

- Anmeldung mit Benutzername und Passwort (JWT), jeder Benutzer sieht nur seine eigenen Daten
- **Ausgaben und Einnahmen** erfassen, bearbeiten, löschen – mit Suche, Typ-, Kategorie- und Datumsfilter, Paginierung und CSV-Export
  - Ob eine Buchung Ausgabe oder Einnahme ist, bestimmt ihre Kategorie (Typ *Ausgabe* oder *Einnahme*)
- Eigene Kategorien mit Farben (Standardkategorien werden beim ersten Start angelegt)
- **Budgets:** monatliches Limit pro Ausgabe-Kategorie, gelb ab 80 %, rot über 100 %, mit Markierung, wie weit der Monat ist
- **Wiederkehrende Buchungen** (wöchentlich, monatlich, vierteljährlich, jährlich), z. B. Miete, Abos, Gehalt
  - Ein Job bucht fällige Einträge kurz nach Mitternacht und beim Start (holt Ausfallzeiten nach)
  - Liegt das Startdatum in der Vergangenheit, werden die verpassten Termine nachgebucht; nach einer Pause nicht
- **Regeln:** „Beschreibung enthält X → Kategorie Y“. Werden beim Erfassen und Import vorgeschlagen und lassen sich rückwirkend anwenden. Bei mehreren Treffern gewinnt der längste Suchtext.
- **CSV-Import** von Kontoauszügen (Sparkasse, ING, DKB, N26, comdirect …)
  - Die Datei wird im Browser gelesen: Encoding (UTF-8/Windows-1252), Trennzeichen, Kopfzeile und Spalten werden erkannt
  - Vorschau mit Kategorievorschlag; bereits vorhandene Buchungen (gleiches Datum, Betrag, Beschreibung) sind abgewählt
- Dashboard pro **Woche / Monat / Jahr** (vor- und zurückblättern):
  - Ausgaben mit Veränderung zur Vorperiode (bei laufender Periode fair gegen den gleichen Zeitraum der Vorperiode)
  - Saldo, Einnahmen und Sparquote, Ø pro Tag, Anzahl und Ø pro Ausgabe, Hochrechnung für die laufende Periode
  - Verlauf pro Tag bzw. Monat, Verteilung nach Kategorie, größte Ausgaben, Budgets (Monatsansicht)
  - Vergleich der letzten 12 Wochen/Monate/Jahre inkl. Einnahmen, Durchschnitt, Min und Max
- **Konto:** E-Mail-Adresse hinterlegen, Passwort ändern (meldet andere Geräte ab), **Passwort vergessen** per Mail-Link
- **Installierbar als App (PWA)** auf Handy und Desktop, mit Navigation unten und Schnellaktionen „Neue Ausgabe/Einnahme“

## Starten mit Docker

```bash
cp .env.example .env   # optional, sonst gelten Defaults
docker compose up -d --build
```

App: http://localhost:8080 (Port über `APP_PORT` änderbar). Die Daten liegen im Volume `db-data`.

## Authentifizierung

- **Login/Registrierung:** `POST /api/auth/login` bzw. `/api/auth/register` mit `{ "username", "password" }`. Passwörter werden mit BCrypt gehasht.
- **Tokens in httpOnly-Cookies:** JavaScript im Browser kann sie nicht lesen, XSS kann sie also nicht stehlen.
  - `access_token`: HS256-JWT, 15 Minuten gültig (`ACCESS_TOKEN_VALIDITY`), `Path=/api`
  - `refresh_token`: zufällig, 30 Tage gültig (`REFRESH_TOKEN_VALIDITY`), `Path=/api/auth`. In der DB steht nur sein SHA-256-Hash.
  - Beide Cookies sind `SameSite=Strict`. Hinter HTTPS `COOKIE_SECURE=true` setzen.
- **Refresh mit Rotation:** `POST /api/auth/refresh` tauscht das Refresh-Token gegen ein neues. Das Frontend tut das automatisch, sobald eine Anfrage mit 401 scheitert, und wiederholt sie dann.
  - Wird ein bereits getauschtes Token erneut benutzt, gilt es als gestohlen. Alle Tokens dieser Anmeldung werden widerrufen.
  - Ausnahme: Innerhalb von 30 s gibt es nur ein neues Access-Token, damit parallele Tabs sich nicht gegenseitig abmelden.
- **Logout:** `POST /api/auth/logout` widerruft die Anmeldung und löscht die Cookies.
- **CSRF-Schutz:** Weil der Browser Cookies automatisch mitschickt, brauchen schreibende Anfragen den Header `X-XSRF-TOKEN` mit dem Wert des Cookies `XSRF-TOKEN`. Ausnahme sind Anfragen mit `Authorization: Bearer …` (Skripte, curl).
- **Rate-Limit für Login und Registrierung:**
  - 5 Fehlversuche pro Benutzer und IP
  - 20 Fehlversuche pro Benutzer insgesamt
  - 30 Versuche pro IP
  - Alle Grenzen gelten je 15 Minuten. Danach antwortet die API mit `429` und `Retry-After`.
  - Die Zähler liegen im Speicher und werden bei einem Neustart zurückgesetzt.
- **Erster Benutzer:** Wer sich als Erstes registriert, übernimmt alle Daten, die vor Einführung der Anmeldung angelegt wurden. Jeder weitere Benutzer startet mit den Standardkategorien.
- **Passwort vergessen:** Nur verfügbar, wenn `MAIL_HOST` gesetzt ist. Der Link in der Mail ist 1 Stunde gültig (`PASSWORD_RESET_VALIDITY`) und nur einmal verwendbar; in der DB steht nur sein Hash. Danach sind alle Anmeldungen des Kontos beendet. Anfragen verraten nicht, ob es eine Adresse gibt, und sind auf 5 Mails pro Adresse und IP je 15 Minuten begrenzt. `APP_BASE_URL` muss auf die öffentliche Adresse zeigen, sonst führen die Links ins Leere.
- **`JWT_SECRET`** in `.env` setzen (mind. 32 Zeichen, z. B. `openssl rand -base64 48`). Mit `REGISTRATION_ENABLED=false` lässt sich die Registrierung abschalten.

## Produktion

Anleitung für den STRATO-VPS mit HTTPS über Caddy, Deploy- und Backup-Skript: [deploy/README.md](deploy/README.md)

## Lokale Entwicklung

```bash
docker compose up -d db                 # nur die Datenbank
cd backend && mvn spring-boot:run       # API auf :8080
cd frontend && npm install && npm run dev   # UI auf :5173, /api wird an :8080 weitergeleitet
```

Tests: `cd backend && mvn test`

Mails lokal testen mit [Mailpit](https://mailpit.axllent.org/) (Weboberfläche auf :8025):

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_STARTTLS=false APP_BASE_URL=http://localhost:5173 \
  mvn spring-boot:run
```

Der Service Worker der PWA wird nur im Produktions-Build registriert (`npm run build && npm run preview`), nicht mit `npm run dev`.

## Architektur

```
Browser ──> nginx (frontend, :80)
              ├── /        statisches Vue-Build (SPA)
              └── /api/*   ──> Spring Boot (backend, :8080) ──> PostgreSQL (db, :5432)
```

### API

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/auth/config` | Ob die Registrierung offen ist (öffentlich) |
| POST | `/api/auth/register`, `/api/auth/login` | Konto anlegen bzw. anmelden, setzt Token-Cookies (öffentlich) |
| POST | `/api/auth/refresh`, `/api/auth/logout` | Tokens erneuern bzw. abmelden (öffentlich, per Refresh-Cookie) |
| GET | `/api/auth/me` | Angemeldeter Benutzer |
| GET | `/api/expenses?from&to&categoryId&q&page&size` | Ausgaben (paginiert, neueste zuerst) |
| GET | `/api/expenses/export?…` | CSV-Export mit denselben Filtern |
| POST/PUT/DELETE | `/api/expenses[/{id}]` | Ausgabe anlegen/ändern/löschen |
| GET/POST/PUT/DELETE | `/api/categories[/{id}]` | Kategorien verwalten (Löschen nur wenn unbenutzt) |
| GET | `/api/stats/summary?period=WEEK\|MONTH\|YEAR&date=` | Kennzahlen der Periode, die `date` enthält |
| GET | `/api/stats/trend?period=…&count=12&date=` | Summen der letzten `count` Perioden |
