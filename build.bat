@echo off
echo Terminating running instances...
taskkill /F /IM VirtualClipboard.exe /T 2>nul

echo Cleaning and packaging JAR...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Maven build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo Removing old distribution...
if exist dist rd /s /q dist

echo Generating native application image...
"C:\Program Files\Java\jdk-24\bin\jpackage.exe" ^
  --input target ^
  --dest dist ^
  --name VirtualClipboard ^
  --main-jar virtual-clipboard-1.0-SNAPSHOT.jar ^
  --main-class com.virtualclipboard.App ^
  --type app-image

if %ERRORLEVEL% EQU 0 (
    echo.
    echo -------------------------------------------------------
    echo BUILD SUCCESSFUL!
    echo Executable: dist\VirtualClipboard\VirtualClipboard.exe
    echo -------------------------------------------------------
) else (
    echo Building native image failed!
)

pause
