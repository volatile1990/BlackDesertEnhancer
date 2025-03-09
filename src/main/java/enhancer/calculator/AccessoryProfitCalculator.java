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

    // Default stacks that can be overridden
    private AccessoryStack monStack = AccessoryStack.FOURTY;
    private AccessoryStack duoStack = AccessoryStack.FOURTY;
    private AccessoryStack triStack = AccessoryStack.FOURTYFIVE;
    private AccessoryStack tetStack = AccessoryStack.HUNDREDTEN_FREE;

    // Cached market accessories list
    private List<Accessory> cachedAccessories = null;

    // Add method to set the progress callback
    @Setter
    private Consumer<String> progressCallback;

    // Completely restructured method to calculate by enhancement level
    public List<AccessoryResult> calculateProfits() {
        if (cachedAccessories == null || cachedAccessories.isEmpty()) {
            // Nur wenn keine Daten im Cache sind, neu laden
            BDOMarket market = new BDOMarket();
            market.setProgressCallback(this.progressCallback);
            cachedAccessories = market.getAccessories();
        }

        return calculateProfitsWithAccessories(cachedAccessories);
    }

    // Neue Methode zur Berechnung mit gegebenen Accessoires - parallelisiert
    public List<AccessoryResult> calculateProfitsWithAccessories(List<Accessory> accessories) {
        // Accessoires im Cache speichern
        this.cachedAccessories = accessories;

        // Thread-sichere Map für Zwischenergebnisse
        Map<String, AccessoryResult> resultMap = new ConcurrentHashMap<>();

        // Initialisiere Ergebnisobjekte für alle Accessoires
        for (Accessory accessory : accessories) {
            resultMap.put(accessory.getName(), new AccessoryResult(
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
                                          Map<String, AccessoryResult> resultMap,
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
                    AccessoryResult accessoryResult = resultMap.get(accessory.getName());
                    updateAccessoryResult(accessoryResult, level, result.avgItems, profit);

                    // Fortschritt melden
                    int completed = completedCount.incrementAndGet();
                    if (completed % 10 == 0 || completed == totalCount) {
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
    private synchronized void updateAccessoryResult(AccessoryResult result, int level, long items, long profit) {
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