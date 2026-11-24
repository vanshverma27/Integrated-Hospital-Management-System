@echo off
title Smart Hospital Resource and Treatment Management System
echo ====================================================================
echo   SMART HOSPITAL RESOURCE AND TREATMENT MANAGEMENT SYSTEM
echo ====================================================================
echo Compiling Java source files...
javac -cp "lib/mysql-connector-j-8.4.0.jar;." -d . *.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed! Please check Java installation.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Starting Hospital Server on http://localhost:8080/ ...
echo (Press Ctrl+C to stop the server)
echo.
java -cp "lib/mysql-connector-j-8.4.0.jar;." HospitalServer
pause
