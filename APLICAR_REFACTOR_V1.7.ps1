$ErrorActionPreference = "Stop"

$root = (Get-Location).Path
$patch = Join-Path $root "_v170_patch"
$app = Join-Path $root "app"
$drawable = Join-Path $app "src\main\res\drawable-nodpi"

if (!(Test-Path (Join-Path $app "build.gradle.kts"))) {
    throw "Abra o Terminal na raiz do projeto iDepth26 antes de executar este arquivo."
}
if (!(Test-Path $patch)) {
    throw "Pasta _v170_patch não encontrada. Extraia o ZIP inteiro dentro da raiz do projeto."
}

Write-Host "" 
Write-Host "iDepth 26 v1.7.0 - aplicando refactor..." -ForegroundColor Yellow

# Backup leve do código/configuração antes da substituição.
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backup = Join-Path $root ("_backup_pre_v170_" + $stamp)
New-Item -ItemType Directory -Force -Path $backup | Out-Null
if (Test-Path (Join-Path $app "src\main\java\com\chilenoapps\idepth26")) {
    New-Item -ItemType Directory -Force -Path (Join-Path $backup "java") | Out-Null
    Copy-Item (Join-Path $app "src\main\java\com\chilenoapps\idepth26\*.java") (Join-Path $backup "java") -Force -ErrorAction SilentlyContinue
}
Copy-Item (Join-Path $app "build.gradle.kts") (Join-Path $backup "app-build.gradle.kts") -Force
Copy-Item (Join-Path $root "build.gradle.kts") (Join-Path $backup "root-build.gradle.kts") -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $root "gradle.properties") (Join-Path $backup "gradle.properties") -Force -ErrorAction SilentlyContinue

# Limpa somente recursos antigos de wallpapers para não sobrar imagem da versão anterior.
New-Item -ItemType Directory -Force -Path $drawable | Out-Null
Get-ChildItem $drawable -File -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -like "wall_*" -or $_.Name -like "offline_*" -or $_.Name -like "online_*"
} | Remove-Item -Force

# Aplica configuração e código/resources v1.7.0.
Copy-Item (Join-Path $patch "build.gradle.kts") (Join-Path $root "build.gradle.kts") -Force
Copy-Item (Join-Path $patch "settings.gradle.kts") (Join-Path $root "settings.gradle.kts") -Force
Copy-Item (Join-Path $patch "gradle.properties") (Join-Path $root "gradle.properties") -Force
Copy-Item (Join-Path $patch "app\build.gradle.kts") (Join-Path $app "build.gradle.kts") -Force
Copy-Item (Join-Path $patch "app\src\main\*") (Join-Path $app "src\main") -Recurse -Force

# Validação do catálogo antes de compilar.
$offBg = @(Get-ChildItem $drawable -Filter "offline_*_bg.webp" -File).Count
$offTh = @(Get-ChildItem $drawable -Filter "offline_*_thumb.webp" -File).Count
$onBg  = @(Get-ChildItem $drawable -Filter "online_*_bg.webp" -File).Count
$onTh  = @(Get-ChildItem $drawable -Filter "online_*_thumb.webp" -File).Count
if ($offBg -ne 63 -or $offTh -ne 63 -or $onBg -ne 84 -or $onTh -ne 84) {
    throw "Falha na validação dos wallpapers. Offline=$offBg/$offTh Online=$onBg/$onTh"
}
Write-Host "Catálogo validado: 63 offline + 84 online." -ForegroundColor Green
Write-Host "Regra VIP: 5 FREE em cada aba; restante VIP." -ForegroundColor Green

# JDK 17 já usado com sucesso neste projeto.
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -like "jdk-17*" | Select-Object -First 1
if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if (!$env:JAVA_HOME) {
    throw "JDK 17 não localizado."
}
Write-Host ("JAVA_HOME=" + $env:JAVA_HOME)

$gradle = "C:\Gradle\gradle-8.11.1\bin\gradle.bat"
if (!(Test-Path $gradle)) {
    throw "Gradle 8.11.1 não localizado em C:\Gradle\gradle-8.11.1."
}

Write-Host "Compilando APK..." -ForegroundColor Yellow
& $gradle clean :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "A compilação falhou. Envie um print das últimas linhas do Terminal."
}

$apk = Join-Path $app "build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path $apk)) {
    throw "BUILD terminou, mas app-debug.apk não foi localizado."
}
$dest = Join-Path $env:USERPROFILE "Desktop\iDepth26-v1.7.0-ChilenoAPPS.apk"
Copy-Item $apk $dest -Force

Write-Host "" 
Write-Host "PRONTO - BUILD SUCCESSFUL" -ForegroundColor Green
Write-Host "Offline: 63 (5 FREE + 58 VIP)" -ForegroundColor Green
Write-Host "Online: 84 (5 FREE + 79 VIP) + updates Telegram" -ForegroundColor Green
Write-Host ("APK: " + $dest) -ForegroundColor Green
Write-Host ("Backup do código anterior: " + $backup) -ForegroundColor DarkGray
