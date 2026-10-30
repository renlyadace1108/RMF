# ==============================================================================
# RMF 一键发布构建至 Google Drive 云端更新通道脚本
# 功能：
# 1. 扫描/打包 Android (手机版、平板版) APK 与 Windows (安装包、绿色压缩包)；
# 2. 调用 Google Drive API 上传各端构建至 RMF_CloudSync 同步目录；
# 3. 自动生成并上传 rmf_release_info.json 版本清单，驱动 Android & Windows 应用内一键自升级。
# ==============================================================================

[CmdletBinding()]
param (
    [string]$VersionName = "1.5.8.6",
    [int]$VersionCode = 1050806,
    [string]$Changelog = "1. 全新支持 Google Drive 应用内全自动更新（手机/平板/电脑一键平滑升级）`n2. 优化 SQLite WAL 刷盘与云端数据安全同步`n3. 细节打磨与性能优化",
    [string]$PhoneApkPath = "",
    [string]$PadApkPath = "",
    [string]$WindowsInstallerPath = "",
    [string]$WindowsZipPath = "",
    [string]$ClientId = "",
    [string]$ClientSecret = "",
    [string]$RefreshToken = ""
)

$ErrorActionPreference = "Stop"
$rootDir = $PSScriptRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   RMF Google Drive Release Publisher (v$VersionName)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. 读取本地 Windows 客户端已保存的 Google Drive 凭据（若未传参）
$configPath = Join-Path $env:APPDATA "RMF\rmf_config.json"
if (Test-Path $configPath) {
    try {
        $json = Get-Content $configPath -Raw | ConvertFrom-Json
        if ([string]::IsNullOrWhiteSpace($ClientId) -and $json.GoogleClientId) {
            $ClientId = $json.GoogleClientId
        }
        if ([string]::IsNullOrWhiteSpace($ClientSecret) -and $json.GoogleClientSecret) {
            $ClientSecret = $json.GoogleClientSecret
        }
        if ([string]::IsNullOrWhiteSpace($RefreshToken) -and $json.GoogleDriveRefreshToken) {
            $RefreshToken = $json.GoogleDriveRefreshToken
        }
    } catch {
        Write-Warning "读取本地 rmf_config.json 凭据时出错: $_"
    }
}

if ([string]::IsNullOrWhiteSpace($ClientId) -or [string]::IsNullOrWhiteSpace($ClientSecret)) {
    Write-Host "`n[提示] 未在本地配置中找到 Google Client ID / Secret。" -ForegroundColor Yellow
    if ([string]::IsNullOrWhiteSpace($ClientId)) {
        $ClientId = Read-Host "请输入 Google Client ID"
    }
    if ([string]::IsNullOrWhiteSpace($ClientSecret)) {
        $ClientSecret = Read-Host "请输入 Google Client Secret"
    }
}

if ([string]::IsNullOrWhiteSpace($RefreshToken)) {
    Write-Host "`n[提示] 未找到 Refresh Token。请在 Windows 客户端「Google 服务」中完成一次授权，" -ForegroundColor Yellow
    Write-Host "或在浏览器打开授权链接换取 code 后传入 RefreshToken。" -ForegroundColor Yellow
    $RefreshToken = Read-Host "请输入 Google Drive Refresh Token (可直接回车跳过并使用测试/已授权模式)"
}

# 2. 自动定位构建产物（若用户未手动指定）
Write-Host "`n[1/4] 检索本地发布构建产物..." -ForegroundColor Yellow

# Android Phone APK
if ([string]::IsNullOrWhiteSpace($PhoneApkPath)) {
    $candidatePhone = Join-Path $rootDir "rmf-android\app\build\outputs\apk\phone\release\app-phone-release.apk"
    if (Test-Path $candidatePhone) { $PhoneApkPath = $candidatePhone }
}
if ($PhoneApkPath -and (Test-Path $PhoneApkPath)) {
    Write-Host "  ✓ 找到 Android 手机版: $PhoneApkPath ($( [math]::Round((Get-Item $PhoneApkPath).Length / 1MB, 2) ) MB)" -ForegroundColor Green
} else {
    Write-Host "  - 未检测到 Android 手机版 APK (可先运行 gradlew assemblePhoneRelease)" -ForegroundColor Gray
}

# Android Pad APK
if ([string]::IsNullOrWhiteSpace($PadApkPath)) {
    $candidatePad = Join-Path $rootDir "rmf-android\app\build\outputs\apk\pad\release\app-pad-release.apk"
    if (Test-Path $candidatePad) { $PadApkPath = $candidatePad }
}
if ($PadApkPath -and (Test-Path $PadApkPath)) {
    Write-Host "  ✓ 找到 Android 平板版: $PadApkPath ($( [math]::Round((Get-Item $PadApkPath).Length / 1MB, 2) ) MB)" -ForegroundColor Green
} else {
    Write-Host "  - 未检测到 Android 平板版 APK (可先运行 gradlew assemblePadRelease)" -ForegroundColor Gray
}

# Windows Installer
if ([string]::IsNullOrWhiteSpace($WindowsInstallerPath)) {
    $installerPattern = Join-Path $rootDir "rmf-windows\installer_output\*.exe"
    $found = Get-Item $installerPattern -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($found) { $WindowsInstallerPath = $found.FullName }
}
if ($WindowsInstallerPath -and (Test-Path $WindowsInstallerPath)) {
    Write-Host "  ✓ 找到 Windows 安装包: $WindowsInstallerPath ($( [math]::Round((Get-Item $WindowsInstallerPath).Length / 1MB, 2) ) MB)" -ForegroundColor Green
} else {
    Write-Host "  - 未检测到 Windows 安装包 (可先运行 build_installer.ps1)" -ForegroundColor Gray
}

# Windows Zip
if ([string]::IsNullOrWhiteSpace($WindowsZipPath)) {
    $zipPattern = Join-Path $rootDir "release\RMF-Windows-*.zip"
    $foundZip = Get-Item $zipPattern -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($foundZip) { $WindowsZipPath = $foundZip.FullName }
}
if ($WindowsZipPath -and (Test-Path $WindowsZipPath)) {
    Write-Host "  ✓ 找到 Windows 压缩包: $WindowsZipPath ($( [math]::Round((Get-Item $WindowsZipPath).Length / 1MB, 2) ) MB)" -ForegroundColor Green
} else {
    Write-Host "  - 未检测到 Windows 便携压缩包" -ForegroundColor Gray
}

# 3. 换取 Access Token
if (![string]::IsNullOrWhiteSpace($RefreshToken)) {
    Write-Host "`n[2/4] 连接 Google OAuth 2.0 端点刷新 Access Token..." -ForegroundColor Yellow
    $tokenBody = @{
        client_id     = $ClientId.Trim()
        client_secret = $ClientSecret.Trim()
        refresh_token = $RefreshToken.Trim()
        grant_type    = "refresh_token"
    }

    try {
        $tokenRes = Invoke-RestMethod -Method Post -Uri "https://oauth2.googleapis.com/token" -Body $tokenBody
        $accessToken = $tokenRes.access_token
        Write-Host "  ✓ 成功获取 Access Token" -ForegroundColor Green
    } catch {
        Write-Error "刷新 Token 失败: $_"
        exit 1
    }

    $headers = @{
        Authorization = "Bearer $accessToken"
    }

    # 4. 定位或创建 RMF_CloudSync 专属同步文件夹
    Write-Host "`n[3/4] 检索 Google Drive 专属同步文件夹 (RMF_CloudSync)..." -ForegroundColor Yellow
    $folderQuery = "mimeType = 'application/vnd.google-apps.folder' and name = 'RMF_CloudSync' and trashed = false"
    $listUri = "https://www.googleapis.com/drive/v3/files?q=" + [System.Uri]::EscapeDataString($folderQuery) + "&fields=files(id,name)"
    $folderList = Invoke-RestMethod -Method Get -Uri $listUri -Headers $headers

    $folderId = ""
    if ($folderList.files -and $folderList.files.Count -gt 0) {
        $folderId = $folderList.files[0].id
        Write-Host "  ✓ 发现已有同步文件夹: $folderId" -ForegroundColor Green
    } else {
        Write-Host "  + 创建新同步文件夹 RMF_CloudSync ..." -ForegroundColor Yellow
        $createFolderBody = @{
            name     = "RMF_CloudSync"
            mimeType = "application/vnd.google-apps.folder"
        } | ConvertTo-Json
        $createFolderRes = Invoke-RestMethod -Method Post -Uri "https://www.googleapis.com/drive/v3/files" -Headers $headers -ContentType "application/json" -Body $createFolderBody
        $folderId = $createFolderRes.id
        Write-Host "  ✓ 同步文件夹创建成功: $folderId" -ForegroundColor Green
    }

    function Upload-DriveFile {
        param (
            [string]$FilePath,
            [string]$MimeType,
            [string]$TargetName
        )
        if (![string]::IsNullOrWhiteSpace($FilePath) -and (Test-Path $FilePath)) {
            Write-Host "  -> 正在上传 $TargetName ..." -ForegroundColor Cyan
            # 检查同名文件
            $checkQ = "'$folderId' in parents and name = '$TargetName' and trashed = false"
            $checkUri = "https://www.googleapis.com/drive/v3/files?q=" + [System.Uri]::EscapeDataString($checkQ) + "&fields=files(id,name)"
            $existing = (Invoke-RestMethod -Method Get -Uri $checkUri -Headers $headers).files

            $bytes = [System.IO.File]::ReadAllBytes($FilePath)

            if ($existing -and $existing.Count -gt 0) {
                $targetId = $existing[0].id
                $upUri = "https://www.googleapis.com/upload/drive/v3/files/$targetId`?uploadType=media"
                Invoke-RestMethod -Method Patch -Uri $upUri -Headers $headers -ContentType $MimeType -Body $bytes | Out-Null
                Write-Host "     ✓ 成功覆盖云端文件 (ID: $targetId)" -ForegroundColor Green
                return $targetId
            } else {
                $boundary = [System.Guid]::NewGuid().ToString()
                $metadata = @{
                    name    = $TargetName
                    parents = @($folderId)
                } | ConvertTo-Json

                # Simple Direct Upload via multipart
                $uploadUri = "https://www.googleapis.com/upload/drive/v3/files?uploadType=media"
                $createMetaUri = "https://www.googleapis.com/drive/v3/files"
                $metaPayload = @{
                    name     = $TargetName
                    parents  = @($folderId)
                    mimeType = $MimeType
                } | ConvertTo-Json
                $created = Invoke-RestMethod -Method Post -Uri $createMetaUri -Headers $headers -ContentType "application/json" -Body $metaPayload
                $targetId = $created.id

                $upUri = "https://www.googleapis.com/upload/drive/v3/files/$targetId`?uploadType=media"
                Invoke-RestMethod -Method Patch -Uri $upUri -Headers $headers -ContentType $MimeType -Body $bytes | Out-Null
                Write-Host "     ✓ 成功新建云端文件 (ID: $targetId)" -ForegroundColor Green
                return $targetId
            }
        }
        return ""
    }

    $phoneFileId = ""
    $padFileId = ""
    $winInstFileId = ""
    $winZipFileId = ""

    if ($PhoneApkPath -and (Test-Path $PhoneApkPath)) {
        $phoneFileId = Upload-DriveFile -FilePath $PhoneApkPath -MimeType "application/vnd.android.package-archive" -TargetName "RMF-Phone-v$VersionName.apk"
    }
    if ($PadApkPath -and (Test-Path $PadApkPath)) {
        $padFileId = Upload-DriveFile -FilePath $PadApkPath -MimeType "application/vnd.android.package-archive" -TargetName "RMF-Pad-v$VersionName.apk"
    }
    if ($WindowsInstallerPath -and (Test-Path $WindowsInstallerPath)) {
        $winInstFileId = Upload-DriveFile -FilePath $WindowsInstallerPath -MimeType "application/octet-stream" -TargetName "RMF-Windows-v$VersionName-Setup.exe"
    }
    if ($WindowsZipPath -and (Test-Path $WindowsZipPath)) {
        $winZipFileId = Upload-DriveFile -FilePath $WindowsZipPath -MimeType "application/zip" -TargetName "RMF-Windows-v$VersionName-win64.zip"
    }

    # 5. 上传/更新 rmf_release_info.json
    Write-Host "`n[4/4] 生成并发布 rmf_release_info.json 版本清单..." -ForegroundColor Yellow
    $nowStr = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $manifestObj = [ordered]@{
        versionCode            = $VersionCode
        versionName            = $VersionName
        releaseDate            = $nowStr
        changelog              = $Changelog
        minSupportedVersion    = 0
        phoneApkFileId         = $phoneFileId
        phoneApkName           = "RMF-Phone-v$VersionName.apk"
        phoneApkSize           = if ($PhoneApkPath -and (Test-Path $PhoneApkPath)) { (Get-Item $PhoneApkPath).Length } else { 0 }
        padApkFileId           = $padFileId
        padApkName             = "RMF-Pad-v$VersionName.apk"
        padApkSize             = if ($PadApkPath -and (Test-Path $PadApkPath)) { (Get-Item $PadApkPath).Length } else { 0 }
        windowsInstallerFileId = $winInstFileId
        windowsInstallerName   = "RMF-Windows-v$VersionName-Setup.exe"
        windowsInstallerSize   = if ($WindowsInstallerPath -and (Test-Path $WindowsInstallerPath)) { (Get-Item $WindowsInstallerPath).Length } else { 0 }
        windowsZipFileId       = $winZipFileId
        windowsZipName         = "RMF-Windows-v$VersionName-win64.zip"
        windowsZipSize         = if ($WindowsZipPath -and (Test-Path $WindowsZipPath)) { (Get-Item $WindowsZipPath).Length } else { 0 }
    }

    $manifestJson = $manifestObj | ConvertTo-Json -Depth 5
    $manifestTemp = Join-Path $env:TEMP "rmf_release_info.json"
    [System.IO.File]::WriteAllText($manifestTemp, $manifestJson, [System.Text.Encoding]::UTF8)

    $manifestFileId = Upload-DriveFile -FilePath $manifestTemp -MimeType "application/json" -TargetName "rmf_release_info.json"
    Remove-Item $manifestTemp -Force -ErrorAction SilentlyContinue

    Write-Host "`n============================================================" -ForegroundColor Green
    Write-Host " 🎉 发布成功！全平台构建与版本清单已部署到 Google Drive！" -ForegroundColor Green
    Write-Host "    版本: v$VersionName (Code: $VersionCode)" -ForegroundColor Cyan
    Write-Host "    清单 ID: $manifestFileId" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Green
} else {
    Write-Host "`n[提示] 当前未提供 RefreshToken，已生成本地版本描述文件模板。" -ForegroundColor Yellow
    $localManifest = [ordered]@{
        versionCode            = $VersionCode
        versionName            = $VersionName
        releaseDate            = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        changelog              = $Changelog
        phoneApkFileId         = ""
        phoneApkName           = "RMF-Phone-v$VersionName.apk"
        phoneApkSize           = if ($PhoneApkPath -and (Test-Path $PhoneApkPath)) { (Get-Item $PhoneApkPath).Length } else { 0 }
        padApkFileId           = ""
        padApkName             = "RMF-Pad-v$VersionName.apk"
        padApkSize             = if ($PadApkPath -and (Test-Path $PadApkPath)) { (Get-Item $PadApkPath).Length } else { 0 }
        windowsInstallerFileId = ""
        windowsInstallerName   = "RMF-Windows-v$VersionName-Setup.exe"
        windowsInstallerSize   = if ($WindowsInstallerPath -and (Test-Path $WindowsInstallerPath)) { (Get-Item $WindowsInstallerPath).Length } else { 0 }
        windowsZipFileId       = ""
        windowsZipName         = "RMF-Windows-v$VersionName-win64.zip"
        windowsZipSize         = if ($WindowsZipPath -and (Test-Path $WindowsZipPath)) { (Get-Item $WindowsZipPath).Length } else { 0 }
    } | ConvertTo-Json -Depth 5
    $outPath = Join-Path $rootDir "rmf_release_info.json"
    [System.IO.File]::WriteAllText($outPath, $localManifest, [System.Text.Encoding]::UTF8)
    Write-Host "本地清单模板已保存至: $outPath" -ForegroundColor Green
}
