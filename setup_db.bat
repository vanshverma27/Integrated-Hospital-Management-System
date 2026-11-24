@echo off
echo ====================================================================
echo   DATABASE SETUP - Smart Hospital Resource & Treatment System
echo ====================================================================
echo This script initializes the 'model_db' database in MySQL.
echo.
set /p MYSQL_USER="Enter MySQL Username [default: root]: " || set MYSQL_USER=root
if "%MYSQL_USER%"=="" set MYSQL_USER=root

echo Importing hospital_db.sql...
mysql -u %MYSQL_USER% -p < hospital_db.sql
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Database setup completed successfully!
) else (
    echo.
    echo Database setup failed. Make sure MySQL service is running.
)
pause
