# Installation d'un POSTE DE BUREAU LTStation (lance par INSTALLER-BUREAU.bat, en administrateur).
# Le poste travaille sur la base de la station a travers le VPN, avec son lecteur de cartes, sans aucun equipement.
# Aucun PostgreSQL n'est installe ici. Les acces viennent du fichier ACCES-BUREAUX.txt cree sur le PC de la station.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$DataDir = Join-Path $env:APPDATA 'LTStation Station\data'
$log = Join-Path $here '..\installation-bureau.log'
Start-Transcript -Path $log -Append | Out-Null

function Step($t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }
function Fail($t) {
    Write-Host ''; Write-Host "ERREUR : $t" -ForegroundColor Red
    Write-Host 'Rien n''a ete modifie sur la station. Envoyez le fichier installation-bureau.log au fournisseur.'
    Stop-Transcript | Out-Null; exit 1
}

# ---------------------------------------------------------------- 1. Acces a la station
Step 'Acces a la station (fichier ACCES-BUREAUX.txt)'
$site = (Read-Host 'Adresse de la station (VPN, ex. 100.101.102.103)').Trim()
if (-not $site) { Fail 'adresse de la station vide.' }
# Le mot de passe cree par site-acces-bureaux.ps1 fait 20 caracteres. Ctrl+V ne colle pas toujours dans cette fenetre :
# coller avec un CLIC DROIT. Un mot de passe trop court (collage rate) est redemande.
$pw = ''
for ($try = 1; $try -le 3 -and $pw.Length -lt 20; $try++) {
    Write-Host 'Pour coller le mot de passe : CLIC DROIT dans cette fenetre (pas Ctrl+V). Il fait 20 caracteres.'
    $sec = Read-Host 'Mot de passe du compte ltstation_bureau' -AsSecureString
    $pw = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)).Trim()
    if ($pw.Length -lt 20) { Write-Host "Mot de passe incomplet ($($pw.Length) caracteres au lieu de 20) : recommencez." -ForegroundColor Yellow }
}
if ($pw.Length -lt 20) { Fail 'mot de passe incomplet : reprendre ACCES-BUREAUX.txt.' }
Write-Host "Test de la connexion a $site (port 5432)..."
if (-not (Test-NetConnection -ComputerName $site -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue)) {
    Fail "la station $site ne repond pas : verifier que Tailscale est connecte sur ce PC et sur le PC de la station."
}
Write-Host 'Station joignable.'

# ---------------------------------------------------------------- 2. Application
Step 'Application LTStation Station'
$app = Get-ChildItem (Join-Path $here 'LTStation Station Setup*.exe') | Sort-Object Name | Select-Object -Last 1
if (-not $app) { Fail 'installateur de l''application absent du dossier fichiers.' }
$p = Start-Process -FilePath $app.FullName -ArgumentList '/S' -Wait -PassThru
if ($p.ExitCode -ne 0) { Fail "l'installation de l'application a echoue (code $($p.ExitCode))." }

# ---------------------------------------------------------------- 3. Reglages du poste
Step 'Reglages du poste de bureau'
New-Item -ItemType Directory -Force -Path (Join-Path $DataDir 'station') | Out-Null
$utf8 = New-Object Text.UTF8Encoding $false
[IO.File]::WriteAllLines((Join-Path $DataDir '.env'), [string[]]@(
    '# Poste de bureau : base de la station par le VPN (ce fichier reste sur ce poste)',
    "DB_HOST=$site", 'DB_PORT=5432', 'DB_USER=ltstation_bureau', "DB_PASSWORD=$pw",
    'DB_NAME=center', 'STATION_DB_NAME=station', 'POSTE_BUREAU=1'), $utf8)

# Cles de cartes de la station (indispensables pour lire et ecrire les cartes) + enregistrement automatique du poste
$props = Join-Path $DataDir 'station\StationSoftware.properties'
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
Write-Host 'Poste de bureau installe. Lancez LTStation Station avec l''icone du bureau' -ForegroundColor Green
Write-Host 'et connectez-vous avec votre identifiant d''employe (profil Operateur de saisie, Consultation ou Administrateur).'
Stop-Transcript | Out-Null
exit 0
