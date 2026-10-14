# Restaurer libmpv depuis une Release portable publiee par Kovelyx.
# Aucun acces aux depots Nuvio : l'archive est relue depuis les propres Releases.
# L'empreinte SHA-256 est une mesure d'integrite, pas une preuve de conformite des licences.
param(
    [string]$Repository = $env:GITHUB_REPOSITORY,
    [string]$Destination = "apps/desktop/composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($Repository)) {
    $Repository = "anderson76389/Kovelyx"
}
if ($Repository -ne "anderson76389/Kovelyx") {
    throw "Source refusee : seul le depot Kovelyx est autorise."
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) est necessaire."
}

$expectedHash = "07c68bb211f23a218ded0a36eb12207dc3aeb44e5318ffca6ce9dcc7c3173906"
$expectedSize = 115320296L
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Test-RuntimeFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -ne $expectedSize) { return $false }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    return $actual -eq $expectedHash
}

if (Test-RuntimeFile $destinationPath) {
    Write-Host "DLL MPV deja presente et verifiee dans Kovelyx."
    exit 0
}

$releasesJson = & gh api "repos/$Repository/releases?per_page=15"
if ($LASTEXITCODE -ne 0) { throw "Lecture des Releases Kovelyx impossible." }
$releases = @($releasesJson | ConvertFrom-Json)
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("kovelyx-own-runtime-" + [guid]::NewGuid().ToString("N"))
New-Item -Path $work -ItemType Directory -Force | Out-Null
try {
    foreach ($release in $releases) {
        if ($release.draft) { continue }
        $asset = @($release.assets | Where-Object {
            $_.name -match '^Kovelyx-Windows-Portable-.*[.]zip$'
        } | Select-Object -First 1)
        if ($asset.Count -eq 0) { continue }

        $archivePath = Join-Path $work $asset[0].name
        Write-Host "Recherche du runtime dans la Release Kovelyx $($release.tag_name)..."
        & gh release download $release.tag_name --repo $Repository --pattern $asset[0].name --dir $work --clobber
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $archivePath)) { continue }

        $archive = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
        try {
            # La DLL peut etre dans la distribution, ou imbriquee dans son JAR Compose.
            $candidates = @($archive.Entries | Where-Object {
                $_.FullName -match '(^|/)libmpv-2[.]dll$'
            })
            $jarEntries = @($archive.Entries | Where-Object {
                $_.FullName -match '[.]jar$'
            })

            $counter = 0
            foreach ($candidate in $candidates) {
                $probe = Join-Path $work ("runtime-" + ($counter++) + ".dll")
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($candidate, $probe, $true)
                if (Test-RuntimeFile $probe) {
                    New-Item -Path (Split-Path $destinationPath -Parent) -ItemType Directory -Force | Out-Null
                    Copy-Item -LiteralPath $probe -Destination $destinationPath -Force
                    Write-Host "DLL MPV restauree depuis Kovelyx, empreinte SHA-256 verifiee."
                    exit 0
                }
            }
            foreach ($jarEntry in $jarEntries) {
                $jarFile = Join-Path $work ("jar-" + ($counter++) + ".zip")
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($jarEntry, $jarFile, $true)
                $jar = [System.IO.Compression.ZipFile]::OpenRead($jarFile)
                try {
                    $candidate = @($jar.Entries | Where-Object {
                        $_.FullName -match '(^|/)libmpv-2[.]dll$'
                    } | Select-Object -First 1)
                    if ($candidate.Count -eq 0) { continue }
                    $probe = Join-Path $work ("runtime-" + $counter + ".dll")
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($candidate[0], $probe, $true)
                    if (Test-RuntimeFile $probe) {
                        New-Item -Path (Split-Path $destinationPath -Parent) -ItemType Directory -Force | Out-Null
                        Copy-Item -LiteralPath $probe -Destination $destinationPath -Force
                        Write-Host "DLL MPV restauree du JAR Kovelyx, SHA-256 et taille verifies."
                        exit 0
                    }
                } finally {
                    $jar.Dispose()
                }
            }
        } finally {
            $archive.Dispose()
        }
        Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
    }
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
throw "Aucune Release Kovelyx ne contient la DLL MPV attendue. Refus de telecharger une DLL non verifiee."
