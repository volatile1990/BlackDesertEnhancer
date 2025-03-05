package enhancer.manos;

import java.util.Random;

public class ManosArmor {
	private int currentLevel;
	private int currentFails;
	private long durabilityLost;
	private int blackGemUsed;
	private int concBlackGemUsed;
	private long cronsUsed;
	private double[] enhanceChances;
	private Random random;

	private long BLACK_GEM_COST = 2000000;
	private long CONC_BLACK_GEM_COST = 15000000;
	private long MEM_COST = 3000000;

	public ManosArmor(int currentLevel) {
		this();
		this.currentLevel = currentLevel;
	}

	public ManosArmor() {
		this.currentLevel = 0;
		this.currentFails = 0;
		this.durabilityLost = 0;
		blackGemUsed = 0;
		concBlackGemUsed = 0;
		this.cronsUsed = 0;
		this.enhanceChances = new double[] { 100, 100, 100, 100, 100, 90, 80, 70, 60, 50, 40, 30, 20, 15, 10, 30, 25, 20, 15, 6 };
		this.random = new Random();
	}

	public boolean enhance(boolean useCrons) {
		if (currentLevel >= enhanceChances.length) {
			System.out.println("Maximales Level erreicht!");
			return true;
		}

		double currentChance = enhanceChances[currentLevel];
		int maxFails = (int) (2 * (100 / currentChance));

		if (this.currentLevel < 15) {
			blackGemUsed += 5;
		} else {
			concBlackGemUsed += 1;
		}

		if (currentFails >= maxFails) {
//			System.out.println("100% Erfolg! Level erhöht von " + currentLevel + " auf " + (currentLevel + 1));
			currentLevel++;
			currentFails = 0;
			return true;
		} else {
			boolean success = random.nextDouble() * 100 <= currentChance;
			if (success) {
//				System.out.println("Erfolg! Level erhöht von " + currentLevel + " auf " + (currentLevel));
				++currentLevel;
				currentFails = 0;
				return true;
			} else {
//				System.out.println("Fehlgeschlagen. Aktuelles Level: " + currentLevel + ", Fehlversuche: " + (currentFails));
				++currentFails;

				if (currentLevel < 15) {
					durabilityLost += 5;
				} else {
					durabilityLost += 10;
				}

				if (currentLevel >= 17) {

					if (useCrons) {
						if (currentLevel == 17) {
							this.cronsUsed += 60;
						} else if (currentLevel == 18) {
							this.cronsUsed += 355;
						}
					} else {
						currentLevel--;
					}

				}

				return false;
			}
		}
	}

	public int getCurrentLevel() {
		return currentLevel;
	}

	public int getCurrentFails() {
		return currentFails;
	}

	public long getCost(boolean useArtisan) {

		long blackGemCost = BLACK_GEM_COST * blackGemUsed;
		blackGemCost += CONC_BLACK_GEM_COST * concBlackGemUsed;

		long memCost = 0;
		if (useArtisan) {
			memCost += MEM_COST * (durabilityLost / 5);
		} else {
			memCost += MEM_COST * durabilityLost;
		}

		return blackGemCost + memCost;
	}

	public long getArtisansUsed() {
		return durabilityLost / 5;
	}

	public long getCronsUsed() {
		return this.cronsUsed;
	}
}