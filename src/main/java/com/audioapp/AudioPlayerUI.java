package com.audioapp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The main UI class for the Media Player application.
 * Contains all the GUI components and handles user interactions.
 */
public class AudioPlayerUI extends JFrame {
    
    private static final Logger LOGGER = Logger.getLogger(AudioPlayerUI.class.getName());
    
    // UI Components
    private JButton loadButton;
    private JButton playButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton previousButton;
    private JButton nextButton;
    private JToggleButton repeatButton;
    private JToggleButton shuffleButton;
    private JToggleButton themeToggleButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JLabel durationLabel;
    private JSlider volumeSlider;
    private JList<String> playlistList;
    private DefaultListModel<String> playlistModel;
    private Canvas videoCanvas;
    private JPanel videoPanel;
    private JSplitPane mainSplitPane;
    
    // Media player
    private MediaPlayerManager mediaPlayer;
    
    // File
    private File currentFile;
    
    // Playlist
    private List<File> playlist = new ArrayList<>();
    private int currentPlaylistIndex = -1;
    private boolean repeat = false;
    private boolean shuffle = false;
    
    // Theme
    private boolean darkTheme = false;
    
    /**
     * Constructor initializes the UI and media player.
     */
    public AudioPlayerUI() {
        setTitle("Media Player Max");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        // Initialize media player
        mediaPlayer = new MediaPlayerManager();
        
        // Check if media player was successfully initialized
        if (!mediaPlayer.isMediaPlayerAvailable()) {
            LOGGER.warning("VLC libraries not found. Video playback will be disabled.");
            JOptionPane.showMessageDialog(this,
                "VLC libraries were not found on your system. Video playback will be disabled.\n" +
                "To enable video support, please install VLC media player.",
                "Missing VLC Libraries",
                JOptionPane.WARNING_MESSAGE);
        }
        
        mediaPlayer.setEventListener(new MediaPlayerManager.MediaPlayerEventListener() {
            @Override
            public void onPlaybackComplete() {
                SwingUtilities.invokeLater(() -> {
                    onPlaybackComplete();
                });
            }
            
            @Override
            public void onTimeChanged(long newTime) {
                SwingUtilities.invokeLater(() -> {
                    updateProgress((int)newTime);
                });
            }
            
            @Override
            public void onPlay() {
                SwingUtilities.invokeLater(() -> {
                    updateButtonStates(false, true, true);
                });
            }
            
            @Override
            public void onPause() {
                SwingUtilities.invokeLater(() -> {
                    updateButtonStates(true, false, true);
                });
            }
            
            @Override
            public void onStop() {
                SwingUtilities.invokeLater(() -> {
                    updateButtonStates(true, false, false);
                });
            }
            
            @Override
            public void onComplete() {
                // Already handled by onPlaybackComplete
            }
            
            @Override
            public void onError() {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error during playback");
                    updateButtonStates(true, false, false);
                });
            }
        });
        
        // Initialize UI components
        initUI();
        
        // Register keyboard shortcuts
        registerKeyboardShortcuts();
        
        // Add window listener to cleanup resources when the window is closed
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                }
                LOGGER.info("Application closing, resources cleaned up");
            }
        });
        
        LOGGER.info("MediaPlayerUI initialized");
    }
    
    /**
     * Initializes all UI components and layouts.
     */
    private void initUI() {
        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Status panel at the top
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("No file loaded");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        durationLabel = new JLabel("00:00 / 00:00");
        durationLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(durationLabel, BorderLayout.EAST);
        
        mainPanel.add(statusPanel, BorderLayout.NORTH);
        
        // Main content panel (center) with split pane
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setResizeWeight(0.7); // Give more weight to the video/progress area
        
        // Left side - Video and progress panel
        JPanel mediaPanel = new JPanel(new BorderLayout(5, 5));
        
        // Video panel - will be shown only for video files
        videoPanel = new JPanel(new BorderLayout());
        videoPanel.setBorder(BorderFactory.createEtchedBorder());
        videoPanel.setBackground(Color.BLACK);
        videoPanel.setPreferredSize(new Dimension(640, 480));
        
        // Create the video canvas
        videoCanvas = new Canvas();
        videoCanvas.setBackground(Color.BLACK);
        videoCanvas.setPreferredSize(new Dimension(640, 480));
        videoPanel.add(videoCanvas, BorderLayout.CENTER);
        
        // Set the video surface on the media player
        if (mediaPlayer != null) {
            mediaPlayer.setVideoSurface(videoCanvas);
        }
        
        // By default, hide the video panel until a video file is loaded
        videoPanel.setVisible(false);
        
        mediaPanel.add(videoPanel, BorderLayout.CENTER);
        
        // Progress bar panel
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(false);
        
        // Make the progress bar clickable to seek
        progressBar.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (currentFile != null && !mediaPlayer.isStopped()) {
                    int mouseX = evt.getX();
                    int progressBarWidth = progressBar.getWidth();
                    float percentage = (float) mouseX / progressBarWidth;
                    int seekPosition = (int) (percentage * mediaPlayer.getTotalDuration());
                    mediaPlayer.seekTo(seekPosition);
                }
            }
        });
        
        progressPanel.add(progressBar, BorderLayout.CENTER);
        mediaPanel.add(progressPanel, BorderLayout.SOUTH);
        
        // Right side - Playlist
        JPanel playlistPanel = new JPanel(new BorderLayout(5, 5));
        playlistPanel.setBorder(BorderFactory.createTitledBorder("Playlist"));
        
        playlistModel = new DefaultListModel<>();
        playlistList = new JList<>(playlistModel);
        playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && playlistList.getSelectedIndex() != -1) {
                currentPlaylistIndex = playlistList.getSelectedIndex();
                loadPlaylistItem(currentPlaylistIndex);
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(playlistList);
        playlistPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Playlist controls
        JPanel playlistControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addToPlaylistButton = new JButton("Add");
        addToPlaylistButton.addActionListener(e -> addToPlaylist());
        
        JButton removeFromPlaylistButton = new JButton("Remove");
        removeFromPlaylistButton.addActionListener(e -> removeFromPlaylist());
        
        JButton clearPlaylistButton = new JButton("Clear");
        clearPlaylistButton.addActionListener(e -> clearPlaylist());
        
        playlistControlPanel.add(addToPlaylistButton);
        playlistControlPanel.add(removeFromPlaylistButton);
        playlistControlPanel.add(clearPlaylistButton);
        
        playlistPanel.add(playlistControlPanel, BorderLayout.SOUTH);
        
        // Add to split pane
        mainSplitPane.setLeftComponent(mediaPanel);
        mainSplitPane.setRightComponent(playlistPanel);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
        
        // Volume control panel
        JPanel volumePanel = new JPanel(new BorderLayout(5, 0));
        JLabel volumeLabel = new JLabel("Volume: ");
        volumePanel.add(volumeLabel, BorderLayout.WEST);
        
        volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 80);
        volumeSlider.addChangeListener(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(volumeSlider.getValue());
            }
        });
        volumePanel.add(volumeSlider, BorderLayout.CENTER);
        
        // Theme toggle button
        themeToggleButton = new JToggleButton("Dark Theme");
        themeToggleButton.addActionListener(e -> toggleTheme());
        volumePanel.add(themeToggleButton, BorderLayout.EAST);
        
        // Add volume panel to main panel
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(volumePanel, BorderLayout.NORTH);
        
        // Buttons panel at the bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        
        loadButton = new JButton("Load");
        loadButton.setToolTipText("Load media file (Ctrl+O)");
        loadButton.addActionListener(e -> loadFile());
        
        previousButton = new JButton("◀◀");
        previousButton.setToolTipText("Previous track (Ctrl+P)");
        previousButton.setEnabled(false);
        previousButton.addActionListener(e -> playPrevious());
        
        playButton = new JButton("▶");
        playButton.setToolTipText("Play (Space)");
        playButton.setEnabled(false);
        playButton.addActionListener(e -> playMedia());
        
        pauseButton = new JButton("⏸");
        pauseButton.setToolTipText("Pause (Space)");
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> pauseMedia());
        
        stopButton = new JButton("⏹");
        stopButton.setToolTipText("Stop (Ctrl+S)");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopMedia());
        
        nextButton = new JButton("▶▶");
        nextButton.setToolTipText("Next track (Ctrl+N)");
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> playNext());
        
        repeatButton = new JToggleButton("🔁");
        repeatButton.setToolTipText("Repeat (Ctrl+R)");
        repeatButton.addActionListener(e -> {
            repeat = repeatButton.isSelected();
            LOGGER.info("Repeat " + (repeat ? "enabled" : "disabled"));
        });
        
        shuffleButton = new JToggleButton("🔀");
        shuffleButton.setToolTipText("Shuffle (Ctrl+H)");
        shuffleButton.addActionListener(e -> {
            shuffle = shuffleButton.isSelected();
            LOGGER.info("Shuffle " + (shuffle ? "enabled" : "disabled"));
        });
        
        buttonPanel.add(loadButton);
        buttonPanel.add(previousButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(repeatButton);
        buttonPanel.add(shuffleButton);
        
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        
        // Add the main panel to the frame
        add(mainPanel);
    }
    
    /**
     * Updates the progress display with the current playback position.
     * 
     * @param currentTime Current playback time in milliseconds
     */
    private void updateProgress(int currentTime) {
        int totalTime = mediaPlayer.getTotalDuration();
        
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
    
    /**
     * Registers keyboard shortcuts for the application.
     */
    private void registerKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        
        // Space for play/pause
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "PlayPause");
        actionMap.put("PlayPause", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentFile != null) {
                    if (mediaPlayer.isPaused() || mediaPlayer.isStopped()) {
                        playMedia();
                    } else {
                        pauseMedia();
                    }
                }
            }
        });
        
        // Ctrl+O for open file
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK), "OpenFile");
        actionMap.put("OpenFile", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                loadFile();
            }
        });
        
        // Ctrl+S for stop
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK), "Stop");
        actionMap.put("Stop", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentFile != null) {
                    stopMedia();
                }
            }
        });
        
        // Ctrl+P for previous track
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK), "Previous");
        actionMap.put("Previous", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                playPrevious();
            }
        });
        
        // Ctrl+N for next track
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK), "Next");
        actionMap.put("Next", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                playNext();
            }
        });
        
        // Ctrl+R for repeat toggle
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK), "Repeat");
        actionMap.put("Repeat", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                repeatButton.doClick();
            }
        });
        
        // Ctrl+H for shuffle toggle
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, java.awt.event.InputEvent.CTRL_DOWN_MASK), "Shuffle");
        actionMap.put("Shuffle", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                shuffleButton.doClick();
            }
        });
        
        // F for fullscreen toggle (for videos)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "FullScreen");
        actionMap.put("FullScreen", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mediaPlayer.isVideo()) {
                    toggleFullScreen();
                }
            }
        });
    }
    
    /**
     * Toggles fullscreen mode for video playback.
     */
    private boolean isFullScreen = false;
    private Rectangle normalBounds;
    
    private void toggleFullScreen() {
        if (!isFullScreen) {
            // Save current bounds
            normalBounds = getBounds();
            
            // Hide decorations and go fullscreen
            dispose();
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setVisible(true);
            
            // Hide other components, only show video
            mainSplitPane.setDividerLocation(1.0);
            isFullScreen = true;
        } else {
            // Restore windowed mode
            dispose();
            setUndecorated(false);
            setBounds(normalBounds);
            setVisible(true);
            
            // Restore split pane
            mainSplitPane.setDividerLocation(0.7);
            isFullScreen = false;
        }
    }
    
    /**
     * Opens a file chooser dialog to select a media file.
     */
    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a media file");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Media files (*.mp3, *.wav, *.mp4, *.avi, *.mkv)", 
                "mp3", "wav", "aiff", "mp4", "avi", "mkv", "mov", "wmv", "flv"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            
            try {
                // Stop any current playback
                mediaPlayer.stop();
                
                // Load the new file
                boolean loaded = mediaPlayer.loadFile(currentFile);
                if (loaded) {
                    statusLabel.setText("Loaded: " + currentFile.getName());
                    updateButtonStates(true, false, false);
                    
                    // Show/hide video panel based on media type
                    boolean isVideo = mediaPlayer.isVideo();
                    videoPanel.setVisible(isVideo);
                    
                    // If it's a video, ensure the canvas is properly set up
                    if (isVideo && mediaPlayer.isMediaPlayerAvailable()) {
                        LOGGER.info("Video file detected, preparing video surface");
                        
                        // Make sure the video panel is visible and has focus
                        videoPanel.setVisible(true);
                        
                        // Force validate the canvas and panel
                        videoCanvas.setSize(videoPanel.getSize());
                        videoCanvas.setMinimumSize(new Dimension(320, 240));
                        videoCanvas.setPreferredSize(videoPanel.getSize());
                        videoPanel.validate();
                        videoCanvas.validate();
                        
                        // Immediately set the video surface again
                        mediaPlayer.setVideoSurface(videoCanvas);
                        
                        // Log video surface information for debugging
                        LOGGER.info("Video panel dimensions: " + videoPanel.getWidth() + "x" + videoPanel.getHeight());
                        LOGGER.info("Video canvas dimensions: " + videoCanvas.getWidth() + "x" + videoCanvas.getHeight());
                        LOGGER.info("Video canvas displayable: " + videoCanvas.isDisplayable());
                    }
                    
                    // Add to playlist if not already there
                    addToPlaylistIfNotExists(currentFile);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error loading file", e);
                JOptionPane.showMessageDialog(this,
                        "Error loading file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Error loading file");
                updateButtonStates(false, false, false);
            }
        }
    }
    
    /**
     * Starts or resumes media playback.
     */
    private void playMedia() {
        if (currentFile != null) {
            try {
                // For video files, ensure the video panel is visible
                if (mediaPlayer.isVideo() && mediaPlayer.isMediaPlayerAvailable()) {
                    LOGGER.info("Setting up video surface before playback");
                    
                    // Make sure videoPanel is visible
                    videoPanel.setVisible(true);
                    
                    // Set explicit size
                    videoCanvas.setPreferredSize(new Dimension(640, 480));
                    videoCanvas.setMinimumSize(new Dimension(320, 240));
                    
                    // Force components to show
                    videoCanvas.setVisible(true);
                    videoPanel.validate();
                    videoPanel.repaint();
                    
                    // Set the video surface
                    mediaPlayer.setVideoSurface(videoCanvas);
                    
                    // A small delay to allow UI to update
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Start playback
                mediaPlayer.play();
                statusLabel.setText("Playing: " + currentFile.getName());
                updateButtonStates(true, true, true);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error playing file", e);
                JOptionPane.showMessageDialog(this,
                        "Error playing file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Pauses media playback.
     */
    private void pauseMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            statusLabel.setText("Paused: " + currentFile.getName());
            updateButtonStates(true, false, true);
        }
    }
    
    /**
     * Stops media playback and resets to the beginning.
     */
    private void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            progressBar.setValue(0);
            if (currentFile != null) {
            statusLabel.setText("Stopped: " + currentFile.getName());
            } else {
                statusLabel.setText("Stopped");
            }
            updateButtonStates(true, false, false);
        }
    }
    
    /**
     * Plays the previous track in the playlist.
     */
    private void playPrevious() {
        if (!playlist.isEmpty() && currentPlaylistIndex > 0) {
            currentPlaylistIndex--;
            loadPlaylistItem(currentPlaylistIndex);
        }
    }
    
    /**
     * Plays the next track in the playlist.
     */
    private void playNext() {
        if (!playlist.isEmpty() && currentPlaylistIndex < playlist.size() - 1) {
            currentPlaylistIndex++;
            loadPlaylistItem(currentPlaylistIndex);
        } else if (!playlist.isEmpty() && repeat) {
            // If repeat is enabled and we're at the end, go back to the first track
            currentPlaylistIndex = 0;
            loadPlaylistItem(currentPlaylistIndex);
        }
    }
    
    /**
     * Adds the current file to the playlist if it doesn't already exist.
     */
    private void addToPlaylistIfNotExists(File file) {
        if (!playlist.contains(file)) {
            playlist.add(file);
            playlistModel.addElement(file.getName());
            
            // If this is the first item, set it as current
            if (playlist.size() == 1) {
                currentPlaylistIndex = 0;
                playlistList.setSelectedIndex(0);
            }
            
            updatePlaylistControls();
        }
    }
    
    /**
     * Opens a file chooser to add files to the playlist.
     */
    private void addToPlaylist() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Add to playlist");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Media files (*.mp3, *.wav, *.mp4, *.avi, *.mkv)", 
                "mp3", "wav", "aiff", "mp4", "avi", "mkv", "mov", "wmv", "flv"));
        fileChooser.setMultiSelectionEnabled(true);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            for (File file : files) {
                addToPlaylistIfNotExists(file);
            }
        }
    }
    
    /**
     * Removes the selected item from the playlist.
     */
    private void removeFromPlaylist() {
        int selectedIndex = playlistList.getSelectedIndex();
        if (selectedIndex != -1) {
            playlist.remove(selectedIndex);
            playlistModel.remove(selectedIndex);
            
            // Adjust current index if necessary
            if (currentPlaylistIndex == selectedIndex) {
                if (selectedIndex < playlist.size()) {
                    // Play the next item in the same position
                    loadPlaylistItem(selectedIndex);
                } else if (!playlist.isEmpty()) {
                    // Play the last item
                    currentPlaylistIndex = playlist.size() - 1;
                    loadPlaylistItem(currentPlaylistIndex);
                } else {
                    // Playlist is empty
                    currentPlaylistIndex = -1;
                    stopMedia();
                }
            } else if (currentPlaylistIndex > selectedIndex) {
                // Adjust index for items that moved up
                currentPlaylistIndex--;
            }
            
            updatePlaylistControls();
        }
    }
    
    /**
     * Clears the entire playlist.
     */
    private void clearPlaylist() {
        if (!playlist.isEmpty()) {
            playlist.clear();
            playlistModel.clear();
            currentPlaylistIndex = -1;
            stopMedia();
            updatePlaylistControls();
        }
    }
    
    /**
     * Loads and plays the playlist item at the specified index.
     */
    private void loadPlaylistItem(int index) {
        if (index >= 0 && index < playlist.size()) {
            currentPlaylistIndex = index;
            playlistList.setSelectedIndex(index);
            
            try {
                // Stop current playback
                mediaPlayer.stop();
                
                // Load and play the new file
                currentFile = playlist.get(index);
                boolean loaded = mediaPlayer.loadFile(currentFile);
                
                if (loaded) {
                    statusLabel.setText("Loaded: " + currentFile.getName());
                    
                    // Show/hide video panel based on media type
                    boolean isVideo = mediaPlayer.isVideo();
                    videoPanel.setVisible(isVideo);
                    
                    // If it's a video, ensure the video surface is properly displayable
                    if (isVideo) {
                        // Make sure the video panel is visible and validate layout
                        validate();
                        videoPanel.validate();
                        videoCanvas.validate();
                        
                        // Update the video surface reference in case it changed
                        mediaPlayer.setVideoSurface(videoCanvas);
                    }
                    
                    // Start playback
                    playMedia();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error loading playlist item", e);
                JOptionPane.showMessageDialog(this,
                        "Error loading file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Updates the enabled state of the playlist control buttons.
     */
    private void updatePlaylistControls() {
        boolean hasItems = !playlist.isEmpty();
        boolean hasMultipleItems = playlist.size() > 1;
        
        previousButton.setEnabled(hasItems && currentPlaylistIndex > 0);
        nextButton.setEnabled(hasItems && (currentPlaylistIndex < playlist.size() - 1 || repeat));
        repeatButton.setEnabled(hasMultipleItems);
        shuffleButton.setEnabled(hasMultipleItems);
    }
    
    /**
     * Toggles between dark and light themes.
     */
    private void toggleTheme() {
        darkTheme = themeToggleButton.isSelected();
        
        try {
            if (darkTheme) {
                // Apply dark theme
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                SwingUtilities.updateComponentTreeUI(this);
                
                // Additional dark theme customizations
                applyDarkThemeColors();
                
                themeToggleButton.setText("Light Theme");
                LOGGER.info("Dark theme applied");
            } else {
                // Apply light theme (system default)
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                SwingUtilities.updateComponentTreeUI(this);
                
                themeToggleButton.setText("Dark Theme");
                LOGGER.info("Light theme applied");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to switch themes", e);
        }
    }
    
    /**
     * Applies dark theme colors to components.
     */
    private void applyDarkThemeColors() {
        Color darkBg = new Color(50, 50, 50);
        Color darkFg = new Color(220, 220, 220);
        
        // Apply colors to all components
        setBackground(darkBg);
        getContentPane().setBackground(darkBg);
        getContentPane().setForeground(darkFg);
        
        // Update UI components recursively
        SwingUtilities.invokeLater(() -> {
            applyThemeToComponents(getContentPane(), darkBg, darkFg);
        });
    }
    
    /**
     * Recursively applies theme colors to components.
     */
    private void applyThemeToComponents(Container container, Color bg, Color fg) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel && c != videoPanel) { // Don't change video panel bg
                c.setBackground(bg);
                c.setForeground(fg);
            } else if (c instanceof JLabel) {
                c.setForeground(fg);
            } else if (c instanceof JList) {
                c.setBackground(bg);
                c.setForeground(fg);
            } else if (c instanceof JScrollPane) {
                ((JScrollPane) c).getViewport().setBackground(bg);
            }
            
            if (c instanceof Container) {
                applyThemeToComponents((Container) c, bg, fg);
            }
        }
    }
    
    /**
     * Updates the enabled state of the playback control buttons.
     * 
     * @param play Whether the play button should be enabled
     * @param pause Whether the pause button should be enabled
     * @param stop Whether the stop button should be enabled
     */
    private void updateButtonStates(boolean play, boolean pause, boolean stop) {
        playButton.setEnabled(play);
        pauseButton.setEnabled(pause);
        stopButton.setEnabled(stop);
        updatePlaylistControls();
    }
    
    /**
     * Handles event when media playback completes.
     * This method is called by the progress updater when playback ends.
     */
    public void onPlaybackComplete() {
        LOGGER.info("Playback completed");
        
        if (!playlist.isEmpty()) {
            if (repeat && currentPlaylistIndex == playlist.size() - 1) {
                // If repeating and at the end of playlist, start from beginning
                currentPlaylistIndex = 0;
                loadPlaylistItem(currentPlaylistIndex);
            } else if (currentPlaylistIndex < playlist.size() - 1) {
                // Play next track if available
                playNext();
            } else {
                // End of playlist
                updateButtonStates(true, false, false);
            }
        } else {
            updateButtonStates(true, false, false);
        }
    }
}
