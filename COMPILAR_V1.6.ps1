$ErrorActionPreference = "Stop"

Write-Host "=== iDepth 26 v1.6 - compilacao local ===" -ForegroundColor Cyan

# 1) JDK 17
$jdkCandidates = @(
  "C:\Program Files\Eclipse Adoptium",
  "C:\Program Files\Java"
)
$jdk = $null
foreach ($base in $jdkCandidates) {
  if (Test-Path $base) {
    $found = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like "jdk-17*" -or $_.Name -like "temurin-17*" } |
      Select-Object -First 1
    if ($found) { $jdk = $found.FullName; break }
  }
}
if (-not $jdk) {
  throw "JDK 17 nao encontrado. Instale com: winget install EclipseAdoptium.Temurin.17.JDK"
}
$env:JAVA_HOME = $jdk
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Write-Host "JDK: $env:JAVA_HOME" -ForegroundColor Green

# 2) Android SDK
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "Android SDK nao encontrado em $sdk" }
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
Write-Host "Android SDK: $sdk" -ForegroundColor Green

$sdkmanager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $sdkmanager) {
  Write-Host "Garantindo Android 36 e Build Tools 35.0.0..." -ForegroundColor Yellow
  & $sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
}

# 3) Gradle 8.11.1, exigido pelo AGP 8.10/API 36
$gradleHome = "C:\Gradle\gradle-8.11.1"
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"
if (-not (Test-Path $gradleBat)) {
  Write-Host "Baixando Gradle 8.11.1..." -ForegroundColor Yellow
  New-Item -ItemType Directory -Path "C:\Gradle" -Force | Out-Null
  $zip = "$env:TEMP\gradle-8.11.1-bin.zip"
  Invoke-WebRequest "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" -OutFile $zip
  Expand-Archive $zip -DestinationPath "C:\Gradle" -Force
  Remove-Item $zip -Force
}

Write-Host "Gradle: $gradleBat" -ForegroundColor Green
& $gradleBat --version

# 4) Build
Push-Location $PSScriptRoot
try {
  & $gradleBat clean :app:assembleDebug --stacktrace
  if ($LASTEXITCODE -ne 0) { throw "Build falhou com codigo $LASTEXITCODE" }

  $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
  if (-not (Test-Path $apk)) { throw "APK nao encontrado depois do build." }

  $dest = Join-Path $PSScriptRoot "iDepth26-v1.6-ChilenoAPPS.apk"
  Copy-Item $apk $dest -Force
  Write-Host "" 
  Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
  Write-Host "APK: $dest" -ForegroundColor Cyan
} finally {
  Pop-Location
}
