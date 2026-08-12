# BDO Enhance Lab

Eine statische, für GitHub Pages geeignete Webanwendung zur Analyse von Enhancement-Kosten im Black Desert Online Central Market. Accessoires, Silver Embroidered und Manos-Kleidung werden mit getrennten Regelprofilen berechnet.

Für Verkaufspreise zählt ausschließlich die niedrigste aktuell vorhandene Preisstufe mit mindestens einem Verkäufer. Fehlt beim BASE-Einkauf eine Sell-Order, wird stattdessen die höchste im Orderbuch zulässige Preorder-Preisstufe angesetzt und sichtbar als **Preorder** markiert. Guide-, Durchschnitts- und zuletzt erzielte Preise werden niemals verwendet.

## Lokal starten

Voraussetzung ist Node.js 24.

```powershell
npm install
npm run dev
```

Qualitätsprüfung und Produktions-Build:

```powershell
npm run check
```

Der gebaute statische Inhalt liegt anschließend in `dist/`.

## GitHub Pages veröffentlichen

Der Workflow [pages.yml](.github/workflows/pages.yml) testet die Anwendung, baut sie mit dem Repository-Basispfad `/BlackDesertEnhancer/` und veröffentlicht `dist/` über GitHub Pages.

1. Repository zu GitHub pushen.
2. Unter **Settings → Pages → Build and deployment** als Quelle **GitHub Actions** wählen.
3. Den Workflow manuell starten oder auf einen Push nach `main` warten.

Der Workflow versucht zusätzlich alle sechs Stunden einen validierten Markt-Snapshot für den Ausfallpfad zu erstellen. Er übernimmt nur einen Snapshot mit allen drei Regelkategorien, gültigen Listingzuständen und mindestens 70 % Orderbuchabdeckung; andernfalls bleibt die zuletzt eingecheckte Version unverändert.

## Marktdaten und Stabilität

Die Browser-App verwendet ausschließlich CORS-fähige GET-Endpunkte der öffentlichen [Arsha Market API](https://github.com/guy0090/api.arsha.io). Der offizielle Pearl-Abyss-POST-Endpunkt kann von einer reinen GitHub-Pages-App wegen CORS nicht zuverlässig gelesen werden. Nur der serverseitige GitHub-Actions-Snapshot darf bei einem Arsha-Ausfall den offiziellen komprimierten Orderbuch-Endpunkt abrufen; ein getesteter Huffman-Decoder wandelt ihn vor dem statischen Build um.

Der Abruf arbeitet mit:

- 9-Sekunden-Timeout pro Versuch, maximal drei Versuchen und exponentiellem Backoff mit Jitter;
- Berücksichtigung von `Retry-After` bei temporären Fehlern;
- gebündelten Orderbuch-Abfragen, maximal drei parallelen Requests und kurzer Circuit-Breaker-Pause;
- Schema-, Größen-, ID-/SID- und Safe-Integer-Validierung;
- Teilresultaten statt eines globalen Abbruchs;
- 30-Minuten-Last-known-good-Cache und versioniertem EU-Snapshot als sichtbarem Fallback.

Ein erfolgreich geladenes Ziel-Orderbuch ohne Verkäufer ist der Zustand **Kein Listing**. Nur für BASE wird in diesem Fall die höchste vorhandene Orderbuch-Preisstufe als maximaler Preorder-Einkaufspreis genutzt. Auch dabei wird ausdrücklich nicht auf einen Guide- oder letzten Verkaufspreis zurückgefallen.

## Berechnungsmodelle

- **Accessoires:** stückweise Failstack-Chancen mit 90-%-Cap, Zerstörung/Rebuild und stufenspezifischem Ancient Anvil.
- **Silver Embroidered:** eigene +1-Kurve; darüber Accessoire-Kurven, UI-Bezeichnungen +1 bis +4.
- **Manos-Kleidung:** feste aktuelle PC-Chancen, Black Gems, Concentrated Magical Black Gems, Memory-Fragment-Reparatur, Agris und Downgrades.

Die Kosten werden deterministisch als Erwartungswert berechnet, nicht per zufälliger Monte-Carlo-Stichprobe. Failstack-Kosten können als direkter Marktwert ihrer aktuellen Materialbeschaffung oder bewusst als bereits vorhanden angesetzt werden. Bei einem Ancient-Anvil-Erfolg behandelt das Modell den gewachsenen Stack als weggepackten Vermögenswert und verwendet für einen späteren Rebuild einen neuen konfigurierten Startstack.

Nicht enthalten sind derzeit Cron-Strategien und Manos-Life-Accessoires. Die dritte Ansicht heißt deshalb bewusst **Manos-Kleidung**.

## Struktur

- `web/` – TypeScript-App, Marktadapter, Berechnungen und Tests
- `public/data/market-eu.json` – gebündelter Last-known-good-Snapshot
- `scripts/update-market-snapshot.mjs` – atomarer Snapshot-Generator
- `.github/workflows/pages.yml` – Tests, Build und Pages-Deployment
- `src/` – bestehende Java/Swing-Desktopanwendung; sie wird für den Pages-Build nicht verwendet
- `AUDIT.md` – vollständige Auditbefunde, Korrekturen und verbleibende Grenzen

## Quellen und Hinweis

Regelquellen: [Ancient Anvil](https://www.naeu.playblackdesert.com/DE-DE/Wiki?wikiNo=402), [Manos-Itemdaten](https://bdocodex.com/us/item/705037/), [aktuelle Silver-Embroidered-Änderung](https://www.naeu.playblackdesert.com/es-ES/News/Detail?groupContentNo=8996) und [direkte Failstack-Beschaffung](https://www.sa.playblackdesert.com/es-mx/Wiki?wikiNo=48).

Dies ist ein unabhängiges Community-Tool. Markt- und Spieldaten können sich durch Patches ändern; die Anwendung zeigt daher Quelle und Abrufzeit der Marktdaten an.
