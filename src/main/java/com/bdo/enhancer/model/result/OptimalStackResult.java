package com.bdo.enhancer.model.result;

import com.bdo.enhancer.model.stack.AbstractStack;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modellklasse zum Speichern der optimalen Stack-Kombination für ein Accessoire
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptimalStackResult {

    /** Name des Accessoires */
    public String accessoryName;

    public AbstractStack optimalPriStack;

    public AbstractStack optimalDuoStack;

    public AbstractStack optimalTriStack;

    public long totalProfit;

    /**
     * Gibt einen formatierten String mit den optimalen Stacks zurück
     */
    public String getFormattedStacks() {
        return String.format("PRI: %s, DUO: %s, TRI: %s",
                optimalPriStack, optimalDuoStack, optimalTriStack);
    }

    /**
     * Gibt einen formatierten String mit dem Profit zurück
     */
    public String getFormattedProfit() {
        return String.format("%,d", totalProfit);
    }
}