package com.audioapp;

import javazoom.jl.player.Player;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handles the audio playback functionality using JLayer.
 * This class manages loading, playing, pausing, and stopping audio files.
 */
public class AudioPlayer {
    
    private static final Logger LOGGER = Logger.getLogger(AudioPlayer.class.getName());
    
    // Audio player
    private AdvancedPlayer player;
    
    // File input stream
    private FileInputStream fileInputStream;
    private BufferedInputStream bufferedInputStream;
    
    // Current file
    private File currentFile;
    
    // Playback state
    private boolean isPaused = false;
    private boolean isStopped = true;
    private int pausedPosition = 0;
    private int totalDuration = 0;
    private int currentPosition = 0;
    
    // Volume control (0-100)
    private int volume = 80;
    
    // Player thread
    private Thread playerThread;
    
    /**
     * Loads an audio file for playback.
     * 
     * @param file The audio file to load
     * @return true if the file was loaded successfully
     */
    public boolean loadFile(File file) throws Exception {
        // Clean up existing resources
        stop();
        
        try {
            currentFile = file;
            
            // Reset state
            isPaused = false;
            isStopped = true;
            pausedPosition = 0;
            
            // Open file streams
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            
            // Calculate the total duration (approximate)
            totalDuration = calculateDuration(file);
            
            LOGGER.info("Loaded audio file: " + file.getName() + ", Duration: " + totalDuration + "ms");
            
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load audio file", e);
            throw new Exception("Failed to load audio file: " + e.getMessage());
        }
    }
    
    /**
     * Starts or resumes playback of the loaded audio file.
     */
    public void play() throws Exception {
        if (currentFile == null) {
            throw new Exception("No file loaded");
        }
        
        if (isPaused) {
            // Resume from paused position
            resumePlayback();
            LOGGER.info("Resuming playback from: " + pausedPosition + "ms");
        } else if (isStopped) {
            // Start new playback
            startPlayback(0);
            LOGGER.info("Starting new playback");
        }
    }
    
    /**
     * Pauses the current playback.
     */
    public void pause() {
        if (player != null && !isPaused && !isStopped) {
            isPaused = true;
            pausedPosition = currentPosition;
            player.close();
            
            // Stop the player thread
            if (playerThread != null) {
                playerThread.interrupt();
                try {
                    playerThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            LOGGER.info("Playback paused at: " + pausedPosition + "ms");
        }
    }
    
    /**
     * Stops the current playback and resets to the beginning.
     */
    public void stop() {
        if (player != null) {
            player.close();
            isStopped = true;
            isPaused = false;
            pausedPosition = 0;
            currentPosition = 0;
            
            // Stop the player thread
            if (playerThread != null) {
                playerThread.interrupt();
                try {
                    playerThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            LOGGER.info("Playback stopped");
        }
        
        // Close file streams
        closeStreams();
    }
    
    /**
     * Sets the playback volume.
     * 
     * @param volume Volume level from 0 (mute) to 100 (max)
     */
    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        LOGGER.info("Volume set to: " + this.volume + "%");
        // Note: JLayer doesn't have built-in volume control
        // For a real application, you would need to implement volume control
        // using audio processing or an alternative library
    }
    
    /**
     * Gets the current playback position in milliseconds.
     * 
     * @return Current position in milliseconds
     */
    public int getCurrentPosition() {
        return currentPosition;
    }
    
    /**
     * Gets the total duration of the loaded audio file in milliseconds.
     * 
     * @return Total duration in milliseconds
     */
    public int getTotalDuration() {
        return totalDuration;
    }
    
    /**
     * Checks if playback is currently paused.
     * 
     * @return true if paused
     */
    public boolean isPaused() {
        return isPaused;
    }
    
    /**
     * Checks if playback is currently stopped.
     * 
     * @return true if stopped
     */
    public boolean isStopped() {
        return isStopped;
    }
    
    /**
     * Seeks to a specific position in the audio file.
     * Note: This is an approximation as MP3 files don't support precise seeking.
     * 
     * @param position Position in milliseconds to seek to
     */
    public void seekTo(int position) {
        if (currentFile == null) {
            return;
        }
        
        // Bound the position
        position = Math.max(0, Math.min(position, totalDuration));
        
        // Stop current playback
        boolean wasPlaying = !isPaused && !isStopped;
        stop();
        
        try {
            // Start playback from the specified position
            currentPosition = position;
            
            if (wasPlaying) {
                startPlayback(position);
                LOGGER.info("Seeking to position: " + position + "ms");
            } else {
                pausedPosition = position;
                isPaused = true;
                isStopped = false;
                LOGGER.info("Seek position set to: " + position + "ms (paused)");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error seeking to position", e);
        }
    }
    
    /**
     * Starts playback from the specified position.
     * 
     * @param startPosition Position in bytes to start from
     */
    private void startPlayback(int startPosition) {
        try {
            // Close any existing streams
            closeStreams();
            
            // Open new streams
            fileInputStream = new FileInputStream(currentFile);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            
            // Skip to the position if needed
            if (startPosition > 0) {
                // Convert time position to bytes (approximation)
                long skipBytes = (long) (startPosition / 1000.0 * 128000 / 8); // Assuming 128kbps
                bufferedInputStream.skip(skipBytes);
            }
            
            // Update state
            isPaused = false;
            isStopped = false;
            currentPosition = startPosition;
            
            // Create a new player
            player = new AdvancedPlayer(bufferedInputStream);
            player.setPlayBackListener(new PlaybackListener() {
                @Override
                public void playbackFinished(PlaybackEvent evt) {
                    // Playback has finished naturally
                    if (!isPaused) {
                        isStopped = true;
                        isPaused = false;
                        currentPosition = totalDuration;
                        LOGGER.info("Playback finished naturally");
                    }
                }
            });
            
            // Start the player in a separate thread
            playerThread = new Thread(() -> {
                try {
                    player.play();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Playback error", e);
                } finally {
                    if (!isPaused) {
                        isStopped = true;
                    }
                }
            });
            
            playerThread.setDaemon(true);
            playerThread.start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start playback", e);
            isStopped = true;
            isPaused = false;
        }
    }
    
    /**
     * Resumes playback from the paused position.
     */
    private void resumePlayback() {
        if (isPaused) {
            startPlayback(pausedPosition);
        }
    }
    
    /**
     * Closes the file streams.
     */
    private void closeStreams() {
        try {
            if (bufferedInputStream != null) {
                bufferedInputStream.close();
                bufferedInputStream = null;
            }
            if (fileInputStream != null) {
                fileInputStream.close();
                fileInputStream = null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing streams", e);
        }
    }
    
    /**
     * Calculates the approximate duration of the MP3 file.
     * This is a simple approximation based on file size and bitrate.
     * 
     * @param file The MP3 file
     * @return Approximate duration in milliseconds
     */
    private int calculateDuration(File file) {
        // Assume a default bitrate of 128 kbps for MP3 files
        long fileSize = file.length();
        
        // Calculate duration in milliseconds (fileSize * 8 / bitrate)
        return (int) (fileSize * 8 / 128000 * 1000);
    }
    
    /**
     * Updates the current position based on elapsed time.
     * This is called periodically by the progress updater.
     * 
     * @param elapsedTime Elapsed time in milliseconds
     */
    public void updatePosition(int elapsedTime) {
        if (!isPaused && !isStopped) {
            this.currentPosition = elapsedTime;
        }
    }
}
