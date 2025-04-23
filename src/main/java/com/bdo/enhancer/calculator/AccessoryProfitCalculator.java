package com.bdo.enhancer.calculator;

import com.bdo.enhancer.core.AccessoryEnhancer;
import com.bdo.enhancer.market.MarketDataService;
import com.bdo.enhancer.model.constants.Constants;
import com.bdo.enhancer.model.item.Accessory;
import com.bdo.enhancer.model.result.AccessoryEnhancementResult;
import com.bdo.enhancer.model.result.EnhancementResult;
import com.bdo.enhancer.model.result.SimulationRun;
import com.bdo.enhancer.model.stack.AbstractStack;
import com.bdo.enhancer.model.stack.AccessoryStack;
import com.bdo.enhancer.model.stack.CostumeStack;
import com.bdo.enhancer.model.stack.FailStackSet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Setter
@Getter
@Slf4j
public class AccessoryProfitCalculator {

    private int simulationRuns = Constants.SIMULATION_RUN_COUNT;

    // Default stacks that can be overridden - using the new Stack interface instead of OldAccessoryStack
    private AbstractStack monStack = AccessoryStack.THIRTY;
    private AbstractStack duoStack = AccessoryStack.FOURTY;
    private AbstractStack triStack = AccessoryStack.FOURTYFIVE;
    private AbstractStack tetStack = AccessoryStack.HUNDREDTEN_FREE;

    // Cached market accessories list
    private List<Accessory> cachedAccessories = null;

    // Add method to set the progress callback
    @Setter
    private Consumer<String> progressCallback;

    public List<AccessoryEnhancementResult> calculateProfits() {
        if (cachedAccessories == null || cachedAccessories.isEmpty()) {
            // Reload if nothing is cached
            MarketDataService marketService = new MarketDataService();
            marketService.setProgressCallback(this.progressCallback);
            cachedAccessories = marketService.getAccessories();
        }

        return calculateProfitsWithAccessories(cachedAccessories);
    }

    public List<AccessoryEnhancementResult> calculateProfitsWithAccessories(List<Accessory> accessories) {

        // Cache accessories to calculate without fetching every time
        this.cachedAccessories = accessories;

        // Initialize enhancement results for all accessories
        Map<String, AccessoryEnhancementResult> resultMap = new ConcurrentHashMap<>();
        for (Accessory accessory : accessories) {
            resultMap.put(accessory.getName(), new AccessoryEnhancementResult(
                    accessory.getName(),
                    accessory.getBaseStock(),
                    0, 0,  // DUO items and profit (to be filled)
                    0, 0,  // TRI items and profit (to be filled)
                    0, 0   // TET items and profit (to be filled)
            ));
        }

        // Erstelle einen Thread-Pool
        ExecutorService executorService = Executors.newWorkStealingPool();

        try {
            // Simulate and calculate DUO enhancement
            calculateLevelInParallel(accessories, resultMap, 2, executorService);

            // Simulate and calculate TRI enhancement
            calculateLevelInParallel(accessories, resultMap, 3, executorService);

            // Simulate and calculate TET enhancement
            calculateLevelInParallel(accessories, resultMap, 4, executorService);

        } finally {
            executorService.shutdown();
        }

        // Final progress update
        updateProgress("All calculations complete");

        // Convert map to list for return
        return new ArrayList<>(resultMap.values());
    }

    private void calculateLevelInParallel(List<Accessory> accessories,
                                          Map<String, AccessoryEnhancementResult> resultMap,
                                          int targetLevel,
                                          ExecutorService executorService) {
        String levelName = getLevelName(targetLevel);
        updateProgress("Calculating " + levelName + " enhancements for all accessories...");

        // Progress bar values
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalCount = accessories.size();

        // Create a list of CompletableFuture tasks
        List<CompletableFuture<Void>> futures = accessories.stream()
                .map(accessory -> CompletableFuture.runAsync(() -> {
                    try {
                        // Simulate enhancement and calculate cost/profit
                        EnhancementResult result = calculateEnhancementCost(accessory, targetLevel);
                        long profit = calculateProfit(getPrice(accessory, targetLevel), result.avgCost);

                        // Update resultMap
                        AccessoryEnhancementResult accessoryResult = resultMap.get(accessory.getName());
                        updateAccessoryResult(accessoryResult, targetLevel, result.avgItems, profit);

                        // Update progress bar output
                        int completed = completedCount.incrementAndGet();
                        updateProgress("Calculating " + levelName + " enhancements: " + completed + "/" + totalCount + " complete");
                    } catch (Exception e) {
                        log.error("Error calculating enhancement for {} at level {}", accessory.getName(), targetLevel, e);
                    }
                }, executorService))
                .collect(Collectors.toList());

        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private long getPrice(Accessory accessory, int level) {
        return switch (level) {
            case 2 -> accessory.getDuoPrice();
            case 3 -> accessory.getTriPrice();
            case 4 -> accessory.getTetPrice();
            default -> throw new IllegalArgumentException("Unsupported enhancement level: " + level);
        };
    }

    private synchronized void updateAccessoryResult(AccessoryEnhancementResult result, int level, long items, long profit) {
        switch (level) {
            case 2 -> {
                result.duoItems = items;
                result.duoProfit = profit;
            }
            case 3 -> {
                result.triItems = items;
                result.triProfit = profit;
            }
            case 4 -> {
                result.tetItems = items;
                result.tetProfit = profit;
            }
            default -> throw new IllegalArgumentException("Unsupported enhancement level: " + level);
        }
    }

    private String getLevelName(int level) {
        return switch (level) {
            case 2 -> "DUO";
            case 3 -> "TRI";
            case 4 -> "TET";
            default -> "Level " + level;
        };
    }

    // Helper method to send progress updates
    private void updateProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
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

    private SimulationRun simulateEnhancement(Accessory item, int targetLevel) {

        if (item.isCostume()) {
            // Apply costume stacks
            monStack = CostumeStack.findByStackCount(monStack.getStackCount());
            duoStack = CostumeStack.findByStackCount(duoStack.getStackCount());
            triStack = CostumeStack.findByStackCount(triStack.getStackCount());
            tetStack = CostumeStack.findByStackCount(tetStack.getStackCount());
        }

        // Get stack costs
        long monStackCost = monStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;
        long duoStackCost = duoStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;
        long triStackCost = triStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;
        long tetStackCost = tetStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;

        // Setup used stacks
        FailStackSet stacksUsed = new FailStackSet(this.monStack, this.duoStack, this.triStack, this.tetStack);

        // Setup enhance chances
        double[] enhanceChances = new double[]{
                monStack.getMonChance(),
                duoStack.getDuoChance(),
                triStack.getTriChance(),
                tetStack.getTetChance()
        };

        // Setup failstack cost
        long[] failstackCost = new long[]{monStackCost, duoStackCost, triStackCost, tetStackCost};

        // Init enhancer with stack data
        AccessoryEnhancer enhancer = new AccessoryEnhancer(item.getBasePrice(), enhanceChances, failstackCost);
        enhancer.setStacksUsed(stacksUsed);

        // Enhance until target level is reached
        while (enhancer.getCurrentLevel() < targetLevel) {
            enhancer.enhance();
        }

        // Seperate cost value needed as it also includes stacks used
        return new SimulationRun(enhancer.getTotalEnhanceCost(), enhancer.getTotalItemsConsumed());
    }

    private long calculateProfit(long salePrice, long cost) {
        return (long) ((salePrice - cost) * Constants.MARKET_TAX);
    }
}