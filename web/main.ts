import "./styles.css";
import { DEFAULT_MATERIAL_PRICES, MATERIALS } from "./config";
import { analyzeItems } from "./calculations";
import { loadMarket, type MarketLoadResult } from "./market";
import type {
  CalculationSettings,
  ItemAnalysis,
  MarketQuote,
  MarketSnapshot,
  MaterialKey,
  Region,
  ResultLevel,
} from "./types";

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) throw new Error("App-Container fehlt");

let snapshot: MarketSnapshot | null = null;
let marketResult: MarketLoadResult | null = null;
const activeRegion: Region = "eu";
let expandedItemId: number | null = null;
let loading = true;
let searchTerm = "";
let sortMode = "profit";
let onlyCalculated = false;
let refreshGeneration = 0;
const manuallyEditedMaterials = new Set<MaterialKey>();

const settings: CalculationSettings = {
  taxRate: 0.845,
  materialPrices: Object.fromEntries(MATERIALS.map((material) => [material.key, Number.NaN])) as Record<MaterialKey, number>,
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
    <header class="market-overview">
      <div class="page-title">
        <h1>Manos-Kleidung</h1>
        <p>EU Central Market</p>
      </div>
      <div class="market-controls">
        <div class="market-source" aria-live="polite">
          <span class="status-dot" id="status-dot"></span>
          <span id="header-status">Marktdaten werden geladen</span>
          <span>Abruf <time id="updated-at">—</time></span>
        </div>
        <button class="button primary" id="refresh-button" type="button">Aktualisieren</button>
      </div>
    </header>

    <section class="workspace" id="results" aria-label="Manos-Kalkulation">
      <div class="warning-stack" id="warnings" role="status" aria-live="polite" hidden></div>

      <div class="toolbar">
        <label class="search-field">
          <span class="sr-only">Manos-Kleidung durchsuchen</span>
          <span class="search-icon" aria-hidden="true"></span>
          <input id="search-input" type="search" placeholder="Kleidung suchen …" autocomplete="off" />
        </label>
        <label class="field">
          <span>Sortierung</span>
          <select id="sort-select">
            <option value="profit">Bester Profit</option>
            <option value="tri">TRI-Profit</option>
            <option value="availability">Preisabdeckung</option>
            <option value="name">Name A–Z</option>
          </select>
        </label>
        <label class="check-field"><input id="only-calculated" type="checkbox" /><span>Nur mit Ergebnis</span></label>
        <button class="button secondary" id="settings-button" type="button" aria-expanded="false" aria-controls="settings-panel">Parameter</button>
      </div>

      <div class="settings-panel" id="settings-panel" hidden>
        <div class="field-grid economy-grid">
          <label class="field"><span>Netto-Verkauf</span><div class="input-suffix"><input id="tax-input" type="number" min="1" max="100" step="0.1" value="84.5" /><i>%</i></div></label>
          ${MATERIALS.map((material) => `
            <label class="field material-field"><span>${material.label}<small data-material-state="${material.key}">Wird geladen</small></span><div class="input-suffix"><input type="number" min="0" step="1000" data-material="${material.key}" value="" placeholder="${DEFAULT_MATERIAL_PRICES[material.key]}" /><i>Silber</i></div></label>
          `).join("")}
        </div>
      </div>

      <div class="table-shell">
        <table>
          <caption class="sr-only">Manos-Enhancement-Profit nach aktuellen Orderbuchpreisen</caption>
          <thead><tr><th scope="col">Kleidung</th><th scope="col">BASE-Kaufpreis</th><th scope="col">DUO</th><th scope="col">TRI</th><th scope="col">TET</th><th scope="col">Bestes Ergebnis</th></tr></thead>
          <tbody id="results-body"></tbody>
        </table>
        <div class="empty-state" id="empty-state">
          <span class="loading-orbit" aria-hidden="true"></span>
          <strong>Marktdaten werden geladen</strong>
          <p>Es werden ausschließlich die Orderbücher der acht Manos-Kleidungsstücke und ihrer drei Materialien abgefragt.</p>
        </div>
      </div>
    </section>

    <details class="calculation-notes">
      <summary>Berechnungsregeln</summary>
      <div class="notes-grid">
        <p><strong>Verkaufspreis</strong> Niedrigstes aktuell gelistetes Angebot mit mindestens einem Verkäufer.</p>
        <p><strong>BASE ohne Listing</strong> Höchste zulässige Preisstufe des Orderbuchs für eine Preorder.</p>
        <p><strong>Fallback</strong> Arsha läuft im Browser; der GitHub-Snapshot nutzt zusätzlich den von Velia Inn dokumentierten Pearl-Abyss-Zugriff.</p>
        <p><strong>Manos-Modell</strong> Feste Chancen, Reparatur, Downgrade und Ancient Anvil; keine Cron Stones.</p>
      </div>
    </details>
  </main>

  <footer>
    <a href="https://github.com/guy0090/api.arsha.io" target="_blank" rel="noreferrer">Arsha API</a>
    <a href="https://developers.veliainn.com/" target="_blank" rel="noreferrer">Velia-Inn-Marktdokumentation</a>
  </footer>
`;

function element<T extends HTMLElement>(id: string): T {
  const node = document.getElementById(id);
  if (!node) throw new Error(`Element #${id} fehlt`);
  return node as T;
}

function formatSilver(value: number | null, compact = true): string {
  if (value === null || !Number.isFinite(value)) return "—";
  return compact ? compactSilver.format(value) : fullSilver.format(Math.round(value));
}

function stateLabel(state: MarketQuote["state"]): string {
  return ({ fresh: "API", cached: "Cache", snapshot: "Snapshot", unlisted: "Kein Listing", error: "Fehler" })[state];
}

function quoteStamp(quote: MarketQuote): string {
  const parsed = Date.parse(quote.fetchedAt);
  return Number.isFinite(parsed) ? quoteTime.format(parsed) : "Zeit unbekannt";
}

function quoteOrigin(quote: MarketQuote): string {
  if (/Velia Inn-documented Pearl Abyss/i.test(quote.source)) return "PA/Velia-Fallback";
  if (/Arsha/i.test(quote.source)) return quote.state === "fresh" ? "Arsha" : `${stateLabel(quote.state)} · Arsha`;
  return stateLabel(quote.state);
}

function applyMarketMaterials(): void {
  if (!snapshot) return;
  for (const material of MATERIALS) {
    const quote = snapshot.materials[material.key];
    const input = document.querySelector<HTMLInputElement>(`[data-material="${material.key}"]`);
    const state = document.querySelector<HTMLElement>(`[data-material-state="${material.key}"]`);
    if (manuallyEditedMaterials.has(material.key)) {
      if (state) {
        state.textContent = "Manuell";
        state.title = "Manuell eingegebener Preis";
        state.dataset.state = "manual";
      }
      continue;
    }
    if (quote && quote.price !== null) {
      settings.materialPrices[material.key] = quote.price;
      if (input) input.value = String(quote.price);
    } else {
      settings.materialPrices[material.key] = Number.NaN;
      if (input) input.value = "";
    }
    if (state && quote) {
      state.textContent = quote.price === null
        ? `${quoteOrigin(quote)} · ${quoteStamp(quote)}`
        : `${quoteOrigin(quote)} · ${quote.sellersAtLowest} günstig / ${quote.totalSellers} gesamt · ${quoteStamp(quote)}`;
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
  refresh.disabled = loading;
  refresh.textContent = loading ? "Aktualisiere …" : "Aktualisieren";

  if (loading) {
    headerStatus.textContent = "Marktdaten werden geladen";
    headerStatus.removeAttribute("title");
    dot.dataset.state = "loading";
    return;
  }
  if (!snapshot || !marketResult) {
    headerStatus.textContent = "Marktdaten nicht verfügbar";
    headerStatus.removeAttribute("title");
    dot.dataset.state = "error";
    updated.textContent = "—";
    updated.removeAttribute("datetime");
    return;
  }

  const labels = { fresh: "Arsha-Orderbücher", partial: "Arsha teilweise", cached: "Lokaler Cache", snapshot: "GitHub-Snapshot" };
  headerStatus.textContent = labels[marketResult.status];
  headerStatus.title = snapshot.source;
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
  let analyses = analyzeItems(snapshot.items, settings);
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
    meta.textContent = `${quoteOrigin(quote)} · ${quoteStamp(quote)}`;
    meta.title = `Quelle: ${quote.source}`;
  } else if (quote.kind === "preorder") {
    meta.textContent = `Max. BASE-Kaufauftrag · ${quote.buyersAtPrice} auf dieser Stufe / ${quote.totalBuyers} gesamt · ${quoteOrigin(quote)} · ${quoteStamp(quote)}`;
    meta.title = `Höchste zulässige Kaufpreis-Stufe · Quelle: ${quote.source} · Stand: ${dateTime.format(Date.parse(quote.fetchedAt))}`;
  } else {
    meta.textContent = `${quote.sellersAtLowest} günstig · ${quote.totalSellers} gesamt · ${quoteOrigin(quote)} · ${quoteStamp(quote)}`;
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
  const quote = analysis.item.levels[String(resultLevel)];
  const cell = document.createElement("td");
  cell.className = `profit-cell ${profitClass(result.profit)}`;
  const strong = document.createElement("strong");
  if (result.profit === null) {
    strong.textContent = result.unavailableReason !== "target"
      ? "Nicht berechenbar"
      : quote?.state === "error" ? "Preis fehlt" : "Kein Listing";
    cell.append(strong);
    const reason = document.createElement("small");
    reason.textContent = result.unavailableReason === "material"
      ? "Materialpreis fehlt"
      : result.unavailableReason === "tax"
        ? "Netto-Verkauf ungültig"
        : result.unavailableReason === "base"
          ? "BASE-Preis fehlt"
          : `${result.label}-Verkaufspreis fehlt`;
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
  const grid = document.createElement("div");
  grid.className = "detail-grid";

  for (const result of analysis.results) {
    const card = document.createElement("article");
    const heading = document.createElement("strong");
    heading.className = "detail-level";
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
      : [["Berechnung", result.unavailableReason === "material"
        ? "Nicht möglich — aktueller Materialpreis fehlt"
        : result.unavailableReason === "tax"
          ? "Nicht möglich — Netto-Verkauf ist ungültig"
          : result.unavailableReason === "base"
            ? "Nicht möglich — aktueller BASE-Preis fehlt"
            : `Nicht möglich — aktuelles ${result.label}-Listing fehlt`]];
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

  if (loading) {
    empty.hidden = false;
    empty.querySelector("strong")!.textContent = "Marktdaten werden geladen";
    return;
  }

  const analyses = getVisibleAnalyses();
  empty.hidden = analyses.length > 0;
  if (analyses.length === 0) {
    empty.querySelector("strong")!.textContent = "Keine passenden Einträge";
    empty.querySelector("p")!.textContent = snapshot
      ? "Suche oder Filter anpassen."
      : "Weder Live-Orderbücher noch ein gültiger Snapshot sind verfügbar.";
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
    toggle.setAttribute("aria-controls", `detail-${analysis.item.id}`);
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
    basePrice.textContent = baseQuote?.price === null
      ? baseQuote.state === "error" ? "Preis fehlt" : "Kein Listing"
      : formatSilver(baseQuote?.price ?? null);
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
      .sort((left, right) => (right.profit ?? 0) - (left.profit ?? 0))[0];
    const bestStrong = document.createElement("strong");
    bestStrong.textContent = bestResult ? bestResult.label : "—";
    const bestSmall = document.createElement("small");
    bestSmall.textContent = bestResult
      ? `${bestResult.profit! >= 0 ? "+" : ""}${formatSilver(bestResult.profit)}`
      : "Keine Rechnung";
    bestCell.append(bestStrong, bestSmall);
    row.append(bestCell);
    fragment.append(row);
    if (expandedItemId === analysis.item.id) {
      const detail = makeDetailRow(analysis);
      detail.id = `detail-${analysis.item.id}`;
      fragment.append(detail);
    }
  }
  body.append(fragment);
}

function renderAll(): void {
  renderStatus();
  renderResults();
}

async function refreshMarket(force: boolean, foreground = true): Promise<void> {
  const generation = ++refreshGeneration;
  const requestedRegion = activeRegion;
  if (foreground) {
    loading = true;
    renderAll();
  }
  try {
    const result = await loadMarket(requestedRegion, force);
    if (generation !== refreshGeneration || requestedRegion !== activeRegion) return;
    marketResult = result;
    snapshot = result.snapshot;
    applyMarketMaterials();
  } catch (error) {
    if (generation !== refreshGeneration || requestedRegion !== activeRegion) return;
    if (foreground) {
      snapshot = null;
      marketResult = null;
    }
    const warnings = element<HTMLDivElement>("warnings");
    warnings.hidden = false;
    warnings.textContent = error instanceof Error ? error.message : "Marktdaten konnten nicht geladen werden.";
  } finally {
    if (generation !== refreshGeneration || requestedRegion !== activeRegion) return;
    loading = false;
    renderAll();
  }
}

element<HTMLButtonElement>("refresh-button").addEventListener("click", () => void refreshMarket(true));
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
element<HTMLInputElement>("tax-input").addEventListener("input", (event) => {
  const input = event.currentTarget as HTMLInputElement;
  const value = Number(input.value);
  if (Number.isFinite(value) && value > 0 && value <= 100) {
    input.removeAttribute("aria-invalid");
    settings.taxRate = value / 100;
  } else {
    input.setAttribute("aria-invalid", "true");
    settings.taxRate = Number.NaN;
  }
  renderResults();
});
document.querySelectorAll<HTMLInputElement>("[data-material]").forEach((input) => {
  input.addEventListener("input", () => {
    const key = input.dataset.material as MaterialKey;
    if (input.value.trim() === "") {
      input.removeAttribute("aria-invalid");
      manuallyEditedMaterials.delete(key);
      applyMarketMaterials();
      renderResults();
      return;
    }
    const value = Number(input.value);
    if (Number.isFinite(value) && value >= 0) {
      input.removeAttribute("aria-invalid");
      settings.materialPrices[key] = value;
      manuallyEditedMaterials.add(key);
      const state = document.querySelector<HTMLElement>(`[data-material-state="${key}"]`);
      if (state) {
        state.textContent = "Manuell";
        state.dataset.state = "manual";
      }
      renderResults();
    } else {
      input.setAttribute("aria-invalid", "true");
      settings.materialPrices[key] = Number.NaN;
      manuallyEditedMaterials.delete(key);
      const state = document.querySelector<HTMLElement>(`[data-material-state="${key}"]`);
      if (state) {
        state.textContent = "Ungültig";
        state.removeAttribute("title");
        state.dataset.state = "error";
      }
      renderResults();
    }
  });
});
element<HTMLTableSectionElement>("results-body").addEventListener("click", (event) => {
  const target = event.target as HTMLElement;
  const toggle = target.closest<HTMLButtonElement>("[data-expand-id]");
  if (!toggle) return;
  const itemId = Number(toggle.dataset.expandId);
  expandedItemId = expandedItemId === itemId ? null : itemId;
  renderResults();
  document.querySelector<HTMLButtonElement>(`[data-expand-id="${itemId}"]`)?.focus();
});

async function initialize(): Promise<void> {
  await refreshMarket(false);
  if (!snapshot || !marketResult) return;
  if (marketResult.refreshRecommended) {
    void refreshMarket(true, false);
  }
}

void initialize();
