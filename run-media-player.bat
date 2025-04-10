@echo off
echo Starting MediaPlayerMax...

REM Check if Java is installed
java -version >nul 2>&1
if errorlevel 1 (
    echo Error: Java is not installed or not in PATH
    echo Please install Java 11 or higher
    pause
    exit /b 1
)

REM Check if VLC is installed (for video playback)
if not exist "C:\Program Files\VideoLAN\VLC\vlc.exe" (
    if not exist "C:\Program Files (x86)\VideoLAN\VLC\vlc.exe" (
        echo Warning: VLC is not installed in the default location
        echo Video playback may not work without VLC
        echo Please install VLC media player
        timeout /t 5
    )
)

REM Run the application
java -jar target/MediaPlayerMax.jar

if errorlevel 1 (
    echo Error running the application
    pause
) 