package com.bdo.enhancer.config;

import com.bdo.enhancer.model.constants.Constants;
import com.bdo.enhancer.model.stack.AccessoryStack;
import lombok.Data;

/**
 * Application configuration
 */
@Data
public class EnhancerConfig {
    
    // Default failstack settings
    private AccessoryStack defaultMonStack = AccessoryStack.FOURTY;
    private AccessoryStack defaultDuoStack = AccessoryStack.FOURTY;
    private AccessoryStack defaultTriStack = AccessoryStack.FOURTYFIVE;
    private AccessoryStack defaultTetStack = AccessoryStack.HUNDREDTEN_FREE;
    
    // Simulation settings
    private int simulationRuns = Constants.SIMULATION_RUN_COUNT;
    private int optimizationRuns = Constants.OPTIMIZATION_RUN_COUNT;
    
    // Thread settings for parallel calculations
    private int threadCount = Runtime.getRuntime().availableProcessors();
    
    // UI settings
    private boolean useDarkMode = true;
    
    // Singleton instance
    private static EnhancerConfig instance;
    
    /**
     * Private constructor for singleton pattern
     */
    private EnhancerConfig() {
        // Initialization
    }
    
    /**
     * Returns the singleton instance
     */
    public static synchronized EnhancerConfig getInstance() {
        if (instance == null) {
            instance = new EnhancerConfig();
        }
        return instance;
    }
}
