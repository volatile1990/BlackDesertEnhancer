package enhancer.calculator;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import enhancer.Constants;
import enhancer.accessory.GenericAccessory;
import enhancer.market.Accessory;
import enhancer.market.BDOMarket;
import enhancer.models.AccessoryResult;
import enhancer.models.AccessoryStack;
import enhancer.models.EnhancementResult;
import enhancer.models.SimulationRun;

public class AccessoryProfitCalculator {

	private static final int SIMULATION_RUNS = 100000;

	// Used Stacks
	private static final AccessoryStack MON_STACK = AccessoryStack.TWENTY;
	private static final AccessoryStack DUO_STACK = AccessoryStack.FOURTY;
	private static final AccessoryStack TRI_STACK = AccessoryStack.FOURTYFIVE;
	private static final AccessoryStack TET_STACK = AccessoryStack.HUNDREDTEN_FREE;

	public static void main(String[] args) {
		new AccessoryProfitCalculator().calculateAndPrintProfits();
	}

	public void calculateAndPrintProfits() {
		BDOMarket market = new BDOMarket();
		List<Accessory> accessories = market.getAccessories();
		List<AccessoryResult> results = new ArrayList<>();

		// Calculate profits for all accessories
		for (Accessory accessory : accessories) {
			EnhancementResult triResult = calculateEnhancementCost(accessory, false);
			long triProfit = calculateProfit(accessory.triPrice, triResult.avgCost);

			EnhancementResult tetResult = calculateEnhancementCost(accessory, true);
			long tetProfit = calculateProfit(accessory.tetPrice, tetResult.avgCost);

			results.add(new AccessoryResult(accessory.name, accessory.baseStock, triResult.avgItems, triProfit, tetResult.avgItems, tetProfit));
		}

		// Format strings für die verschiedenen Tabellen
		String triFormat = "%-39s | %13s | %12s | %15s%n";
		String tetFormat = "%-39s | %13s | %12s | %15s%n";

		// Sort by TRI Profit per Item (descending) to find the most profitable TRI
		// upgrades
		results.sort((a, b) -> Double.compare(b.triProfitPerItem, a.triProfitPerItem));

		// Print TRI Analysis
		printProfitTable(results, "TRI Profit Analysis", true);

		// Sort by TET Profit per Item (descending) to find the most profitable TET
		// upgrades
		results.sort((a, b) -> Double.compare(b.tetProfitPerItem, a.tetProfitPerItem));

		// Print TET Analysis
		printProfitTable(results, "TET Profit Analysis", false);

	}

	private void printProfitTable(List<AccessoryResult> results, String title, boolean isTri) {
		String format = "%-39s | %13s | %12s | %15s | %15s | %15s%n";
		System.out.println("\n" + title);
		System.out.println("=".repeat(125));
		System.out.printf(format, "Name", "Avg " + (isTri ? "TRI" : "TET") + " Items", "Base Stock", (isTri ? "TRI" : "TET") + " Profit", "TRI Profit/Item", "TET Profit/Item");
		System.out.println("-".repeat(125));

		for (AccessoryResult result : results) {
			long items = isTri ? result.triItems : result.tetItems;
			long profit = isTri ? result.triProfit : result.tetProfit;
			System.out.printf(format, truncateString(result.name, 39), items, result.baseStock, formatNumber(profit), formatNumber(result.triProfitPerItem), formatNumber(result.tetProfitPerItem));
		}
		System.out.println("=".repeat(125));
	}

	private String formatNumber(double number) {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
		DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
		return formatter.format(number);
	}

	private String truncateString(String str, int maxLength) {
		if (str.length() <= maxLength) {
			return str;
		}
		return str.substring(0, maxLength - 3) + "...";
	}

	private EnhancementResult calculateEnhancementCost(Accessory accessory, boolean doTet) {
		long totalCost = 0;
		long totalItems = 0;

		for (int i = 0; i < SIMULATION_RUNS; i++) {
			SimulationRun run = simulateEnhancement(accessory, doTet);
			totalCost += run.cost;
			totalItems += run.items;
		}

		return new EnhancementResult(totalCost / SIMULATION_RUNS, totalItems / SIMULATION_RUNS);
	}

	private SimulationRun simulateEnhancement(Accessory accessory, boolean doTet) {

		long monStackCost = MON_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
		long duoStackCost = DUO_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
		long triStackCost = TRI_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;
		long tetStackCost = TET_STACK.blackStoneCount * Constants.BLACK_STONE_PRICE;

		double[] enhanceChances = new double[] { MON_STACK.mon, DUO_STACK.duo, TRI_STACK.tri, TET_STACK.tet };
		long[] failstackCost = new long[] { monStackCost, duoStackCost, triStackCost, tetStackCost };
		GenericAccessory enhanceAccessory = new GenericAccessory(accessory.basePrice, enhanceChances, failstackCost);
		int targetLevel = (doTet) ? 4 : 3;

		while (enhanceAccessory.getCurrentLevel() < targetLevel) {
			enhanceAccessory.enhance();
		}

		return new SimulationRun(enhanceAccessory.getEnhanceCost(), enhanceAccessory.getItemsNeeded());

	}

	private long calculateProfit(long salePrice, long cost) {
		return (long) ((salePrice - cost) * Constants.MARKET_TAX);
	}

	private String formatNumber(long number) {
		return String.format("%,d", number);
	}

}
