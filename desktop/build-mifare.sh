#!/usr/bin/env bash
# Prépare build/mifare : passerelle 32 bits et mwrf32.dll (copiée depuis le dossier CardMake d'origine, MWRF32_DLL pour un autre chemin).
cd "$(dirname "$0")" || exit 1
DLL="${MWRF32_DLL:-../../LTStation/CardMake/mwrf32.dll}"
mkdir -p build/mifare
rm -f build/mifare/mwrf32.dll build/mifare/mwrf-helper.exe
cp "$DLL" build/mifare/mwrf32.dll || exit 1
chmod u+w build/mifare/mwrf32.dll
cd mifare || exit 1
MSYS_NO_PATHCONV=1 "$WINDIR/Microsoft.NET/Framework/v4.0.30319/csc.exe" /nologo /platform:x86 /target:exe /out:mwrf-helper.exe MifareHelper.cs || exit 1
mv mwrf-helper.exe ../build/mifare/
