<#
Builds every supported Minecraft version using the same version matrix as .github/workflows/release.yml.
Usage: build-all-versions.ps1 [-Only 1.20.1,1.21.4]
Output: dist\mods\novaclient-<version>-mc<minecraft>.jar and build-report.txt
#>
param([string[]]$Only = @(), [string]$Min = '1.16.5')
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$proj = Join-Path $root 'NovaClient'
$proj26 = Join-Path $root 'NovaClient26'
$out = Join-Path $root 'dist\mods'
New-Item -ItemType Directory -Force $out | Out-Null

function Invoke-Gradle([string]$exe, [string[]]$arguments, [string]$log) {
  $stdout = "$log.stdout"
  $stderr = "$log.stderr"
  $process = Start-Process -FilePath $exe -ArgumentList $arguments -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr
  $lines = @()
  if (Test-Path $stdout) { $lines += Get-Content $stdout }
  if (Test-Path $stderr) { $lines += Get-Content $stderr }
  $lines | Set-Content $log
  if (Test-Path $stdout) { Remove-Item $stdout }
  if (Test-Path $stderr) { Remove-Item $stderr }
  Get-Content $log | ForEach-Object { Write-Host $_ }
  return $process.ExitCode
}

function Get-Gradle([string]$version, [string]$projectRoot) {
  $candidates = @(
    (Join-Path $projectRoot ".gradle-dist\gradle-$version"),
    (Join-Path $proj ".gradle-dist\gradle-$version"),
    (Join-Path (Join-Path $proj26 'NovaClient') ".gradle-dist\gradle-$version")
  )
  $gradleHome = $candidates | Where-Object { Test-Path (Join-Path $_ 'bin\gradle.bat') } | Select-Object -First 1
  if (-not $gradleHome) { $gradleHome = Join-Path $projectRoot ".gradle-dist\gradle-$version" }
  $exe = Join-Path $gradleHome 'bin\gradle.bat'
  if (-not (Test-Path $exe)) {
    New-Item -ItemType Directory -Force (Join-Path $projectRoot '.gradle-dist') | Out-Null
    $zip = Join-Path $projectRoot "gradle-$version-bin.zip"
    Write-Host "Downloading Gradle $version for $projectRoot..."
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zip
    Expand-Archive -Force $zip (Join-Path $projectRoot '.gradle-dist')
    Remove-Item $zip
  }
  return $exe
}

$tested = @()
$testedFile = Join-Path $root 'tools\tested-versions.txt'
if (Test-Path $testedFile) {
  $tested = @(Get-Content $testedFile | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') } | ForEach-Object { $_.Trim() })
}
$versionProps = Get-Content (Join-Path $proj 'gradle.properties')
$modVersion = (($versionProps | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', '').Trim()

# Discover precisely the same Yarn and Mojang-mapped build matrices as the release workflow.
$matrixOutput = & python (Join-Path $root 'tools\ci\discover.py')
if ($LASTEXITCODE -ne 0) { throw 'CI version discovery failed; no version builds were started.' }
$matrixLine = $matrixOutput | Where-Object { $_ -like 'matrix=*' } | Select-Object -First 1
$matrix26Line = $matrixOutput | Where-Object { $_ -like 'matrix26=*' } | Select-Object -First 1
if (-not $matrixLine) { throw 'CI version discovery produced no Yarn matrix.' }
$matrix = (($matrixLine -replace '^matrix=', '') | ConvertFrom-Json).include
$matrix26 = if ($matrix26Line) { (($matrix26Line -replace '^matrix26=', '') | ConvertFrom-Json).include } else { @() }
if ($Only.Count -gt 0) {
  $matrix = @($matrix | Where-Object { $Only -contains $_.mc })
  $matrix26 = @($matrix26 | Where-Object { $Only -contains $_.mc })
}
if ($Min -ne '1.16.5') {
  function VersionKey([string]$value) { (@($value -split '\.' | ForEach-Object { [int]$_ }) + @(0,0,0)) -join '.' }
  $matrix = @($matrix | Where-Object { (VersionKey $_.mc) -ge (VersionKey $Min) })
  $matrix26 = @($matrix26 | Where-Object { (VersionKey $_.mc) -ge (VersionKey $Min) })
}
Write-Host "CI matrix selected: $($matrix.Count) Yarn versions and $($matrix26.Count) Minecraft 26.x versions."

$ok = [System.Collections.Generic.List[string]]::new()
$fail = [System.Collections.Generic.List[string]]::new()
$skip = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $matrix) {
  $mc = [string]$entry.mc
  $jdkHome = 'C:\Program Files\Java\jdk-21.0.12.1'
  if (-not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) { throw 'JDK 21 is required for the release workflow Yarn matrix.' }
  $env:JAVA_HOME = $jdkHome
  $env:PATH = "$jdkHome\bin;$env:PATH"
  Write-Host "`n===== Minecraft $mc (Yarn, Java $($entry.java), JDK 21) =====" -ForegroundColor Cyan
  $gradle = Get-Gradle ([string]$entry.gradle) $proj
  $strict = if ($entry.tested) { 'true' } else { 'false' }
  $log = Join-Path $out "build-$mc.log"
  Push-Location $proj
  try {
    $arguments = @('build', '--no-daemon', '--stacktrace', "-Pmod_version=$modVersion", "-Pminecraft_version=$mc", "-Pyarn_mappings=$($entry.yarn)", "-Pfabric_version=$($entry.fabric_api)", "-Pjava_version=$($entry.java)", "-Ploom_version=$($entry.loom)", "-Pstrict_mixins=$strict")
    $code = Invoke-Gradle $gradle $arguments $log
  } finally { Pop-Location }
  $jar = Join-Path $proj "build\libs\novaclient-$modVersion.jar"
  if ($code -eq 0 -and (Test-Path $jar)) {
    Copy-Item $jar (Join-Path $out "novaclient-$modVersion-mc$mc.jar") -Force
    Remove-Item $log -ErrorAction SilentlyContinue
    $ok.Add($mc)
  } else { $fail.Add($mc) }
}

foreach ($entry in $matrix26) {
  $mc = [string]$entry.mc
  $jdkHome = 'C:\Program Files\Java\jdk-25.0.5.0.3'
  if (-not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) { throw 'JDK 25 is required for the release workflow Minecraft 26.x matrix.' }
  $env:JAVA_HOME = $jdkHome
  $env:PATH = "$jdkHome\bin;$env:PATH"
  Write-Host "`n===== Minecraft $mc (Mojang mappings, Java 25, JDK 25) =====" -ForegroundColor Cyan
  $gradleVersion = if (([string]$entry.loom) -eq '1.17-SNAPSHOT') { '9.5.0' } else { '9.4.0' }
  $gradle = Get-Gradle $gradleVersion $proj26
  $strict = if ($entry.tested) { 'true' } else { 'false' }
  $log = Join-Path $out "build-$mc.log"
  Push-Location $proj26
  try {
    $arguments = @('build', '--no-daemon', '--stacktrace', "-Pmod_version=$modVersion", "-Pminecraft_version=$mc", "-Ploader_version=$($entry.loader)", "-Pfabric_version=$($entry.fabric_api)", '-Pjava_version=25', "-Ploom_version=$($entry.loom)", "-Pstrict_mixins=$strict")
    $code = Invoke-Gradle $gradle $arguments $log
  } finally { Pop-Location }
  $jar = Join-Path $proj26 "build\libs\novaclient-$modVersion.jar"
  if ($code -eq 0 -and (Test-Path $jar)) {
    Copy-Item $jar (Join-Path $out "novaclient-$modVersion-mc$mc.jar") -Force
    Remove-Item $log -ErrorAction SilentlyContinue
    $ok.Add($mc)
  } else { $fail.Add($mc) }
}

$report = @(
  "Nova Client $modVersion",
  "CI versions selected: $($matrix.Count + $matrix26.Count)",
  "Built OK: $($ok -join ', ')",
  "Failed: $($fail -join ', ') (see build-<version>.log in dist\mods)",
  "Skipped during CI discovery: $($skip -join '; ')"
)
$report | Set-Content (Join-Path $out 'build-report.txt')
Write-Host "`n================================================================"
$report | ForEach-Object { Write-Host $_ }
Write-Host "Artifacts: $out"
Write-Host '================================================================'
if ($fail.Count -gt 0 -or ($ok.Count -eq 0 -and $matrix.Count + $matrix26.Count -gt 0)) { exit 1 }
