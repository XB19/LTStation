@echo off
rem PARTIE PRIVEE : installe les cles de cartes sur ce poste. A ne jamais publier ni envoyer par e-mail.
rem Lancer apres installer-base.bat et apres un premier demarrage de l'application.
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

if not defined LTS_CENTER_DB set "LTS_CENTER_DB=center"
if not defined LTS_PROPS set "LTS_PROPS=%APPDATA%\LTStation Station\data\station\StationSoftware.properties"
if not defined PGHOST set "PGHOST=localhost"
if not defined PGPORT set "PGPORT=5432"
if not defined PGUSER set "PGUSER=postgres"

set "PSQL="
for /d %%D in ("%ProgramFiles%\PostgreSQL\*") do if exist "%%D\bin\psql.exe" set "PSQL=%%D\bin\psql.exe"
if not defined PSQL (
  echo PostgreSQL est introuvable.
  goto :fin
)
if not defined PGPASSWORD set /p "PGPASSWORD=Mot de passe postgres : "
echo Cles de cartes de Center ...
"%PSQL%" -q -v ON_ERROR_STOP=1 -d "%LTS_CENTER_DB%" -f cles-cartes.sql <nul >nul || goto :erreur
echo Cles du logiciel Station ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer-cles.ps1" -Props "%LTS_PROPS%" -Keys "%~dp0cles-station.properties" || goto :erreur
echo.
echo Termine. Redemarrez l'application LTStation Station pour que les cles soient prises en compte.
goto :fin

:erreur
echo.
echo ERREUR : les cles n'ont pas ete installees. Notez le message ci-dessus.
:fin
echo.
if not defined LTS_NO_PAUSE pause
endlocal
