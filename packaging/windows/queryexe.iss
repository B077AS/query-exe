; Inno Setup script for the QueryExe installer (a branded wizard: welcome banner, install folder, desktop icon, start when done).
; Compiled by packaging/package.mjs (`windows-installer`), which first builds the jpackage app-image (QueryExe.exe, the app and its
; private Java runtime) and then passes these defines:
;   /DAppVersion=...     the version shown in the installer and in Apps & features, such as 0.1.0
;   /DFileVersion=...    the same as digits only (1.2.3), for the file properties
;   /DAppImageDir=...    the jpackage app-image folder to package
;   /DOutputDir=...      where to write the setup exe
;   /DOutputName=...     the file name of the setup exe, without .exe
;   /DIconFile=...       QueryExe's .ico

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef FileVersion
  #define FileVersion "0.0.0"
#endif
#ifndef AppImageDir
  #define AppImageDir "..\..\target\jpackage\QueryExe"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\target\dist"
#endif
#ifndef OutputName
  #define OutputName "QueryExe-" + AppVersion + "-windows-x64-setup"
#endif
#ifndef IconFile
  #define IconFile "..\..\src\main\resources\icon.ico"
#endif

[Setup]
; Never change AppId: it is how a newer installer finds an installed QueryExe and upgrades it in place.
AppId={{5D2A7C41-8E3B-4F6A-B9D0-1C7E4A8F2B35}
AppName=QueryExe
AppVersion={#AppVersion}
AppVerName=QueryExe {#AppVersion}
AppPublisher=QueryExe
AppPublisherURL=https://github.com/B077AS/query-exe
AppSupportURL=https://github.com/B077AS/query-exe/issues
AppUpdatesURL=https://github.com/B077AS/query-exe/releases
VersionInfoVersion={#FileVersion}
VersionInfoDescription=QueryExe installer
; For this user only: no administrator prompt, and QueryExe lands in %LOCALAPPDATA%\Programs\QueryExe.
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DefaultDirName={autopf}\QueryExe
DefaultGroupName=QueryExe
; A first install may pick the folder; an upgrade silently reuses the one QueryExe is already in.
DisableDirPage=auto
DisableProgramGroupPage=yes
DisableWelcomePage=no
WizardStyle=modern
WizardImageFile=wizard-large.bmp,wizard-large-150.bmp,wizard-large-200.bmp
WizardSmallImageFile=wizard-small.bmp,wizard-small-150.bmp,wizard-small-200.bmp
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\QueryExe.exe
UninstallDisplayName=QueryExe
OutputDir={#OutputDir}
OutputBaseFilename={#OutputName}
Compression=lzma2
SolidCompression=yes
; QueryExe may be running when a newer one is installed: close it, install, offer to start it again.
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\QueryExe"; Filename: "{app}\QueryExe.exe"
Name: "{autodesktop}\QueryExe"; Filename: "{app}\QueryExe.exe"; Tasks: desktopicon

; Uninstalling removes the program, not your data: saved connections and settings live in %APPDATA%\QueryExe and are left
; alone, and your passwords stay in the Windows credential store.

[Run]
Filename: "{app}\QueryExe.exe"; Description: "{cm:LaunchProgram,QueryExe}"; Flags: nowait postinstall skipifsilent
