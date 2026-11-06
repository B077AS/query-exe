; Inno Setup script for the QueryExe installer.
; Compiled by the launcher's `installer` Maven profile, which first builds the
; jpackage app-image (launcher exe + private runtime + bundled client seed) and
; then passes these defines:
;   /DAppVersion=...    client version bundled as the seed (drives the setup filename)
;   /DAppImageDir=...   the jpackage app-image directory to package
;   /DOutputDir=...     where to write the setup exe

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef AppImageDir
  #define AppImageDir "..\..\target\jpackage\QueryExe"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\target\installer"
#endif

[Setup]
; Never change AppId: it is how upgrades find and replace an existing install.
AppId={{8C3F5E2A-4B7D-4E11-9A6C-2F1D8E5B9C3A}
AppName=QueryExe
AppVersion={#AppVersion}
AppVerName=QueryExe {#AppVersion}
AppPublisher=QueryExe
VersionInfoVersion={#AppVersion}
; Per-user install: no admin prompt.
PrivilegesRequired=lowest
DefaultDirName={autopf}\QueryExe
DefaultGroupName=QueryExe
; Fresh installs may choose the folder; upgrades silently reuse the existing one.
DisableDirPage=auto
DisableProgramGroupPage=yes
DisableWelcomePage=no
WizardStyle=modern
WizardImageFile=wizard-large.bmp
WizardSmallImageFile=wizard-small.bmp
SetupIconFile=query-exe.ico
UninstallDisplayIcon={app}\QueryExe.exe
UninstallDisplayName=QueryExe
OutputDir={#OutputDir}
OutputBaseFilename=QueryExe-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
CloseApplications=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; Always drop the installed client jar so every (re)install starts from this
; installer's bundled seed — BundledClientSeeder re-copies it on first launch,
; and the GitHub update flow takes over from there. Doubling as a repair path:
; reinstalling fixes a corrupt jar.
Type: files; Name: "{userappdata}\QueryExe\bin\query-exe.jar"
Type: files; Name: "{userappdata}\QueryExe\bin\query-exe.jar.download"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\QueryExe"; Filename: "{app}\QueryExe.exe"
Name: "{autodesktop}\QueryExe"; Filename: "{app}\QueryExe.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\QueryExe.exe"; Description: "{cm:LaunchProgram,QueryExe}"; Flags: nowait postinstall skipifsilent
