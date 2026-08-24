$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = $root

$targetDrawable = Join-Path $project "app\src\main\res\drawable-nodpi"
$targetJava = Join-Path $project "app\src\main\java\com\chilenoapps\idepth26\BuiltInWallpapers.java"
$sourceDrawable = Join-Path $root "_offline_new\drawable-nodpi"
$sourceJava = Join-Path $root "_offline_new\java\BuiltInWallpapers.java"

if (!(Test-Path $targetDrawable) -or !(Test-Path $targetJava)) {
    throw "Projeto iDepth26 nao encontrado. Extraia este ZIP diretamente na raiz C:\Users\Admin\StudioProjects\iDepth26-v13-ChilenoAPPS"
}

Write-Host "[1/4] Removendo TODAS as imagens offline antigas..." -ForegroundColor Yellow
Get-ChildItem $targetDrawable -File | Where-Object { $_.Name -match '^wall_' } | Remove-Item -Force

Write-Host "[2/4] Instalando as 21 imagens do ZIP enviado..." -ForegroundColor Yellow
Copy-Item (Join-Path $sourceDrawable "*") $targetDrawable -Force
Copy-Item $sourceJava $targetJava -Force

Write-Host "[3/4] Preparando Java 17..." -ForegroundColor Yellow
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object Name -like "jdk-17*" | Select-Object -First 1
if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "[4/4] Compilando APK..." -ForegroundColor Yellow
$gradle = "C:\Gradle\gradle-8.11.1\bin\gradle.bat"
if (!(Test-Path $gradle)) { throw "Gradle 8.11.1 nao encontrado em $gradle" }
& $gradle :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "A compilacao falhou." }

$apk = Join-Path $project "app\build\outputs\apk\debug\app-debug.apk"
$desktop = Join-Path $env:USERPROFILE "Desktop\iDepth26-v1.6.1-OFFLINE-21-ChilenoAPPS.apk"
Copy-Item $apk $desktop -Force

Write-Host "" 
Write-Host "PRONTO." -ForegroundColor Green
Write-Host "As imagens offline antigas foram removidas e substituidas pelas 21 imagens enviadas." -ForegroundColor Green
Write-Host "APK: $desktop" -ForegroundColor Green
