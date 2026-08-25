$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "iDepth 26 - HOTFIX Catalogo Telegram -> Aba Online" -ForegroundColor Yellow
Write-Host "----------------------------------------------------" -ForegroundColor DarkGray

$project = (Get-Location).Path
$source = Join-Path $project "_fix_catalog"
$mainTarget = Join-Path $project "app\src\main\java\com\chilenoapps\idepth26\MainActivity.java"
$mainSource = Join-Path $source "app\src\main\java\com\chilenoapps\idepth26\MainActivity.java"
$functionTarget = Join-Path $project "supabase\functions\wallpaper-catalog\index.ts"
$functionSource = Join-Path $source "supabase\functions\wallpaper-catalog\index.ts"

if (!(Test-Path $mainTarget)) {
    throw "Projeto iDepth 26 nao encontrado nesta pasta. Abra o Terminal na raiz do projeto."
}
if (!(Test-Path $mainSource) -or !(Test-Path $functionSource)) {
    throw "Arquivos do hotfix nao encontrados. Extraia todo o ZIP na raiz do projeto."
}

$backupDir = Join-Path $project ("_backup_catalogo_telegram_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
Copy-Item $mainTarget (Join-Path $backupDir "MainActivity.java") -Force

New-Item -ItemType Directory -Force -Path (Split-Path $functionTarget) | Out-Null
Copy-Item $mainSource $mainTarget -Force
Copy-Item $functionSource $functionTarget -Force

Write-Host "OK: Aba Online atualizada para mostrar primeiro as novidades do Telegram." -ForegroundColor Green
Write-Host "OK: Edge Function wallpaper-catalog adicionada ao projeto." -ForegroundColor Green

# JDK 17
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -like "jdk-17*" |
    Select-Object -First 1
if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$gradle = "C:\Gradle\gradle-8.11.1\bin\gradle.bat"
if (!(Test-Path $gradle)) {
    throw "Gradle 8.11.1 nao encontrado em $gradle"
}

Write-Host ""
Write-Host "Compilando APK..." -ForegroundColor Cyan
& $gradle :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "A compilacao falhou. Envie o print das ultimas linhas do Terminal."
}

$apk = Join-Path $project "app\build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path $apk)) {
    throw "BUILD terminou, mas o APK nao foi encontrado."
}
$desktopApk = Join-Path $env:USERPROFILE "Desktop\iDepth26-v1.7.0-CATALOGO-TELEGRAM-ChilenoAPPS.apk"
Copy-Item $apk $desktopApk -Force
Write-Host "APK: $desktopApk" -ForegroundColor Green

# Salva apenas os arquivos deste hotfix no GitHub quando Git estiver disponivel.
try {
    if (Get-Command git -ErrorAction SilentlyContinue) {
        git add -- "app/src/main/java/com/chilenoapps/idepth26/MainActivity.java" "supabase/functions/wallpaper-catalog/index.ts"
        git diff --cached --quiet
        if ($LASTEXITCODE -ne 0) {
            git commit -m "fix: integrar wallpapers Telegram a aba Online"
        }
        git push origin main
        if ($LASTEXITCODE -eq 0) {
            Write-Host "GitHub atualizado." -ForegroundColor Green
        } else {
            Write-Host "Aviso: o push do GitHub nao concluiu. O APK e os arquivos locais continuam corretos." -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "Aviso: nao foi possivel atualizar o GitHub automaticamente: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Facilita o unico passo manual do Supabase.
Get-Content $functionTarget -Raw | Set-Clipboard
Write-Host ""
Write-Host "IMPORTANTE - SUPABASE" -ForegroundColor Yellow
Write-Host "O codigo da funcao wallpaper-catalog JA ESTA COPIADO na area de transferencia." -ForegroundColor White
Write-Host "No Supabase: Edge Functions -> Deploy a new function -> Via Editor" -ForegroundColor White
Write-Host "Nome: wallpaper-catalog" -ForegroundColor Cyan
Write-Host "Cole com Ctrl+V, Deploy e deixe Verify JWT = OFF." -ForegroundColor White
Write-Host ""
Write-Host "Depois disso, abra o app e toque em 'Atualizar catalogo online'." -ForegroundColor Green

try {
    Start-Process "https://supabase.com/dashboard/project/eyxwgmcxullybhsqboqh/functions"
} catch {}
