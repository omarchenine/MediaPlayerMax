package com.audioapp;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;

/**
 * Main entry point for the Audio Player application.
 * This class initializes the Swing UI and starts the application.
 */
public class AudioPlayerApp {
    
    // Logger for application events
    private static final Logger LOGGER = Logger.getLogger(AudioPlayerApp.class.getName());
    
    /**
     * Main method to start the application.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        // Configure the logger
        configureLogger();
        
        LOGGER.info("Starting Audio Player application");
        
        // Run the application in the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Set the look and feel to the system's look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                LOGGER.info("Set system look and feel: " + UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to set system look and feel", e);
            }
            
            // Create and display the UI
            AudioPlayerUI playerUI = new AudioPlayerUI();
            playerUI.setVisible(true);
            LOGGER.info("Application UI initialized and displayed");
        });
    }
    
    /**
     * Configures the application logger.
     */
    private static void configureLogger() {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
        
        // Remove the default console handler attached to the root logger
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler h : handlers) {
            if (h instanceof ConsoleHandler) {
                rootLogger.removeHandler(h);
            }
        }
    }
}
