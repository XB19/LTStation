@echo off
rem Installe un POSTE DE BUREAU LTStation : double-cliquer, accepter la demande d'autorisation de Windows, repondre aux 2 questions.
net session >nul 2>&1
if errorlevel 1 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fichiers\installer-bureau.ps1"
echo.
pause
