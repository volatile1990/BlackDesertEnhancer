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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    // Add method to set the progress callback
    // Add a progress callback
    @Setter
    private Consumer<String> progressCallback;

    public static void main(String[] args) {
        new AccessoryProfitCalculator().calculateAndPrintProfits();
    }

    // Completely restructured method to calculate by enhancement level
    public List<AccessoryResult> calculateProfits() {

        BDOMarket market = new BDOMarket();
        market.setProgressCallback(this.progressCallback);
        List<Accessory> accessories = market.getAccessories();

        // Store intermediate results
        Map<String, AccessoryResult> resultMap = new HashMap<>();

        // Initialize result objects for all accessories
        for (Accessory accessory : accessories) {
            resultMap.put(accessory.getName(), new AccessoryResult(
                    accessory.getName(),
                    accessory.getBaseStock(),
                    0, 0,  // DUO items and profit (to be filled)
                    0, 0,  // TRI items and profit (to be filled)
                    0, 0   // TET items and profit (to be filled)
            ));
        }

        // Calculate DUO profits for all accessories
        updateProgress("Calculating DUO enhancements for all accessories...");
        for (Accessory accessory : accessories) {
            EnhancementResult duoResult = calculateEnhancementCost(accessory, 2);
            long duoProfit = calculateProfit(accessory.getDuoPrice(), duoResult.avgCost);

            // Update the result object
            AccessoryResult result = resultMap.get(accessory.getName());
            result.duoItems = duoResult.avgItems;
            result.duoProfit = duoProfit;
        }

        // Calculate TRI profits for all accessories
        updateProgress("Calculating TRI enhancements for all accessories...");
        for (Accessory accessory : accessories) {
            EnhancementResult triResult = calculateEnhancementCost(accessory, 3);
            long triProfit = calculateProfit(accessory.getTriPrice(), triResult.avgCost);

            // Update the result object
            AccessoryResult result = resultMap.get(accessory.getName());
            result.triItems = triResult.avgItems;
            result.triProfit = triProfit;
        }

        // Calculate TET profits for all accessories
        updateProgress("Calculating TET enhancements for all accessories...");
        for (Accessory accessory : accessories) {
            EnhancementResult tetResult = calculateEnhancementCost(accessory, 4);
            long tetProfit = calculateProfit(accessory.getTetPrice(), tetResult.avgCost);

            // Update the result object
            AccessoryResult result = resultMap.get(accessory.getName());
            result.tetItems = tetResult.avgItems;
            result.tetProfit = tetProfit;
        }

        // Final progress update
        updateProgress("All calculations complete");

        // Convert map to list for return
        return new ArrayList<>(resultMap.values());
    }

    // Helper method to send progress updates
    private void updateProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
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
        // Existing code remains the same
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
}
