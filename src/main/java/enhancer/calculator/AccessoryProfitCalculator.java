package enhancer.calculator;

import enhancer.Constants;
import enhancer.accessory.GenericAccessory;
import enhancer.market.BDOMarket;
import enhancer.models.AccessoryResult;
import enhancer.models.AccessoryStack;
import enhancer.models.EnhancementResult;
import enhancer.models.SimulationRun;
import enhancer.models.market.Accessory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Slf4j
public class AccessoryProfitCalculator {

    private int simulationRuns = 10000;

    // Default stacks that can be overridden
    private AccessoryStack monStack = AccessoryStack.FOURTY;
    private AccessoryStack duoStack = AccessoryStack.FOURTY;
    private AccessoryStack triStack = AccessoryStack.FOURTYFIVE;
    private AccessoryStack tetStack = AccessoryStack.HUNDREDTEN_FREE;

    public static void main(String[] args) {
        new AccessoryProfitCalculator().calculateAndPrintProfits();
    }

    // Modified method that returns calculation results
    public List<AccessoryResult> calculateProfits() {
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

        return results;
    }

    // Original method for console output
    public void calculateAndPrintProfits() {
        List<AccessoryResult> results = calculateProfits();

        Printer printer = new Printer();
        printer.printDetailedAnalysisByLevel(results);
    }

    private EnhancementResult calculateEnhancementCost(Accessory accessory, int targetLevel) {
        long totalCost = 0;
        long totalItems = 0;

        for (int i = 0; i < simulationRuns; i++) {
            SimulationRun run = simulateEnhancement(accessory, targetLevel);
            totalCost += run.cost;
            totalItems += run.items;
        }

        return new EnhancementResult(totalCost / simulationRuns, totalItems / simulationRuns);
    }

    private SimulationRun simulateEnhancement(Accessory accessory, int targetLevel) {

        long monStackCost = monStack.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long duoStackCost = duoStack.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long triStackCost = triStack.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long tetStackCost = tetStack.blackStoneCount * Constants.BLACK_STONE_PRICE;

        double[] enhanceChances = new double[]{monStack.mon, duoStack.duo, triStack.tri, tetStack.tet};
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