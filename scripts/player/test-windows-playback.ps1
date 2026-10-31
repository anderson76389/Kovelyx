param([Parameter(Mandatory=$true)][string]$AppRoot)
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
$java = Join-Path $AppRoot 'runtime/bin/java.exe'
if (-not (Test-Path $java)) { throw 'Packaged Java launcher missing' }
$classpath = "$work;$(Join-Path $AppRoot 'app/*')"
& $java '--add-opens=java.desktop/java.awt=ALL-UNNAMED' '--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED' '-cp' $classpath WindowsNativePlaybackSmoke ([Uri]$fixture).AbsoluteUri (Join-Path $work 'playback.png') 2>&1 | Tee-Object -FilePath (Join-Path $work 'playback.log')
if ($LASTEXITCODE -ne 0) { throw 'Packaged video playback failed; inspect playback.log' }
'Native MP4 playback and time progression passed using packaged libraries.' >> $env:GITHUB_STEP_SUMMARY
