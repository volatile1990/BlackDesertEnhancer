package enhancer.calculator;

import enhancer.Constants;
import enhancer.accessory.GenericAccessory;
import enhancer.models.AccessoryStack;
import enhancer.models.OptimalStackResult;
import enhancer.models.market.Accessory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Klasse zum Berechnen der optimalen Failstack-Kombination für jedes Accessoire
 * Optimiert für TRI Enhancement (Level 3)
 */
@Slf4j
public class OptimalStackCalculator {

    // Einstellbare Parameter
    private final int simulationRunsPerCombination;
    private final int threadCount;

    @Getter
    private final List<OptimalStackResult> results = new ArrayList<>();

    /**
     * Konstruktor mit Standard-Simulationsläufen und Thread-Anzahl
     */
    public OptimalStackCalculator() {
        this(10000, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Konstruktor mit anpassbaren Parametern
     *
     * @param simulationRunsPerCombination Anzahl der Simulationsläufe pro Stack-Kombination
     * @param threadCount Anzahl der zu verwendenden Threads
     */
    public OptimalStackCalculator(int simulationRunsPerCombination, int threadCount) {
        this.simulationRunsPerCombination = simulationRunsPerCombination;
        this.threadCount = threadCount;
    }

    /**
     * Findet die optimale Stack-Kombination für jedes Accessoire
     *
     * @param accessories Liste aller Accessoires
     * @param progressCallback Callback für Fortschrittsmeldungen
     * @return Liste mit optimalen Stack-Kombinationen für jedes Accessoire
     */
    public List<OptimalStackResult> findOptimalStacks(List<Accessory> accessories,
                                                      java.util.function.Consumer<String> progressCallback) {
        results.clear();

        // Thread-Pool für parallele Berechnung
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        try {
            // Log der Konfiguration
            logConfiguration(progressCallback);

            // Für jedes Accessoire die optimale Kombination berechnen
            int totalAccessories = accessories.size();
            AtomicInteger processedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(totalAccessories);
            long startTime = System.currentTimeMillis();

            for (Accessory accessory : accessories) {
                executorService.submit(() -> {
                    try {
                        OptimalStackResult optimalResult = findOptimalStacksForAccessory(accessory);
                        synchronized (results) {
                            results.add(optimalResult);
                        }

                        // Fortschritt melden
                        int completed = processedCount.incrementAndGet();
                        if (progressCallback != null) {
                            String message = createProgressMessage(
                                    accessory.getName(), completed, totalAccessories, startTime);
                            progressCallback.accept(message);
                        }
                    } catch (Exception e) {
                        log.error("Error optimizing stacks for " + accessory.getName(), e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Warten bis alle Berechnungen abgeschlossen sind
            latch.await();

            if (progressCallback != null) {
                progressCallback.accept("Stack optimization completed for all accessories (optimized for TRI)");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while optimizing stacks", e);
        } finally {
            executorService.shutdown();
        }

        return results;
    }

    /**
     * Gibt Konfigurationsinformationen aus
     */
    private void logConfiguration(java.util.function.Consumer<String> progressCallback) {
        AccessoryStack[] nonFreeStacks = getNonFreeStacks();
        int totalStacks = AccessoryStack.values().length;
        int usedStacks = nonFreeStacks.length;
        long combinations = (long) Math.pow(usedStacks, 2); // Nur PRI und DUO variieren für TRI

        String message = String.format(
                "Optimizing for TRI with non-FREE stacks: %d of %d stacks used (%d combinations per accessory)",
                usedStacks, totalStacks, combinations);

        log.info(message);

        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }

    /**
     * Erstellt eine Fortschrittsmeldung mit Zeitschätzung
     */
    private String createProgressMessage(String accessoryName, int completed, int total, long startTime) {
        double percentage = completed * 100.0 / total;

        // Berechne verstrichene Zeit und schätze verbleibende Zeit
        long elapsedTimeMs = System.currentTimeMillis() - startTime;
        long estimatedTotalTimeMs = (completed > 0)
                ? (long) (elapsedTimeMs * (total / (double) completed))
                : 0;
        long remainingTimeMs = Math.max(0, estimatedTotalTimeMs - elapsedTimeMs);

        // Formatiere Zeitangaben
        String elapsedTime = formatTime(elapsedTimeMs);
        String remainingTime = formatTime(remainingTimeMs);

        return String.format(
                "Optimizing TRI stacks: %d/%d completed (%.1f%%) - %s (Elapsed: %s, Remaining: %s)",
                completed, total, percentage, accessoryName, elapsedTime, remainingTime
        );
    }

    /**
     * Formatiert Millisekunden in ein lesbares Format (mm:ss oder hh:mm:ss)
     */
    private String formatTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Findet die optimale Stack-Kombination für ein einzelnes Accessoire
     * Optimiert für TRI Enhancement
     *
     * @param accessory Das zu analysierende Accessoire
     * @return Die optimale Stack-Kombination mit Profiten
     */
    private OptimalStackResult findOptimalStacksForAccessory(Accessory accessory) {
        // Filtere alle Stacks, die "FREE" im Namen haben
        AccessoryStack[] nonFreeStacks = getNonFreeStacks();

        OptimalStackResult bestResult = new OptimalStackResult(
                accessory.getName(),
                AccessoryStack.FOURTY,  // Standardwerte
                AccessoryStack.FOURTY,
                AccessoryStack.FOURTYFIVE,
                Long.MIN_VALUE
        );

        // Alle möglichen Kombinationen von nicht-freien PRI und DUO stacks durchprobieren
        for (AccessoryStack priStack : nonFreeStacks) {
            for (AccessoryStack duoStack : nonFreeStacks) {
                // Probiere alle verfügbaren TRI stacks
                for (AccessoryStack triStack : nonFreeStacks) {
                    // Berechne Gewinn für diese Kombination, jetzt für TRI Level (3) statt TET (4)
                    long triProfit = calculateProfitForCombination(
                            accessory, priStack, duoStack, triStack, 3);

                    // Wenn besser als bisher bestes Ergebnis, merken
                    if (triProfit > bestResult.totalProfit) {
                        bestResult = new OptimalStackResult(
                                accessory.getName(),
                                priStack,
                                duoStack,
                                triStack,
                                triProfit
                        );
                    }
                }
            }
        }

        return bestResult;
    }

    /**
     * Filtert alle Stacks, die "FREE" im Namen haben
     *
     * @return Array mit allen Stacks, die nicht "FREE" im Namen haben
     */
    private AccessoryStack[] getNonFreeStacks() {
        return Arrays.stream(AccessoryStack.values())
                .filter(stack -> !stack.name().contains("FREE"))
                .toArray(AccessoryStack[]::new);
    }

    /**
     * Berechnet den Profit für eine bestimmte Kombination von Stacks
     *
     * @param accessory Das zu analysierende Accessoire
     * @param priStack PRI Stack
     * @param duoStack DUO Stack
     * @param triStack TRI Stack
     * @param targetLevel Ziel-Level (jetzt 3 für TRI)
     * @return Berechneter Profit
     */
    private long calculateProfitForCombination(Accessory accessory,
                                               AccessoryStack priStack,
                                               AccessoryStack duoStack,
                                               AccessoryStack triStack,
                                               int targetLevel) {
        // Stack-Kosten berechnen
        long priStackCost = priStack.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long duoStackCost = duoStack.blackStoneCount * Constants.BLACK_STONE_PRICE;
        long triStackCost = triStack.blackStoneCount * Constants.BLACK_STONE_PRICE;

        // Enhancement-Chancen und Kosten für diese Kombination
        double[] enhanceChances = new double[]{priStack.mon, duoStack.duo, triStack.tri, 0}; // TET-Chance nicht relevant
        long[] failstackCost = new long[]{priStackCost, duoStackCost, triStackCost, 0}; // TET-Kosten nicht relevant

        // Kosten und Verbrauchte Items berechnen
        long totalCost = 0;
        long totalItems = 0;

        for (int i = 0; i < simulationRunsPerCombination; i++) {
            GenericAccessory enhanceAccessory = new GenericAccessory(
                    accessory.getBasePrice(), enhanceChances, failstackCost);

            while (enhanceAccessory.getCurrentLevel() < targetLevel) {
                enhanceAccessory.enhance();
            }

            totalCost += enhanceAccessory.getTotalEnhanceCost();
            totalItems += enhanceAccessory.getTotalItemsConsumed();
        }

        // Durchschnittliche Kosten berechnen
        long avgCost = totalCost / simulationRunsPerCombination;

        // Profit berechnen - jetzt mit TRI-Preis statt TET-Preis
        long salePrice = accessory.getTriPrice(); // Für TRI
        return (long) ((salePrice - avgCost) * Constants.MARKET_TAX);
    }
}