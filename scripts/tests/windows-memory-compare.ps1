param(
    [Parameter(Mandatory=$true)][string]$BaselineRoot,
    [Parameter(Mandatory=$true)][string]$CandidateRoot,
    [string]$OutputPath = (Join-Path $env:RUNNER_TEMP 'kovelyx-memory-comparison.json')
)
$ErrorActionPreference = 'Stop'
# This measures only idle startup working set on a clean GitHub runner.
# It cannot predict poster-heavy use or prolonged native-video playback.
function Measure-Kovelyx([string]$Root, [string]$Label) {
    $rootPath = [System.IO.Path]::GetFullPath($Root).TrimEnd([char]92, [char]47)
    $exe = Join-Path $rootPath 'Kovelyx.exe'
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw "$Label Kovelyx.exe absent: $exe" }

    $oldAppData = $env:APPDATA
    $oldLocalAppData = $env:LOCALAPPDATA
    $sandbox = Join-Path $env:RUNNER_TEMP "kovelyx-memory-$Label"
    $env:APPDATA = Join-Path $sandbox 'Roaming'
    $env:LOCALAPPDATA = Join-Path $sandbox 'Local'
    New-Item -ItemType Directory -Force -Path $env:APPDATA, $env:LOCALAPPDATA | Out-Null
    try {
        $launcher = Start-Process -FilePath $exe -WorkingDirectory $rootPath -PassThru
        Start-Sleep -Seconds 22
        $samples = @()
        for ($tick = 0; $tick -lt 5; $tick++) {
            $procs = @(Get-CimInstance Win32_Process | Where-Object {
                $_.ExecutablePath -and $_.ExecutablePath.StartsWith($rootPath + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
            })
            if ($procs.Count -lt 1) {
                throw "$Label: Kovelyx n'est plus actif au cours du test."
            }
            $ws = 0L
            $private = 0L
            foreach ($item in $procs) {
                $p = Get-Process -Id $item.ProcessId -ErrorAction SilentlyContinue
                if ($p) {
                    $ws += [long]$p.WorkingSet64
                    $private += [long]$p.PrivateMemorySize64
                }
            }
            $samples += [pscustomobject]@{
                workingSetMiB = [math]::Round($ws / 1MB, 2)
                privateMiB = [math]::Round($private / 1MB, 2)
                processCount = $procs.Count
            }
            Start-Sleep -Seconds 2
        }
        return [pscustomobject]@{
            label = $Label
            workingSetMiB = [math]::Round(($samples | Measure-Object -Property workingSetMiB -Average).Average, 2)
            privateMiB = [math]::Round(($samples | Measure-Object -Property privateMiB -Average).Average, 2)
            samples = $samples
        }
    }
    finally {
        # Stop only executables hosted inside this test's own extracted folder.
        $running = @(Get-CimInstance Win32_Process | Where-Object {
            $_.ExecutablePath -and $_.ExecutablePath.StartsWith($rootPath + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
        })
        foreach ($item in $running) {
            Stop-Process -Id $item.ProcessId -Force -ErrorAction SilentlyContinue
        }
        $env:APPDATA = $oldAppData
        $env:LOCALAPPDATA = $oldLocalAppData
    }
}

$baseline = Measure-Kovelyx -Root $BaselineRoot -Label 'baseline'
Start-Sleep -Seconds 3
$candidate = Measure-Kovelyx -Root $CandidateRoot -Label 'candidate'
$result = [pscustomobject]@{
    scenario = 'Clean profile, startup + 22s warmup + five idle samples (2s apart)'
    baseline = $baseline
    candidate = $candidate
    workingSetDifferenceMiB = [math]::Round($candidate.workingSetMiB - $baseline.workingSetMiB, 2)
    privateMemoryDifferenceMiB = [math]::Round($candidate.privateMiB - $baseline.privateMiB, 2)
    imageCacheLimitBeforeMiB = 256
    imageCacheLimitAfterMiB = 128
    limitations = 'Not a physical Windows machine. No user catalogues, artwork stress, fullscreen decoding or long video playback.'
}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
if ($env:GITHUB_STEP_SUMMARY) {
    "### Mesure RAM du vrai Kovelyx.exe sur runner Windows" >> $env:GITHUB_STEP_SUMMARY
    "| Mesure | Release précédente | Candidat |" >> $env:GITHUB_STEP_SUMMARY
    "|---|---:|---:|" >> $env:GITHUB_STEP_SUMMARY
    "| Working set (Mio) | $($baseline.workingSetMiB) | $($candidate.workingSetMiB) |" >> $env:GITHUB_STEP_SUMMARY
    "| Mémoire privée (Mio) | $($baseline.privateMiB) | $($candidate.privateMiB) |" >> $env:GITHUB_STEP_SUMMARY
    "| Plafond cache affiches (Mio) | 256 | 128 |" >> $env:GITHUB_STEP_SUMMARY
    "Comparaison à froid sans catalogues personnels ni lecture vidéo longue. Ces mesures ne garantissent pas la RAM sur le PC de l'utilisateur." >> $env:GITHUB_STEP_SUMMARY
}
Write-Host "Working set baseline=$($baseline.workingSetMiB) MiB; candidate=$($candidate.workingSetMiB) MiB"
Write-Host "Private memory baseline=$($baseline.privateMiB) MiB; candidate=$($candidate.privateMiB) MiB"
