package com.bdo.enhancer.model.result;

import lombok.Data;

/**
 * Result class for costume enhancement calculations
 */
@Data
public class CostumeEnhancementResult {
    private final String name;
    private final long basePrice;

    // DUO enhancement results
    public long duoItems;
    public long duoProfit;

    // TRI enhancement results
    public long triItems;
    public long triProfit;

    // TET enhancement results
    public long tetItems;
    public long tetProfit;

    /**
     * Constructor with all parameters for costume enhancement calculation results
     */
    public CostumeEnhancementResult(
            String name,
            long basePrice,
            long duoItems,
            long duoProfit,
            long triItems,
            long triProfit,
            long tetItems,
            long tetProfit) {
        this.name = name;
        this.basePrice = basePrice;
        this.duoItems = duoItems;
        this.duoProfit = duoProfit;
        this.triItems = triItems;
        this.triProfit = triProfit;
        this.tetItems = tetItems;
        this.tetProfit = tetProfit;
    }
}