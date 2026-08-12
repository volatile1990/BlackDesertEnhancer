package com.bdo.enhancer.core;

import com.bdo.enhancer.model.constants.Constants;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManosClothingEnhancerTest {

    @Test
    void usesFixedPriThroughTetChances() {
        assertEquals(30.0, ManosClothingEnhancer.getFixedSuccessChance(15));
        assertEquals(25.0, ManosClothingEnhancer.getFixedSuccessChance(16));
        assertEquals(20.0, ManosClothingEnhancer.getFixedSuccessChance(17));
        assertEquals(15.0, ManosClothingEnhancer.getFixedSuccessChance(18));
    }

    @Test
    void usesCurrentPcChancesForLowerEnhancementLevels() {
        assertEquals(100.0, ManosClothingEnhancer.getFixedSuccessChance(5));
        assertEquals(100.0, ManosClothingEnhancer.getFixedSuccessChance(6));
        assertEquals(70.0, ManosClothingEnhancer.getFixedSuccessChance(7));
    }

    @Test
    void usesExactManosAgrisThresholds() {
        int[] expectedThresholds = {3, 4, 4, 5, 7, 10, 14, 20, 7, 8, 10, 15, 35};

        for (int level = 7; level <= 19; level++) {
            assertEquals(expectedThresholds[level - 7],
                    ManosClothingEnhancer.getPityThreshold(level));
        }
    }

    @Test
    void consumesOneConcentratedBlackGemPerPriThroughTetAttempt() {
        Random successfulRolls = new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        ManosClothingEnhancer enhancer = new ManosClothingEnhancer(100_000_000, successfulRolls);
        while (enhancer.getCurrentLevel() < 15) {
            enhancer.enhance();
        }

        for (int targetLevel = 16; targetLevel <= 19; targetLevel++) {
            long costBeforeAttempt = enhancer.getTotalEnhanceCost();
            enhancer.enhance();
            assertEquals(Constants.CONCENTRATED_MAGICAL_BLACK_GEM_PRICE,
                    enhancer.getTotalEnhanceCost() - costBeforeAttempt);
            assertEquals(targetLevel, enhancer.getCurrentLevel());
        }
    }

    @Test
    void failuresDoNotIncreaseTheDisplayedChance() {
        AtomicInteger rolls = new AtomicInteger();
        Random scriptedRandom = new Random() {
            @Override
            public double nextDouble() {
                // Reach +15 immediately, then force failures on the PRI attempt.
                return rolls.getAndIncrement() < 15 ? 0.0 : 0.99;
            }
        };
        ManosClothingEnhancer enhancer = new ManosClothingEnhancer(100_000_000, scriptedRandom);

        while (enhancer.getCurrentLevel() < 15) {
            enhancer.enhance();
        }

        assertEquals(30.0, enhancer.getCurrentSuccessChance());
        enhancer.enhance();
        assertEquals(15, enhancer.getCurrentLevel());
        assertEquals(30.0, enhancer.getCurrentSuccessChance());
        enhancer.enhance();
        assertEquals(30.0, enhancer.getCurrentSuccessChance());
    }

    @Test
    void succeedsOnTheClickAfterTheAgrisThresholdIsReached() {
        ManosClothingEnhancer enhancer = enhancerWithSuccessfulRollsUntil(15);

        for (int failedAttempts = 0; failedAttempts < 7; failedAttempts++) {
            enhancer.enhance();
            assertEquals(15, enhancer.getCurrentLevel());
        }

        enhancer.enhance();
        assertEquals(ManosClothingEnhancer.PRI_LEVEL, enhancer.getCurrentLevel());
    }

    @Test
    void onlyDowngradesWhenADuoOrHigherAttemptFails() {
        ManosClothingEnhancer priEnhancer = enhancerWithSuccessfulRollsUntil(16);
        priEnhancer.enhance();
        assertEquals(ManosClothingEnhancer.PRI_LEVEL, priEnhancer.getCurrentLevel());

        ManosClothingEnhancer duoEnhancer = enhancerWithSuccessfulRollsUntil(17);
        duoEnhancer.enhance();
        assertEquals(ManosClothingEnhancer.PRI_LEVEL, duoEnhancer.getCurrentLevel());
    }

    private ManosClothingEnhancer enhancerWithSuccessfulRollsUntil(int level) {
        AtomicInteger rolls = new AtomicInteger();
        Random scriptedRandom = new Random() {
            @Override
            public double nextDouble() {
                return rolls.getAndIncrement() < level ? 0.0 : 0.99;
            }
        };
        ManosClothingEnhancer enhancer = new ManosClothingEnhancer(100_000_000, scriptedRandom);
        while (enhancer.getCurrentLevel() < level) {
            enhancer.enhance();
        }
        return enhancer;
    }
}
