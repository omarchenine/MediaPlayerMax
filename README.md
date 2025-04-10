# MediaPlayerMax

A professional Java Swing-based audio player application that provides a seamless experience for playing MP3 files with comprehensive playback controls.

## 👨‍💻 Authors

- Omar Seifeddine Chenine
- Ilyes Benmalti

## 🎵 Features

- **File Management**
  - Load MP3 files through an intuitive file chooser interface
  - Support for multiple audio formats

- **Playback Controls**
  - Play, Pause, and Stop functionality
  - Real-time progress tracking with visual progress bar
  - Current playback status and file information display

- **Audio Controls**
  - Volume adjustment slider
  - Duration and current time display
  - High-quality audio playback

## 🛠️ Technical Requirements

- Java 11 or higher
- Maven 3.6.0 or higher
- VLC Media Player (for video playback)

## 📦 Dependencies

- JLayer 1.0.1 - Core MP3 playback functionality
- VLCJ - Video playback functionality

## 🚀 Getting Started

### For Windows Users:

1. **Install Prerequisites**
   - Install Java 11 or higher
   - Install Maven
   - Install VLC Media Player (64-bit version if you have 64-bit Windows)

2. **Build the application**
   ```bash
   mvn clean package
   ```

3. **Run the application (Choose one method)**
   
   Method 1 - Using Java directly:
   ```bash
   java -jar target/MediaPlayerMax.jar
   ```
   
   Method 2 - Using the batch script:
   ```bash
   run-media-player.bat
   ```

### For Mac Users:

1. **Install Prerequisites**
   - Install Java 11 or higher
   - Install Maven
   - Install VLC Media Player

2. **Build the application**
   ```bash
   mvn clean package
   ```

3. **Run the application (Choose one method)**
   
   Method 1 - Using the app bundle:
   ```bash
   open MediaPlayerMax.app
   ```
   
   Method 2 - Using the shell script:
   ```bash
   ./run-mac-app.command
   ```

## 📚 Submission Requirements

### Required Files
1. **Source Code**
   - Complete NetBeans/Eclipse project files
   - All Java source files
   - Project configuration files

2. **Documentation**
   - Technical Report (PDF) including:
     - Design choices and architecture
     - Implementation challenges
     - Solutions and workarounds
     - Future improvements

3. **Screenshots**
   - Application running with media loaded
   - Main interface features visible
   - Playback controls in action

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
