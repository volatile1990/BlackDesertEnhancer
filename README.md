# Manos-Kleidung

Statische GitHub-Pages-Anwendung für die Enhancement-Kosten der acht Manos-Kleidungsstücke im EU Central Market. Accessoires, Silver Embroidered und andere Regionen sind nicht Teil der Webanwendung.

## Preise

- Verkaufspreise sind ausschließlich das niedrigste aktuell gelistete Angebot mit mindestens einem Verkäufer.
- Fehlt bei BASE ein Verkaufsangebot, gilt die höchste im Orderbuch zulässige Preisstufe als Preorder-Einkaufspreis.
- Durchschnitts-, Guide- und Last-Sold-Preise werden nicht verwendet.
- Abgefragt werden 32 Kleidungs-Orderbücher (BASE, DUO, TRI und TET) sowie Black Gem, Concentrated Magical Black Gem und Memory Fragment: insgesamt 35 statt zuvor über 600 Orderbücher.

Im Browser wird die CORS-fähige [Arsha API](https://github.com/guy0090/api.arsha.io) verwendet. Der GitHub-Actions-Snapshot fragt bei einem Arsha-Ausfall zusätzlich den von [Velia Inn](https://developers.veliainn.com/) dokumentierten Pearl-Abyss-Endpunkt ab. Dieser zweite Weg ist wegen fehlender CORS-Freigabe nur im Build möglich, nicht direkt von GitHub Pages.

Der Browser-Cache gilt zehn Minuten. Jede übernommene Einzelquote darf höchstens 24 Stunden alt sein; ein teilweise fehlgeschlagener Abruf kann ihren Zeitstempel nicht verlängern. Das Manos-only-Schema und der Cache-Key haben Version 3, sodass alte gemischte Daten automatisch verworfen werden.

Fehlt einer der drei aktuellen Materialpreise, wird keine Profitrechnung ausgegeben. Die grauen Zahlen in leeren Materialfeldern sind nur Eingabeplatzhalter; gerechnet wird damit erst nach einer bewussten manuellen Eingabe.

## Lokal

Node.js 24:

```powershell
npm install
npm run check
npm run snapshot
npm run dev
```

`npm run snapshot` ersetzt `public/data/market-eu.json` nur bei vollständiger, validierter Abdeckung aller 35 Orderbücher. Einzelne leere Arsha-Bücher werden zusätzlich direkt bei Pearl Abyss geprüft und können danach „nicht gelistet“ bedeuten; eine komplett leere Antwort wird als Anbieterfehler verworfen. Die Datei wird über einen temporären Pfad ersetzt.

## GitHub Pages

Der Workflow [.github/workflows/pages.yml](.github/workflows/pages.yml) läuft bei jedem Push auf `main`, manuell und alle sechs Stunden. Er aktualisiert den Snapshot, testet, baut und veröffentlicht `dist/`. Scheitern beide Marktwege in einem geplanten Lauf, wird nicht neu deployed; die zuletzt erfolgreich veröffentlichte Seite bleibt dadurch erhalten.

Seite: [volatile1990.github.io/BlackDesertEnhancer](https://volatile1990.github.io/BlackDesertEnhancer/)

## Berechnung

Das deterministische Manos-Modell berücksichtigt die festen Erfolgschancen, Black Gems bis +15, Concentrated Magical Black Gems ab PRI, Haltbarkeitsreparatur mit Memory Fragments, Downgrades und die stufenspezifischen Ancient-Anvil-Schwellen. Cron Stones sind nicht eingerechnet.

`src/` enthält weiterhin die frühere Java/Swing-Anwendung, wird aber weder geladen noch in den Pages-Build aufgenommen.
