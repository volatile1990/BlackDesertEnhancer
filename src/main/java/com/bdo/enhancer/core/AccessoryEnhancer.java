package com.bdo.enhancer.core;

import com.bdo.enhancer.model.constants.Constants;
import com.bdo.enhancer.model.stack.FailStackSet;
import com.bdo.enhancer.model.stack.AccessoryStack;
import lombok.Getter;
import lombok.Setter;

import java.util.Random;

/**
 * Represents a generic accessory that can be enhanced with different success rates
 * and costs depending on the enhancement level.
 */
@Getter
@Setter
public class AccessoryEnhancer {

	// Constants
	private static final int BASE_LEVEL = 0;

	// Base properties
	private final long basePrice;
	private final Random random;

	// Enhancement parameters
	private final double[] enhanceChances;      // Success chance per level
	private final long[] failstackCost;         // Cost of failstack at each level
	private final int[] pityThreshold;          // Number of fails needed for guaranteed success
	private final double[] chanceIncreaseOnFail;// How much the chance increases per fail
	private final double[] changeIncreaseOnFailAfterSoftcap;
	private final int[] softcapThreshold;

	private FailStackSet stacksUsed;

	// State tracking
	private final int[] failCounter;            // Number of fails at each level

	@Getter
	private int currentLevel = BASE_LEVEL;

	@Getter
	private long totalEnhanceCost;              // Total cost of enhancement attempts

	@Getter
	private int totalItemsConsumed;             // Total number of items used

	/**
	 * Creates an AccessoryEnhancer with default enhancement parameters.
	 */
	public AccessoryEnhancer() {
		this(450000000,
				new double[] { 83.5, 54, 47.4 },
				new long[] {
						406 * Constants.BLACK_STONE_PRICE,
						8 * Constants.CRYSTALLIZED_DESPAIR_PRICE,
						35 * Constants.CRYSTALLIZED_DESPAIR_PRICE
				});
	}

	/**
	 * Creates an AccessoryEnhancer with custom enhancement parameters.
	 *
	 * @param basePrice       The base price of the accessory
	 * @param enhanceChances  Success chances for each enhancement level
	 * @param failstackCost   Cost of failstack for each enhancement level
	 */
	public AccessoryEnhancer(long basePrice, double[] enhanceChances, long[] failstackCost) {
		this.basePrice = basePrice;
		this.enhanceChances = enhanceChances;
		this.failstackCost = failstackCost;

		this.random = new Random();
		this.pityThreshold = new int[] { 5, 6, 8, 10 };
		this.failCounter = new int[] { 0, 0, 0, 0 };
		this.chanceIncreaseOnFail = new double[] { 0.025, 0.01, 0.0075, 0.0025 };
		this.changeIncreaseOnFailAfterSoftcap = new double[] { 0.005, 0.002, 0.0015, 0.0005 };
		this.softcapThreshold = new int[] { 18, 40, 44, 110 };

		this.totalEnhanceCost = 0;
		this.totalItemsConsumed = 0;

		this.stacksUsed = null;
	}

	/**
	 * Attempts to enhance the accessory to the next level.
	 */
	public void enhance() {
		// Calculate and add material cost
		addMaterialCost();

		// Roll for success
		double currentSuccessChance = calculateSuccessChance();
		boolean success = rollForSuccess(currentSuccessChance);

		// Process result
		boolean pity = hasReachedPity();
		if (success || pity) {
			handleSuccess(pity);
		} else {
			handleFailure();
		}
	}

	/**
	 * Adds the material cost for the current enhancement attempt.
	 */
	private void addMaterialCost() {
		if (currentLevel == BASE_LEVEL) {
			// Base level requires 2 accessories
			totalEnhanceCost += 2 * basePrice;
			totalItemsConsumed += 2;
		} else {
			// Higher levels require 1 accessory
			totalEnhanceCost += basePrice;
			totalItemsConsumed += 1;
		}
	}

	/**
	 * Calculates the current success chance based on level and fail count.
	 *
	 * @return the success percentage (0-100)
	 */
	private double calculateSuccessChance() {
		int stack = switch (currentLevel) {
			case 0 -> this.stacksUsed.getMonStack().stackCount;
			case 1 -> this.stacksUsed.getDuoStack().stackCount;
			case 2 -> this.stacksUsed.getTriStack().stackCount;
			case 3 -> this.stacksUsed.getTetStack().stackCount;
            default -> throw new IllegalStateException("Unexpected value: " + currentLevel);
        };

		if (failCounter[currentLevel] + stack > this.softcapThreshold[currentLevel]) {
			return enhanceChances[currentLevel] +
					(failCounter[currentLevel] * changeIncreaseOnFailAfterSoftcap[currentLevel]) * 100;
		}

		return enhanceChances[currentLevel] +
				(failCounter[currentLevel] * chanceIncreaseOnFail[currentLevel]) * 100;
	}

	/**
	 * Performs a random roll to determine if enhancement succeeds.
	 *
	 * @param successChance the chance of success (0-100)
	 * @return true if the roll is successful, false otherwise
	 */
	private boolean rollForSuccess(double successChance) {
		double roll = random.nextDouble() * 100;
		return roll <= successChance;
	}

	/**
	 * Checks if the pity system guarantees a success.
	 *
	 * @return true if pity threshold is reached, false otherwise
	 */
	private boolean hasReachedPity() {
		return failCounter[currentLevel] >= pityThreshold[currentLevel];
	}

	/**
	 * Handles successful enhancement.
	 */
	private void handleSuccess(boolean pity) {
		// Add failstack cost if not from pity system
		if (!pity) {
			totalEnhanceCost += failstackCost[currentLevel];
		}

		// Reset fail counter and increase level
		failCounter[currentLevel] = 0;
		currentLevel++;
	}

	/**
	 * Handles failed enhancement.
	 */
	private void handleFailure() {
		// Reset level to base and increment fail counter
		failCounter[currentLevel]++;
		currentLevel = BASE_LEVEL;
	}
}
