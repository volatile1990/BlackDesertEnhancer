# Audit: Manos-only-Webanwendung

Stand: 15. September 2026

## Umfang

Geprüft wurden der tatsächlich ausgeführte Webcode, alle Marktpfade, Request-Anzahl, Preiswahl, Cache und Snapshot, Eingabevalidierung, Manos-Berechnung, UI, Tests, Produktions-Build und GitHub-Pages-Workflow. Der alte Java/Swing-Code unter `src/` ist nicht Bestandteil der Webanwendung.

## Behobene Ursache der unveränderten Preise

Der bisherige Teilfehlerpfad übernahm eine alte Einzelquote samt altem `fetchedAt`, setzte den äußeren Snapshot-Zeitpunkt aber auf den Zeitpunkt des neuen, fehlgeschlagenen Versuchs. Dadurch blieb der Gesamt-Cache frisch und konnte beispielsweise einen Preis vom 12. August immer wieder weitertragen.

Korrekturen:

- neues, inkompatibles Manos-only-Schema 3 und neuer Local-Storage-Key;
- Prüfung des Alters jeder einzelnen Quote statt nur des äußeren Snapshots;
- maximal 24 Stunden alte Fallback-Quoten, danach `Preis fehlt` statt einer Rechnung mit Altwerten;
- ein vollständiger Arsha-Ausfall schreibt den Cache nicht mehr neu;
- jeder angezeigte Preis enthält seinen eigenen Abrufzeitpunkt und seine Quelle im Tooltip.

## Marktumfang und Ausfallsicherheit

Die dynamische Katalogsuche und alle Accessoire-/Silver-Orderbücher wurden aus dem Webpfad entfernt. Eine geprüfte ID-Liste enthält genau acht Manos-Kleidungsstücke. Die Oberfläche ist auf den verwendeten EU-Markt begrenzt. Dort entstehen höchstens 35 logische Orderbuchpaare: acht Items mal vier Stufen plus drei Materialien. Zuvor waren es über 600.

Der Browser nutzt gebündelte Arsha-GET-Abfragen mit No-Store, Timeout, gezielten Retries, begrenzter Parallelität, Response-Größenlimit, Safe-Integer-/Schema-/ID-Prüfung und rekursiver Teilung fehlerhafter Batches. Ein globaler Circuit Breaker wurde entfernt, weil er bei parallelen Batchfehlern gerade die kleineren Recovery-Abfragen blockieren konnte; Deadline und Parallelitätslimit begrenzen den Abruf stattdessen deterministisch.

Der Build-Snapshot nutzt Arsha zuerst. Fehlende und leere Paare werden einzeln über den von Velia Inn dokumentierten Pearl-Abyss-POST-Endpunkt gegengeprüft. Der Decoder akzeptiert das aktuelle Huffman-Binärformat und ältere JSON-Envelopes. Dieser direkte Endpoint liefert keine CORS-Freigabe und kann deshalb nicht aus einer statischen GitHub-Pages-Seite aufgerufen werden. Ein neuer Snapshot ersetzt den Last-known-good-Stand nur bei 35/35 erwarteten Orderbüchern. Ein bestätigtes einzelnes leeres Buch bleibt als aktuelles „nicht gelistet“ erhalten; eine komplett leere Antwort gilt als unplausibler Anbieterfehler. Fremde oder fehlende Antworten zählen nicht zur Abdeckung.

Live-Prüfung am 15. September 2026:

- direkter Pearl-Abyss-Pfad: 32/32 Manos-Itemorderbücher erfolgreich;
- lokaler Snapshot-Lauf einschließlich Materialien: 35/35 Orderbücher erfolgreich;
- Arsha: im Prüfzeitraum durchgehend HTTP 500 / Fehlercode 103; deshalb wurde der direkte Build-Fallback tatsächlich benutzt;
- acht IDs und englische Namen separat über die Arsha-Datenbank bestätigt.

## Preissemantik

Orderbuchreihenfolge wird nicht vorausgesetzt.

- Verkauf: `min(price)` ausschließlich über Preisstufen mit `sellers > 0`.
- BASE mit Verkäufern: ebenfalls niedrigster Ask.
- BASE ohne Verkäufer: `max(price)` des validierten Orderbuchs als höchstmögliche Preorder-Stufe, auch wenn auf genau dieser Stufe noch kein Käufer steht.
- DUO, TRI und TET ohne Verkäufer: kein Preis und keine Profitrechnung.
- Materialien: nur niedrigstes aktives Verkaufsangebot, niemals Preorder.
- Guide-, Durchschnitts-, `basePrice`- und Last-Sold-Werte werden nicht als Marktpreis verwendet.

## Manos-Berechnung

Die Webrechnung enthält nur noch das Manos-Profil. Geprüft sind die 20 festen Chancen, Black-Gem-Mengen, eine Concentrated Magical Black Gem je PRI–PEN-Versuch, 5/10 Haltbarkeit, Memory-Fragment-Reparatur, Downgrade, Rebuild und Ancient-Anvil-Schwellen. Die Berechnung ist ein deterministischer Erwartungswert und verwendet Gleitkommazahlen für erwartete Kosten und Basisteile.

Nur drei Materialpreise bleiben konfigurierbar. Nicht verwendete Stack-Parameter, Stack-Materialien, Optimierungsdialoge, Kategorie-Tabs und Marketing-/Erklärüberschriften wurden aus der Weboberfläche entfernt.

## Prüfung

Die Tests decken insbesondere ab:

- exakt acht Manos-IDs und nur drei Materialien;
- SID-Zuordnung BASE 0, DUO 17, TRI 18 und TET 19;
- niedrigstes aktives, unsortiertes Ask;
- höchste BASE-Preorder-Stufe;
- kein Verkaufspreis bei buyers-only Orderbüchern;
- strikte Snapshot-/Orderbuchvalidierung;
- Ablauf jeder Einzelquote nach 24 Stunden;
- Retry nur für temporäre HTTP-Fehler;
- vollständige Manos-Chancentabelle und finite Downgrade-Erwartungen;
- keine Rechnung ohne aktuellen BASE-, Ziel- oder Materialpreis oder mit ungültigem Netto-Verkauf; `NaN` kann nicht unbemerkt als Ergebnis weiterlaufen.

Der Pages-Workflow führt Snapshot, Tests und TypeScript/Vite-Produktions-Build aus und veröffentlicht nur das statische `dist/`-Artefakt. Ein geplanter Lauf deployt nur nach erfolgreichem Snapshot; bei gleichzeitigem API-Ausfall kann er daher keinen neueren, zuvor erfolgreich veröffentlichten Stand durch den älteren Repository-Fallback ersetzen.

Das Abhängigkeits-Audit meldete zunächst eine moderate Schwachstelle im nur zur Entwicklung verwendeten Vitest-Mocker. Der Lockfile wurde auf die korrigierte Vitest-Version aktualisiert; `npm audit` meldet danach keine bekannte Schwachstelle.

## Verbleibende Grenzen

1. Arsha ist ein Community-Dienst und kann trotz Retry ausfallen oder upstream-gecachte Daten liefern.
2. GitHub Pages kann den direkten Pearl-Abyss-Fallback wegen CORS nicht zur Laufzeit nutzen. Er steht im spätestens alle sechs Stunden erzeugten Build-Snapshot bereit.
3. Bei gleichzeitigem Ausfall beider Wege werden nur höchstens 24 Stunden alte Einzelpreise verwendet; danach bleibt die entsprechende Rechnung bewusst leer.
4. Marktliquidität und Spielregeln können sich ändern. Ein gelisteter Preis garantiert keinen Verkauf.
