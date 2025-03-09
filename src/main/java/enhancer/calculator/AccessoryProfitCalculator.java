package enhancer.calculator;

import enhancer.Constants;
import enhancer.accessory.GenericAccessory;
import enhancer.market.BDOMarket;
import enhancer.models.AccessoryResult;
import enhancer.models.AccessoryStack;
import enhancer.models.EnhancementResult;
import enhancer.models.SimulationRun;
import enhancer.models.market.Accessory;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
public class AccessoryProfitCalculator {

    private static final int SIMULATION_RUNS = 100000;

    // Used Stacks
    private static final AccessoryStack MON_STACK = AccessoryStack.FOURTY;
    private static final AccessoryStack DUO_STACK = AccessoryStack.FOURTY;
    private static final AccessoryStack TRI_STACK = AccessoryStack.FOURTYFIVE;
    private static final AccessoryStack TET_STACK = AccessoryStack.HUNDREDTEN_FREE;

    public static void main(String[] args) {
        new AccessoryProfitCalculator().calculateAndPrintProfits();
    }

    public void calculateAndPrintProfits() {
        BDOMarket market = new BDOMarket();
        List<Accessory> accessories = market.getAccessories();
        List<AccessoryResult> results = new ArrayList<>();

        // Calculate profits for all accessories
        for (Accessory accessory : accessories) {
            EnhancementResult duoResult = calculateEnhancementCost(accessory, 2);
            long duoProfit = calculateProfit(accessory.getDuoPrice(), duoResult.avgCost);

            EnhancementResult triResult = calculateEnhancementCost(accessory, 3);
            long triProfit = calculateProfit(accessory.getTriPrice(), triResult.avgCost);

            EnhancementResult tetResult = calculateEnhancementCost(accessory, 4);
            long tetProfit = calculateProfit(accessory.getTetPrice(), tetResult.avgCost);

            results.add(new AccessoryResult(accessory.getName(), accessory.getBaseStock(), duoResult.avgItems, duoProfit, triResult.avgItems, triProfit, tetResult.avgItems, tetProfit));
        }

        Printer printer = new Printer();
        //printer.printComprehensiveTable(results);
        printer.printDetailedAnalysisByLevel(results);

        /*
        // Sort by DUO Profit per Item (descending) to find the most profitable DUO upgrades
        results.sort((a, b) -> Double.compare(b.duoProfitPerItem, a.duoProfitPerItem));

        // Print DUO Analysis
        printProfitTable(results, "DUO Profit Analysis", 2);

        // Sort by TRI Profit per Item (descending) to find the most profitable TRI upgrades
        results.sort((a, b) -> Double.compare(b.triProfitPerItem, a.triProfitPerItem));

        // Print TRI Analysis
        printProfitTable(results, "TRI Profit Analysis", 3);

        // Sort by TET Profit per Item (descending) to find the most profitable TET upgrades
        results.sort((a, b) -> Double.compare(b.tetProfitPerItem, a.tetProfitPerItem));

        // Print TET Analysis
        printProfitTable(results, "TET Profit Analysis", 4);*/

    }

    private void printProfitTable(List<AccessoryResult> results, String title, int level) {

        String levelTag = switch (level) {
            case 2 -> {
                yield "DUO";
            }
            case 3 -> {
                yield "TRI";
            }
            case 4 -> {
                yield "TET";
            }
            default -> throw new IllegalStateException("Unexpected value: " + level);
        };

        String format = "%-39s | %13s | %12s | %15s | %15s  | %15s | %15s";
        log.info("\n{}", title);
        log.info("=".repeat(125));
        log.info("{}", String.format(format, "Name", "Avg " + levelTag + " Items", "Base Stock", levelTag + " Profit", "DUO Profit/Item", "TRI Profit/Item", "TET Profit/Item"));
        log.info("-".repeat(125));

        for (AccessoryResult result : results) {
            long items = 0;
            long profit = switch (level) {
                case 2 -> {
                    items = result.duoItems;
                    yield result.duoProfit;
                }
                case 3 -> {
                    items = result.triItems;
                    yield result.triProfit;
                }
                case 4 -> {
                    items = result.tetItems;
                    yield result.tetProfit;
                }
                default -> 0;
            };

            log.info("{}", String.format(format, truncateString(result.name, 39), items, result.baseStock, formatNumber(profit), formatNumber(result.duoProfitPerItem), formatNumber(result.triProfitPerItem), formatNumber(result.tetProfitPerItem)));
        }
        log.info("=".repeat(125));
    }

    private String formatNumber(double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(number);
    }

    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    private EnhancementResult calculateEnhancementCost(Accessory accessory, int targetLevel) {
        long totalCost = 0;
        long totalItems = 0;

        for (int i = 0; i < SIMULATION_RUNS; i++) {
            SimulationRun run = simulateEnhancement(accessory, targetLevel);
            totalCost += run.cost;
            totalItems += run.items;
        }

        return new EnhancementResult(totalCost / SIMULATION_RUNS, totalItems / SIMULATION_RUNS);
    }

    private SimulationRun simulateEnhancement(Accessory accessory, int targetLevel) {

        long monStackCost = MON_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long duoStackCost = DUO_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long triStackCost = TRI_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long tetStackCost = TET_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;

        double[] enhanceChances = new double[]{MON_STACK.mon, DUO_STACK.duo, TRI_STACK.tri, TET_STACK.tet};
        long[] failstackCost = new long[]{monStackCost, duoStackCost, triStackCost, tetStackCost};
        GenericAccessory enhanceAccessory = new GenericAccessory(accessory.getBasePrice(), enhanceChances, failstackCost);

        while (enhanceAccessory.getCurrentLevel() < targetLevel) {
            enhanceAccessory.enhance();
        }

        return new SimulationRun(enhanceAccessory.getTotalEnhanceCost(), enhanceAccessory.getTotalItemsConsumed());

    }

    private long calculateProfit(long salePrice, long cost) {
        return (long) ((salePrice - cost) * Constants.MARKET_TAX);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

}