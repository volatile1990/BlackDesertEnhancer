package com.bdo.enhancer.model.result;

public class AccessoryEnhancementResult {
	public String name;
	public long baseStock;
	public long triItems;
	public long triProfit;
	public long tetItems;
	public long tetProfit;
	public long duoItems;
	public long duoProfit;
	public double duoProfitPerItem;
	public double triProfitPerItem;
	public double tetProfitPerItem;

	public AccessoryEnhancementResult(String name, long baseStock, long duoItems, long duoProfit, long triItems, long triProfit, long tetItems, long tetProfit) {
		this.name = name;
		this.baseStock = baseStock;

		this.triItems = triItems;
		this.triProfit = triProfit;

		this.tetItems = tetItems;
		this.tetProfit = tetProfit;

		this.duoItems = duoItems;
		this.duoProfit = duoProfit;

		this.duoProfitPerItem = (double) duoProfit / duoItems;
		this.triProfitPerItem = (double) triProfit / triItems;
		this.tetProfitPerItem = (double) tetProfit / tetItems;
	}
}
