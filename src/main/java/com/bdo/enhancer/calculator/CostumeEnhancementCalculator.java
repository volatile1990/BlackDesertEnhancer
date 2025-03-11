package com.bdo.enhancer.calculator;

import com.bdo.enhancer.model.constants.Constants;
import com.bdo.enhancer.model.item.Costume;
import com.bdo.enhancer.model.result.EnhancementResult;
import com.bdo.enhancer.model.result.SimulationRun;
import com.bdo.enhancer.model.stack.CostumeStack;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Random;

/**
 * Calculator for costume enhancement profit calculations
 */
@Setter
@Getter
public class CostumeEnhancementCalculator {

    private int simulationRuns = Constants.SIMULATION_RUN_COUNT;
    private final Random random = new Random();

    // Default values for stacks
    private CostumeStack monStack = CostumeStack.FOURTY;
    private CostumeStack duoStack = CostumeStack.FOURTY;
    private CostumeStack triStack = CostumeStack.FOURTYFIVE;
    private CostumeStack tetStack = CostumeStack.HUNDREDTEN_FREE;

    /**
     * Calculates the profit result for a specific enhancement level.
     *
     * @param costume The costume to enhance
     * @param targetLevel The target enhancement level (2=DUO, 3=TRI, 4=TET)
     * @return The result with costs and consumed items
     */
    public EnhancementResult calculateEnhancementCost(Costume costume, int targetLevel) {
        long totalCost = 0;
        long totalItems = 0;

        for (int i = 0; i < simulationRuns; i++) {
            SimulationRun run = simulateEnhancement(costume, targetLevel);
            totalCost += run.cost;
            totalItems += run.items;
        }

        return new EnhancementResult(totalCost / simulationRuns, totalItems / simulationRuns);
    }

    /**
     * Simulates one run of the enhancement process for a costume.
     *
     * @param costume The costume to enhance
     * @param targetLevel The target enhancement level
     * @return The result of the simulation
     */
    private SimulationRun simulateEnhancement(Costume costume, int targetLevel) {
        long cost = 0;
        int currentLevel = 0;
        int itemsUsed = 0;

        // Calculate stack costs
        long monStackCost = Constants.BLACK_STONE_PRICE * monStack.blackStoneCount;
        long duoStackCost = Constants.BLACK_STONE_PRICE * duoStack.blackStoneCount;
        long triStackCost = Constants.BLACK_STONE_PRICE * triStack.blackStoneCount;
        long tetStackCost = Constants.BLACK_STONE_PRICE * tetStack.blackStoneCount;

        while (currentLevel < targetLevel) {
            // Base costume for each attempt (if not at base level)
            if (currentLevel == 0) {
                cost += 2 * costume.getBasePrice(); // At base level, two items needed
                itemsUsed += 2;
            } else {
                cost += costume.getBasePrice(); // At higher levels, only one additional item
                itemsUsed += 1;
            }

            double successChance = getSuccessChance(currentLevel);
            if (random.nextDouble() * 100 <= successChance) {
                // Add stack cost on success
                cost += getStackCost(currentLevel);
                currentLevel++;
            } else {
                // On failure, back to base level
                currentLevel = 0;
            }
        }

        return new SimulationRun(cost, itemsUsed);
    }

    /**
     * Determines the success probability for a specific enhancement level.
     *
     * @param level The current enhancement level
     * @return The success probability in percent
     */
    private double getSuccessChance(int level) {
        return switch (level) {
            case 0 -> monStack.mon;  // PRI chance
            case 1 -> duoStack.duo;  // DUO chance
            case 2 -> triStack.tri;  // TRI chance
            case 3 -> tetStack.tet;  // TET chance
            default -> throw new IllegalArgumentException("Unsupported enhancement level: " + level);
        };
    }

    /**
     * Determines the costs for the failstack at a specific enhancement level.
     *
     * @param level The current enhancement level
     * @return The costs for the failstack
     */
    private long getStackCost(int level) {
        return switch (level) {
            case 0 -> Constants.BLACK_STONE_PRICE * monStack.blackStoneCount;  // PRI stack cost
            case 1 -> Constants.BLACK_STONE_PRICE * duoStack.blackStoneCount;  // DUO stack cost
            case 2 -> Constants.BLACK_STONE_PRICE * triStack.blackStoneCount;  // TRI stack cost
            case 3 -> Constants.BLACK_STONE_PRICE * tetStack.blackStoneCount;  // TET stack cost
            default -> throw new IllegalArgumentException("Unsupported enhancement level: " + level);
        };
    }

    /**
     * Calculates the profit for a given costume and enhancement level
     *
     * @param costume The costume
     * @param targetLevel The target enhancement level
     * @return The calculated profit (positive or negative)
     */
    public long calculateProfit(Costume costume, int targetLevel) {
        EnhancementResult result = calculateEnhancementCost(costume, targetLevel);
        long salePrice = switch (targetLevel) {
            case 2 -> costume.getDuoPrice();
            case 3 -> costume.getTriPrice();
            case 4 -> costume.getTetPrice();
            default -> throw new IllegalArgumentException("Unsupported enhancement level: " + targetLevel);
        };

        return (long) ((salePrice - result.avgCost) * Constants.MARKET_TAX);
    }

    /**
     * Inner class for costume enhancement calculation result
     */
    @Data
    public static class CostumeResult {
        private final String name;
        private final int duoItems;
        private final long duoProfit;
        private final int triItems;
        private final long triProfit;
        private final int tetItems;
        private final long tetProfit;
    }
}
