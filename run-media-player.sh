#!/bin/bash

# Set VLC library path for macOS
export JNA_LIBRARY_PATH=/Applications/VLC.app/Contents/MacOS/lib
export DYLD_LIBRARY_PATH=/Applications/VLC.app/Contents/MacOS/lib

# Clean and compile with Maven
mvn clean compile

# Run the application with the correct library path
mvn exec:java -Dexec.mainClass="com.audioapp.AudioPlayerApp" -Djna.library.path=/Applications/VLC.app/Contents/MacOS/lib

# If you prefer to test the video surface separately
# mvn exec:java -Dexec.mainClass="com.audioapp.VideoSurfaceTest" -Djna.library.path=/Applications/VLC.app/Contents/MacOS/lib 