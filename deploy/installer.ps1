# Installation complète de LTStation sur le poste de la station, sans question :
# PostgreSQL, bases Center et Station, licence, clés de cartes, application, réglages.
# Lancé par INSTALLER.bat (droits administrateur). Les mots de passe viennent de config.ps1, créé avec le kit.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $here 'config.ps1')
# Réglages d'essai facultatifs (le kit normal ne les utilise pas)
$CenterDb = if ($env:LTS_CENTER_DB) { $env:LTS_CENTER_DB } else { 'center' }
$StationDb = if ($env:LTS_STATION_DB) { $env:LTS_STATION_DB } else { 'station' }
if ($env:LTS_PG_PASSWORD) { $PgPassword = $env:LTS_PG_PASSWORD }
$DataDir = if ($env:LTS_DATA_DIR) { $env:LTS_DATA_DIR } else { Join-Path $env:APPDATA 'LTStation Station\data' }
$log = Join-Path $here '..\installation.log'
Start-Transcript -Path $log -Append | Out-Null

function Step($text) { Write-Host ''; Write-Host "== $text" -ForegroundColor Cyan }
function Fail($text) {
    Write-Host ''
    Write-Host "ERREUR : $text" -ForegroundColor Red
    Write-Host "Rien n'a ete efface. Envoyez le fichier installation.log au fournisseur."
    Stop-Transcript | Out-Null
    exit 1
}

function Find-Psql {
    Get-ChildItem "$env:ProgramFiles\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName | Select-Object -Last 1 -ExpandProperty FullName
}

function Psql([string]$db, [string[]]$args2) {
    $env:PGPASSWORD = $script:PgPassword
    $out = & $script:psql -h localhost -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 -q -At @args2 2>&1
    $code = $LASTEXITCODE
    Remove-Item Env:PGPASSWORD
    return @{ Code = $code; Out = ($out | Out-String) }
}

# ---------------------------------------------------------------- 1. PostgreSQL
Step 'PostgreSQL'
$psql = Find-Psql
if (-not $psql) {
    Write-Host 'Installation de PostgreSQL (quelques minutes, ne pas fermer cette fenetre)...'
    $setup = Get-ChildItem (Join-Path $here 'postgresql-*-windows-x64.exe') | Select-Object -First 1
    $p = Start-Process -FilePath $setup.FullName -Wait -PassThru -ArgumentList @(
        '--mode', 'unattended', '--unattendedmodeui', 'minimal', '--superpassword', $PgPassword,
        '--serverport', '5432', '--enable-components', 'server,commandlinetools', '--disable-components', 'pgAdmin,stackbuilder')
    if ($p.ExitCode -ne 0) { Fail "l'installation de PostgreSQL a echoue (code $($p.ExitCode))." }
    $psql = Find-Psql
    if (-not $psql) { Fail 'PostgreSQL est installe mais psql est introuvable.' }
} else {
    Write-Host "PostgreSQL deja present : $psql"
}
$script:psql = $psql
$script:PgPassword = $PgPassword
if ((Psql 'postgres' @('-c', 'select 1')).Code -ne 0) {
    Write-Host 'Le mot de passe du kit ne convient pas a ce PostgreSQL deja installe.'
    $sec = Read-Host 'Mot de passe de l utilisateur postgres de ce poste' -AsSecureString
    $script:PgPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    if ((Psql 'postgres' @('-c', 'select 1')).Code -ne 0) { Fail 'connexion a PostgreSQL impossible avec ce mot de passe.' }
}

# ---------------------------------------------------------------- 2. Bases de données
Step 'Bases de donnees'
foreach ($db in @($CenterDb, $StationDb)) {
    $exists = (Psql 'postgres' @('-c', "select 1 from pg_database where datname = '$db'")).Out.Trim()
    if ($exists -eq '1') { Fail "la base $db existe deja sur ce poste : elle n'a pas ete modifiee." }
}
foreach ($db in @($CenterDb, $StationDb)) {
    $r = Psql 'postgres' @('-c', "CREATE DATABASE $db ENCODING 'UTF8' TEMPLATE template0")
    if ($r.Code -ne 0) { Fail "creation de la base $db : $($r.Out)" }
}
foreach ($f in @(@($CenterDb, 'center.sql'), @($StationDb, 'station.sql'), @($CenterDb, 'licence.sql'), @($CenterDb, 'cles-cartes.sql'))) {
    Write-Host "  $($f[1])"
    $r = Psql $f[0] @('-f', (Join-Path $here $f[1]))
    if ($r.Code -ne 0) { Fail "chargement de $($f[1]) : $($r.Out)" }
}

# ---------------------------------------------------------------- 3. Application
Step 'Application LTStation Station'
if ($env:LTS_SKIP_APP) {
    Write-Host '(essai : application non installee)'
} else {
    $app = Get-ChildItem (Join-Path $here 'LTStation Station Setup*.exe') | Select-Object -First 1
    $p = Start-Process -FilePath $app.FullName -ArgumentList '/S' -Wait -PassThru
    if ($p.ExitCode -ne 0) { Fail "l'installation de l'application a echoue (code $($p.ExitCode))." }
}

# ---------------------------------------------------------------- 4. Réglages de l'application
Step 'Reglages'
$data = $DataDir
New-Item -ItemType Directory -Force -Path (Join-Path $data 'station') | Out-Null
$utf8 = New-Object Text.UTF8Encoding $false
$envLines = @(
    '# Base de donnees du serveur LTStation integre (ce fichier reste sur ce poste)',
    'DB_HOST=localhost', 'DB_PORT=5432', 'DB_USER=postgres', "DB_PASSWORD=$script:PgPassword",
    "DB_NAME=$CenterDb", "STATION_DB_NAME=$StationDb", "SUPERADMIN_USER=$AdminUser", "SUPERADMIN_PASSWORD=$AdminPassword")
[IO.File]::WriteAllLines((Join-Path $data '.env'), [string[]]$envLines, $utf8)

$props = Join-Path $data 'station\StationSoftware.properties'
$lines = New-Object 'System.Collections.Generic.List[string]'
if (Test-Path $props) { foreach ($l in (Get-Content -LiteralPath $props -Encoding UTF8)) { $lines.Add($l) } }
$add = @(Get-Content -LiteralPath (Join-Path $here 'cles-station.properties') -Encoding UTF8 | Where-Object { $_ -match '^\s*[A-Za-z]+\s*=' })
$add += 'AutoRegister=1'
foreach ($kl in $add) {
    $name = ($kl -split '=', 2)[0].Trim()
    $i = -1
    for ($j = 0; $j -lt $lines.Count; $j++) { if ($lines[$j] -match ('^\s*' + [regex]::Escape($name) + '\s*[=:]')) { $i = $j; break } }
    if ($i -ge 0) { $lines[$i] = $kl } else { $lines.Add($kl) }
}
[IO.File]::WriteAllLines($props, $lines.ToArray(), $utf8)

Step 'Termine'
Write-Host 'LTStation est installe. Les identifiants sont dans le fichier ACCES.txt du kit.' -ForegroundColor Green
Write-Host 'Lancez l application avec l icone LTStation Station du bureau.'
Stop-Transcript | Out-Null
exit 0
