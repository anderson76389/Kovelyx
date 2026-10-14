param(
    [Parameter(Mandatory=$true)][string]$ZipPath,
    [Parameter(Mandatory=$true)][string]$InstallDir,
    [Parameter(Mandatory=$true)][long]$AppProcessId,
    [Parameter(Mandatory=$true)][string]$LogPath
)
$ErrorActionPreference = 'Stop'
$parent = Split-Path -Parent $InstallDir
$stamp = [Guid]::NewGuid().ToString('N')
$stage = Join-Path $parent ('.kovelyx-stage-' + $stamp)
$backup = Join-Path $parent ('.kovelyx-previous-' + $stamp)
$swapped = $false

function Log([string]$Message) {
    ('[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) |
        Out-File -LiteralPath $LogPath -Append -Encoding utf8
}
try {
    if (-not (Test-Path -LiteralPath (Join-Path $InstallDir 'Kovelyx.portable'))) {
        throw 'Portable marker absent; refusing to modify an installed copy.'
    }
    if (-not (Test-Path -LiteralPath $ZipPath)) { throw 'Downloaded ZIP missing.' }
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $stage -Force

    $candidates = @(
        Get-ChildItem -LiteralPath $stage -Recurse -Filter Kovelyx.portable -File |
            Where-Object {
                $folder = $_.Directory.FullName
                (Test-Path -LiteralPath (Join-Path $folder 'Kovelyx.exe')) -and
                (Test-Path -LiteralPath (Join-Path $folder 'app')) -and
                (Test-Path -LiteralPath (Join-Path $folder 'runtime'))
            } | Select-Object -ExpandProperty DirectoryName
    )
    if ($candidates.Count -ne 1) { throw 'Incomplete or ambiguous portable Kovelyx archive.' }
    $newDir = $candidates[0]
    Log 'New portable archive extracted and validated.'

    for ($attempt = 0; $attempt -lt 360; $attempt++) {
        if (-not (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 250
    }
    if (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue) {
        throw 'Kovelyx is still running: current installation left unchanged.'
    }

    Rename-Item -LiteralPath $InstallDir -NewName (Split-Path -Leaf $backup)
    try {
        Move-Item -LiteralPath $newDir -Destination $InstallDir
        $swapped = $true
    }
    catch {
        Move-Item -LiteralPath $backup -Destination $InstallDir
        throw
    }
    Log 'New portable version installed. Older version retained at backup path.'
    Start-Process -FilePath (Join-Path $InstallDir 'Kovelyx.exe') -WorkingDirectory $InstallDir
    Log 'New Kovelyx started.'
}
catch {
    Log ("Update failed: " + $_.Exception.Message)
    if ($swapped -and (Test-Path -LiteralPath $backup)) {
        # A failure to launch is recoverable using the preserved backup.
        Log ("Previous version retained for rollback at: " + $backup)
    }
    throw
}
finally {
    if (Test-Path -LiteralPath $stage) {
        Remove-Item -LiteralPath $stage -Force -Recurse -ErrorAction SilentlyContinue
    }
}
