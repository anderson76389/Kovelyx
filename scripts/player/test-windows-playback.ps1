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
        $second = Start-Process -FilePath (Join-Path $AppRoot 'Kovelyx.exe') -WorkingDirectory $AppRoot -PassThru
        if (-not $second.WaitForExit(10000)) {
            taskkill /PID $second.Id /T /F | Out-Null
            throw 'A second launch left a duplicate application running'
        }
        $process.Refresh()
        if ($process.HasExited) { throw 'The primary application exited when launched a second time' }
        "Primary application working set on CI VM: $([math]::Round($process.WorkingSet64 / 1MB)) MiB; not a physical-device benchmark." | Add-Content $report
        # jpackage can keep a launcher process while a child owns the AWT window.
        # Resolve the actual application HWND instead of Process.MainWindowHandle.
        Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class KovelyxSmokeWindows {
    public delegate bool EnumProc(IntPtr window, IntPtr data);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, IntPtr data);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr window, StringBuilder text, int count);
    [DllImport("user32.dll", SetLastError = true)] public static extern bool PostMessage(IntPtr window, uint message, IntPtr wparam, IntPtr lparam);
    public static IntPtr[] Find(uint[] processes) {
        var ids = new HashSet<uint>(processes);
        var result = new List<IntPtr>();
        EnumWindows((window, data) => {
            uint pid; GetWindowThreadProcessId(window, out pid);
            if (ids.Contains(pid)) {
                var title = new StringBuilder(512);
                GetWindowText(window, title, title.Capacity);
                if (title.ToString() == "Kovelyx Player Smoke") result.Add(window);
            }
            return true;
        }, IntPtr.Zero);
        return result.ToArray();
    }
}
'@
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        $windows = @()
        do {
            $appProcesses = @(Get-CimInstance Win32_Process | Where-Object {
                $_.ExecutablePath -and $_.ExecutablePath.StartsWith($AppRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
            })
            $windows = @([KovelyxSmokeWindows]::Find([uint[]]@($appProcesses.ProcessId)))
            if ($windows.Count -eq 0) { Start-Sleep -Milliseconds 200 }
        } while ($windows.Count -eq 0 -and [DateTime]::UtcNow -lt $deadline)
        if ($windows.Count -ne 1) { throw "Expected one application window after second launch, found $($windows.Count)" }
        # WM_CLOSE takes the same Swing/Compose path as the user's window close button.
        if (-not [KovelyxSmokeWindows]::PostMessage($windows[0], 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)) {
            throw 'Cannot send normal close to application window'
        }
        if (-not $process.WaitForExit(15000)) { throw 'Application process remained alive after normal window close' }
        $remaining = @(Get-Process -Id @($appProcesses.ProcessId) -ErrorAction SilentlyContinue)
        if ($remaining.Count -gt 0) { throw "Application child processes remained after normal close: $($remaining.Id -join ',')" }

        'Second launch forwarded; original process exited after normal window close.' >> $env:GITHUB_STEP_SUMMARY

    } finally {
        $env:NUVIO_DESKTOP_SMOKE_PLAYER_URL = $oldSource
        $env:KOVELYX_PLAYBACK_SMOKE_RESULT = $oldReport
        if ($null -ne $process -and -not $process.HasExited) {
            taskkill /PID $process.Id /T /F | Out-Null
        }
    }
}
