; Build with scripts/build-setup.ps1. Keep AppId stable across releases.
#ifndef AppVersion
  #define AppVersion "1.0.1"
#endif
#ifndef AppSource
  #define AppSource "..\build\compose\binaries\main\app\CineK"
#endif
#define AppName "CineK"
#define AppExeName "CineK.exe"

[Setup]
AppId={{733B9CED-6AE5-41DD-9A0D-AF74FE07A4E9}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=CineK
DefaultDirName={autopf}\CineK
DefaultGroupName=CineK
DisableProgramGroupPage=yes
AllowNoIcons=yes
OutputDir=..\out\setup
OutputBaseFilename=CineK-{#AppVersion}-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
SetupIconFile=..\icon.ico
UninstallDisplayIcon={app}\CineK.ico
LicenseFile=..\LICENSE.md
ChangesAssociations=yes
CloseApplications=yes
CloseApplicationsFilter=CineK.exe
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "fileassoc"; Description: "Register CineK for video and audio files"; GroupDescription: "File associations:"; Flags: checkedonce

[Files]
Source: "{#AppSource}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\LICENSE.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\CineK"; Filename: "{app}\CineK.exe"; IconFilename: "{app}\CineK.ico"; WorkingDir: "{app}"
Name: "{autodesktop}\CineK"; Filename: "{app}\CineK.exe"; IconFilename: "{app}\CineK.ico"; WorkingDir: "{app}"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\CineK"; Flags: uninsdeletekeyifempty; Tasks: fileassoc
; HKA selects HKLM for all-users installs and HKCU for current-user installs.
; Own only CineK registrations; never overwrite another player's extension defaults.
Root: HKA; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\CineK.exe"; ValueType: string; ValueName: ""; ValueData: "{app}\CineK.exe"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\CineK\Capabilities"; ValueType: string; ValueName: "ApplicationName"; ValueData: "CineK"; Flags: uninsdeletekey; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities"; ValueType: string; ValueName: "ApplicationDescription"; ValueData: "CineK video and audio player"; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities"; ValueType: string; ValueName: "ApplicationIcon"; ValueData: """{app}\CineK.ico"",0"; Tasks: fileassoc
Root: HKA; Subkey: "Software\RegisteredApplications"; ValueType: string; ValueName: "CineK"; ValueData: "Software\CineK\Capabilities"; Flags: uninsdeletevalue; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe"; ValueType: string; ValueName: "FriendlyAppName"; ValueData: "CineK"; Flags: uninsdeletekey; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.ico"",0"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.exe"" ""%1"""; Tasks: fileassoc

; Video file type and playback verbs.
Root: HKA; Subkey: "Software\Classes\CineK.Video"; ValueType: string; ValueName: ""; ValueData: "CineK Video"; Flags: uninsdeletekey; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Video"; ValueType: string; ValueName: "FriendlyTypeName"; ValueData: "CineK Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Video"; ValueType: string; ValueName: "PerceivedType"; ValueData: "video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Video\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.ico"",0"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Video\shell\open"; ValueType: string; ValueName: "FriendlyAppName"; ValueData: "CineK"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Video\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.exe"" ""%1"""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Video\shell\play\command"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.exe"" ""%1"""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mp4\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mp4"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mp4"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.avi\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".avi"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".avi"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mkv\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mkv"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mkv"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mov\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mov"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mov"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.wmv\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".wmv"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".wmv"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.flv\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".flv"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".flv"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.webm\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".webm"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".webm"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.m4v\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".m4v"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".m4v"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mpeg\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mpeg"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mpeg"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mpg\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mpg"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mpg"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.3gp\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".3gp"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".3gp"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.ts\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".ts"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".ts"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.m2ts\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".m2ts"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".m2ts"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mts\OpenWithProgids"; ValueType: string; ValueName: "CineK.Video"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mts"; ValueData: "CineK.Video"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mts"; ValueData: ""; Tasks: fileassoc

; Audio file type and playback verbs.
Root: HKA; Subkey: "Software\Classes\CineK.Audio"; ValueType: string; ValueName: ""; ValueData: "CineK Audio"; Flags: uninsdeletekey; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Audio"; ValueType: string; ValueName: "FriendlyTypeName"; ValueData: "CineK Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Audio"; ValueType: string; ValueName: "PerceivedType"; ValueData: "audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Audio\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.ico"",0"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Audio\shell\open"; ValueType: string; ValueName: "FriendlyAppName"; ValueData: "CineK"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Audio\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.exe"" ""%1"""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\CineK.Audio\shell\play\command"; ValueType: string; ValueName: ""; ValueData: """{app}\CineK.exe"" ""%1"""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.mp3\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mp3"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".mp3"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.aac\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".aac"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".aac"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.flac\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".flac"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".flac"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.m4a\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".m4a"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".m4a"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.ogg\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".ogg"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".ogg"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.opus\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".opus"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".opus"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.wav\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".wav"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".wav"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.wma\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".wma"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".wma"; ValueData: ""; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\.aiff\OpenWithProgids"; ValueType: string; ValueName: "CineK.Audio"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: fileassoc
Root: HKA; Subkey: "Software\CineK\Capabilities\FileAssociations"; ValueType: string; ValueName: ".aiff"; ValueData: "CineK.Audio"; Tasks: fileassoc
Root: HKA; Subkey: "Software\Classes\Applications\CineK.exe\SupportedTypes"; ValueType: string; ValueName: ".aiff"; ValueData: ""; Tasks: fileassoc

[Run]
Filename: "{app}\CineK.exe"; Description: "Launch CineK"; Flags: nowait postinstall skipifsilent
Filename: "{code:DefaultAppsUri}"; Description: "Choose CineK's default media file types in Windows Settings"; Flags: shellexec nowait postinstall skipifsilent unchecked; Tasks: fileassoc

[Code]
function DefaultAppsUri(Param: String): String;
begin
  if IsAdminInstallMode then
    Result := 'ms-settings:defaultapps?registeredAppMachine=CineK'
  else
    Result := 'ms-settings:defaultapps?registeredAppUser=CineK';
end;
