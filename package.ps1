#Requires -Version 5.1
# Lensora Studio - Simple Packaging Script (uses mvnw)
# Usage: 
#   Just run it
#       .\package.ps1
#   
#   Or with custom version
#       .\package.ps1 -AppVersion "1.2.0"

# | Option                     | Description             | Size          |
# | -------------------------- | ----------------------- | ------------- |
# | `installer-icon.ico`       | Installer window icon   | Multi-res ICO |
# | `installer-banner.bmp`     | Top banner in installer | 493×58 px     |
# | `installer-background.bmp` | Background in installer | 493×312 px    |


param
(
    [string]$OutputDir = ".\.dist",
    [string]$AppVersion = "1.0.0"
)

$AppName       = "Lensora Studio"
$Vendor        = "Lensora Foundation"
$MainClass     = "com.lensora.lensorastudio.Launcher"
$JarFile       = "lensora-studio-1.0-SNAPSHOT-all.jar"
$InstallerType = "exe"   # or "exe"

# Detect Maven command (mvnw or mvn)
$MvnCmd = ""
if (Test-Path ".\mvnw.cmd") 
{
    $MvnCmd = ".\mvnw.cmd"
} 
elseif (Test-Path ".\mvnw")
{
    $MvnCmd = ".\mvnw"
} 
elseif (Get-Command mvn -ErrorAction SilentlyContinue) 
{
    $MvnCmd = "mvn"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Lensora Studio Packager" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Validate environment
if (-not (Get-Command java -ErrorAction SilentlyContinue)) 
{
    Write-Host "ERROR: Java not found in PATH" -ForegroundColor Red
    exit 1
}
if ($MvnCmd -eq "") 
{
    Write-Host "ERROR: Neither mvnw nor mvn found" -ForegroundColor Red
    exit 1
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) 
{
    Write-Host "ERROR: jpackage not found. Install JDK 26+" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path "pom.xml")) 
{
    Write-Host "ERROR: pom.xml not found. Run from project root." -ForegroundColor Red
    exit 1
}

Write-Host "  Maven: $MvnCmd" -ForegroundColor Gray

# Build
Write-Host "`n[1/4] Building with Maven..." -ForegroundColor Yellow
& $MvnCmd clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) 
{
    Write-Host "ERROR: Maven build failed" -ForegroundColor Red
    exit 1
}
Write-Host "  Build successful" -ForegroundColor Green

# Check JAR
$JarPath = "target\$JarFile"
if (-not (Test-Path $JarPath))
{
    Write-Host "ERROR: JAR not found: $JarPath" -ForegroundColor Red
    exit 1
}

# Prepare output
Write-Host "`n[2/4] Preparing output directory..." -ForegroundColor Yellow
if (Test-Path $OutputDir) 
{
    Remove-Item "$OutputDir\*" -Recurse -Force -ErrorAction SilentlyContinue
} 
else 
{
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# Create temp input dir for jpackage
$TempDir = Join-Path $env:TEMP ("lensora_" + (Get-Random -Maximum 99999))
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
Copy-Item $JarPath (Join-Path $TempDir $JarFile) -Force

# Create EXE installer
Write-Host "`n[3/4] Creating EXE installer..." -ForegroundColor Yellow
$JvmOpts = @(
    "-Djavafx.enablePreview=true"
    "-Djavafx.suppressPreviewWarning=true"
    "--enable-native-access=ALL-UNNAMED"
)

$JpackageArgs = @(
    "--type", $InstallerType
    "--name", $AppName
    "--app-version", $AppVersion
    "--vendor", $Vendor
    "--input", $TempDir
    "--main-jar", $JarFile
    "--main-class", $MainClass
    "--dest", $OutputDir
    "--win-dir-chooser"
    "--win-menu"
    "--win-menu-group", $Vendor
    "--win-shortcut"
    "--win-shortcut-prompt"
    "--win-per-user-install"
    "--description", "Lensora Studio - Studio Project & File Management System"
    "--copyright", "Copyright 2026 Lensora Foundation. All Rights Reserved"
    "--resource-dir", "src\main\resources\com\lensora\lensorastudio\installer"
    "--verbose"
)

foreach ($opt in $JvmOpts) {
    $JpackageArgs += "--java-options"
    $JpackageArgs += $opt
}

# Add icon if exists
$iconCandidates = @(
    "src\main\resources\com\lensora\lensorastudio\installer\installer-icon.ico"
    "src\main\resources\images\lensora.ico" 
    "src\main\resources\icon.ico"
    "icon.ico"
)
foreach ($icon in $iconCandidates) {
    if (Test-Path $icon) {
        $JpackageArgs += "--icon"
        $JpackageArgs += (Resolve-Path $icon).Path
        Write-Host "  Using icon: $icon" -ForegroundColor Gray
        break
    }
}

# Add resources if exists
$resourceDir = "src\main\resources\com\lensora\lensorastudio\installer"
if (Test-Path $resourceDir) {
    $JpackageArgs += "--resource-dir"
    $JpackageArgs += (Resolve-Path $resourceDir).Path
    Write-Host "  Using installer resources: $resourceDir" -ForegroundColor Gray
}

jpackage @JpackageArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: jpackage failed" -ForegroundColor Red
    Remove-Item $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}

# Create ZIP
Write-Host "`n[4/4] Creating portable ZIP..." -ForegroundColor Yellow
$ZipName = "$AppName-$AppVersion-portable.zip"
$ZipTemp = Join-Path $env:TEMP ("lensorazip_" + (Get-Random -Maximum 99999))
$AppDir = Join-Path $ZipTemp "$AppName-$AppVersion"
New-Item -ItemType Directory -Path $AppDir -Force | Out-Null

Copy-Item $JarPath (Join-Path $AppDir $JarFile) -Force

# Create batch launcher
$BatContent = @'
@echo off
setlocal
set "DIR=%~dp0"
java -Djavafx.enablePreview=true -Djavafx.suppressPreviewWarning=true --enable-native-access=ALL-UNNAMED -jar "%DIR%JARNAME" %*
'@.Replace("JARNAME", $JarFile)

$BatPath = Join-Path $AppDir "Start-LensoraStudio.bat"
[System.IO.File]::WriteAllText($BatPath, $BatContent)

# Create PowerShell launcher
$PsContent = @'
$args = @(
    "-Djavafx.enablePreview=true"
    "-Djavafx.suppressPreviewWarning=true"
    "--enable-native-access=ALL-UNNAMED"
    "-jar"
    "$PSScriptRoot\JARNAME"
)
& java @args
'@.Replace("JARNAME", $JarFile)

$PsPath = Join-Path $AppDir "Start-LensoraStudio.ps1"
[System.IO.File]::WriteAllText($PsPath, $PsContent)

# Create README
$Readme = @"
Lensora Studio PORTABLE
Version: VERSION

REQUIREMENTS:
- Java 26 or later

HOW TO RUN:
- Double-click: Start-LensoraStudio.bat
- Or run: Start-LensoraStudio.ps1

JVM options are pre-configured in the launchers.
"@.Replace("VERSION", $AppVersion)

$ReadmePath = Join-Path $AppDir "README.txt"
[System.IO.File]::WriteAllText($ReadmePath, $Readme)

Compress-Archive -Path $AppDir -DestinationPath (Join-Path $OutputDir $ZipName) -Force

# Cleanup
Remove-Item $TempDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $ZipTemp -Recurse -Force -ErrorAction SilentlyContinue

# Summary
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "  Packaging Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

$files = Get-ChildItem $OutputDir -File
foreach ($f in $files) {
    $size = if ($f.Length -gt 1MB) { "{0:N1} MB" -f ($f.Length/1MB) } else { "{0:N1} KB" -f ($f.Length/1KB) }
    Write-Host "  $size  $($f.Name)" -ForegroundColor White
}

Write-Host "`nOutput: $(Resolve-Path $OutputDir)" -ForegroundColor Cyan
Write-Host "`nDone!" -ForegroundColor Green