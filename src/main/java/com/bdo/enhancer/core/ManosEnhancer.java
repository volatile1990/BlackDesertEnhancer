package com.bdo.enhancer.core;

import com.bdo.enhancer.model.item.ManosArmor;

import java.text.NumberFormat;
import java.util.Locale;

public class ManosEnhancer {
	private static final int SIMULATION_COUNT = 10000;
	private static final int TARGET_LEVEL = 19;
	private static final boolean USE_CRONS = true;
	private static final boolean USE_ARTISANS = true;

	public void simulateEnhancement() {
		long totalCost = 0;
		long totalArtisans = 0;
		long totalCrons = 0;

		for (int i = 0; i < SIMULATION_COUNT; i++) {
			ManosArmor manosArmor = new ManosArmor(0);
			while (manosArmor.getCurrentLevel() < TARGET_LEVEL) {
				manosArmor.enhance(USE_CRONS);
			}
			totalCost += manosArmor.getCost(USE_ARTISANS);
			totalArtisans += manosArmor.getArtisansUsed();
			totalCrons += manosArmor.getCronsUsed();
		}

		double avgCost = (double) totalCost / SIMULATION_COUNT;
		double avgArtisans = (double) totalArtisans / SIMULATION_COUNT;
		double avgCrons = (double) totalCrons / SIMULATION_COUNT;

		NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.GERMAN);
		System.out.println("Durchschnittliche Kosten: " + numberFormat.format(Math.round(avgCost)));
		System.out.println("Durchschnittliche Artisans: " + numberFormat.format(Math.round(avgArtisans)));
		System.out.println("Durchschnittliche Crons: " + numberFormat.format(Math.round(avgCrons)));
	}
}