# Vollständiges Projekt-Audit

Stand: 12. August 2026

## Umfang und Ergebnis

Geprüft wurden Architektur, GitHub-Pages-Tauglichkeit, Markt-API und CORS, Preissemantik, Fehlerpfade, Parallelität, Cache, Zahlenbereiche, Enhancement-Regeln, Erwartungswertmodell, UI-Zustände, Barrierefreiheit, Tests, Build und Abhängigkeiten.

Der bisherige Java/Swing-Stand ist keine Webanwendung und lässt sich nicht auf GitHub Pages ausführen. Die neue TypeScript/Vite-Anwendung ist deshalb eine getrennte, statisch baubare Neufassung. Die Java-Quellen bleiben als Legacy-Desktopcode erhalten, sind aber nicht Teil des Web-Builds.

## P0: Markt- und Architekturfehler

### Offizielle API ist aus GitHub Pages nicht lesbar

Der bestehende Connector sendet Browser-unzulässige Header und POSTs direkt an `eu-trade.naeu.playblackdesert.com`. Dessen Antwort enthält für eine fremde GitHub-Pages-Origin keinen nutzbaren `Access-Control-Allow-Origin`-Header. GitHub Pages kann selbst keinen Runtime-Proxy betreiben.

**Überarbeitung:** Die Web-App nutzt zur Laufzeit nur CORS-fähige Arsha-GETs. Kategorien und Orderbücher werden gebündelt abgefragt. Die GitHub Action kann serverseitig – und damit ohne Browser-CORS – einen statischen Snapshot über den offiziellen komprimierten Orderbuch-Endpunkt erzeugen; dessen Binärformat wird größenvalidiert und mit einem getesteten Huffman-Decoder gelesen. Falls sekundengenaue oder vertraglich garantierte Daten erforderlich werden, ist weiterhin ein eigener Proxy beziehungsweise eine selbst gehostete Arsha-Instanz nötig.

### Falscher Preis bei fehlenden Listings

Der Desktop-Connector initialisiert Preise aus `GetWorldMarketSubList` und überschreibt sie nur, wenn ein Orderbuch Verkäufer enthält. Damit bleiben bei null Verkäufern Guide-/letzte Verkaufspreise als angeblich aktueller Marktpreis stehen. Auch der 4-Millionen-Filter nutzt vorab einen solchen Referenzpreis.

**Überarbeitung:** Die Webdomäne trennt `price`, Quote-Status, Verkäufer an der günstigsten Stufe, Gesamtverkäufer, Quelle und Abrufzeit. Nur `orders.filter(sellers > 0)` wird berücksichtigt. Bei keinem Ask ist `price: null` und `state: unlisted`; die Rechnung und das Ranking bleiben aus. Käufer, Guidepreis, Durchschnitt und letzter Verkauf sind keine Fallbacks.

### Ein API-Fehler leert den gesamten Lauf

Im Desktopcode fehlen Connect-/Read-Timeouts, Retry, Backoff, `Retry-After`, Schema-/Statusprüfung und ein Circuit Breaker. Kategorie-Futures werden mit `join()` global gekoppelt, ein Detailfehler entfernt das vollständige Item, und ein äußerer Catch liefert eine leere Liste. Die UI kann anschließend alte Zeilen als scheinbar erfolgreich aktualisiert stehen lassen. Ein Abruf erzeugt außerdem ungefähr 265 Einzelrequests, ein großer Teil davon seriell.

**Überarbeitung:** Timeout, gezielte Wiederholungen für Netzwerkfehler sowie 408/425/429/5xx, exponentieller Full-Jitter, begrenzte Parallelität, Batches, `allSettled`-Teilresultate, kurze Circuit-Pause und atomarer Last-known-good-Snapshot. Fehler, Cache und Snapshot sind sichtbare Zustände; sie werden nie als frische Daten beschriftet.

### Ungültige oder überlaufende API-Werte

Der Desktopcode liest `basePrice` als 32-Bit-`int`, obwohl aktuelle Marktpreise darüber liegen können. Status, Content-Type, Responsegröße, IDs, SIDs und JSON-Schema werden nicht konsequent geprüft.

**Überarbeitung:** Alle externen Ganzzahlen müssen nichtnegative JavaScript-Safe-Integer sein. Antwortgröße, JSON-Content-Type, Objektform, Orderzahl sowie ID/SID-Zuordnung werden validiert. Unerwartete HTML-/Fehlerantworten werden verworfen.

### Materialpreise sind statisch

Black Stone, Black Gem, Concentrated Magical Black Gem und Memory Fragment waren feste Konstanten; dadurch konnten selbst korrekte Itempreise keine aktuellen Kosten ergeben.

**Überarbeitung:** Alle verwendeten Materialien einschließlich Crystallized Despair und Primordial Black Stone werden über dasselbe Listing-first-Orderbuch geladen. Manuelle Werte bleiben möglich und werden sichtbar als manuell markiert.

## P0: Fach- und Rechenfehler

### Softcap-Formel kann Chancen senken

Die Java-Berechnung wendet nach Überschreiten eines Softcaps den kleinen Zuwachs rückwirkend auf alle hinzugekommenen Fehlschläge an. So kann eine Chance beim nächsten Fehlschlag sinken. Außerdem fehlt das 90-%-Cap und `roll <= chance` erzeugt einen theoretischen Erfolg bei exakt 0 %.

**Überarbeitung:** Chance wird rein aus dem gesamten aktuellen FS stückweise vor/nach Softcap berechnet und auf 90 % begrenzt. Regressionstests decken den Übergang ab, etwa Standard-PRI FS18 = 70 % und FS19 = 70,5 %.

### Silver Embroidered verwendet die falsche +1-Kurve

Silver besitzt eine eigene +1-Kurve: 30 % Basis, +3 Prozentpunkte bis FS14, danach +0,6; höhere Stufen folgen der Standard-Accessoire-Kurve. Der Desktopcode nutzt nach Fehlschlägen pauschal die Accessoire-Kurve. Zudem werden Silver-Stufen als PRI/DUO/TRI/TET beschriftet.

**Überarbeitung:** Eigenes Profil und eigene Ansicht mit +1/+2/+3/+4-Bezeichnungen. Hart codierte, fehlerhafte Tabellenwerte wurden durch Profilformeln ersetzt.

### Zufallssimulation, Ganzzahldivision und veralteter Profit

Der Desktoprechner verwendet rechenintensive Monte-Carlo-Läufe, schneidet durchschnittlichen Itemverbrauch durch Ganzzahldivision ab und hält abgeleitete Profitwerte teilweise nach Feldänderungen nicht synchron; dadurch sind NaN oder veraltete Werte möglich.

**Überarbeitung:** Exakte endliche Erwartungswertrekursion mit Gleitkomma-Itemverbrauch. Ergebnisse werden als unveränderliche Ableitung bei jedem Parameterwechsel neu berechnet.

### Agris und gewachsener Failstack sind vermischt

Ancient Anvil setzt Agris zurück, verbraucht laut offizieller Regel aber die aktuelle Enhancement Chance nicht. Das Desktopmodell setzt einen gemeinsamen Fehlerzähler zurück und verliert damit den gewachsenen Stack.

**Überarbeitung:** Die Webrechnung macht die gewählte Kostenstrategie explizit: Nach einem garantierten Klick gilt der gewachsene Stack als weggepackter Vermögenswert. Ein späterer Rebuild bezieht einen neuen konfigurierten Startstack. Dadurch wird der Stack weder gelöscht noch als stillschweigend kostenlos wiederverwendet. Eine spätere Inventar-/Stackzustandssimulation kann diese Strategie erweitern.

### Failstacks ab +50 wurden falsch beziehungsweise kostenlos bewertet

Die alte Kostentabelle setzt teils unplausible Black-Stone-Mengen an; hohe Stacks waren implizit kostenlos. Das überschätzt besonders TET-Profite.

**Überarbeitung:** Sichtbare Auswahl zwischen Marktwert der direkten Beschaffung und bewusst `vorhanden / Kosten 0`. +50 bis +100 werden über 4/8/15/25/35/50 Crystallized Despair, +110 über 25 Primordial Black Stones bewertet. Die Materialpreise kommen aus aktiven Listings oder einem klar markierten manuellen Wert.

### Manos-Prüfung

Die aktuelle Manos-Kleidungsfolge wurde gegen aktuelle Itemdaten geprüft. Korrekt sind die Chancen:

`100, 100, 100, 100, 100, 100, 100, 70, 60, 50, 40, 30, 20, 15, 10, 30, 25, 20, 15, 6`

Damit sind +6 und +7 derzeit tatsächlich 100 %; verbreitete 90/80-Tabellen sind veraltet. Ebenfalls bestätigt sind Black-Gem-Gruppen, eine Concentrated Magical Black Gem pro PRI–PEN-Versuch, 5/10 Haltbarkeit und Downgrade ab einem fehlgeschlagenen DUO→TRI-Versuch. Die Webrekursion bildet Material, Reparatur, Agris, Downgrade und Rebuild ab.

## P1: Datenmodell und UX

- Der Desktop-Ergebnistyp kennt keine Kategorie. Die Web-App bietet drei getrennte Tabs und kontextspezifische Labels/Parameter.
- Namensbasierte Klassifikation bleibt patch- und sprachabhängig. Die App lädt fest englische Namen, nutzt enge Präfixe und schließt unklare Manos-Life-Accessoires aus, anstatt sie falsch zu berechnen. Eine langfristige ID-Profilliste bleibt vorzuziehen.
- Seit August 2025 sind im Livekatalog nur noch wenige Silver-Embroidered-Items relevant; alte Cook-Fixtures sind keine aktuelle Katalogerwartung.
- Die dritte Kategorie wird ausdrücklich **Manos-Kleidung** genannt. Manos-Life-Accessoires benötigen ein separates Profil und sind noch nicht enthalten.
- Zeitstempel, Quelle, Verkäuferzahl, `Live`/`Cache`/`Snapshot`/`Kein Listing`/`Fehler`, Teilfehler und manuelle Materialwerte sind sichtbar.
- Tastaturnavigation, semantische Tabelle, Dialog, Live-Status, Skip-Link, Fokuszustände, reduzierbare Animation und responsive Darstellung wurden ergänzt.

## P1: Abhängigkeiten und Build

Der Legacy-Mavenstand verwendet unter anderem alte Versionen von `org.json`, Commons Lang und Logback mit veröffentlichten Advisories; Java 16 ist EOL. Diese JVM-Abhängigkeiten werden nicht in den Web-Build übernommen. Die Webanwendung besitzt nur die gelockten Build-/Testwerkzeuge TypeScript, Vite und Vitest, keine Runtime-NPM-Abhängigkeiten und eine restriktive Content Security Policy.

Die Pages-Pipeline führt Tests und Produktions-Build aus, erzeugt den korrekten Repository-Basispfad und deployed nur das statische `dist/`-Artefakt.

`npm ci` und `npm audit` melden für die 49 gelockten Web-Buildpakete derzeit keine bekannte Schwachstelle. Dependabot überwacht npm- und GitHub-Actions-Versionen wöchentlich.

Im Repository fehlt weiterhin eine Lizenzdatei. Sie wurde nicht automatisch erfunden, weil die Wahl der Lizenz eine Rechteentscheidung des Eigentümers ist. Ebenso bleibt der Legacy-Mavenstand ohne Wrapper; beides blockiert den statischen Pages-Build nicht.

## Testabdeckung der Web-Neufassung

- niedrigstes aktives Ask bei ungeordneten Preisstufen;
- leeres und buyers-only Orderbuch ergibt `null`;
- ungültiges Schema und Zahlen über Safe-Integer werden verworfen;
- temporäres 503 wird wiederholt, permanentes 404 nicht;
- HTML statt JSON wird verworfen;
- Standard-/Silver-Softcaps, Monotonie und 90-%-Cap;
- vollständige aktuelle Manos-Chancentabelle;
- direkte Stackmaterial-Kosten und bewusster Owned-Modus;
- fractional expected items, finite Manos-Downgrade-Erwartung;
- fehlendes Listing deaktiviert Profit;
- Kategorie- und SID-Trennung.

## Bewusst verbleibende Grenzen

1. Die öffentliche Arsha-Instanz ist ein Community-Dienst und laut Dokumentation gecacht; „Live“ bedeutet daher der aktuell abrufbare Orderbuch-Snapshot, nicht garantierte Sekundengenauigkeit.
2. Eine rein statische Pages-App kann keinen offiziellen API-Proxy betreiben. Für garantierte Verfügbarkeit ist ein separater Dienst erforderlich.
3. Cron-Stone-Strategien und Manos-Life-Accessoires sind noch nicht modelliert; die UI behauptet dies nicht.
4. Enhancement-Regeln, Itemkatalog und direkte Stackbeschaffung können sich durch Patches ändern. Regeltests und der Snapshot-Workflow reduzieren, beseitigen aber nicht das Patch-Risiko.
5. Ein erfolgreicher Verkauf zu einer aktuell gelisteten Preisstufe ist nicht garantiert; die Rechnung ist eine Erwartungswertanalyse, keine Handelszusage.

## Primär- und Referenzquellen

- [Arsha API Quellcode](https://github.com/guy0090/api.arsha.io)
- [Arsha API V2 Dokumentation](https://www.postman.com/bdomarket/arsha-io-bdo-market-api/documentation/qpavrc8/bdo-market-api-v2)
- [Pearl Abyss: Ancient Anvil](https://www.naeu.playblackdesert.com/DE-DE/Wiki?wikiNo=402)
- [Aktuelle Manos-Kleidungsdaten](https://bdocodex.com/us/item/705037/)
- [Pearl Abyss: Silver-Embroidered-Entfernung, August 2025](https://www.naeu.playblackdesert.com/es-ES/News/Detail?groupContentNo=8996)
- [Pearl Abyss: direkte zusätzliche Enhancement Chance](https://www.sa.playblackdesert.com/es-mx/Wiki?wikiNo=48)
