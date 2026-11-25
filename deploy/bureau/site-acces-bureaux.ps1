# A lancer UNE FOIS sur le PC de la station (clic droit > Executer avec PowerShell, en administrateur).
# Ouvre la base PostgreSQL de la station aux postes de bureau, UNIQUEMENT a travers le VPN Tailscale (adresses 100.64.0.0/10) :
#   - compte PostgreSQL dedie "ltstation_bureau" (jamais le compte postgres) ;
#   - PostgreSQL ecoute le reseau, mais pg_hba.conf et le pare-feu n'acceptent que le VPN ;
#   - redemarrage du service PostgreSQL (la station ne doit pas vendre pendant ces quelques secondes).
$ErrorActionPreference = 'Stop'
$Role = 'ltstation_bureau'
$Vpn = '100.64.0.0/10'
$Bases = @('center', 'station')

function Fail($t) { Write-Host "ERREUR : $t" -ForegroundColor Red; Read-Host 'Entree pour fermer'; exit 1 }

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Fail 'lancer ce script en administrateur.'
}
$bin = Get-ChildItem "$env:ProgramFiles\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
if (-not $bin) { Fail 'PostgreSQL introuvable sur ce PC.' }
$psql = $bin.FullName
$pgRoot = Split-Path (Split-Path $psql)
$data = Join-Path $pgRoot 'data'
$service = Get-Service 'postgresql*' | Select-Object -First 1
if (-not $service) { Fail 'service PostgreSQL introuvable.' }

$sec = Read-Host 'Mot de passe du compte postgres de ce PC (fichier ACCES.txt du kit)' -AsSecureString
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
& $psql -h localhost -U postgres -d postgres -At -c 'select 1' | Out-Null
if ($LASTEXITCODE -ne 0) { Fail 'mot de passe postgres incorrect.' }

# Mot de passe du compte des bureaux : lettres et chiffres seulement (aucun caractere a echapper)
$alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
$bytes = New-Object byte[] 20
([Security.Cryptography.RNGCryptoServiceProvider]::new()).GetBytes($bytes)
$pw = -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })

Write-Host '== Compte PostgreSQL des bureaux' -ForegroundColor Cyan
$exists = & $psql -h localhost -U postgres -d postgres -At -c "select 1 from pg_roles where rolname = '$Role'"
$verb = if ($exists -eq '1') { 'ALTER' } else { 'CREATE' }
& $psql -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -q -c "$verb ROLE $Role LOGIN PASSWORD '$pw'"
if ($LASTEXITCODE -ne 0) { Fail 'creation du compte impossible.' }
foreach ($db in $Bases) {
    $sql = @"
GRANT CONNECT ON DATABASE $db TO $Role;
GRANT USAGE, CREATE ON SCHEMA public TO $Role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO $Role;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO $Role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $Role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO $Role;
"@
    & $psql -h localhost -U postgres -d $db -v ON_ERROR_STOP=1 -q -c $sql
    if ($LASTEXITCODE -ne 0) { Fail "droits sur la base $db impossibles." }
    Write-Host "  droits donnes sur la base $db"
}

Write-Host '== Acces reseau (VPN seulement)' -ForegroundColor Cyan
& $psql -h localhost -U postgres -d postgres -q -c "ALTER SYSTEM SET listen_addresses = '*'"
$hba = Join-Path $data 'pg_hba.conf'
$line = "host    center,station    $Role    $Vpn    scram-sha-256"
if (-not (Select-String -Path $hba -SimpleMatch $line -Quiet)) {
    Copy-Item $hba "$hba.avant-bureaux" -Force
    Add-Content -Path $hba -Value "`r`n# LTStation : postes de bureau, a travers le VPN Tailscale uniquement`r`n$line" -Encoding ascii
}
if (-not (Get-NetFirewallRule -DisplayName 'LTStation PostgreSQL (VPN)' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName 'LTStation PostgreSQL (VPN)' -Direction Inbound -Protocol TCP -LocalPort 5432 -RemoteAddress $Vpn -Action Allow | Out-Null
}
Write-Host "  redemarrage du service $($service.Name)..."
Restart-Service $service.Name -Force
Remove-Item Env:PGPASSWORD

$ip = ''
$ts = Get-Command tailscale -ErrorAction SilentlyContinue
if (-not $ts -and (Test-Path "$env:ProgramFiles\Tailscale\tailscale.exe")) { $ts = "$env:ProgramFiles\Tailscale\tailscale.exe" }
if ($ts) { $ip = (& $ts ip -4 2>$null | Select-Object -First 1) }

$out = Join-Path ([Environment]::GetFolderPath('Desktop')) 'ACCES-BUREAUX.txt'
@"
ACCES DES POSTES DE BUREAU - CONFIDENTIEL
=========================================
A saisir pendant l'installation de chaque poste de bureau (INSTALLER-BUREAU.bat).

Adresse de la station (VPN)  : $(if ($ip) { $ip } else { "(installer Tailscale sur ce PC, puis : tailscale ip -4)" })
Utilisateur PostgreSQL       : $Role
Mot de passe                 : $pw

Ne pas envoyer par e-mail ni messagerie. Relancer ce script pour changer le mot de passe.
"@ | Set-Content -Path $out -Encoding UTF8
Write-Host ''
Write-Host "Termine. Les acces sont dans $out" -ForegroundColor Green
Read-Host 'Entree pour fermer'
