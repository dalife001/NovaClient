# Makes sure the launcher uses the mod built into it (clears any old "Local Nova jar" override).
$dir = Join-Path $env:APPDATA '.novaclient'
$p = Join-Path $dir 'launcher.json'
if (Test-Path $p) {
  try {
    $h = [ordered]@{}
    (Get-Content $p -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $h[$_.Name] = $_.Value }
    if ($h['localModJar']) { $h['localModJar'] = ''; [IO.File]::WriteAllText($p, ($h | ConvertTo-Json)); Write-Host 'Cleared the old Local Nova jar setting.' }
  } catch {}
}
