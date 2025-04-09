#!/bin/bash

# Go to the directory containing this script
cd "$(dirname "$0")"

# Set VLC library path for macOS
export JNA_LIBRARY_PATH=/Applications/VLC.app/Contents/MacOS/lib
export DYLD_LIBRARY_PATH=/Applications/VLC.app/Contents/MacOS/lib

# Run the JAR file with the VLC path set
java -Djna.library.path=/Applications/VLC.app/Contents/MacOS/lib -jar target/audio-player-1.0-SNAPSHOT-jar-with-dependencies.jar 