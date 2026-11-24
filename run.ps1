# Smart Hospital Resource and Treatment Management System Runner
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "   SMART HOSPITAL RESOURCE AND TREATMENT MANAGEMENT SYSTEM" -ForegroundColor Green
Write-Host "====================================================================" -ForegroundColor Cyan

$javaBin = (Get-Command java -ErrorAction SilentlyContinue).Source
$javacBin = (Get-Command javac -ErrorAction SilentlyContinue).Source

if (-not $javacBin -and (Test-Path "C:\Program Files\Java\jdk-25.0.2\bin\javac.exe")) {
    $javacBin = "C:\Program Files\Java\jdk-25.0.2\bin\javac.exe"
    $javaBin = "C:\Program Files\Java\jdk-25.0.2\bin\java.exe"
}

Write-Host "Compiling Java backend..." -ForegroundColor Yellow
& $javacBin -cp "lib/mysql-connector-j-8.4.0.jar;." -d . *.java
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Compilation successful!" -ForegroundColor Green
Write-Host "Starting HospitalServer on http://localhost:8080/ ..." -ForegroundColor Cyan
& $javaBin -cp "lib/mysql-connector-j-8.4.0.jar;." HospitalServer
