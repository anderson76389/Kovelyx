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
Contrôler les licences et les sauvegardes de clés de signature à part.
"@ | Set-Content -Path (Join-Path $target "LIRE-MOI.txt") -Encoding UTF8

Write-Host "Sauvegarde terminée : $target"
