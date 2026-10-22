<#
 Builds Nova Client for many Minecraft versions on YOUR PC (same thing the GitHub release pipeline does).
 Usage (from the NovaClient folder, via build-all-versions.bat):
   build-all-versions.ps1                      -> every version 1.16.5 .. newest that has Yarn mappings
   build-all-versions.ps1 -Only 1.20.1,1.21.1  -> just those
 Output: dist\mods\novaclient-<version>-mc<minecraft>.jar  (+ build-report.txt)
#>
param([string[]]$Only = @(), [string]$Min = '1.16.5')
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'
$root   = Split-Path -Parent $PSScriptRoot
$proj   = Join-Path $root 'NovaClient'
$out    = Join-Path $root 'dist\mods'
$ua = @{ 'User-Agent' = 'NovaClient-local/1.0' }

function Vkey($v) { ($v -split '\.' | ForEach-Object { '{0:D4}' -f [int]$_ }) -join '.' }
function Get-Json($url) { for ($i=0; $i -lt 4; $i++) { try { return Invoke-RestMethod -Uri $url -Headers $ua -TimeoutSec 30 } catch { if ($i -eq 3) { throw }; Start-Sleep -Seconds (2*($i+1)) } } }
function LoomFor($mc) { $k = Vkey $mc; if ($k -ge (Vkey '1.21.11')) { '1.14-SNAPSHOT' } elseif ($k -ge (Vkey '1.21.9')) { '1.11-SNAPSHOT' } elseif ($k -ge (Vkey '1.21.5')) { '1.10-SNAPSHOT' } else { '1.9-SNAPSHOT' } }

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { Write-Host 'Install a JDK 21 first: winget install EclipseAdoptium.Temurin.21.JDK  (then reopen this window)'; exit 1 }

# Gradle is downloaded once per needed version, next to the project (1.21.11 needs Gradle 9 for Loom 1.14)
function Get-Gradle($ver) {
  $home_ = Join-Path $proj ".gradle-dist\gradle-$ver"
  if (-not (Test-Path (Join-Path $home_ 'bin\gradle.bat'))) {
    Write-Host "Downloading Gradle $ver (one time)..."
    $zip = Join-Path $proj 'gradle-dl.zip'
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$ver-bin.zip" -OutFile $zip
    Expand-Archive -Force $zip (Join-Path $proj '.gradle-dist'); Remove-Item $zip
  }
  return (Join-Path $home_ 'bin\gradle.bat')
}
function GradleFor($mc) { if ((Vkey $mc) -ge (Vkey '1.21.11')) { '9.2.1' } else { '8.14.3' } }

# ---- which versions? ----
Write-Host 'Asking Fabric which Minecraft versions exist...'
$games = Get-Json 'https://meta.fabricmc.net/v2/versions/game'
$versions = $games | Where-Object { $_.stable -and $_.version -match '^\d+(\.\d+)+$' -and (Vkey $_.version) -ge (Vkey $Min) } |
            ForEach-Object { $_.version } | Sort-Object { Vkey $_ } -Descending -Unique
# Minecraft 26.x and newer has no Yarn mappings and the code is not ported to it yet, so it is skipped.
if ($Only.Count -gt 0) { $versions = $versions | Where-Object { $Only -contains $_ } }
$mojang = @{}; (Get-Json 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json').versions | ForEach-Object { $mojang[$_.id] = $_.url }
$props = Get-Content (Join-Path $proj 'NovaClient\gradle.properties')
$modVersion = (($props | Where-Object { $_ -match '^mod_version=' }) -replace 'mod_version=','').Trim()

New-Item -ItemType Directory -Force $out | Out-Null
$ok = @(); $fail = @(); $skip = @()
foreach ($mc in $versions) {
  Write-Host ''; Write-Host "===== Minecraft $mc =====" -ForegroundColor Cyan
  try {
    $yarn = (Get-Json "https://meta.fabricmc.net/v2/versions/yarn/$mc" | Sort-Object build -Descending | Select-Object -First 1).version
    $q = 'https://api.modrinth.com/v2/project/fabric-api/version?loaders=' + [uri]::EscapeDataString('["fabric"]') + '&game_versions=' + [uri]::EscapeDataString("[`"$mc`"]")
    $apis = @(Get-Json $q)
    $api = ($apis | Where-Object { $_.version_type -eq 'release' } | Select-Object -First 1).version_number
    if (-not $api -and $apis.Count) { $api = $apis[0].version_number }
    $java = 8; if ($mojang[$mc]) { $j = (Get-Json $mojang[$mc]).javaVersion; if ($j) { $java = [int]$j.majorVersion } }
  } catch { $skip += "$mc (lookup failed: $($_.Exception.Message))"; continue }
  if (-not $yarn -or -not $api) { $skip += "$mc (no Yarn mapping or no Fabric API)"; continue }

  Push-Location (Join-Path $proj 'NovaClient')
  $log = Join-Path $out "build-$mc.log"
  $gradle = Get-Gradle (GradleFor $mc)
  & $gradle build --no-daemon "-Pminecraft_version=$mc" "-Pyarn_mappings=$yarn" "-Pfabric_version=$api" "-Pjava_version=$java" "-Ploom_version=$(LoomFor $mc)" '-Pstrict_mixins=false' *>&1 | Tee-Object -FilePath $log | Out-Host
  $code = $LASTEXITCODE
  Pop-Location
  $jar = Join-Path $proj "NovaClient\build\libs\novaclient-$modVersion.jar"
  if ($code -eq 0 -and (Test-Path $jar)) {
    Copy-Item $jar (Join-Path $out "novaclient-$modVersion-mc$mc.jar") -Force
    Remove-Item $log -ErrorAction SilentlyContinue
    $ok += $mc
  } else { $fail += $mc }
}

$report = @("Nova Client $modVersion", "Built OK : $($ok -join ', ')", "Failed   : $($fail -join ', ')  (see build-<version>.log in this folder)", "Skipped  : $($skip -join '; ')")
$report | Set-Content (Join-Path $out 'build-report.txt')
Write-Host ''; Write-Host '================================================================'
$report | ForEach-Object { Write-Host $_ }
Write-Host "Jars are in: $out"
Write-Host '================================================================'
