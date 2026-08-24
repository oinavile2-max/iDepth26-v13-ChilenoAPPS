$ErrorActionPreference = "Stop"
$root = (Get-Location).Path
$src = Join-Path $root "_v170_hotfix\app\src\main\java\com\chilenoapps\idepth26\WallpaperAdjustActivity.java"
$dst = Join-Path $root "app\src\main\java\com\chilenoapps\idepth26\WallpaperAdjustActivity.java"
if (!(Test-Path $dst)) { throw "Abra o Terminal na raiz do projeto iDepth26." }
if (!(Test-Path $src)) { throw "Extraia o ZIP inteiro na raiz do projeto." }
Copy-Item $src $dst -Force
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object Name -like "jdk-17*" | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME=$jdk.FullName; $env:Path="$env:JAVA_HOME\bin;$env:Path" }
$gradle = "C:\Gradle\gradle-8.11.1\bin\gradle.bat"
& $gradle :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "A compilacao falhou. Envie o print das ultimas linhas." }
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$dest = Join-Path $env:USERPROFILE "Desktop\iDepth26-v1.7.0-ChilenoAPPS.apk"
Copy-Item $apk $dest -Force
Write-Host "PRONTO - HOTFIX APLICADO E BUILD SUCCESSFUL" -ForegroundColor Green
Write-Host ("APK: " + $dest) -ForegroundColor Green
