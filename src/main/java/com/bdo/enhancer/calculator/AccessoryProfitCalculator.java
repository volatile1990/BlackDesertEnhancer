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
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Setter
@Getter
@Slf4j
public class AccessoryProfitCalculator {

    private int simulationRuns = Constants.SIMULATION_RUN_COUNT;

    // Anzahl der Threads für die parallele Berechnung
    private int threadCount = Runtime.getRuntime().availableProcessors();

    // Default stacks that can be overridden - using the new Stack interface instead of OldAccessoryStack
    private AbstractStack monStack = AccessoryStack.FOURTY;
    private AbstractStack duoStack = AccessoryStack.FOURTY;
    private AbstractStack triStack = AccessoryStack.FOURTYFIVE;
    private AbstractStack tetStack = AccessoryStack.HUNDREDTEN_FREE;

    // Cached market accessories list
    private List<Accessory> cachedAccessories = null;

    // Add method to set the progress callback
    @Setter
    private Consumer<String> progressCallback;

    // Completely restructured method to calculate by enhancement level
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
        // Accessoires im Cache speichern
        this.cachedAccessories = accessories;

        // Thread-sichere Map für Zwischenergebnisse
        Map<String, AccessoryEnhancementResult> resultMap = new ConcurrentHashMap<>();

        // Initialisiere Ergebnisobjekte für alle Accessoires
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
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        try {
            // DUO-Berechnungen (Level 2)
            calculateLevelInParallel(accessories, resultMap, 2, executorService);

            // TRI-Berechnungen (Level 3)
            calculateLevelInParallel(accessories, resultMap, 3, executorService);

            // TET-Berechnungen (Level 4)
            calculateLevelInParallel(accessories, resultMap, 4, executorService);

        } finally {
            // Thread-Pool beenden
            executorService.shutdown();
        }

        // Final progress update
        updateProgress("All calculations complete");

        // Convert map to list for return
        return new ArrayList<>(resultMap.values());
    }

    // Neue Methode für parallele Berechnung pro Enhancement-Level
    private void calculateLevelInParallel(List<Accessory> accessories,
                                          Map<String, AccessoryEnhancementResult> resultMap,
                                          int level,
                                          ExecutorService executorService) {
        String levelName = getLevelName(level);
        updateProgress("Calculating " + levelName + " enhancements for all accessories...");

        // CountDownLatch zur Synchronisation
        CountDownLatch latch = new CountDownLatch(accessories.size());

        // Fortschrittsanzeige
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalCount = accessories.size();

        // Für jedes Accessory einen Task starten
        for (Accessory accessory : accessories) {
            executorService.submit(() -> {
                try {
                    // Berechnung für dieses Accessory
                    EnhancementResult result = calculateEnhancementCost(accessory, level);
                    long profit = calculateProfit(getPrice(accessory, level), result.avgCost);

                    // Ergebnis aktualisieren
                    AccessoryEnhancementResult accessoryResult = resultMap.get(accessory.getName());
                    updateAccessoryResult(accessoryResult, level, result.avgItems, profit);

                    // Fortschritt melden
                    int completed = completedCount.incrementAndGet();
                    if (completed % 5 == 0 || completed == totalCount) {
                        updateProgress("Calculating " + levelName + " enhancements: " +
                                completed + "/" + totalCount + " complete");
                    }
                } catch (Exception e) {
                    log.error("Error calculating enhancement for {} at level {}", accessory.getName(), level, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            // Warten bis alle Tasks für dieses Level abgeschlossen sind
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for enhancement calculations", e);
        }
    }

    // Hilfsmethode zum Abrufen des Preises für ein bestimmtes Level
    private long getPrice(Accessory accessory, int level) {
        return switch (level) {
            case 2 -> accessory.getDuoPrice();
            case 3 -> accessory.getTriPrice();
            case 4 -> accessory.getTetPrice();
            default -> throw new IllegalArgumentException("Unsupported enhancement level: " + level);
        };
    }

    // Hilfsmethode zum Aktualisieren des Ergebnisses für ein bestimmtes Level
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

    // Hilfsmethode um den Namen des Levels zu erhalten
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

    private SimulationRun simulateEnhancement(Accessory accessory, int targetLevel) {

        if (StringUtils.containsIgnoreCase(accessory.getName(), "Silver")) {
            // Get costume stack for corresponding stack count
            monStack = CostumeStack.findByStackCount(monStack.getStackCount());
            duoStack = CostumeStack.findByStackCount(duoStack.getStackCount());
            triStack = CostumeStack.findByStackCount(triStack.getStackCount());
            tetStack = CostumeStack.findByStackCount(tetStack.getStackCount());
        }

        // Berechne die Kosten der Failstacks
        long monStackCost = monStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;
        long duoStackCost = duoStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;
        long triStackCost = triStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;
        long tetStackCost = tetStack.getBlackStoneCount() * Constants.BLACK_STONE_PRICE;

        // Die FailStackSet-Klasse muss ebenfalls angepasst werden, um mit Stack statt OldAccessoryStack zu arbeiten
        FailStackSet stacksUsed = new FailStackSet(this.monStack, this.duoStack, this.triStack, this.tetStack);

        double[] enhanceChances = new double[]{
                monStack.getMonChance(),
                duoStack.getDuoChance(),
                triStack.getTriChance(),
                tetStack.getTetChance()
        };

        long[] failstackCost = new long[]{monStackCost, duoStackCost, triStackCost, tetStackCost};

        AccessoryEnhancer enhancer = new AccessoryEnhancer(accessory.getBasePrice(), enhanceChances, failstackCost);
        enhancer.setStacksUsed(stacksUsed);

        while (enhancer.getCurrentLevel() < targetLevel) {
            enhancer.enhance();
        }

        return new SimulationRun(enhancer.getTotalEnhanceCost(), enhancer.getTotalItemsConsumed());
    }

    private long calculateProfit(long salePrice, long cost) {
        return (long) ((salePrice - cost) * Constants.MARKET_TAX);
    }
}