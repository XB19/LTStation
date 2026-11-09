@echo off
rem Installe LTStation sur ce poste : double-cliquer, accepter la demande d'autorisation de Windows, attendre.
net session >nul 2>&1
if errorlevel 1 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fichiers\installer.ps1"
echo.
pause
