@echo off
echo Starting Clip-Pal in test mode...
mvn exec:java
if %ERRORLEVEL% neq 0 (
    echo.
    echo Application crashed or failed to start.
    pause
)
