param([Parameter(Mandatory=$true)][string]$AppRoot, [switch]$IncludeApplication)
$ErrorActionPreference = 'Stop'
$AppRoot = (Resolve-Path -LiteralPath $AppRoot).Path
$work = Join-Path $env:RUNNER_TEMP 'kovelyx-native-playback'
New-Item -ItemType Directory -Force $work | Out-Null
$fixture = Join-Path $work 'video.mp4'
$encoded = Get-Content 'apps/tv/app/src/androidTest/assets/media3-smoke.mp4.b64' -Raw
[IO.File]::WriteAllBytes($fixture, [Convert]::FromBase64String($encoded))
if ((Get-FileHash $fixture -Algorithm SHA256).Hash.ToLowerInvariant() -ne '566c872a74649ee8c04e9c43dccb4d17ec40ebdb4c365acd1bf5387ce3416cef') { throw 'MP4 fixture checksum mismatch' }
& javac -d $work scripts/player/WindowsNativePlaybackSmoke.java
if ($LASTEXITCODE -ne 0) { throw 'Playback harness compilation failed' }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
# jpackage deliberately strips command-line launchers from its private runtime.
# Use the CI JDK to exercise the shipped JARs and native DLLs, without substituting libraries.
if (-not (Test-Path $java)) { throw 'CI Java launcher missing' }
$classpath = "$work;$(Join-Path $AppRoot 'app/*')"
& $java '--add-opens=java.desktop/java.awt=ALL-UNNAMED' '--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED' '-cp' $classpath WindowsNativePlaybackSmoke ([Uri]$fixture).AbsoluteUri (Join-Path $work 'playback.png') 2>&1 | Tee-Object -FilePath (Join-Path $work 'playback.log')
if ($LASTEXITCODE -ne 0) { throw 'Packaged video playback failed; inspect playback.log' }
'Native MP4 playback and time progression passed using packaged libraries.' >> $env:GITHUB_STEP_SUMMARY
if ($IncludeApplication) {
    $report = Join-Path $work 'application-playback.txt'
    Remove-Item $report -ErrorAction SilentlyContinue
    $oldSource = $env:NUVIO_DESKTOP_SMOKE_PLAYER_URL
    $oldReport = $env:KOVELYX_PLAYBACK_SMOKE_RESULT
    $process = $null
    try {
        $env:NUVIO_DESKTOP_SMOKE_PLAYER_URL = ([Uri]$fixture).AbsoluteUri
        $env:KOVELYX_PLAYBACK_SMOKE_RESULT = $report
        $process = Start-Process -FilePath (Join-Path $AppRoot 'Kovelyx.exe') -WorkingDirectory $AppRoot -PassThru
        $deadline = [DateTime]::UtcNow.AddSeconds(60)
        while ([DateTime]::UtcNow -lt $deadline -and -not (Test-Path $report)) {
            $process.Refresh()
            if ($process.HasExited) { throw "Application exited during video startup: $($process.ExitCode)" }
            Start-Sleep -Milliseconds 200
        }
        if (-not (Test-Path $report)) { throw 'Application never reported video progression within 60 seconds' }
        $result = Get-Content $report -Raw
        Write-Host $result
        if (-not $result.StartsWith('PASS ')) { throw "Application player failed: $result" }
        'Packaged Kovelyx.exe, private Java runtime and real player surface: video progression passed.' >> $env:GITHUB_STEP_SUMMARY
    } finally {
        $env:NUVIO_DESKTOP_SMOKE_PLAYER_URL = $oldSource
        $env:KOVELYX_PLAYBACK_SMOKE_RESULT = $oldReport
        if ($null -ne $process -and -not $process.HasExited) {
            taskkill /PID $process.Id /T /F | Out-Null
        }
    }
}
