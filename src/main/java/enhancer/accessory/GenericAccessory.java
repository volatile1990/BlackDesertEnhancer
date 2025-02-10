package enhancer.accessory;

import java.util.Random;

import enhancer.Constants;

public class GenericAccessory {

	private long basePrice = 450000000;
	private long enhanceCost;
	private int itemsNeeded;
	private int currentLevel = 0;
	private Random random;
	private double[] enhanceChances;
	private long[] failstackCost;
	private int[] pity;
	private int[] fails;
	private double[] chanceIncreaseOnFail;

	public GenericAccessory() {
		this.random = new Random();

		long priStackCost = 406 * Constants.BLACK_STONE_PRICE;
		long duoStackCost = 8 * Constants.CRYSTALLIZED_DESPAIR_PRICE;
		long triStackCost = 35 * Constants.CRYSTALLIZED_DESPAIR_PRICE;

		this.failstackCost = new long[] { priStackCost, duoStackCost, triStackCost };
		this.enhanceChances = new double[] { 83.5, 54, 47.4 };
		this.pity = new int[] { 5, 6, 8, 10 };
		this.fails = new int[] { 0, 0, 0, 0 };
		this.chanceIncreaseOnFail = new double[] { 0, 03, 0, 01, 0, 0075, 0, 0025 };

		this.enhanceCost = 0;
		this.itemsNeeded = 0;
	}

	public GenericAccessory(int basePrice, double[] enhanceChances, long[] failstackCost) {
		this();
		this.basePrice = basePrice;
		this.enhanceChances = enhanceChances;
		this.failstackCost = failstackCost;
	}

	public boolean enhance() {

		if (currentLevel == 0) {
			enhanceCost += 2 * basePrice;
			itemsNeeded += 2;
		} else {
			enhanceCost += basePrice;
			++itemsNeeded;
		}

		double roll = random.nextDouble() * 100;

		boolean success = roll <= getSuccessChance();
		if (success || fails[currentLevel] >= pity[currentLevel]) {

			if (fails[currentLevel] < pity[currentLevel]) {
				enhanceCost += failstackCost[currentLevel];
			}

			fails[currentLevel] = 0;
			++currentLevel;

			return true;
		} else {
			currentLevel = 0;
			++fails[currentLevel];
		}

		return false;
	}

	private double getSuccessChance() {
		return enhanceChances[currentLevel] + fails[currentLevel] * chanceIncreaseOnFail[currentLevel];
	}

	public long getEnhanceCost() {
		return this.enhanceCost;
	}

	public int getItemsNeeded() {
		return this.itemsNeeded;
	}

	public int getCurrentLevel() {
		return this.currentLevel;
	}
}
