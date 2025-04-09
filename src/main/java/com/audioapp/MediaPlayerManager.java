package com.audioapp;

import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.List;
import com.sun.jna.NativeLibrary;

/**
 * MediaPlayerManager handles all media playback functionality including audio and video.
 * This class uses VLC's Java bindings (VLCJ) to provide robust media playback capabilities.
 */
public class MediaPlayerManager {
    
    private static final Logger LOGGER = Logger.getLogger(MediaPlayerManager.class.getName());
    
    private MediaPlayerFactory factory;
    private EmbeddedMediaPlayer mediaPlayer;
    private EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private File currentFile;
    private boolean isVideo = false;
    private Canvas videoSurface;
    private VideoSurface vlcVideoSurface;
    private MediaPlayerEventListener eventListener;
    private String currentMediaPath;
    private List<String> videoExtensions = Arrays.asList("mp4", "avi", "mkv", "mov", "wmv", "flv");
    
    // For direct rendering
    private BufferedImage frameImage;
    private int[] imageBuffer;
    
    // List of common video file extensions
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts"
    );
    
    // Mac specific fields
    private boolean usingMacWorkaround = false;
    private java.awt.Window macVideoWindow = null;
    
    /**
     * Interface for media player events.
     */
    public interface MediaPlayerEventListener {
        void onPlaybackComplete();
        void onTimeChanged(long newTime);
        void onPlay();
        void onPause();
        void onStop();
        void onComplete();
        void onError();
    }
    
    /**
     * Constructs a MediaPlayerManager and initializes VLC components.
     */
    public MediaPlayerManager() {
        try {
            // Set VLC library path for Mac OS X
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                String vlcLibPath = "/Applications/VLC.app/Contents/MacOS/lib";
                NativeLibrary.addSearchPath("libvlc", vlcLibPath);
                System.setProperty("jna.library.path", vlcLibPath);
                LOGGER.info("Setting Mac VLC library path to: " + vlcLibPath);
            }
            
            // Create factory with minimal options
        factory = new MediaPlayerFactory();
            
            // Directly create an embedded media player instead of using the component
        mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();
        
            LOGGER.info("MediaPlayerManager initialized with VLC version: " + 
                        factory.application().version());
            
            // Add event handler for playback events
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                if (eventListener != null) {
                    eventListener.onPlaybackComplete();
                }
            }
            
            @Override
                public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                    if (eventListener != null) {
                        eventListener.onTimeChanged(newTime);
                    }
                }
                
                @Override
                public void playing(MediaPlayer mediaPlayer) {
                    if (eventListener != null) {
                        eventListener.onPlay();
                    }
            }
            
            @Override
                public void paused(MediaPlayer mediaPlayer) {
                    if (eventListener != null) {
                        eventListener.onPause();
                    }
            }
            
            @Override
                public void stopped(MediaPlayer mediaPlayer) {
                if (eventListener != null) {
                        eventListener.onStop();
                    }
                }
                
                @Override
                public void error(MediaPlayer mediaPlayer) {
                    if (eventListener != null) {
                        eventListener.onError();
                }
            }
        });
        } catch (UnsatisfiedLinkError e) {
            // VLC libraries not found
            LOGGER.severe("VLC libraries not found. Video playback will be disabled: " + e.getMessage());
            LOGGER.info("Please install VLC media player to enable video support.");
            
            // Disable video features but don't crash the app
            factory = null;
            mediaPlayer = null;
            mediaPlayerComponent = null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing media player", e);
            throw new RuntimeException("Failed to initialize media player", e);
        }
    }
    
    /**
     * Sets an event listener to receive playback events.
     * 
     * @param listener The event listener to set
     */
    public void setEventListener(MediaPlayerEventListener listener) {
        this.eventListener = listener;
    }
    
    /**
     * Sets the Canvas component to be used as the video surface.
     * This method should be called before playing a video file.
     *
     * @param canvas the Canvas component to be used as the video surface
     */
    public void setVideoSurface(Canvas canvas) {
        this.videoSurface = canvas;
        
        // If we have a media player, always try to attach the video surface
        if (mediaPlayer != null && canvas != null) {
            LOGGER.info("Setting video surface: Canvas provided");
            
            // Detach any existing surface first
            try {
                if (vlcVideoSurface != null) {
                    mediaPlayer.videoSurface().set(null);
                    vlcVideoSurface = null;
                }
                
                // Clean up Mac workaround window if it exists
                if (macVideoWindow != null) {
                    macVideoWindow.dispose();
                    macVideoWindow = null;
                    usingMacWorkaround = false;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error detaching old video surface", e);
            }
            
            // Setup direct rendering for macOS
            setupDirectRendering(canvas);
        }
    }
    
    /**
     * Sets up direct rendering for video display on macOS.
     * Uses callback-based rendering to a BufferedImage that will be drawn to the canvas.
     * 
     * @param canvas The canvas to render video on
     */
    private void setupDirectRendering(final Canvas canvas) {
        if (canvas == null || !canvas.isDisplayable()) {
            LOGGER.warning("Cannot set up direct rendering: Canvas is not displayable");
            return;
        }
        
        try {
            // Initial image with default size
            int width = Math.max(640, canvas.getWidth());
            int height = Math.max(480, canvas.getHeight());
            
            frameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            imageBuffer = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
            
            // Override the canvas paint method to draw our frame image
            canvas.getGraphics();  // Ensure the canvas has a graphics context
            
            // Setup callbacks
            BufferFormatCallback bufferFormatCallback = new BufferFormatCallback() {
                @Override
                public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
                    // Resize image if the video dimensions change
                    if (sourceWidth > 0 && sourceHeight > 0 && 
                        (frameImage.getWidth() != sourceWidth || frameImage.getHeight() != sourceHeight)) {
                        
                        LOGGER.info("Resizing video buffer to " + sourceWidth + "x" + sourceHeight);
                        frameImage = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_RGB);
                        imageBuffer = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
                    }
                    return new RV32BufferFormat(sourceWidth, sourceHeight);
                }
                
                @Override
                public void allocatedBuffers(ByteBuffer[] buffers) {
                    // Required but can be empty
                }
            };
            
            RenderCallback renderCallback = new RenderCallback() {
                @Override
                public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat) {
                    // Copy the video data from native buffer to the image
                    ByteBuffer byteBuffer = nativeBuffers[0];
                    byteBuffer.asIntBuffer().get(imageBuffer, 0, imageBuffer.length);
                    
                    // Redraw the canvas with the new frame
                    if (canvas.isDisplayable() && canvas.isVisible()) {
                        Graphics g = canvas.getGraphics();
                        if (g != null) {
                            g.drawImage(frameImage, 0, 0, canvas.getWidth(), canvas.getHeight(), null);
                            g.dispose();
                        }
                    }
                }
            };
            
            // Create a callback surface and set it on the media player
            CallbackVideoSurface videoSurface = factory.videoSurfaces().newVideoSurface(
                bufferFormatCallback, renderCallback, true);
            
        mediaPlayer.videoSurface().set(videoSurface);
            this.vlcVideoSurface = videoSurface;
            
            LOGGER.info("Direct rendering video surface successfully set up");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set up direct rendering", e);
        }
    }
    
    /**
     * Checks if the media player is available (VLC libraries loaded).
     * 
     * @return true if the media player is available, false otherwise
     */
    public boolean isMediaPlayerAvailable() {
        return mediaPlayer != null;
    }
    
    /**
     * Loads a media file for playback.
     * 
     * @param file The media file to load
     * @return true if the file was loaded successfully, false otherwise
     */
    public boolean loadFile(File file) {
        if (!isMediaPlayerAvailable()) {
            LOGGER.warning("Cannot load file: Media player not available (VLC libraries missing)");
            return false;
        }
        
        if (file == null || !file.exists()) {
            LOGGER.warning("Cannot load file: File is null or does not exist");
            return false;
        }

        // Stop current playback if any
        if (mediaPlayer != null && mediaPlayer.status().isPlayable()) {
            mediaPlayer.controls().stop();
        }

        // Check if the file is a video based on its extension
        String extension = getFileExtension(file).toLowerCase();
        isVideo = VIDEO_EXTENSIONS.contains(extension);
        
        LOGGER.info("Loading file: " + file.getAbsolutePath());
        LOGGER.info("File type: " + (isVideo ? "Video" : "Audio") + " (extension: " + extension + ")");
        
        // Store the current file
        currentFile = file;
        
        try {
            // Simple standard options for all platforms
            String[] options = {"--no-video-title-show"};
            
            if (mediaPlayer == null) {
                initializeMediaPlayer();
            }
            
            // Load the media file
            boolean loaded = mediaPlayer.media().prepare(file.getAbsolutePath(), options);
            
            if (!loaded) {
                LOGGER.warning("Failed to load media file: " + file.getAbsolutePath());
                return false;
            }
            
            // Attach the video surface if this is a video file
            if (isVideo && videoSurface != null) {
                LOGGER.info("Video file detected, preparing video surface");
                setupDirectRendering(videoSurface);
            }
            
            LOGGER.info("Successfully loaded media file: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading media file", e);
            return false;
        }
    }
    
    /**
     * Starts or resumes playback of the current media file.
     */
    public void play() {
        if (!isMediaPlayerAvailable()) {
            LOGGER.warning("Cannot play: Media player not available (VLC libraries missing)");
            return;
        }
        
        if (mediaPlayer == null || currentFile == null) {
            LOGGER.warning("Cannot play: No media loaded");
            return;
        }
        
        try {
            // For video files, make sure the video surface is attached before playing
            if (isVideo && videoSurface != null) {
                LOGGER.info("Playing video file, checking video surface");
                
                // Make sure component is visible
                videoSurface.setVisible(true);
                
                // Try to attach video surface if not already attached
                if (vlcVideoSurface == null) {
                    LOGGER.info("No video surface attached yet, attempting to attach");
                    setupDirectRendering(videoSurface);
                }
            }
            
            // Start playback
            mediaPlayer.controls().play();
            if (eventListener != null) {
                eventListener.onPlay();
            }
            LOGGER.info("Playback started");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during playback", e);
            if (eventListener != null) {
                eventListener.onError();
            }
        }
    }
    
    /**
     * Pauses playback.
     */
    public void pause() {
        if (!isMediaPlayerAvailable()) {
            return;
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.controls().pause();
            LOGGER.info("Playback paused");
        }
    }
    
    /**
     * Stops playback and resets position.
     */
    public void stop() {
        if (!isMediaPlayerAvailable()) {
            return;
        }
        
        if (mediaPlayer != null) {
        mediaPlayer.controls().stop();
        LOGGER.info("Playback stopped");
        }
    }
    
    /**
     * Seeks to a specific position in the media.
     * 
     * @param timeInMs The position to seek to, in milliseconds
     */
    public void seekTo(int timeInMs) {
        if (!isMediaPlayerAvailable()) {
            return;
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.controls().setTime(timeInMs);
            LOGGER.info("Seeked to position: " + timeInMs + "ms");
        }
    }
    
    /**
     * Sets the playback volume.
     * 
     * @param volume Volume level from 0 to 100
     */
    public void setVolume(int volume) {
        if (!isMediaPlayerAvailable()) {
            return;
        }
        
        if (mediaPlayer != null) {
            // VLC volume is 0-200, so multiply by 2
            mediaPlayer.audio().setVolume(volume * 2);
        }
    }
    
    /**
     * Gets the total duration of the current media in milliseconds.
     * 
     * @return Total duration in milliseconds
     */
    public int getTotalDuration() {
        if (!isMediaPlayerAvailable()) {
            return 0;
        }
        
        if (mediaPlayer != null) {
            return (int) mediaPlayer.status().length();
        }
        return 0;
    }
    
    /**
     * Checks if playback is currently paused.
     * 
     * @return true if paused, false otherwise
     */
    public boolean isPaused() {
        if (!isMediaPlayerAvailable()) {
            return false;
        }
        
        return mediaPlayer != null && !mediaPlayer.status().isPlaying();
    }
    
    /**
     * Checks if playback is currently stopped.
     * 
     * @return true if stopped, false otherwise
     */
    public boolean isStopped() {
        if (!isMediaPlayerAvailable()) {
            return true;
        }
        
        return mediaPlayer == null || !mediaPlayer.status().isPlayable();
    }
    
    /**
     * Checks if the currently loaded media is a video file.
     *
     * @return true if the media is a video, false otherwise
     */
    public boolean isVideo() {
        if (!isMediaPlayerAvailable()) {
            return false;
        }
        
        if (currentFile == null) {
            return false;
        }
        
        String lowerPath = currentFile.getName().toLowerCase();
        return lowerPath.endsWith(".mp4") || lowerPath.endsWith(".avi") || 
               lowerPath.endsWith(".mkv") || lowerPath.endsWith(".mov") ||
               lowerPath.endsWith(".wmv") || lowerPath.endsWith(".flv");
    }
    
    /**
     * Releases resources used by the media player.
     * Should be called when the application is closing.
     */
    public void release() {
        if (!isMediaPlayerAvailable()) {
            return;
        }
        
        // Clean up Mac workaround if it exists
        if (macVideoWindow != null) {
            macVideoWindow.dispose();
            macVideoWindow = null;
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        
        if (factory != null) {
            factory.release();
            factory = null;
        }
        
        LOGGER.info("Media player resources released");
    }
    
    /**
     * Initializes the media player if it hasn't been initialized yet.
     */
    private void initializeMediaPlayer() {
        if (factory == null) {
            factory = new MediaPlayerFactory();
        }
        
        if (mediaPlayerComponent == null) {
            mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        }
        
        if (mediaPlayer == null) {
            mediaPlayer = mediaPlayerComponent.mediaPlayer();
            // Set up event listeners or other configuration as needed
            setupEventListeners();
        }
    }
    
    /**
     * Sets up event listeners for the media player.
     */
    private void setupEventListeners() {
        // Add event listeners as needed for playback events
        // For example:
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                LOGGER.info("Playback finished");
                // Additional handling for playback completion
            }
            
            @Override
            public void error(MediaPlayer mediaPlayer) {
                LOGGER.severe("Media player error occurred");
                // Error handling
            }
        });
    }
    
    /**
     * Extracts the file extension from a file.
     * 
     * @param file the file to extract the extension from
     * @return the file extension (without the dot) or an empty string if there is no extension
     */
    private String getFileExtension(File file) {
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }

    /**
     * Attempts to get the native component ID for a Canvas component.
     * This is primarily used for Mac OS X video surface creation.
     * 
     * @param component the canvas component to get the ID for
     * @return the native component ID, or -1 if it cannot be obtained
     */
    private long getComponentId(Canvas component) {
        if (component == null) {
            return -1;
        }
        
        try {
            // Try to use reflection to get the component ID
            java.lang.reflect.Method method = Class.forName("com.sun.jna.Native")
                .getMethod("getComponentID", java.awt.Component.class);
            Object result = method.invoke(null, component);
            if (result instanceof Long) {
                return (Long) result;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to get native component ID: " + e.getMessage());
        }
        
        return -1;
    }
} 