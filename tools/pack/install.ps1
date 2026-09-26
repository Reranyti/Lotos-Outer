# Установщик рекомендуемой сборки Lotus Blight / Lotus Blight recommended pack installer.
# Запускается через install.bat. Раскладывает моды, шейдеры и конфиги из папки files\ в выбранную
# папку Minecraft и скачивает с официальных страниц моды, которые нельзя класть в архив (см. pack.json).
# Без окон / No dialogs:  install.ps1 -Target <folder> -Unattended  (old mods are moved to a backup).

param([string]$Target, [switch]$Unattended)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$pack = Get-Content -Raw -Encoding UTF8 (Join-Path $here 'pack.json') | ConvertFrom-Json
$title = "Lotus Blight $($pack.version)"

function Ask([string]$text) {
    if ($Unattended) { return $true }
    return [System.Windows.Forms.MessageBox]::Show($text, $title, 'YesNo', 'Question') -eq 'Yes'
}
function Say([string]$text, [string]$icon = 'Information') {
    if ($Unattended) { Write-Output $text; return }
    [void][System.Windows.Forms.MessageBox]::Show($text, $title, 'OK', $icon)
}

# 1. Куда ставить / Where to install
$target = if ($Target) { $Target } else { Join-Path $env:APPDATA '.minecraft' }
if (-not $Target -and -not (Ask "Установить сборку в стандартную папку Minecraft?`n$target`n`nНет — выбрать другую папку.`n`nInstall into the default Minecraft folder? No = pick another folder.")) {
    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
    $dialog.Description = 'Папка Minecraft (или профиля) / Minecraft (or profile) folder'
    if ($dialog.ShowDialog() -ne 'OK') { exit }
    $target = $dialog.SelectedPath
}
New-Item -ItemType Directory -Force -Path $target | Out-Null

# 2. Forge 1.20.1
$versions = Join-Path $target 'versions'
$forge = $false
if (Test-Path $versions) {
    $forge = @(Get-ChildItem $versions -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '1.20.1-forge-47*' }).Count -gt 0
}
if (-not $forge) {
    if (-not (Ask "В этой папке не найден Forge 1.20.1 (47.x). Без него моды не запустятся.`nСкачать: $($pack.forgeUrl)`n`nПродолжить установку всё равно?`n`nForge 1.20.1 wasn't found here - the mods won't run without it. Continue anyway?")) {
        Start-Process $pack.forgeUrl
        exit
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

# 3. Старые моды / Existing mods
$mods = Join-Path $target 'mods'
if ((Test-Path $mods) -and @(Get-ChildItem $mods -File -ErrorAction SilentlyContinue).Count -gt 0) {
    if (Ask "В папке mods уже есть моды. Перенести их в mods-backup-$stamp, чтобы две версии одного мода не конфликтовали?`n(Рекомендуется. Нет — файлы сборки просто добавятся поверх.)`n`nMove the current mods to mods-backup-$stamp? (Recommended)") {
        Move-Item $mods (Join-Path $target "mods-backup-$stamp")
    }
}

# 4. Файлы сборки / Pack files (configs that get replaced are backed up first)
$files = Join-Path $here 'files'
$configBackup = Join-Path $target "config-backup-$stamp"
Get-ChildItem $files -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($files.Length + 1)
    $dest = Join-Path $target $relative
    if ($relative -like 'config\*' -and (Test-Path $dest)) {
        $backup = Join-Path $configBackup $relative.Substring(7)
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backup) | Out-Null
        Copy-Item $dest $backup -Force
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    Copy-Item $_.FullName $dest -Force
}

# 5. Моды с официальных страниц / Mods downloaded from their official pages (their licenses don't let us bundle them)
$failed = @()
foreach ($d in $pack.downloads) {
    $dest = Join-Path (Join-Path $target $d.folder) $d.file
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $d.url -OutFile $dest -UserAgent 'LotusBlight-pack-installer'
        $sha1 = (Get-FileHash -Algorithm SHA1 $dest).Hash.ToLower()
        if ($sha1 -ne $d.sha1) {
            Remove-Item $dest -Force
            throw "checksum"
        }
    } catch {
        $failed += "$($d.name): $($d.page)"
    }
}

# 6. Готово / Done
$message = "Сборка установлена в:`n$target`n`nВ лаунчере выберите профиль Forge 1.20.1 и выделите игре 4-6 ГБ памяти.`nШейдеры Complementary Reimagined лежат в shaderpacks и включаются в настройках видео.`n`nThe pack is installed. Pick the Forge 1.20.1 profile in the launcher and give the game 4-6 GB of memory."
if ($failed.Count -gt 0) {
    $message += "`n`nНе удалось скачать, поставьте вручную в папку mods / Couldn't download, put into mods manually:`n" + ($failed -join "`n")
    Say $message 'Warning'
} else {
    Say $message
}
