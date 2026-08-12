import "./styles.css";
import {
  DEFAULT_MATERIAL_PRICES,
  DEFAULT_STACKS,
  LEVEL_LABELS,
  MATERIALS,
  STACK_OPTIONS,
} from "./config";
import { analyzeItems, optimizeTriStacks } from "./calculations";
import { loadMarket, type MarketLoadResult } from "./market";
import type {
  CalculationSettings,
  Category,
  ItemAnalysis,
  MarketQuote,
  MarketSnapshot,
  MaterialKey,
  Region,
  ResultLevel,
} from "./types";

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) throw new Error("App-Container fehlt");

const categoryCopy: Record<Category, { title: string; eyebrow: string; description: string; rule: string }> = {
  accessory: {
    title: "Accessoires",
    eyebrow: "Klassische Enhancement-Logik",
    description: "Eigene Failstacks je Stufe, Zerstörung bei Fehlschlag und Ancient-Anvil-Schutz.",
    rule: "PRI · DUO · TRI · TET",
  },
  silver: {
    title: "Silver Embroidered",
    eyebrow: "Separate +Stufen",
    description: "Eigene +1-Erfolgskurve; höhere Stufen folgen der Accessoire-Logik.",
    rule: "+1 · +2 · +3 · +4",
  },
  manos: {
    title: "Manos-Kleidung",
    eyebrow: "Feste Erfolgschancen",
    description: "Keine Failstacks; Black Gems, Haltbarkeitsreparatur und Downgrades ab TRI-Versuchen.",
    rule: "Fixed chance · no stacks",
  },
};

let snapshot: MarketSnapshot | null = null;
let marketResult: MarketLoadResult | null = null;
let activeCategory: Category = "accessory";
let activeRegion: Region = "eu";
let expandedItemId: number | null = null;
let loading = true;
let searchTerm = "";
let sortMode = "profit";
let onlyCalculated = false;
const manuallyEditedMaterials = new Set<MaterialKey>();

const settings: CalculationSettings = {
  taxRate: 0.845,
  stackCostMode: "market",
  stacks: { ...DEFAULT_STACKS },
  materialPrices: { ...DEFAULT_MATERIAL_PRICES },
};

const compactSilver = new Intl.NumberFormat("de-DE", {
  notation: "compact",
  maximumFractionDigits: 1,
});
const fullSilver = new Intl.NumberFormat("de-DE", { maximumFractionDigits: 0 });
const decimal = new Intl.NumberFormat("de-DE", { maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat("de-DE", { dateStyle: "medium", timeStyle: "short" });
const quoteTime = new Intl.DateTimeFormat("de-DE", {
  day: "2-digit",
  month: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

app.innerHTML = `
  <main id="top">
    <section class="market-overview" aria-labelledby="page-title">
      <div class="page-title">
        <h1 id="page-title">BDO Enhancement-Kosten</h1>
        <p id="hero-region-label">EU Central Market</p>
      </div>
      <div class="market-controls">
        <div class="market-source" aria-live="polite">
          <span class="status-dot" id="status-dot"></span>
          <span id="header-status">Marktdaten werden geladen</span>
          <span>Stand <time id="updated-at">—</time></span>
        </div>
        <label class="field compact-field">
          <span>Region</span>
          <select id="region-select" aria-label="Marktregion">
            <option value="eu">EU</option>
            <option value="na">NA</option>
          </select>
        </label>
        <button class="button primary" id="refresh-button" type="button">Aktualisieren</button>
      </div>
    </section>

    <section class="category-section" aria-labelledby="category-title">
      <h2 class="sr-only" id="category-title">Verstärkungstyp</h2>
      <div class="category-tabs" role="tablist" aria-label="Verstärkungstyp auswählen">
        ${(["accessory", "silver", "manos"] as Category[]).map((category, index) => `
          <button class="category-tab${index === 0 ? " active" : ""}" id="tab-${category}" type="button" role="tab" data-category="${category}" aria-controls="results" aria-selected="${index === 0}" tabindex="${index === 0 ? "0" : "-1"}">
            <span class="tab-copy"><strong>${categoryCopy[category].title}</strong><small>${categoryCopy[category].eyebrow}</small></span>
            <span class="tab-count" data-count="${category}">—</span>
          </button>
        `).join("")}
      </div>
      <div class="category-note">
        <p id="category-description">${categoryCopy.accessory.description}</p>
        <span id="category-rule">${categoryCopy.accessory.rule}</span>
      </div>
    </section>

    <section class="workspace" aria-labelledby="workspace-title">
      <div class="workspace-heading">
        <h2 id="workspace-title">Ergebnisse</h2>
        <p id="calculation-method">Erwartungskosten inklusive Ancient Anvil, ohne Cron Stones.</p>
      </div>

      <div class="warning-stack" id="warnings" role="status" aria-live="polite" hidden></div>

      <div class="summary-grid" aria-label="Zusammenfassung">
        <article><span>Items</span><strong id="metric-items">—</strong><small>in dieser Kategorie</small></article>
        <article><span>Berechenbar</span><strong id="metric-calculated">—</strong><small>mit BASE-Einkauf & Ziel-Listing</small></article>
        <article class="accent-metric"><span>Bester Profit</span><strong id="metric-best">—</strong><small id="metric-best-item">wartet auf Marktdaten</small></article>
        <article><span>Preisabdeckung</span><strong id="metric-coverage">—</strong><small>aktive Ziel-Listings</small></article>
      </div>

      <div class="toolbar">
        <label class="search-field">
          <span class="sr-only">Items durchsuchen</span>
          <span class="search-icon" aria-hidden="true"></span>
          <input id="search-input" type="search" placeholder="Item suchen …" autocomplete="off" />
        </label>
        <label class="field">
          <span>Sortierung</span>
          <select id="sort-select">
            <option value="profit">Bester Profit</option>
            <option value="tri">TRI / +3 Profit</option>
            <option value="availability">Preisabdeckung</option>
            <option value="name">Name A–Z</option>
          </select>
        </label>
        <label class="check-field"><input id="only-calculated" type="checkbox" /><span>Nur mit Ergebnis</span></label>
        <button class="button secondary" id="settings-button" type="button" aria-expanded="false" aria-controls="settings-panel">Parameter</button>
      </div>

      <div class="settings-panel" id="settings-panel" hidden>
        <section id="stack-settings" aria-labelledby="stack-settings-title">
          <div class="settings-title"><h3 id="stack-settings-title">Stacks je Versuchsstufe</h3><p id="stack-context">Gilt für Accessoires und Silver Embroidered.</p></div>
          <div class="field-grid stack-grid">
            ${[0, 1, 2, 3].map((stage) => `
              <label class="field"><span data-stack-label="${stage}">${["PRI", "DUO", "TRI", "TET"][stage]}</span><select data-stack-stage="${stage}">${STACK_OPTIONS.map((value) => `<option value="${value}"${DEFAULT_STACKS[stage] === value ? " selected" : ""}>+${value}</option>`).join("")}</select></label>
            `).join("")}
          </div>
        </section>
        <section aria-labelledby="economy-settings-title">
          <div class="settings-title"><h3 id="economy-settings-title">Steuer & Materialien</h3><p>Live-Listings können jederzeit manuell überschrieben werden.</p></div>
          <div class="field-grid economy-grid">
            <label class="field"><span>Netto-Verkauf</span><div class="input-suffix"><input id="tax-input" type="number" min="1" max="100" step="0.1" value="84.5" /><i>%</i></div></label>
            <label class="field"><span>Stack-Bewertung</span><select id="stack-cost-mode"><option value="market">Marktwert der Erzeugung</option><option value="owned">Vorhanden / Kosten 0</option></select></label>
            ${MATERIALS.map((material) => `
              <label class="field material-field"><span>${material.label}<small data-material-state="${material.key}">Fallback</small></span><div class="input-suffix"><input type="number" min="0" step="1000" data-material="${material.key}" value="${DEFAULT_MATERIAL_PRICES[material.key]}" /><i>Silber</i></div></label>
            `).join("")}
          </div>
        </section>
      </div>

      <div class="table-shell" id="results" role="tabpanel" aria-labelledby="tab-accessory" tabindex="-1">
        <table>
          <caption class="sr-only">Enhancement-Profit nach aktuellen Orderbuchpreisen</caption>
          <thead><tr><th scope="col">Item</th><th scope="col">BASE Einkauf</th><th scope="col" data-level-heading="2">DUO</th><th scope="col" data-level-heading="3">TRI</th><th scope="col" data-level-heading="4">TET</th><th scope="col">Bestes Ergebnis</th></tr></thead>
          <tbody id="results-body"></tbody>
        </table>
        <div class="empty-state" id="empty-state">
          <span class="loading-orbit" aria-hidden="true"></span>
          <strong>Marktdaten werden aufbereitet</strong>
          <p>Orderbücher werden gebündelt und mit begrenzten Wiederholungen geladen.</p>
        </div>
      </div>
    </section>

    <details class="calculation-notes">
      <summary>Berechnungsgrundlagen</summary>
      <div class="notes-grid">
        <p><strong>Zielpreis</strong> Günstigstes aktives Verkäuferangebot.</p>
        <p><strong>BASE ohne Listing</strong> Höchste zulässige Preorder-Preisstufe.</p>
        <p><strong>Fehlende Daten</strong> Cache und Snapshot werden mit Stand gekennzeichnet; ohne Ziel-Listing keine Rechnung.</p>
        <p><strong>Ancient Anvil</strong> Gewachsener Failstack wird nach einem garantierten Klick als weggepackt behandelt; ein Rebuild nutzt den konfigurierten Startstack.</p>
      </div>
    </details>
  </main>

  <footer>
    <a href="https://github.com/guy0090/api.arsha.io" target="_blank" rel="noreferrer">Arsha API</a>
    <a href="https://www.naeu.playblackdesert.com/DE-DE/Wiki?wikiNo=402" target="_blank" rel="noreferrer">Ancient-Anvil-Regeln</a>
  </footer>

  <dialog id="optimize-dialog" aria-labelledby="optimize-title">
    <form method="dialog"><button class="dialog-close" aria-label="Dialog schließen">×</button></form>
    <h2 id="optimize-title">Beste Stack-Kombination</h2>
    <div id="optimize-content"></div>
  </dialog>
`;

function element<T extends HTMLElement>(id: string): T {
  const node = document.getElementById(id);
  if (!node) throw new Error(`Element #${id} fehlt`);
  return node as T;
}

function formatSilver(value: number | null, compact = true): string {
  if (value === null || !Number.isFinite(value)) return "—";
  return `${compact ? compactSilver.format(value) : fullSilver.format(Math.round(value))}`;
}

function stateLabel(state: MarketQuote["state"]): string {
  return ({ fresh: "API", cached: "Cache", snapshot: "Snapshot", unlisted: "Kein Listing", error: "Fehler" })[state];
}

function applyMarketMaterials(): void {
  if (!snapshot) return;
  for (const material of MATERIALS) {
    const quote = snapshot.materials[material.key];
    if (quote?.price !== null && !manuallyEditedMaterials.has(material.key)) {
      settings.materialPrices[material.key] = quote.price;
      const input = document.querySelector<HTMLInputElement>(`[data-material="${material.key}"]`);
      if (input) input.value = String(quote.price);
    }
    const state = document.querySelector<HTMLElement>(`[data-material-state="${material.key}"]`);
    if (state && quote) {
      const age = ["cached", "snapshot"].includes(quote.state) && Number.isFinite(Date.parse(quote.fetchedAt))
        ? ` · ${quoteTime.format(Date.parse(quote.fetchedAt))}`
        : "";
      state.textContent = `${stateLabel(quote.state)} · ${quote.sellersAtLowest} günstig / ${quote.totalSellers} gesamt${age}`;
      state.title = `Quelle: ${quote.source}`;
      state.dataset.state = quote.state;
    }
  }
}

function renderStatus(): void {
  const headerStatus = element<HTMLSpanElement>("header-status");
  const dot = element<HTMLSpanElement>("status-dot");
  const updated = element<HTMLTimeElement>("updated-at");
  const refresh = element<HTMLButtonElement>("refresh-button");
  element("hero-region-label").textContent = `${activeRegion.toUpperCase()} Central Market`;
  refresh.disabled = loading;
  refresh.textContent = loading ? "Aktualisiere …" : "Aktualisieren";

  if (loading) {
    headerStatus.textContent = "Marktdaten werden geladen";
    dot.dataset.state = "loading";
    return;
  }
  if (!snapshot || !marketResult) {
    headerStatus.textContent = "Marktdaten nicht verfügbar";
    dot.dataset.state = "error";
    return;
  }

  const labels = { fresh: "API-Orderbücher", partial: "Teilweise API", cached: "Lokaler Cache", snapshot: "GitHub-Snapshot" };
  headerStatus.textContent = labels[marketResult.status];
  dot.dataset.state = marketResult.status;
  const parsed = Date.parse(snapshot.fetchedAt);
  updated.textContent = Number.isFinite(parsed) ? dateTime.format(parsed) : "Unbekannt";
  updated.dateTime = snapshot.fetchedAt;

  const warnings = element<HTMLDivElement>("warnings");
  warnings.replaceChildren();
  warnings.hidden = marketResult.warnings.length === 0;
  for (const warning of marketResult.warnings) {
    const paragraph = document.createElement("p");
    paragraph.textContent = warning;
    warnings.append(paragraph);
  }
}

function availableResultCount(analysis: ItemAnalysis): number {
  return analysis.results.filter((result) => result.status === "ok").length;
}

function getVisibleAnalyses(): ItemAnalysis[] {
  if (!snapshot) return [];
  let analyses = analyzeItems(
    snapshot.items.filter((item) => item.category === activeCategory),
    settings,
  );
  const query = searchTerm.trim().toLocaleLowerCase("de");
  if (query) analyses = analyses.filter((analysis) => analysis.item.name.toLocaleLowerCase("de").includes(query));
  if (onlyCalculated) analyses = analyses.filter((analysis) => availableResultCount(analysis) > 0);

  return analyses.sort((left, right) => {
    if (sortMode === "name") return left.item.name.localeCompare(right.item.name);
    if (sortMode === "availability") return availableResultCount(right) - availableResultCount(left) || left.item.name.localeCompare(right.item.name);
    if (sortMode === "tri") {
      const leftProfit = left.results.find((result) => result.level === 3)?.profit ?? Number.NEGATIVE_INFINITY;
      const rightProfit = right.results.find((result) => result.level === 3)?.profit ?? Number.NEGATIVE_INFINITY;
      return rightProfit - leftProfit;
    }
    return (right.bestProfit ?? Number.NEGATIVE_INFINITY) - (left.bestProfit ?? Number.NEGATIVE_INFINITY);
  });
}

function addQuoteLine(container: HTMLElement, quote: MarketQuote | undefined): void {
  const meta = document.createElement("small");
  if (!quote) {
    meta.textContent = "Preisstatus unbekannt";
  } else if (quote.price === null) {
    meta.textContent = stateLabel(quote.state);
  } else if (quote.kind === "preorder") {
    const age = ["cached", "snapshot"].includes(quote.state) && Number.isFinite(Date.parse(quote.fetchedAt))
      ? ` · ${quoteTime.format(Date.parse(quote.fetchedAt))}`
      : "";
    meta.textContent = `Preorder-Max · ${quote.buyersAtPrice} dort / ${quote.totalBuyers} Käufer · ${stateLabel(quote.state)}${age}`;
    meta.title = `Höchste im Orderbuch zulässige Kaufpreis-Stufe · Quelle: ${quote.source} · Stand: ${dateTime.format(Date.parse(quote.fetchedAt))}`;
  } else {
    const age = ["cached", "snapshot"].includes(quote.state) && Number.isFinite(Date.parse(quote.fetchedAt))
      ? ` · ${quoteTime.format(Date.parse(quote.fetchedAt))}`
      : "";
    meta.textContent = `${quote.sellersAtLowest} günstig · ${quote.totalSellers} gesamt · ${stateLabel(quote.state)}${age}`;
    meta.title = `Quelle: ${quote.source} · Stand: ${dateTime.format(Date.parse(quote.fetchedAt))}`;
  }
  container.append(meta);
}

function profitClass(value: number | null): string {
  if (value === null) return "unavailable";
  return value >= 0 ? "positive" : "negative";
}

function makeProfitCell(analysis: ItemAnalysis, resultLevel: ResultLevel): HTMLTableCellElement {
  const result = analysis.results.find((entry) => entry.level === resultLevel)!;
  const cell = document.createElement("td");
  cell.className = `profit-cell ${profitClass(result.profit)}`;
  const strong = document.createElement("strong");
  const quote = analysis.item.levels[String(resultLevel)];
  if (result.profit === null) {
    strong.textContent = "Kein Listing";
    cell.append(strong);
    const reason = document.createElement("small");
    reason.textContent = analysis.item.levels["0"]?.price === null ? "BASE fehlt" : `${result.label} nicht gelistet`;
    cell.append(reason);
    return cell;
  }
  strong.textContent = `${result.profit >= 0 ? "+" : ""}${formatSilver(result.profit)}`;
  strong.title = `${fullSilver.format(Math.round(result.profit))} Silber Profit`;
  cell.append(strong);
  addQuoteLine(cell, quote);
  return cell;
}

function makeDetailRow(analysis: ItemAnalysis): HTMLTableRowElement {
  const detailRow = document.createElement("tr");
  detailRow.className = "detail-row";
  const cell = document.createElement("td");
  cell.colSpan = 6;
  const panel = document.createElement("div");
  panel.className = "detail-panel";

  const intro = document.createElement("div");
  intro.className = "detail-intro";
  const title = document.createElement("strong");
  title.textContent = "Deterministischer Erwartungswert";
  const copy = document.createElement("p");
  copy.textContent = activeCategory === "manos"
    ? "Feste Chancen, Materialverbrauch, Reparaturen, Downgrades und stufenspezifische Agris-Schwellen — ohne Cron Stones."
    : "Zerstörte Basisteile, stückweise Failstack-Kurven, Stack-Kosten und stufenspezifische Agris-Schwellen — ohne Cron Stones.";
  intro.append(title, copy);
  if (activeCategory !== "manos") {
    const optimize = document.createElement("button");
    optimize.type = "button";
    optimize.className = "button tertiary";
    optimize.dataset.optimizeId = String(analysis.item.id);
    optimize.textContent = "TRI-Stacks optimieren";
    intro.append(optimize);
  }
  panel.append(intro);

  const grid = document.createElement("div");
  grid.className = "detail-grid";
  for (const result of analysis.results) {
    const card = document.createElement("article");
    const heading = document.createElement("h4");
    heading.textContent = result.label;
    card.append(heading);
    const values: Array<[string, string]> = result.status === "ok"
      ? [
          [analysis.item.levels["0"]?.kind === "preorder" ? "BASE Preorder-Max" : "BASE Listing", `${formatSilver(analysis.item.levels["0"]?.price ?? null, false)} Silber`],
          ["Aktuelles Listing", `${formatSilver(result.salePrice, false)} Silber`],
          ["Ø Herstellkosten", `${formatSilver(result.avgCost, false)} Silber`],
          ["Ø Basisteile", decimal.format(result.expectedItems ?? 0)],
          ["Netto-Marge", result.margin === null ? "—" : `${(result.margin * 100).toFixed(1)} %`],
          ["Erwarteter Profit", `${result.profit !== null && result.profit >= 0 ? "+" : ""}${formatSilver(result.profit, false)} Silber`],
        ]
      : [["Berechnung", "Nicht möglich — BASE-Orderbuch oder aktuelles Ziel-Listing fehlt"]];
    const list = document.createElement("dl");
    for (const [term, value] of values) {
      const dt = document.createElement("dt");
      dt.textContent = term;
      const dd = document.createElement("dd");
      dd.textContent = value;
      list.append(dt, dd);
    }
    card.append(list);
    grid.append(card);
  }
  panel.append(grid);
  cell.append(panel);
  detailRow.append(cell);
  return detailRow;
}

function renderResults(): void {
  const body = element<HTMLTableSectionElement>("results-body");
  const empty = element<HTMLDivElement>("empty-state");
  body.replaceChildren();

  document.querySelectorAll<HTMLElement>("[data-level-heading]").forEach((heading) => {
    const level = Number(heading.dataset.levelHeading) as ResultLevel;
    heading.textContent = LEVEL_LABELS[activeCategory][level];
  });

  if (loading) {
    empty.hidden = false;
    empty.querySelector("strong")!.textContent = "Marktdaten werden aufbereitet";
    empty.querySelector("p")!.textContent = "Orderbücher werden gebündelt und mit begrenzten Wiederholungen geladen.";
    return;
  }

  const analyses = getVisibleAnalyses();
  empty.hidden = analyses.length > 0;
  if (analyses.length === 0) {
    empty.querySelector("strong")!.textContent = "Keine passenden Items";
    empty.querySelector("p")!.textContent = "Passe Suche oder Filter an. Items ohne aktives Listing bleiben standardmäßig sichtbar.";
  }

  const fragment = document.createDocumentFragment();
  for (const analysis of analyses) {
    const row = document.createElement("tr");
    row.className = "result-row";
    row.dataset.itemId = String(analysis.item.id);
    const nameCell = document.createElement("th");
    nameCell.scope = "row";
    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "item-toggle";
    toggle.dataset.expandId = String(analysis.item.id);
    toggle.setAttribute("aria-expanded", String(expandedItemId === analysis.item.id));
    const itemName = document.createElement("strong");
    itemName.textContent = analysis.item.name;
    const itemMeta = document.createElement("small");
    itemMeta.textContent = `Item ${analysis.item.id} · Details ${expandedItemId === analysis.item.id ? "schließen" : "öffnen"}`;
    toggle.append(itemName, itemMeta);
    nameCell.append(toggle);
    row.append(nameCell);

    const baseCell = document.createElement("td");
    baseCell.className = "listing-cell";
    const baseQuote = analysis.item.levels["0"];
    const basePrice = document.createElement("strong");
    basePrice.textContent = baseQuote?.price === null ? "Kein Listing" : formatSilver(baseQuote?.price ?? null);
    if (baseQuote?.kind === "preorder") baseCell.classList.add("preorder-cell");
    if (baseQuote?.price) basePrice.title = `${fullSilver.format(baseQuote.price)} Silber`;
    baseCell.append(basePrice);
    addQuoteLine(baseCell, baseQuote);
    row.append(baseCell);

    row.append(makeProfitCell(analysis, 2), makeProfitCell(analysis, 3), makeProfitCell(analysis, 4));
    const bestCell = document.createElement("td");
    bestCell.className = `best-cell ${profitClass(analysis.bestProfit)}`;
    const bestResult = analysis.results
      .filter((result) => result.profit !== null)
      .sort((a, b) => (b.profit ?? 0) - (a.profit ?? 0))[0];
    const bestStrong = document.createElement("strong");
    bestStrong.textContent = bestResult ? bestResult.label : "—";
    const bestSmall = document.createElement("small");
    bestSmall.textContent = bestResult ? `${bestResult.profit! >= 0 ? "+" : ""}${formatSilver(bestResult.profit)}` : "Keine Rechnung";
    bestCell.append(bestStrong, bestSmall);
    row.append(bestCell);
    fragment.append(row);
    if (expandedItemId === analysis.item.id) fragment.append(makeDetailRow(analysis));
  }
  body.append(fragment);
  renderMetrics(analyses);
}

function renderMetrics(analyses: ItemAnalysis[]): void {
  const calculated = analyses.reduce((sum, analysis) => sum + availableResultCount(analysis), 0);
  const totalTargets = analyses.length * 3;
  const best = analyses
    .filter((analysis) => analysis.bestProfit !== null)
    .sort((left, right) => (right.bestProfit ?? 0) - (left.bestProfit ?? 0))[0];
  element("metric-items").textContent = String(analyses.length);
  element("metric-calculated").textContent = String(calculated);
  element("metric-coverage").textContent = totalTargets ? `${Math.round((calculated / totalTargets) * 100)} %` : "0 %";
  element("metric-best").textContent = best?.bestProfit === null || !best ? "—" : `${best.bestProfit >= 0 ? "+" : ""}${formatSilver(best.bestProfit)}`;
  element("metric-best-item").textContent = best?.item.name ?? "kein aktives Ziel-Listing";
}

function renderCategories(): void {
  const items = snapshot?.items ?? [];
  for (const category of ["accessory", "silver", "manos"] as Category[]) {
    const count = items.filter((item) => item.category === category).length;
    document.querySelector<HTMLElement>(`[data-count="${category}"]`)!.textContent = String(count);
  }
  document.querySelectorAll<HTMLButtonElement>("[data-category]").forEach((button) => {
    const selected = button.dataset.category === activeCategory;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-selected", String(selected));
    button.tabIndex = selected ? 0 : -1;
  });
  element("results").setAttribute("aria-labelledby", `tab-${activeCategory}`);
  element("category-description").textContent = categoryCopy[activeCategory].description;
  element("category-rule").textContent = categoryCopy[activeCategory].rule;
  element("calculation-method").textContent = activeCategory === "manos"
    ? "Feste Chancen inklusive Reparatur, Downgrade und Ancient Anvil — ohne Crons."
    : "Stückweise Failstack-Kurven inklusive Ancient Anvil — ohne Crons.";
  element<HTMLElement>("stack-settings").classList.toggle("disabled-section", activeCategory === "manos");
  element("stack-context").textContent = activeCategory === "manos"
    ? "Manos nutzt feste Chancen; diese Werte beeinflussen die aktuelle Ansicht nicht."
    : activeCategory === "silver"
      ? "+1 nutzt die separate Silver-Embroidered-Kurve."
      : "Failstacks werden stückweise bis und nach dem jeweiligen Softcap berechnet.";
  const labels = activeCategory === "silver" ? ["+1-Versuch", "+2-Versuch", "+3-Versuch", "+4-Versuch"] : ["PRI-Versuch", "DUO-Versuch", "TRI-Versuch", "TET-Versuch"];
  document.querySelectorAll<HTMLElement>("[data-stack-label]").forEach((label) => {
    label.textContent = labels[Number(label.dataset.stackLabel)] ?? "Stack";
  });
}

function renderAll(): void {
  renderStatus();
  renderCategories();
  renderResults();
}

async function refreshMarket(force: boolean): Promise<void> {
  loading = true;
  renderAll();
  try {
    marketResult = await loadMarket(activeRegion, force);
    snapshot = marketResult.snapshot;
    applyMarketMaterials();
  } catch (error) {
    snapshot = null;
    marketResult = null;
    const warnings = element<HTMLDivElement>("warnings");
    warnings.hidden = false;
    warnings.textContent = error instanceof Error ? error.message : "Marktdaten konnten nicht geladen werden.";
  } finally {
    loading = false;
    renderAll();
  }
}

function showOptimization(itemId: number): void {
  if (!snapshot) return;
  const item = snapshot.items.find((candidate) => candidate.id === itemId);
  if (!item) return;
  const result = optimizeTriStacks(item, settings);
  const dialog = element<HTMLDialogElement>("optimize-dialog");
  const title = element("optimize-title");
  const content = element<HTMLDivElement>("optimize-content");
  title.textContent = item.name;
  content.replaceChildren();
  if (!result) {
    const paragraph = document.createElement("p");
    paragraph.textContent = "Für die Optimierung werden ein verfügbarer BASE-Einkaufspreis und ein aktives TRI-/+3-Listing benötigt.";
    content.append(paragraph);
  } else {
    const lead = document.createElement("p");
    lead.textContent = "Beste aufsteigende Kombination mit der gewählten Stack-Bewertung für das aktuelle TRI-/+3-Listing:";
    const stacks = document.createElement("div");
    stacks.className = "optimized-stacks";
    ["PRI / +1", "DUO / +2", "TRI / +3"].forEach((label, index) => {
      const entry = document.createElement("span");
      entry.innerHTML = `<small>${label}</small><strong>+${result.stacks[index]}</strong>`;
      stacks.append(entry);
    });
    const summary = document.createElement("p");
    summary.className = profitClass(result.profit);
    summary.textContent = `Erwarteter Profit: ${result.profit >= 0 ? "+" : ""}${formatSilver(result.profit, false)} Silber · Ø Kosten: ${formatSilver(result.avgCost, false)} Silber`;
    content.append(lead, stacks, summary);
  }
  dialog.showModal();
}

document.querySelectorAll<HTMLButtonElement>("[data-category]").forEach((button) => {
  button.addEventListener("click", () => {
    activeCategory = button.dataset.category as Category;
    expandedItemId = null;
    renderAll();
  });
  button.addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const tabs = Array.from(document.querySelectorAll<HTMLButtonElement>("[data-category]"));
    const current = tabs.indexOf(button);
    const targetIndex = event.key === "Home"
      ? 0
      : event.key === "End"
        ? tabs.length - 1
        : (current + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
    tabs[targetIndex]?.click();
    tabs[targetIndex]?.focus();
  });
});

element<HTMLButtonElement>("refresh-button").addEventListener("click", () => void refreshMarket(true));
element<HTMLSelectElement>("region-select").addEventListener("change", (event) => {
  activeRegion = (event.currentTarget as HTMLSelectElement).value as Region;
  manuallyEditedMaterials.clear();
  void refreshMarket(true);
});
element<HTMLInputElement>("search-input").addEventListener("input", (event) => {
  searchTerm = (event.currentTarget as HTMLInputElement).value;
  renderResults();
});
element<HTMLSelectElement>("sort-select").addEventListener("change", (event) => {
  sortMode = (event.currentTarget as HTMLSelectElement).value;
  renderResults();
});
element<HTMLInputElement>("only-calculated").addEventListener("change", (event) => {
  onlyCalculated = (event.currentTarget as HTMLInputElement).checked;
  renderResults();
});
element<HTMLButtonElement>("settings-button").addEventListener("click", (event) => {
  const panel = element<HTMLDivElement>("settings-panel");
  panel.hidden = !panel.hidden;
  (event.currentTarget as HTMLButtonElement).setAttribute("aria-expanded", String(!panel.hidden));
});
document.querySelectorAll<HTMLSelectElement>("[data-stack-stage]").forEach((select) => {
  select.addEventListener("change", () => {
    settings.stacks[Number(select.dataset.stackStage)] = Number(select.value);
    renderResults();
  });
});
element<HTMLInputElement>("tax-input").addEventListener("input", (event) => {
  const value = Number((event.currentTarget as HTMLInputElement).value);
  if (Number.isFinite(value) && value > 0 && value <= 100) {
    settings.taxRate = value / 100;
    renderResults();
  }
});
element<HTMLSelectElement>("stack-cost-mode").addEventListener("change", (event) => {
  settings.stackCostMode = (event.currentTarget as HTMLSelectElement).value as "market" | "owned";
  renderResults();
});
document.querySelectorAll<HTMLInputElement>("[data-material]").forEach((input) => {
  input.addEventListener("input", () => {
    const key = input.dataset.material as MaterialKey;
    const value = Number(input.value);
    if (Number.isFinite(value) && value >= 0) {
      settings.materialPrices[key] = value;
      manuallyEditedMaterials.add(key);
      const state = document.querySelector<HTMLElement>(`[data-material-state="${key}"]`);
      if (state) {
        state.textContent = "Manuell";
        state.dataset.state = "manual";
      }
      renderResults();
    }
  });
});
element<HTMLTableSectionElement>("results-body").addEventListener("click", (event) => {
  const target = event.target as HTMLElement;
  const optimize = target.closest<HTMLButtonElement>("[data-optimize-id]");
  if (optimize) {
    showOptimization(Number(optimize.dataset.optimizeId));
    return;
  }
  const toggle = target.closest<HTMLButtonElement>("[data-expand-id]");
  if (toggle) {
    const itemId = Number(toggle.dataset.expandId);
    expandedItemId = expandedItemId === itemId ? null : itemId;
    renderResults();
    document.querySelector<HTMLButtonElement>(`[data-expand-id="${itemId}"]`)?.focus();
  }
});

void refreshMarket(false);
