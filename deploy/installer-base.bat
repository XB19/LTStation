@echo off
rem Crée les bases Center et Station sur ce poste (PostgreSQL doit être installé), puis prépare la connexion
rem de l'application LTStation Station. A lancer une seule fois, avant le premier démarrage de l'application.
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

if not defined LTS_CENTER_DB set "LTS_CENTER_DB=center"
if not defined LTS_STATION_DB set "LTS_STATION_DB=station"
if not defined LTS_ENV_FILE set "LTS_ENV_FILE=%APPDATA%\LTStation Station\data\.env"
if not defined PGHOST set "PGHOST=localhost"
if not defined PGPORT set "PGPORT=5432"
if not defined PGUSER set "PGUSER=postgres"

set "PSQL="
for /d %%D in ("%ProgramFiles%\PostgreSQL\*") do if exist "%%D\bin\psql.exe" set "PSQL=%%D\bin\psql.exe"
if not defined PSQL (
  echo PostgreSQL est introuvable. Installez-le d'abord ^(voir LISEZ-MOI.txt, etape 1^).
  goto :fin
)
echo PostgreSQL trouve : %PSQL%
echo.
echo Saisissez le mot de passe de l'utilisateur postgres choisi a l'installation de PostgreSQL.
echo Evitez les caracteres  ^& ^| ^< ^> ^^ %%  dans les mots de passe utilises ici.
if not defined PGPASSWORD set /p "PGPASSWORD=Mot de passe postgres : "
"%PSQL%" -q -d postgres -c "select 1" <nul >nul 2>&1
if errorlevel 1 (
  echo Connexion a PostgreSQL impossible : mot de passe incorrect ou service arrete.
  goto :fin
)

for %%B in ("%LTS_CENTER_DB%" "%LTS_STATION_DB%") do (
  "%PSQL%" -q -At -d postgres -c "select 1 from pg_database where datname = '%%~B'" <nul | findstr 1 >nul
  if not errorlevel 1 (
    echo La base %%~B existe deja : rien n'a ete modifie. Supprimez-la seulement si elle ne contient rien d'utile.
    goto :fin
  )
)

echo.
echo Compte de depart de Center ^(acces complet, sert a creer la station et les employes^).
if not defined ADMINPW set /p "ADMINPW=Choisissez son mot de passe : "
if "%ADMINPW%"=="" (
  echo Mot de passe vide refuse.
  goto :fin
)
echo.
echo Creation de la base %LTS_CENTER_DB% ...
"%PSQL%" -q -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE \"%LTS_CENTER_DB%\" ENCODING 'UTF8' TEMPLATE template0" <nul || goto :erreur
"%PSQL%" -q -v ON_ERROR_STOP=1 -d "%LTS_CENTER_DB%" -f center.sql >nul <nul || goto :erreur
echo Creation de la base %LTS_STATION_DB% ...
"%PSQL%" -q -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE \"%LTS_STATION_DB%\" ENCODING 'UTF8' TEMPLATE template0" <nul || goto :erreur
"%PSQL%" -q -v ON_ERROR_STOP=1 -d "%LTS_STATION_DB%" -f station.sql >nul <nul || goto :erreur


for %%F in ("%LTS_ENV_FILE%") do if not exist "%%~dpF" mkdir "%%~dpF"
> "%LTS_ENV_FILE%" echo # Base de donnees du serveur LTStation integre ^(ce fichier reste sur ce poste^)
>>"%LTS_ENV_FILE%" echo DB_HOST=%PGHOST%
>>"%LTS_ENV_FILE%" echo DB_PORT=%PGPORT%
>>"%LTS_ENV_FILE%" echo DB_USER=%PGUSER%
>>"%LTS_ENV_FILE%" echo DB_PASSWORD=%PGPASSWORD%
>>"%LTS_ENV_FILE%" echo DB_NAME=%LTS_CENTER_DB%
>>"%LTS_ENV_FILE%" echo STATION_DB_NAME=%LTS_STATION_DB%
>>"%LTS_ENV_FILE%" echo SUPERADMIN_USER=admin
>>"%LTS_ENV_FILE%" echo SUPERADMIN_PASSWORD=%ADMINPW%

echo.
echo Termine. Bases creees et application configuree.
echo Compte Center : admin, avec le mot de passe que vous venez de choisir.
echo Etape suivante : LISEZ-MOI.txt, etape 4.
goto :fin

:erreur
echo.
echo ERREUR : l'installation de la base s'est arretee. Notez le message ci-dessus.
:fin
echo.
if not defined LTS_NO_PAUSE pause
endlocal
