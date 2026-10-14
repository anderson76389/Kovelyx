param(
    [Parameter(Mandatory=$true)][string]$MsiPath,
    [Parameter(Mandatory=$true)][string]$CurrentLauncher,
    [Parameter(Mandatory=$true)][long]$AppProcessId,
    [Parameter(Mandatory=$true)][string]$LogPath
)

$ErrorActionPreference = 'Stop'
function Log([string]$Message) {
    ('[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) |
        Out-File -LiteralPath $LogPath -Append -Encoding utf8
}

try {
    if (-not (Test-Path -LiteralPath $MsiPath -PathType Leaf) -or
        [IO.Path]::GetExtension($MsiPath) -ine '.msi') {
        throw 'MSI Kovelyx introuvable ou invalide.'
    }
    if ([IO.Path]::GetFileName($CurrentLauncher) -ine 'Kovelyx.exe') {
        throw 'Lanceur Kovelyx invalide.'
    }
    # Never overwrite files while their JVM process is running.
    for ($attempt = 0; $attempt -lt 360; $attempt++) {
        if (-not (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 250
    }
    if (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue) {
        throw 'Kovelyx ne s''est pas fermé : mise à jour annulée.'
    }

    $msiLog = [IO.Path]::ChangeExtension($LogPath, '.msi.log')
    $arguments = '/i "{0}" /qn /norestart /L*v "{1}"' -f $MsiPath, $msiLog
    Log 'Launching verified Kovelyx MSI update (administrator confirmation may appear).'
    # Request elevation through Windows when necessary; do not bypass UAC.
    $process = Start-Process -FilePath (Join-Path $env:WINDIR 'System32\msiexec.exe') `
        -ArgumentList $arguments -Verb RunAs -Wait -PassThru

    if ($process.ExitCode -ne 0 -and $process.ExitCode -ne 3010) {
        throw ('Windows Installer a retourné le code ' + $process.ExitCode)
    }
    Log ('Installation finished with Windows Installer code ' + $process.ExitCode)
    if (-not (Test-Path -LiteralPath $CurrentLauncher -PathType Leaf)) {
        throw 'Installation terminée, mais le lanceur Kovelyx est introuvable.'
    }
    Start-Process -FilePath $CurrentLauncher -WorkingDirectory (Split-Path -Parent $CurrentLauncher)
    Log 'Kovelyx updated and restarted.'
}
catch {
    Log ('Update failed: ' + $_.Exception.Message)
    throw
}
