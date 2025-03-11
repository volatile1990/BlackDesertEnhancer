package com.bdo.enhancer;

import com.bdo.enhancer.ui.EnhancerMainFrame;

import javax.swing.*;

/**
 * Main application class
 */
public class EnhancerApplication {
    
    /**
     * Application entry point
     */
    public static void main(String[] args) {
        // Start Swing UI in the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Set Look and Feel
                setupLookAndFeel();
                
                // Display main window
                EnhancerMainFrame mainFrame = new EnhancerMainFrame();
                mainFrame.setVisible(true);
            } catch (Exception e) {
                handleStartupError(e);
            }
        });
    }
    
    /**
     * Configures the application's Look and Feel
     */
    private static void setupLookAndFeel() {
        try {
            // Use the system Look and Feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // If system Look and Feel is not available, use the default Look and Feel
            System.err.println("Error setting up Look and Feel: " + e.getMessage());
        }
    }
    
    /**
     * Handles errors during application startup
     */
    private static void handleStartupError(Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(
                null,
                "Error starting application: " + e.getMessage(),
                "Startup Error",
                JOptionPane.ERROR_MESSAGE
        );
        System.exit(1);
    }
}
