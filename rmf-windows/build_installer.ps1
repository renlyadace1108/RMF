# RMF Windows Build & Package Script v1.5.8.4
$ErrorActionPreference = "Stop"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " [RMF] Starting Windows Client Build (v1.5.8.4)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Compile & Publish .NET 10 Win-x64
Write-Host "`n[1/3] Compiling .NET 10 self-contained win-x64..." -ForegroundColor Yellow
$dotnetCmd = "dotnet"
if (!(Get-Command dotnet -ErrorAction SilentlyContinue)) {
    if (Test-Path "$env:USERPROFILE\.dotnet\dotnet.exe") {
        $dotnetCmd = "$env:USERPROFILE\.dotnet\dotnet.exe"
    } elseif (Test-Path "C:\Program Files\dotnet\dotnet.exe") {
        $dotnetCmd = "C:\Program Files\dotnet\dotnet.exe"
    }
}
$publishDir = Join-Path $scriptDir "publish"
& $dotnetCmd publish "$scriptDir\RMF.Windows\RMF.Windows.csproj" -c Release -r win-x64 --self-contained true -p:PublishSingleFile=false -o $publishDir

if ($LASTEXITCODE -ne 0) {
    Write-Error "dotnet publish failed!"
    exit 1
}
Write-Host "dotnet publish completed -> $publishDir" -ForegroundColor Green

# 2. Search Inno Setup Compiler ISCC.exe
Write-Host "`n[2/3] Checking Inno Setup Compiler (ISCC.exe)..." -ForegroundColor Yellow

$candidatePaths = @(
    (Get-Command ISCC.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
    "C:\Program Files\Inno Setup 6\ISCC.exe",
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "D:\Program Files\Inno Setup 6\ISCC.exe",
    "D:\Program Files (x86)\Inno Setup 6\ISCC.exe"
) | Where-Object { !([string]::IsNullOrWhiteSpace($_)) -and (Test-Path $_) }

$isccPath = $candidatePaths | Select-Object -First 1

if ($isccPath) {
    Write-Host "Found Inno Setup Compiler: $isccPath" -ForegroundColor Green
    Write-Host "`n[3/3] Generating Windows Setup Installer..." -ForegroundColor Yellow
    
    $setupIss = Join-Path $scriptDir "setup.iss"
    & "$isccPath" "$setupIss"
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Inno Setup exit code: $LASTEXITCODE"
    } else {
        $outputExe = Join-Path $scriptDir "installer_output\RMF_Setup_v1.5.8.4.exe"
        Write-Host "==================================================" -ForegroundColor Green
        Write-Host "Installer created successfully!" -ForegroundColor Green
        Write-Host "Location: $outputExe" -ForegroundColor Cyan
        Write-Host "==================================================" -ForegroundColor Green
    }
} else {
    Write-Warning "ISCC.exe (Inno Setup) not found in standard paths."
    Write-Host "Tip: Install via winget in PowerShell:" -ForegroundColor Yellow
    Write-Host "  winget install JRSoftware.InnoSetup" -ForegroundColor White
    Write-Host "Or run standalone executable from: publish\RMF.Windows.exe" -ForegroundColor Green
}

# 3. Create portable zip
$releaseDir = Join-Path (Split-Path -Parent $scriptDir) "release"
if (!(Test-Path $releaseDir)) { New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null }
$zipOutput = Join-Path $releaseDir "RMF-Windows-v1.5.8.4-win64.zip"
if (Test-Path $zipOutput) { Remove-Item $zipOutput -Force }
Write-Host "`nCreating portable zip archive: $zipOutput ..." -ForegroundColor Yellow
Compress-Archive -Path "$publishDir\*" -DestinationPath $zipOutput -Force
Write-Host "Portable zip package is ready -> $zipOutput" -ForegroundColor Green