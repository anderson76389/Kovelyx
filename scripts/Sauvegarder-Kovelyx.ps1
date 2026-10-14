param(
    [string]$Destination = (Join-Path $HOME "Documents\Kovelyx-Backups")
)

$ErrorActionPreference = "Stop"
$repository = "anderson76389/Kovelyx"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$target = Join-Path $Destination $timestamp
$mirror = Join-Path $target "Kovelyx.git"
$releases = Join-Path $target "Releases"

foreach ($command in @("git", "gh")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Commande absente : $command. Installe Git et GitHub CLI."
    }
}

New-Item -ItemType Directory -Path $target -Force | Out-Null
Write-Host "Sauvegarde Git de Kovelyx dans : $target"
& git clone --mirror "https://github.com/$repository.git" $mirror
if ($LASTEXITCODE -ne 0) { throw "Échec de la sauvegarde Git." }

Write-Host "Récupération des Releases si l'accès GitHub est configuré..."
New-Item -ItemType Directory -Path $releases -Force | Out-Null
& gh auth status 2>$null
if ($LASTEXITCODE -eq 0) {
    $raw = & gh release list --repo $repository --limit 100 --json tagName
    if ($LASTEXITCODE -ne 0) { throw "Impossible de lister les Releases." }
    $items = @($raw | ConvertFrom-Json)
    foreach ($item in $items) {
        if (-not $item.tagName) { continue }
        $dir = Join-Path $releases $item.tagName
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        & gh release download $item.tagName --repo $repository --dir $dir
        if ($LASTEXITCODE -ne 0) { throw "Échec de la copie des fichiers de la Release $($item.tagName)." }
        Get-ChildItem -Path $dir -File | ForEach-Object {
            $hash = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash
            Add-Content -Path (Join-Path $target "checksums.txt") -Value "$hash  $($item.tagName)/$($_.Name)"
        }
    }
    # Sauvegarde additionnelle : artefacts temporaires du moteur vidéo Windows.
    # Ils expirent sur GitHub Actions : les copier localement au plus tôt.
    $downloads = @(
        @{Workflow='native-windows.yml'; Artifact='kovelyx-windows-mpv'; Folder='Windows-MPV'},
        @{Workflow='windows.yml'; Artifact='Kovelyx-Windows-MSI-experimental'; Folder='Windows-MSI'}
    )
    foreach ($entry in $downloads) {
        $runsJson = & gh run list --repo $repository --workflow $entry.Workflow --branch main --status success --limit 8 --json databaseId
        if ($LASTEXITCODE -ne 0) { Write-Warning ('Liste Actions indisponible : ' + $entry.Workflow); continue }
        $runs = @($runsJson | ConvertFrom-Json)
        foreach ($run in $runs) {
            $destination = Join-Path $target $entry.Folder
            New-Item -ItemType Directory -Path $destination -Force | Out-Null
            & gh run download $run.databaseId --repo $repository --name $entry.Artifact --dir $destination 2>$null
            if ($LASTEXITCODE -ne 0) { continue }
            Get-ChildItem $destination -File | ForEach-Object {
                $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                Add-Content -Path (Join-Path $target 'checksums.txt') -Value ($hash + '  ' + $entry.Folder + '/' + $_.Name)
            }
            if ($entry.Folder -eq 'Windows-MPV') {
                $dll = Join-Path $destination 'libmpv-2.dll'
                if (Test-Path $dll) {
                    $actual = (Get-FileHash $dll -Algorithm SHA256).Hash.ToLowerInvariant()
                    $expected = '07c68bb211f23a218ded0a36eb12207dc3aeb44e5318ffca6ce9dcc7c3173906'
                    if ($actual -ne $expected) { throw 'La DLL MPV sauvegardée ne correspond pas à son SHA256 attendu.' }
                }
            }
            Write-Host ('Artefact sauvegardé : ' + $entry.Artifact)
            break
        }
    }
} else {
    Write-Warning "GitHub CLI non connecté : seules les sources Git sont sauvegardées. Lance gh auth login puis réessaie."
}

@"
KOVELYX - SAUVEGARDE LOCALE
Date : $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Référentiel : https://github.com/$repository
Git mirror : Kovelyx.git
Releases : Releases/
ATTENTION : un miroir Git ne sauvegarde pas les dépendances téléchargées
(Maven, NuGet, SDK Android) ni les anciens binaires LFS hors dépôt.
Les Releases et checksums sont copiés séparément lorsqu'ils existent.
Les derniers artefacts temporaires Windows-MPV et Windows-MSI sont aussi
sauvegardés lorsque leur téléchargement GitHub est encore possible.
Contrôler les licences et les sauvegardes de clés de signature à part.
"@ | Set-Content -Path (Join-Path $target "LIRE-MOI.txt") -Encoding UTF8

Write-Host "Sauvegarde terminée : $target"
