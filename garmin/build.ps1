<#
.SYNOPSIS
  Build, unit-test or run the RefWatch Connect IQ app.
.EXAMPLE
  .\garmin\build.ps1                       # build for fenix5x
  .\garmin\build.ps1 -Device fenix7 -Run   # build and open in the simulator
  .\garmin\build.ps1 -Test                 # build with unit tests and run them in the simulator
  .\garmin\build.ps1 -Release              # release build (no (:debug) code), as for the store
#>
param(
    [string]$Device = "fenix5x",
    [switch]$Test,
    [switch]$Run,
    [switch]$Release,
    [string]$Key = "$env:USERPROFILE\keys_for_garmin\developer_key.der"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$sdkRoot = "$env:APPDATA\Garmin\ConnectIQ\Sdks"
$sdk = Get-ChildItem $sdkRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if (-not $sdk) { throw "No Connect IQ SDK in $sdkRoot. Install one with the SDK Manager (see garmin/README.md)." }
$bin = Join-Path $sdk.FullName "bin"
if (-not (Test-Path $Key)) { throw "Developer key not found at $Key (see garmin/README.md)." }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr" }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

New-Item -ItemType Directory -Force "$root\bin" | Out-Null
$out = "$root\bin\RefWatch-$Device.prg"
$compileArgs = @("-o", $out, "-f", "$root\monkey.jungle", "-y", $Key, "-d", $Device, "-w")
if ($Test) { $compileArgs += "-t" }
# A release build drops (:debug) code, which is where the test fixtures live.
if ($Release) { $compileArgs += "-r" }
# Show monkeyc's stdout and stderr (compiler diagnostics go to stderr). Under
# $ErrorActionPreference = "Stop", Windows PowerShell turns redirected stderr into a
# terminating error, so relax it for this one call and rely on the exit code.
$ErrorActionPreference = "Continue"
& "$bin\monkeyc.bat" @compileArgs 2>&1 | ForEach-Object { Write-Host "$_" }
$compileExit = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($compileExit -ne 0) { throw "monkeyc failed (exit $compileExit); see the compiler output above" }

if ($Test -or $Run) {
    if (-not (Get-Process simulator -ErrorAction SilentlyContinue)) {
        # Start it parked off-screen and inactive: the simulator must never take focus from the user.
        & "$root\tools\sim.ps1" -Start
        Start-Sleep -Seconds 6
    }
    $runArgs = @($out, $Device)
    # monkeydo.bat on Windows takes /t, not -t.
    if ($Test) { $runArgs += "/t" }
    # Same as monkeyc: relax Stop so stderr from monkeydo is not a terminating error.
    $ErrorActionPreference = "Continue"
    $output = & "$bin\monkeydo.bat" @runArgs 2>&1 | Out-String
    $runExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    Write-Output $output
    if ($runExit -ne 0 -and -not $Test) { throw "monkeydo failed (exit $runExit)" }
    # Case-sensitive: the summary line is "PASSED (passed=N, failed=0, errors=0)".
    if ($Test -and (($output -cmatch "FAILED|ERROR") -or ($output -cnotmatch "PASSED"))) {
        throw "Unit tests failed"
    }
}
