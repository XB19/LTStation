# Remplace ou ajoute les clés de cartes dans les réglages du logiciel Station, sans toucher aux autres lignes.
param([Parameter(Mandatory)] [string] $Props, [Parameter(Mandatory)] [string] $Keys)
$ErrorActionPreference = 'Stop'
$keyLines = Get-Content -LiteralPath $Keys -Encoding UTF8 | Where-Object { $_ -match '^\s*[A-Za-z]+\s*=' }
if (-not (Test-Path -LiteralPath $Props)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -LiteralPath $Props) | Out-Null
    $lines = @()
} else {
    Copy-Item -LiteralPath $Props -Destination ($Props + '.avant-cles') -Force
    $lines = @(Get-Content -LiteralPath $Props -Encoding UTF8)
}
foreach ($kl in $keyLines) {
    $name = ($kl -split '=', 2)[0].Trim()
    $i = [Array]::FindIndex([string[]]$lines, [Predicate[string]] { param($l) $l -match ('^\s*' + [regex]::Escape($name) + '\s*[=:]') })
    if ($i -ge 0) { $lines[$i] = $kl } else { $lines += $kl }
}
[IO.File]::WriteAllLines($Props, [string[]]$lines, (New-Object Text.UTF8Encoding $false))
Write-Output ("{0} cles installees dans {1}" -f $keyLines.Count, $Props)
