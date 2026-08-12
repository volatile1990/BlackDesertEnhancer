package com.bdo.enhancer.core;

import com.bdo.enhancer.model.constants.Constants;
import lombok.Getter;

import java.util.Random;

/**
 * Enhancement simulation for Manos life-skill clothes.
 *
 * <p>Unlike accessories and Silver Embroidered Clothes, Manos clothes use
 * fixed success chances. Failstacks are therefore deliberately not part of
 * this class.</p>
 */
@Getter
public class ManosClothingEnhancer {

    public static final int PRI_LEVEL = 16;
    public static final int DUO_LEVEL = 17;
    public static final int TRI_LEVEL = 18;
    public static final int TET_LEVEL = 19;
    public static final int PEN_LEVEL = 20;

    private static final double[] FIXED_SUCCESS_CHANCES = {
            100, 100, 100, 100, 100,
            100, 100, 70, 60, 50,
            40, 30, 20, 15, 10,
            30, 25, 20, 15, 6
    };

    private static final int[] PITY_THRESHOLDS = {
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            3, 4, 4, 5, 7, 10, 14, 20,
            7, 8, 10, 15, 35
    };

    private static final int[] BLACK_GEMS_PER_ATTEMPT = {
            1, 1, 1, 1, 1,
            2, 2, 2,
            3, 3, 3,
            4, 4,
            5, 5
    };

    private final Random random;
    private final int[] failCounter = new int[FIXED_SUCCESS_CHANCES.length];

    private int currentLevel;
    private long totalEnhanceCost;
    private final int totalItemsConsumed = 1;

    public ManosClothingEnhancer(long baseItemPrice) {
        this(baseItemPrice, new Random());
    }

    ManosClothingEnhancer(long baseItemPrice, Random random) {
        this.random = random;
        this.totalEnhanceCost = baseItemPrice;
    }

    public static double getFixedSuccessChance(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= FIXED_SUCCESS_CHANCES.length) {
            throw new IllegalArgumentException("Unsupported Manos enhancement level: " + currentLevel);
        }
        return FIXED_SUCCESS_CHANCES[currentLevel];
    }

    public double getCurrentSuccessChance() {
        return getFixedSuccessChance(currentLevel);
    }

    public void enhance() {
        if (currentLevel >= PEN_LEVEL) {
            throw new IllegalStateException("Manos clothing is already at PEN");
        }

        int attemptedLevel = currentLevel;
        addAttemptCost(attemptedLevel);

        boolean pitySuccess = failCounter[attemptedLevel] >= getPityThreshold(attemptedLevel);
        boolean success = pitySuccess || random.nextDouble() * 100 < getFixedSuccessChance(attemptedLevel);

        if (success) {
            failCounter[attemptedLevel] = 0;
            currentLevel++;
            return;
        }

        failCounter[attemptedLevel]++;
        addRepairCost(attemptedLevel);

        // A failed TRI or higher attempt downgrades the clothing by one level.
        if (attemptedLevel >= DUO_LEVEL) {
            currentLevel--;
        }
    }

    private void addAttemptCost(int attemptedLevel) {
        if (attemptedLevel < 15) {
            totalEnhanceCost += (long) BLACK_GEMS_PER_ATTEMPT[attemptedLevel]
                    * Constants.BLACK_GEM_PRICE;
            return;
        }

        totalEnhanceCost += Constants.CONCENTRATED_MAGICAL_BLACK_GEM_PRICE;
    }

    private void addRepairCost(int attemptedLevel) {
        int durabilityLoss = attemptedLevel < 15 ? 5 : 10;
        totalEnhanceCost += (long) durabilityLoss * Constants.MEMORY_FRAGMENT_PRICE;
    }

    static int getPityThreshold(int attemptedLevel) {
        return PITY_THRESHOLDS[attemptedLevel];
    }
}
