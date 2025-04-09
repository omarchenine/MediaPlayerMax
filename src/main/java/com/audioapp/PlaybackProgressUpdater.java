package com.audioapp;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.util.logging.Logger;

/**
 * Thread that updates the UI elements to reflect playback progress.
 * This includes updating the progress bar and status labels.
 */
public class PlaybackProgressUpdater extends Thread {
    
    private static final Logger LOGGER = Logger.getLogger(PlaybackProgressUpdater.class.getName());
    
    private AudioPlayer audioPlayer;
    private JProgressBar progressBar;
    private JLabel durationLabel;
    private AudioPlayerUI playerUI;
    private volatile boolean running = true;
    private boolean wasPlaying = false;
    
    /**
     * Constructor initializes the updater with UI components to update.
     * 
     * @param audioPlayer The audio player instance
     * @param progressBar The progress bar to update
     * @param statusLabel The status label to update (not used)
     * @param durationLabel The duration label to update
     */
    public PlaybackProgressUpdater(AudioPlayer audioPlayer, JProgressBar progressBar, 
                                  JLabel statusLabel, JLabel durationLabel) {
        this.audioPlayer = audioPlayer;
        this.progressBar = progressBar;
        this.durationLabel = durationLabel;
        
        // Find the AudioPlayerUI parent
        if (progressBar != null) {
            SwingUtilities.getAncestorOfClass(AudioPlayerUI.class, progressBar);
            this.playerUI = (AudioPlayerUI) SwingUtilities.getAncestorOfClass(AudioPlayerUI.class, progressBar);
        }
        
        // Set daemon thread so it doesn't prevent application exit
        setDaemon(true);
        LOGGER.info("Progress updater initialized");
    }
    
    /**
     * Stops the updater thread.
     */
    public void stopUpdater() {
        running = false;
        interrupt();
        LOGGER.info("Progress updater stopped");
    }
    
    /**
     * Thread run method that periodically updates the UI.
     */
    @Override
    public void run() {
        int elapsedTime = 0;
        long startTime = System.currentTimeMillis();
        
        while (running) {
            if (!audioPlayer.isPaused() && !audioPlayer.isStopped()) {
                wasPlaying = true;
                
                // Calculate elapsed time
                elapsedTime = (int) (System.currentTimeMillis() - startTime);
                
                // Update player's internal position
                audioPlayer.updatePosition(elapsedTime);
                
                // Update UI
                updateUI(elapsedTime);
                
                // Check if we've reached the end of the track
                if (elapsedTime >= audioPlayer.getTotalDuration()) {
                    // Notify UI that playback has completed
                    if (playerUI != null) {
                        SwingUtilities.invokeLater(() -> {
                            playerUI.onPlaybackComplete();
                        });
                    }
                    
                    // Reset elapsed time and start time
                    elapsedTime = 0;
                    startTime = System.currentTimeMillis();
                }
            } else if (audioPlayer.isPaused()) {
                // When paused, update start time for when playback resumes
                startTime = System.currentTimeMillis() - elapsedTime;
                wasPlaying = false;
            } else if (audioPlayer.isStopped()) {
                // When stopped naturally (not by user)
                if (wasPlaying && elapsedTime >= audioPlayer.getTotalDuration() * 0.95) {
                    // If we were playing and close to the end, we've completed playback
                    if (playerUI != null) {
                        SwingUtilities.invokeLater(() -> {
                            playerUI.onPlaybackComplete();
                        });
                    }
                    wasPlaying = false;
                }
                
                // Reset when stopped
                elapsedTime = 0;
                startTime = System.currentTimeMillis();
                updateUI(0);
            }
            
            try {
                // Sleep for a short time to reduce CPU usage
                Thread.sleep(100);
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
                Thread.currentThread().interrupt();
            }
        }
        
        LOGGER.info("Progress updater thread exiting");
    }
    
    /**
     * Updates the UI components based on current playback position.
     * 
     * @param elapsedTime The current playback position in milliseconds
     */
    private void updateUI(int elapsedTime) {
        final int currentTime = elapsedTime;
        final int totalTime = audioPlayer.getTotalDuration();
        
        SwingUtilities.invokeLater(() -> {
            // Update progress bar
            if (totalTime > 0) {
                int progress = (int) (((float) currentTime / totalTime) * 100);
                progressBar.setValue(progress);
            } else {
                progressBar.setValue(0);
            }
            
            // Update duration label
            String currentTimeStr = formatTime(currentTime);
            String totalTimeStr = formatTime(totalTime);
            durationLabel.setText(currentTimeStr + " / " + totalTimeStr);
        });
    }
    
    /**
     * Formats time in milliseconds to a MM:SS string.
     * 
     * @param timeInMs Time in milliseconds
     * @return Formatted time string
     */
    private String formatTime(int timeInMs) {
        int seconds = (timeInMs / 1000) % 60;
        int minutes = (timeInMs / 60000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
