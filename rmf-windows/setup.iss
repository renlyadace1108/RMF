; Inno Setup Script for RMF (Resonance Moment Flow)
; Standard Windows installer with customizable installation directory, shortcuts, and full uninstaller.

#define MyAppName "RMF"
#define MyAppFullName "RMF 时间与精力节律管理系统"
#define MyAppVersion "1.4.3.3"
#define MyAppPublisher "Renly"
#define MyAppExeName "RMF.Windows.exe"

[Setup]
AppId={{D8C8B1E9-4E65-4235-90FE-8B41E1C93F12}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppFullName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=d:\RMF\rmf-windows\installer_output
OutputBaseFilename=RMF_Setup_v{#MyAppVersion}
SetupIconFile=d:\RMF\rmf-windows\RMF.Windows\app.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
DisableDirPage=no
DisableProgramGroupPage=no
UninstallDisplayIcon={app}\{#MyAppExeName}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
UsePreviousAppDir=yes
CloseApplications=yes
RestartApplications=no
AppMutex=RMF_Desktop_SingleInstance_Mutex

[Languages]
Name: "chinesesimp"; MessagesFile: "d:\RMF\rmf-windows\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startupicon"; Description: "开机自动启动 RMF"; GroupDescription: "系统集成选项:"; Flags: unchecked

[Files]
Source: "d:\RMF\rmf-windows\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "d:\RMF\rmf-windows\RMF.Windows\app.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "d:\RMF\rmf-windows\RMF.Windows\app_logo_256.png"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppFullName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\app.ico"; AppUserModelID: "Renly.RMF.Desktop"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppFullName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\app.ico"; Tasks: desktopicon; AppUserModelID: "Renly.RMF.Desktop"
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon; AppUserModelID: "Renly.RMF.Desktop"

[Registry]
Root: HKCU; Subkey: "Software\Classes\AppUserModelId\Renly.RMF.Desktop"; ValueType: string; ValueName: "DisplayName"; ValueData: "RMF"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\AppUserModelId\Renly.RMF.Desktop"; ValueType: string; ValueName: "IconUri"; ValueData: "{app}\app.ico"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\AppUserModelId\Renly.RMF.Desktop"; ValueType: dword; ValueName: "ShowInSettings"; ValueData: "1"; Flags: uninsdeletekey

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppFullName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\logs"
Type: filesandordirs; Name: "{app}\cache"

[Code]
// 检测是否存在已安装的 RMF，如果存在则默认返回原安装目录并保留用户所有数据库/配置数据
function GetExistingInstallDir(): String;
var
  InstalledPath: String;
  UninstallKey: String;
begin
  Result := '';
  UninstallKey := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#SetupSetting("AppId")}_is1';

  // 1. 优先从当前用户 (HKCU) 卸载注册表中读取
  if RegQueryStringValue(HKCU, UninstallKey, 'Inno Setup: App Path', InstalledPath) then
  begin
    if (InstalledPath <> '') and DirExists(InstalledPath) then
    begin
      Result := InstalledPath;
      Exit;
    end;
  end;

  if RegQueryStringValue(HKCU, UninstallKey, 'InstallLocation', InstalledPath) then
  begin
    if (InstalledPath <> '') and DirExists(InstalledPath) then
    begin
      Result := InstalledPath;
      Exit;
    end;
  end;

  // 2. 尝试从本地机器 (HKLM 64-bit / 32-bit) 读取
  if RegQueryStringValue(HKLM, UninstallKey, 'Inno Setup: App Path', InstalledPath) then
  begin
    if (InstalledPath <> '') and DirExists(InstalledPath) then
    begin
      Result := InstalledPath;
      Exit;
    end;
  end;

  if RegQueryStringValue(HKLM, UninstallKey, 'InstallLocation', InstalledPath) then
  begin
    if (InstalledPath <> '') and DirExists(InstalledPath) then
    begin
      Result := InstalledPath;
      Exit;
    end;
  end;
end;

// 初始化向导时，如果检测到已安装路径，则自动将安装目标页面定位到原有路径
procedure InitializeWizard();
var
  PrevDir: String;
begin
  PrevDir := GetExistingInstallDir();
  if PrevDir <> '' then
  begin
    WizardForm.DirEdit.Text := PrevDir;
  end;
end;
