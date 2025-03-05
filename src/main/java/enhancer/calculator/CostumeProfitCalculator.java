package enhancer.calculator;

import enhancer.Constants;
import enhancer.market.BDOMarket;
import enhancer.market.Costume;
import enhancer.models.CostumeStack;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CostumeProfitCalculator {

	private static final int SIMULATION_RUNS = 100000;

	// Used Stacks
	private static final CostumeStack MON_STACK = CostumeStack.TEN;
	private static final CostumeStack DUO_STACK = CostumeStack.TWENTY;
	private static final CostumeStack TRI_STACK = CostumeStack.THIRY;
	private static final CostumeStack TET_STACK = CostumeStack.FIFTYFIVE;

	// Enhancement chances
	private static final double MON_CHANCE = MON_STACK.mon;
	private static final double DUO_CHANCE = DUO_STACK.duo;
	private static final double TRI_CHANCE = TRI_STACK.tri;
	private static final double TET_CHANCE = TET_STACK.tet;

	public static void main(String[] args) {
		new CostumeProfitCalculator().calculateAndPrintProfits();
	}

	public void calculateAndPrintProfits() {
		BDOMarket market = new BDOMarket();
		List<Costume> costumes = market.getCostumes();
		List<CostumeResult> results = new ArrayList<>();

		System.out.println("Profit Analysis for Available Costumes");
		System.out.println("=====================================");

		for (Costume costume : costumes) {
			// Calculate TRI profits
			EnhancementResult triResult = calculateEnhancementCost(costume.getBasePrice(), false);
			long triProfit = calculateProfit(costume.getTriPrice(), triResult.avgCost);

			// Calculate TET profits
			EnhancementResult tetResult = calculateEnhancementCost(costume.getBasePrice(), true);
			long tetProfit = calculateProfit(costume.getTetPrice(), tetResult.avgCost);

			results.add(new CostumeResult(costume.getName(), triResult.avgCostumes, triProfit, tetResult.avgCostumes, tetProfit));
		}

		// Format strings f�r die verschiedenen Tabellen
		String triFormat = "%-40s | %12s | %15s%n";
		String tetFormat = "%-40s | %12s | %15s%n";

		// Ausgabe sortiert nach TRI Profit
		results.sort((a, b) -> Long.compare(b.triProfit, a.triProfit));
		System.out.println("\nTRI Profit Analysis");
		System.out.println("=".repeat(75));
		System.out.printf(triFormat, "Name", "Avg TRI Items", "TRI Profit");
		System.out.println("-".repeat(75));
		for (CostumeResult result : results) {
			System.out.printf(triFormat, truncateString(result.name, 39), result.triItems, formatNumber(result.triProfit));
		}
		System.out.println("=".repeat(75));

		System.out.println("\n\n");

		// Ausgabe sortiert nach TET Profit
		results.sort((a, b) -> Long.compare(b.tetProfit, a.tetProfit));
		System.out.println("\nTET Profit Analysis");
		System.out.println("=".repeat(75));
		System.out.printf(tetFormat, "Name", "Avg TET Items", "TET Profit");
		System.out.println("-".repeat(75));
		for (CostumeResult result : results) {
			System.out.printf(tetFormat, truncateString(result.name, 39), result.tetItems, formatNumber(result.tetProfit));
		}
		System.out.println("=".repeat(75));
	}

	private String truncateString(String str, int maxLength) {
		if (str.length() <= maxLength) {
			return str;
		}
		return str.substring(0, maxLength - 3) + "...";
	}

	private static class CostumeResult {
		final String name;
		final long triItems;
		final long triProfit;
		final long tetItems;
		final long tetProfit;

		CostumeResult(String name, long triItems, long triProfit, long tetItems, long tetProfit) {
			this.name = name;
			this.triItems = triItems;
			this.triProfit = triProfit;
			this.tetItems = tetItems;
			this.tetProfit = tetProfit;
		}
	}

	private EnhancementResult calculateEnhancementCost(long basePrice, boolean doTet) {
		long totalCost = 0;
		long totalCostumes = 0;

		for (int i = 0; i < SIMULATION_RUNS; i++) {
			SimulationRun run = simulateEnhancement(basePrice, doTet);
			totalCost += run.cost;
			totalCostumes += run.costumes;
		}

		return new EnhancementResult(totalCost / SIMULATION_RUNS, totalCostumes / SIMULATION_RUNS);
	}

	private SimulationRun simulateEnhancement(long basePrice, boolean doTet) {
		long cost = 0;
		long costumesNeeded = 0;
		boolean done = false;
		double currentMonChance = MON_CHANCE;
		double currentDuoChance = DUO_CHANCE;
		double currentTriChance = TRI_CHANCE;
		double currentTetChance = TET_CHANCE;
		int monFails = 0;
		int duoFails = 0;
		int triFails = 0;
		int tetFails = 0;

		while (!done) {

			cost += (basePrice * 2);
			costumesNeeded += 2;

			// PRI attempt
			if ((Math.random() * 100) <= currentMonChance || monFails >= 5) {

				if (monFails < 5) {
					cost += MON_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
					currentMonChance = MON_CHANCE;
				}
				cost += basePrice;
				costumesNeeded++;
				monFails = 0;

				// DUO attempt
				if ((Math.random() * 100) <= currentDuoChance || duoFails >= 6) {

					if (duoFails < 6) {
						cost += DUO_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
						currentDuoChance = DUO_CHANCE;
					}
					cost += basePrice;
					costumesNeeded++;
					duoFails = 0;

					// TRI attempt
					if ((Math.random() * 100) <= currentTriChance || triFails >= 8) {

						if (triFails < 8) {
							cost += TRI_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
							currentTriChance = TRI_CHANCE;
						}
						triFails = 0;

						if (doTet) {
							cost += basePrice;
							costumesNeeded++;

							// TET attempt
							if ((Math.random() * 100) <= currentTetChance || tetFails >= 10) {

								if (tetFails < 10) {
									cost += TET_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
									currentTetChance = TET_CHANCE;
								}
								tetFails = 0;
								done = true;
							} else {
								currentTetChance += 0.0025;
								tetFails++;
							}
						} else {
							done = true;
						}
					} else {
						currentTriChance += 0.0075;
						triFails++;
					}
				} else {
					currentDuoChance += 0.01;
					duoFails++;
				}
			} else {
				currentMonChance += 0.03;
				monFails++;
			}
		}
		return new SimulationRun(cost, costumesNeeded);
	}

	private long calculateProfit(long salePrice, long cost) {
		return (long) ((salePrice - cost) * Constants.MARKET_TAX);
	}

	private String formatNumber(long number) {
		return String.format("%,d", number);
	}

	private static class EnhancementResult {
		final long avgCost;
		final long avgCostumes;

		EnhancementResult(long avgCost, long avgCostumes) {
			this.avgCost = avgCost;
			this.avgCostumes = avgCostumes;
		}
	}

	private static class SimulationRun {
		final long cost;
		final long costumes;

		SimulationRun(long cost, long costumes) {
			this.cost = cost;
			this.costumes = costumes;
		}
	}
}