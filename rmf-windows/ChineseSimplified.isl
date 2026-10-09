; *** Inno Setup version 6.1.0+ Simplified Chinese messages ***
;

[Messages]

; *** Application titles
SetupAppTitle=安装
SetupWindowTitle=安装 - %1
UninstallAppTitle=卸载
UninstallWindowTitle=卸载 - %1

; *** Misc. common external strings
ExitSetupTitle=退出安装
ExitSetupMessage=安装未完成。如果您现在退出，程序将不会被安装。%n%n您可以稍后再次运行安装程序以完成安装。%n%n退出安装程序吗？
AboutSetupMenuItem=关于安装程序(&A)...
AboutSetupTitle=关于安装程序
AboutSetupMessage=%1 版本 %2%n%3%n%n%1 网址:%n%4
AboutSetupNote=
TranslatorName=
TranslatorURL=

; *** Buttons
ButtonBack=< 上一步(&B)
ButtonNext=下一步(&N) >
ButtonInstall=安装(&I)
ButtonOK=确定
ButtonCancel=取消
ButtonYes=是(&Y)
ButtonYesToAll=全部选是(&A)
ButtonNo=否(&N)
ButtonNoToAll=全部选否(&O)
ButtonFinish=完成(&F)
ButtonBrowse=浏览(&B)...
ButtonWizardBrowse=浏览(&R)...
ButtonNewFolder=新建文件夹(&M)

; *** "Select Language" dialog messages
SelectLanguageTitle=选择安装语言
SelectLanguageLabel=选择安装期间要使用的语言。

; *** Common wizard text
ClickNext=单击“下一步”继续，或单击“取消”退出安装程序。
BeveledLabel=
BrowseDialogTitle=浏览文件夹
BrowseDialogLabel=从下面的列表中选择一个文件夹，然后单击“确定”。
NewFolderName=新建文件夹

; *** "Welcome" wizard page
WelcomeLabel1=欢迎使用 [name] 安装向导
WelcomeLabel2=这将在您的计算机上安装 [name/ver]。%n%n在继续之前，建议关闭所有其他应用程序。

; *** "Password" wizard page
WizardPassword=密码
PasswordLabel1=此安装程序受密码保护。
PasswordLabel3=请输入密码，然后单击“下一步”继续。密码区分大小写。
PasswordEditLabel=密码(&P):
IncorrectPassword=您输入的密码不正确，请重试。

; *** "License Agreement" wizard page
WizardLicense=许可协议
LicenseLabel=在继续之前，请阅读以下重要信息。
LicenseLabel3=请阅读以下许可协议。在继续安装之前，您必须接受此协议的条款。
LicenseAccepted=我接受协议(&A)
LicenseNotAccepted=我拒绝协议(&D)

; *** "Information" wizard pages
WizardInfoBefore=信息
InfoBeforeLabel=在继续之前，请阅读以下重要信息。
InfoBeforeClickLabel=准备好继续安装后，单击“下一步”。
WizardInfoAfter=信息
InfoAfterLabel=在继续之前，请阅读以下重要信息。
InfoAfterClickLabel=准备好继续安装后，单击“下一步”。

; *** "User Information" wizard page
WizardUserInfo=用户信息
UserInfoDesc=请输入您的信息。
UserInfoName=用户名(&U):
UserInfoOrg=组织(&O):
UserInfoSerial=序列号(&S):
UserInfoNameRequired=您必须输入名称。

; *** "Select Destination Location" wizard page
WizardSelectDir=选择安装目标位置
SelectDirDesc=应将 [name] 安装在何处？
SelectDirLabel3=安装程序将把 [name] 安装到以下文件夹中。
SelectDirBrowseLabel=要继续，请单击“下一步”。如果您想选择其他文件夹，请单击“浏览”。
DiskSpaceGBLabel=至少需要 [gb] GB 的可用磁盘空间。
DiskSpaceMBLabel=至少需要 [mb] MB 的可用磁盘空间。
CannotInstallToRiskDir=安装程序无法安装到以下文件夹，因为它是系统管理的重要位置：%n%n%1%n%n请选择其他位置。
CannotInstallToNetworkDrive=安装程序无法安装到网络驱动器。
CannotInstallToUNCPath=安装程序无法安装到 UNC 路径。
InvalidDirectory=您必须输入包含驱动器号的完整路径；例如：%n%nC:\APP%n%n或者以下形式的 UNC 路径：%n%n\\server\share
DirectoryExists=文件夹：%n%n%1%n%n已存在。仍然安装到该文件夹中吗？
DirectoryDoesntExist=文件夹：%n%n%1%n%n不存在。您想要创建此文件夹吗？
DiskSpaceWarningTitle=磁盘空间不足
DiskSpaceWarning=安装程序至少需要 %1 KB 的可用空间才能安装，但所选驱动器只有 %2 KB 可用。%n%n您仍然想要继续吗？
DirNameTooLong=文件夹名称或路径太长。
InvalidPath=路径无效。
InvalidDrive=驱动器无效。
DiskSpaceOverrideTitle=空间覆盖

; *** "Select Components" wizard page
WizardSelectComponents=选择组件
SelectComponentsDesc=应安装哪些组件？
SelectComponentsLabel2=选择您要安装的组件；取消选中您不想安装的组件。准备好后单击“下一步”。
FullInstallation=完全安装
CompactInstallation=精简安装
CustomInstallation=自定义安装
NoUninstallWarningTitle=组件已存在
NoUninstallWarning=安装程序检测到以下组件已安装在您的计算机上：%n%n%1%n%n取消选择这些组件将不会卸载它们。%n%n您仍然要继续吗？
ComponentSize1=%1 KB
ComponentSize2=%1 MB

; *** "Select Additional Tasks" wizard page
WizardSelectTasks=选择附加任务
SelectTasksDesc=应执行哪些附加任务？
SelectTasksLabel2=选择在安装 [name] 时要安装程序执行的附加任务，然后单击“下一步”。

; *** "Select Start Menu Folder" wizard page
WizardSelectProgramGroup=选择开始菜单文件夹
SelectStartMenuFolderDesc=安装程序应该在哪里放置程序的快捷方式？
SelectStartMenuFolderLabel3=安装程序将在以下开始菜单文件夹中创建程序快捷方式。
SelectStartMenuFolderBrowseLabel=要继续，请单击“下一步”。如果您想选择其他文件夹，请单击“浏览”。
NoIconsCheck=不创建开始菜单文件夹(&D)

; *** "Ready to Install" wizard page
WizardReady=准备安装
ReadyLabel1=安装程序现在准备好在您的计算机上开始安装 [name]。
ReadyLabel2a=单击“安装”继续安装，或者如果您想要查看或更改任何设置，请单击“上一步”。
ReadyLabel2b=单击“安装”继续安装。
ReadyMemoUserInfo=用户信息:
ReadyMemoDir=目标位置:
ReadyMemoType=安装类型:
ReadyMemoComponents=选定的组件:
ReadyMemoGroup=开始菜单文件夹:
ReadyMemoTasks=附加任务:

; *** TDownloadWizardPage message strings
DownloadingLabel=正在下载其他文件...
ButtonStopDownload=停止下载(&S)
StopDownload=您确定要停止下载吗？
ErrorDownloadAborted=下载已中止。
ErrorDownloadFailed=下载失败: %1 %2
ErrorDownloadSizeFailed=获取下载大小失败: %1 %2
ErrorFileHash1=文件哈希不匹配: %1
ErrorFileHash2=无效的文件哈希。预期: %1，实际: %2
ErrorProgress=进度无效: %1 / %2
ErrorFileSize=文件大小无效。预期: %1，实际: %2

; *** "Preparing to Install" wizard page
WizardPreparing=正在准备安装
PreparingDesc=安装程序正在准备在您的计算机上安装 [name]。
PreviousInstallNotCompleted=以前的安装/卸载未完成。您需要重新启动计算机以完成该安装。%n%n重新启动计算机后，再次运行安装程序以完成 [name] 的安装。
CannotContinue=安装程序无法继续。请单击“取消”退出。
ApplicationsFound=以下应用程序正在使用安装程序需要更新的文件。建议您允许安装程序自动关闭这些应用程序。
ApplicationsFound2=以下应用程序正在使用安装程序需要更新的文件。建议您允许安装程序自动关闭这些应用程序。安装完成后，安装程序将尝试重新启动这些应用程序。
CloseApplications=自动关闭应用程序(&A)
DontCloseApplications=不关闭应用程序(&D)
ErrorCloseApplications=安装程序无法自动关闭所有应用程序。建议您在继续之前关闭使用需要由安装程序更新的文件的所有应用程序。
PrepareToInstallNeedsRestart=安装程序必须重新启动您的计算机。重新启动计算机后，再次运行安装程序以完成 [name] 的安装。%n%n您想现在重新启动吗？

; *** "Installing" wizard page
WizardInstalling=正在安装
InstallingLabel=安装程序正在将 [name] 安装到您的计算机中，请稍候。

; *** "Setup Completed" wizard page
FinishedHeadingLabel=[name] 安装向导完成
FinishedLabelNoIcons=安装程序已在您的计算机上完成 [name] 的安装。
FinishedLabel=安装程序已在您的计算机上完成 [name] 的安装。可以通过选择安装的快捷方式来启动该应用程序。
ClickFinish=单击“完成”退出安装程序。
FinishedRestartLabel=为了完成 [name] 的安装，安装程序必须重新启动您的计算机。您想现在重新启动吗？
FinishedRestartMessage=为了完成 [name] 的安装，安装程序必须重新启动您的计算机。%n%n您想现在重新启动吗？
ShowReadmeCheck=是的，我想查看 README 文件
YesRadio=是，立即重新启动计算机(&Y)
NoRadio=否，我稍后重新启动计算机(&N)

; *** "Setup Aborted" wizard page
ModifyPathWarningTitle=修改 PATH 警告
ModifyPathWarning=无法修改系统路径。

; *** Installation status messages
StatusClosingApplications=正在关闭应用程序...
StatusCreateDirs=正在创建目录...
StatusExtractFiles=正在解压缩文件...
StatusDownloadFiles=正在下载文件...
StatusCreateIcons=正在创建快捷方式...
StatusCreateIniEntries=正在创建 INI 条目...
StatusCreateRegistryEntries=正在创建注册表条目...
StatusRegisterFiles=正在注册文件...
StatusSavingUninstall=正在保存卸载信息...
StatusRunProgram=正在完成安装...
StatusRestartingApplications=正在重新启动应用程序...
StatusRollback=正在回滚更改...
StopExtraction=您确定要停止提取吗？

; *** Uninstall messages
UninstallAppFullTitle=%1 卸载
UninstallDisplayNameMark=%1 (%2)
UninstallDisplayNameMarks=%1 (%2, %3)
UninstallDisplayNameMark32Bit=32 位
UninstallDisplayNameMark64Bit=64 位
UninstallDisplayNameMarkAllUsers=所有用户
UninstallDisplayNameMarkCurrentUser=当前用户
UninstalledAll=%1 已成功从您的计算机中删除。
UninstalledMost=%1 卸载完成。%n%n部分元素无法删除，您可以手动将其删除。
UninstalledAndNeedsRestart=为了完成 %1 的卸载，必须重新启动您的计算机。%n%n您想现在重新启动吗？
UninstallAppRunningError=卸载程序检测到 %1 正在运行。%n%n请关闭所有其实例，然后单击“确定”继续。
UninstallNotFound=文件“%1”不存在。无法卸载。
UninstallOnlyOnWin64=此安装只能在 64 位 Windows 上卸载。
UninstallOpenError=无法打开文件“%1”。无法卸载。
UninstallStatusLabel=正在从您的计算机中删除 %1，请稍候。
UninstallUnknownEntry=在卸载日志中遇到未知条目 (%1)。
UninstallUnsupportedVer=卸载日志文件“%1”采用当前卸载程序无法识别的格式。无法卸载。
UninstallDataCorrupted=“%1”文件已损坏。无法卸载。
WizardUninstalling=卸载状态
StatusUninstalling=正在卸载 %1...

; *** Verification & Compatibility
WindowsServicePackRequired=此程序需要 %1 Service Pack %2 或更高版本。
WindowsVersionNotSupported=此程序不支持您计算机正在运行的 Windows 版本。
WinVersionTooHighError=此程序不能安装在 %1 版本 %2 或更高版本上。
WinVersionTooLowError=此程序需要 %1 版本 %2 或更高版本。
VerificationFileHashIncorrect=文件的哈希值不正确
VerificationFileNameIncorrect=文件名不正确
VerificationFileSizeIncorrect=文件大小不正确
VerificationFileTagIncorrect=文件标记不正确
VerificationKeyNotFound=签名文件“%1”使用了未知密钥
VerificationSignatureDoesntExist=签名文件“%1”不存在
VerificationSignatureInvalid=签名文件“%1”无效

; *** "Select Language" dialog messages
SelectLanguageLabel=选择安装期间要使用的语言:

[CustomMessages]
NameAndVersion=%1 版本 %2
AdditionalIcons=其他快捷方式:
CreateDesktopIcon=创建桌面快捷方式(&D)
CreateQuickLaunchIcon=创建快速启动快捷方式(&Q)
ProgramOnTheWeb=%1 官方网站
UninstallProgram=卸载 %1
LaunchProgram=启动 %1
AssocFileExtension=将 %1 与 %2 文件扩展名关联(&A)
AssocingFileExtension=正在将 %1 与 %2 文件扩展名关联...
AutoStartProgramGroupDescription=开机启动:
AutoStartProgram=自动启动 %1
AddonHostProgramNotFound=%1 在您选定的文件夹中找不到。%n%n您是否仍要继续？
