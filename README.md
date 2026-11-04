# Ausgaben-Tracker

Webapp zum Erfassen von Ausgaben mit Statistiken für Wochen, Monate und Jahre.

**Stack:** Vue 3 + TypeScript + Vite + Chart.js · Spring Boot 3.5 (Java 21) + JPA + Flyway · PostgreSQL 17 · Docker Compose

## Features

- Ausgaben erfassen, bearbeiten, löschen – mit Suche, Kategorie- und Datumsfilter, Paginierung und CSV-Export
- Eigene Kategorien mit Farben (9 Standardkategorien werden beim ersten Start angelegt)
- Dashboard pro **Woche / Monat / Jahr** (vor- und zurückblättern):
  - Gesamtsumme mit Veränderung zur Vorperiode (bei laufender Periode fair gegen den gleichen Zeitraum der Vorperiode)
  - Ø pro Tag, Anzahl und Ø pro Ausgabe, Hochrechnung für die laufende Periode
  - Verlauf pro Tag bzw. Monat, Verteilung nach Kategorie, größte Ausgaben
  - Vergleich der letzten 12 Wochen/Monate/Jahre inkl. Durchschnitt, Min und Max

## Starten mit Docker

```bash
cp .env.example .env   # optional, sonst gelten Defaults
docker compose up -d --build
```

App: http://localhost:8080 (Port über `APP_PORT` änderbar). Die Daten liegen im Volume `db-data`.

## Lokale Entwicklung

```bash
docker compose up -d db                 # nur die Datenbank
cd backend && mvn spring-boot:run       # API auf :8080
cd frontend && npm install && npm run dev   # UI auf :5173, /api wird an :8080 weitergeleitet
```

Tests: `cd backend && mvn test`

## Architektur

```
Browser ──> nginx (frontend, :80)
              ├── /        statisches Vue-Build (SPA)
              └── /api/*   ──> Spring Boot (backend, :8080) ──> PostgreSQL (db, :5432)
```

### API

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/expenses?from&to&categoryId&q&page&size` | Ausgaben (paginiert, neueste zuerst) |
| GET | `/api/expenses/export?…` | CSV-Export mit denselben Filtern |
| POST/PUT/DELETE | `/api/expenses[/{id}]` | Ausgabe anlegen/ändern/löschen |
| GET/POST/PUT/DELETE | `/api/categories[/{id}]` | Kategorien verwalten (Löschen nur wenn unbenutzt) |
| GET | `/api/stats/summary?period=WEEK\|MONTH\|YEAR&date=` | Kennzahlen der Periode, die `date` enthält |
| GET | `/api/stats/trend?period=…&count=12&date=` | Summen der letzten `count` Perioden |
