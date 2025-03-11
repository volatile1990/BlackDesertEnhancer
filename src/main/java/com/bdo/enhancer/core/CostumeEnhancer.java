package com.bdo.enhancer.core;

import com.bdo.enhancer.model.item.Costume;
import com.bdo.enhancer.model.stack.CostumeStack;
import com.bdo.enhancer.model.result.SimulationRun;
import lombok.Getter;
import lombok.Setter;

import java.util.Random;

/**
 * Enhancer for costumes with custom success rates and behavior logic
 */
@Getter
@Setter
public class CostumeEnhancer {
    
    // Current enhancement level and random generator
    private int currentLevel = 0;
    private final Random random = new Random();
    
    // Current stacks for different enhancement levels
    private CostumeStack monStack = CostumeStack.FOURTY;
    private CostumeStack duoStack = CostumeStack.FOURTY;
    private CostumeStack triStack = CostumeStack.FOURTYFIVE;
    private CostumeStack tetStack = CostumeStack.HUNDREDTEN_FREE;
    
    // Base price of the costume
    private final long basePrice;
    
    // Tracking costs and consumed items
    private long totalCost = 0;
    private int itemsUsed = 0;
    
    /**
     * Constructor with the base price of the costume
     * 
     * @param costume The costume to enhance
     */
    public CostumeEnhancer(Costume costume) {
        this.basePrice = costume.getBasePrice();
    }
    
    /**
     * Performs an enhancement attempt
     * 
     * @return True if successful, False if failed
     */
    public boolean enhance() {
        // Add material costs
        addMaterialCost();
        
        // Determine success probability
        double successChance = getSuccessChance();
        
        // Random attempt for enhancement
        boolean success = random.nextDouble() * 100 <= successChance;
        
        if (success) {
            // On success: Add stack costs and increase level
            totalCost += getStackCost();
            currentLevel++;
            return true;
        } else {
            // On failure: Back to base level
            currentLevel = 0;
            return false;
        }
    }
    
    /**
     * Increases the material costs for an enhancement attempt
     */
    private void addMaterialCost() {
        if (currentLevel == 0) {
            // Base level requires two items
            totalCost += 2 * basePrice;
            itemsUsed += 2;
        } else {
            // Higher levels need one additional item
            totalCost += basePrice;
            itemsUsed += 1;
        }
    }
    
    /**
     * Determines the success probability for the current enhancement level
     * 
     * @return The success probability in percent
     */
    private double getSuccessChance() {
        return switch (currentLevel) {
            case 0 -> monStack.mon;   // PRI chance
            case 1 -> duoStack.duo;   // DUO chance
            case 2 -> triStack.tri;   // TRI chance
            case 3 -> tetStack.tet;   // TET chance
            default -> throw new IllegalStateException("Unsupported enhancement level: " + currentLevel);
        };
    }
    
    /**
     * Determines the costs for the failstack of the current enhancement level
     * 
     * @return The costs of the failstack
     */
    private long getStackCost() {
        // Can be implemented more precisely if needed
        return 0;
    }
    
    /**
     * Creates a simulation result for the current values
     * 
     * @return SimulationRun with costs and consumed items
     */
    public SimulationRun getSimulationRun() {
        return new SimulationRun(totalCost, itemsUsed);
    }
    
    /**
     * Resets the enhancer for a new simulation
     */
    public void reset() {
        currentLevel = 0;
        totalCost = 0;
        itemsUsed = 0;
    }
}
