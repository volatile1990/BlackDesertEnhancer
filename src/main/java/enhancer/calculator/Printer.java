package enhancer.calculator;

import enhancer.models.AccessoryResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class Printer {
    // ANSI Escape Codes für Farben
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";

    // Textformatierung
    private static final String BOLD = "\u001B[1m";

    public void printComprehensiveTable(List<AccessoryResult> results) {
        // Sortiere nach dem höchsten Profit (unabhängig vom Level)
        results.sort((a, b) -> Long.compare(
                Math.max(Math.max(b.duoProfit, b.triProfit), b.tetProfit),
                Math.max(Math.max(a.duoProfit, a.triProfit), a.tetProfit)
        ));

        String format = "%-39s | %10s | %15s | %15s | %15s | %15s";
        log.info("\n" + BOLD + BLUE + "ACCESSORY ENHANCEMENT PROFIT ANALYSIS" + RESET);
        log.info(YELLOW + "{}" + RESET, "=".repeat(130));
        log.info("{}", String.format(format,
                BOLD + "Name" + RESET,
                BOLD + "Base Stock" + RESET,
                BOLD + "DUO Profit" + RESET,
                BOLD + "TRI Profit" + RESET,
                BOLD + "TET Profit" + RESET,
                BOLD + "Best Level" + RESET));
        log.info(YELLOW + "{}" + RESET, "-".repeat(130));

        for (AccessoryResult result : results) {
            // Bestimme das profitabelste Enhancement-Level
            String bestLevel = getBestEnhancementLevel(result);

            log.info("{}", String.format(format,
                    truncateString(result.name),
                    result.baseStock,
                    colorProfitOutput(result.duoProfit, bestLevel.equals("DUO")),
                    colorProfitOutput(result.triProfit, bestLevel.equals("TRI")),
                    colorProfitOutput(result.tetProfit, bestLevel.equals("TET")),
                    colorLevelOutput(bestLevel)
            ));
        }
        log.info(YELLOW + "{}" + RESET, "=".repeat(130));
    }

    // Bestimmt das profitabelste Enhancement-Level
    private String getBestEnhancementLevel(AccessoryResult result) {
        long maxProfit = Math.max(Math.max(result.duoProfit, result.triProfit), result.tetProfit);

        if (maxProfit == result.duoProfit) return "DUO";
        if (maxProfit == result.triProfit) return "TRI";
        return "TET";
    }

    // Farbliche Formatierung der Profit-Werte
    private String colorProfitOutput(long profit, boolean isBest) {
        String formattedProfit = formatNumber(profit);

        if (profit < 0) {
            // Negative Profite in Rot
            return RED + formattedProfit + RESET;
        } else if (isBest) {
            // Bester Profit in Grün und Fett
            return BOLD + GREEN + formattedProfit + RESET;
        } else {
            // Alle anderen positiven Profite in Grün (ohne Fettdruck)
            return GREEN + formattedProfit + RESET;
        }
    }

    // Farbliche Formatierung des besten Levels
    private String colorLevelOutput(String level) {
        return switch (level) {
            case "DUO" -> BLUE + BOLD + level + RESET;
            case "TRI" -> PURPLE + BOLD + level + RESET;
            case "TET" -> YELLOW + BOLD + level + RESET;
            default -> level;
        };
    }

    // Optional: Detaillierte Berichte pro Level können weiterhin angezeigt werden
    public void printDetailedAnalysisByLevel(List<AccessoryResult> results) {
        // Sort by DUO Profit (descending)
        results.sort((a, b) -> Long.compare(b.duoProfit, a.duoProfit));
        printLevelSpecificTable(results, "DUO Profit Analysis", BLUE);

        // Sort by TRI Profit (descending)
        results.sort((a, b) -> Long.compare(b.triProfit, a.triProfit));
        printLevelSpecificTable(results, "TRI Profit Analysis", PURPLE);

        // Sort by TET Profit (descending)
        results.sort((a, b) -> Long.compare(b.tetProfit, a.tetProfit));
        printLevelSpecificTable(results, "TET Profit Analysis", YELLOW);
    }

    public void printLevelSpecificTable(List<AccessoryResult> results, String title, String levelColor) {
        String levelTag = title.substring(0, 3);  // "DUO", "TRI", or "TET"

        // Definiere die Spaltenbreiten
        final int nameWidth = 39;
        final int stockWidth = 12;
        final int profitWidth = 15;
        final int itemsWidth = 12;

        log.info("\n" + BOLD + "{}{}" + RESET, levelColor, title);
        String separator = levelColor + "=".repeat(nameWidth + stockWidth + profitWidth + itemsWidth + 9) + RESET; // +9 für "| " Abstandshalter
        log.info(separator);

        // Header manuell erstellen
        StringBuilder header = new StringBuilder();
        appendWithPadding(header, BOLD + "Name" + RESET, nameWidth);
        header.append(" | ");
        appendWithPadding(header, BOLD + "Base Stock" + RESET, stockWidth);
        header.append(" | ");
        appendWithPadding(header, BOLD + levelTag + " Profit" + RESET, profitWidth);
        header.append(" | ");
        appendWithPadding(header, BOLD + "Avg Items" + RESET, itemsWidth);
        log.info(header.toString());

        log.info("{}{}" + RESET, levelColor, "-".repeat(nameWidth + stockWidth + profitWidth + itemsWidth + 9));

        // Ausgabe der Daten
        for (AccessoryResult result : results) {
            long profit;
            long items;

            switch (levelTag) {
                case "DUO" -> {
                    profit = result.duoProfit;
                    items = result.duoItems;
                }
                case "TRI" -> {
                    profit = result.triProfit;
                    items = result.triItems;
                }
                case "TET" -> {
                    profit = result.tetProfit;
                    items = result.tetItems;
                }
                default -> {
                    profit = 0;
                    items = 0;
                }
            }

            // Alle positiven Profite in Grün
            String coloredProfit = profit < 0
                    ? RED + formatNumber(profit) + RESET
                    : GREEN + formatNumber(profit) + RESET;

            String coloredItems = items > 10
                    ? YELLOW + formatNumber(items) + RESET
                    : formatNumber(items);

            // Zeile manuell erstellen
            StringBuilder row = new StringBuilder();
            appendWithPadding(row, truncateString(result.name), nameWidth);
            row.append(" | ");
            appendWithPadding(row, String.valueOf(result.baseStock), stockWidth);
            row.append(" | ");
            appendWithPadding(row, coloredProfit, profitWidth);
            row.append(" | ");
            appendWithPadding(row, coloredItems, itemsWidth);

            log.info(row.toString());
        }
        log.info(separator);
    }

    /**
     * Fügt einen Text mit fester Breite an, wobei ANSI-Codes bei der Berechnung der Breite ignoriert werden.
     */
    private void appendWithPadding(StringBuilder sb, String text, int width) {
        sb.append(text);
        int effectiveLength = calculateVisibleLength(text);
        int padding = width - effectiveLength;

        if (padding > 0) {
            sb.append(" ".repeat(padding));
        }
    }

    /**
     * Berechnet die sichtbare Länge eines Textes (ohne ANSI-Escape-Codes).
     */
    private int calculateVisibleLength(String text) {
        // Entferne alle ANSI-Escape-Sequenzen für die Längenberechnung
        String plainText = text.replaceAll("\u001B\\[[;\\d]*m", "");
        return plainText.length();
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private String truncateString(String str) {
        if (str.length() <= 39) {
            return str;
        }
        return str.substring(0, 39 - 3) + "...";
    }
}