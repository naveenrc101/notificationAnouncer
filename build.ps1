# ============================================================
#  build.ps1 — Build NotificationAnnouncer APK (no Android Studio)
#  Run in PowerShell as: .\build.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$ProjectDir  = $PSScriptRoot
$SdkRoot     = "$env:USERPROFILE\android-sdk"
$GradleRoot  = "$env:USERPROFILE\gradle-8.4"
$GradleZipUrl = "https://services.gradle.org/distributions/gradle-8.4-bin.zip"

function Print-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Print-OK($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Print-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }

# ── Step 1: Java ────────────────────────────────────────────
Print-Step "Checking Java (JDK 17 required)"
$javaOk = $false
try { $v = java -version 2>&1; $javaOk = $true; Print-OK $v[0] } catch {}

if (-not $javaOk) {
    Print-Warn "Java not found. Installing JDK 17 via winget..."
    winget install Microsoft.OpenJDK.17 --accept-source-agreements --accept-package-agreements --silent
    # Reload PATH
    $env:PATH = [Environment]::GetEnvironmentVariable("PATH","Machine") + ";" +
                [Environment]::GetEnvironmentVariable("PATH","User")
    Print-OK "JDK 17 installed"
}

# ── Step 2: Gradle ──────────────────────────────────────────
Print-Step "Checking Gradle 8.4"
$GradleExe = "$GradleRoot\bin\gradle.bat"

if (-not (Test-Path $GradleExe)) {
    Print-Warn "Downloading Gradle 8.4..."
    $zip = "$env:TEMP\gradle-8.4-bin.zip"
    Invoke-WebRequest $GradleZipUrl -OutFile $zip
    Expand-Archive $zip "$env:USERPROFILE" -Force
    Rename-Item "$env:USERPROFILE\gradle-8.4" "gradle-8.4" -ErrorAction SilentlyContinue
    Remove-Item $zip
    Print-OK "Gradle extracted to $GradleRoot"
} else {
    Print-OK "Gradle already present"
}

# ── Step 3: Android Command-line Tools ──────────────────────
Print-Step "Checking Android SDK command-line tools"
$SdkManager = "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"

if (-not (Test-Path $SdkManager)) {
    Print-Warn "Downloading Android command-line tools..."

    # Fetch latest build number from Google's repository index
    $repoXml = (Invoke-WebRequest "https://dl.google.com/android/repository/repository2-3.xml" -UseBasicParsing).Content
    $buildNum = [regex]::Match($repoXml, 'commandlinetools-win-(\d+)_latest\.zip').Groups[1].Value
    if (-not $buildNum) { throw "Could not determine cmdline-tools version from repository XML" }

    $ctUrl = "https://dl.google.com/android/repository/commandlinetools-win-${buildNum}_latest.zip"
    Print-Warn "Found build $buildNum — downloading from $ctUrl"

    $ctZip = "$env:TEMP\cmdline-tools.zip"
    Invoke-WebRequest $ctUrl -OutFile $ctZip

    $tempDir = "$env:TEMP\cmdline-tools-extract"
    Expand-Archive $ctZip $tempDir -Force

    New-Item -ItemType Directory -Force "$SdkRoot\cmdline-tools" | Out-Null
    $dest = "$SdkRoot\cmdline-tools\latest"
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    Move-Item "$tempDir\cmdline-tools" $dest
    Remove-Item $tempDir -Recurse
    Remove-Item $ctZip
    Print-OK "Command-line tools installed"
} else {
    Print-OK "SDK manager already present"
}

# ── Step 4: Install SDK components ──────────────────────────
Print-Step "Installing SDK platform 34 and build-tools 34.0.0"
$env:ANDROID_HOME     = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot

# Accept all licenses non-interactively
for ($i = 0; $i -lt 10; $i++) { "y" } | & $SdkManager --licenses --sdk_root=$SdkRoot 2>&1 | Out-Null
& $SdkManager "platforms;android-34" "build-tools;34.0.0" --sdk_root=$SdkRoot
Print-OK "SDK components ready"

# ── Step 5: local.properties ────────────────────────────────
Print-Step "Writing local.properties"
$escapedSdk = $SdkRoot -replace '\\', '\\\\'
"sdk.dir=$escapedSdk" | Set-Content "$ProjectDir\local.properties"
Print-OK "sdk.dir = $SdkRoot"

# ── Step 6: Build APK ───────────────────────────────────────
Print-Step "Building debug APK"
Set-Location $ProjectDir
& $GradleExe assembleDebug

$apk = "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    $size = [math]::Round((Get-Item $apk).Length / 1MB, 2)
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "  BUILD SUCCESSFUL" -ForegroundColor Green
    Write-Host "  APK : $apk" -ForegroundColor Green
    Write-Host "  Size: ${size} MB" -ForegroundColor Green
    Write-Host "============================================`n" -ForegroundColor Green
} else {
    Write-Host "`nBuild failed. Check the output above." -ForegroundColor Red
    exit 1
}
