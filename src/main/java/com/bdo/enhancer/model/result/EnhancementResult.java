package com.bdo.enhancer.model.result;

public class EnhancementResult {
	public long avgCost;
	public long avgItems;

	public EnhancementResult(long avgCost, long avgItems) {
		this.avgCost = avgCost;
		this.avgItems = avgItems;
	}
}