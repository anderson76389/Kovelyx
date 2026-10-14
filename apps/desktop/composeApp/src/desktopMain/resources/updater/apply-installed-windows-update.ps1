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

    # Kovelyx closes before the installer replaces its runtime files.
    for ($attempt = 0; $attempt -lt 360; $attempt++) {
        if (-not (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 250
    }
    if (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue) {
        throw 'Kovelyx ne s''est pas fermé : mise à jour annulée.'
    }

    # Read actual product information from the verified MSI.
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.OpenDatabase($MsiPath, 0)
    function Read-KovelyxMsiProperty([string]$name) {
        $view = $database.OpenView("SELECT Value FROM Property WHERE Property = '$name'")
        [void]$view.Execute()
        $record = $view.Fetch()
        $value = if ($null -ne $record) { $record.StringData(1) } else { '' }
        [void]$view.Close()
        return [string]$value
    }
    $productName = Read-KovelyxMsiProperty 'ProductName'
    $productCode = Read-KovelyxMsiProperty 'ProductCode'
    $expectedVersion = Read-KovelyxMsiProperty 'ProductVersion'
    if ($productName -ne 'Kovelyx' -or
        $productCode -notmatch '^[{][0-9A-Fa-f-]{36}[}]$' -or
        $expectedVersion -notmatch '^[0-9]+[.][0-9]+[.][0-9]+$') {
        throw 'MSI non officiel ou version invalide.'
    }
    Log ('MSI verified: Kovelyx ' + $expectedVersion)

    $msiLog = [IO.Path]::ChangeExtension($LogPath, '.msi.log')
    $arguments = '/i "{0}" /qn /norestart /L*v "{1}"' -f $MsiPath, $msiLog
    Log 'Launching Kovelyx MSI update (Windows may request administrator approval).'
    $process = Start-Process -FilePath (Join-Path $env:WINDIR 'System32\msiexec.exe') `
        -ArgumentList $arguments -Verb RunAs -Wait -PassThru
    if ($process.ExitCode -ne 0 -and $process.ExitCode -ne 3010) {
        throw ('Windows Installer a retourné le code ' + $process.ExitCode)
    }

    # Exit code 0 is not enough. Confirm the newly registered installed version.
    $state = $installer.ProductState($productCode)
    if ($state -ne 5) {
        throw ('Windows Installer n''a pas enregistré Kovelyx : état ' + $state)
    }
    $installedVersion = [string]$installer.ProductInfo($productCode, 'VersionString')
    if ($installedVersion -ne $expectedVersion) {
        throw ('Mise à jour incomplète : ' + $installedVersion + ' au lieu de ' + $expectedVersion)
    }
    Log ('Kovelyx update verified: ' + $installedVersion)

    if (-not (Test-Path -LiteralPath $CurrentLauncher -PathType Leaf)) {
        throw 'Installation terminée, mais Kovelyx.exe est introuvable.'
    }
    Start-Process -FilePath $CurrentLauncher -WorkingDirectory (Split-Path -Parent $CurrentLauncher)
    Log 'Kovelyx updated and restarted.'
}
catch {
    Log ('Update failed: ' + $_.Exception.Message)
    # If the update failed, try to bring back a usable Kovelyx window.
    if (Test-Path -LiteralPath $CurrentLauncher -PathType Leaf) {
        try {
            Start-Process -FilePath $CurrentLauncher -WorkingDirectory (Split-Path -Parent $CurrentLauncher)
            Log 'Restarted Kovelyx after update failure.'
        } catch {
            Log ('Restart failed: ' + $_.Exception.Message)
        }
    }
    throw
}
