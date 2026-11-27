; Instalador de MyMouse para Windows (docs/spec-v5-bluetooth-completo.md §5). Se compila con Inno Setup 6 (ISCC.exe) en CI.
;
; Se instala en %LOCALAPPDATA%\Programs\MyMouse, sin permisos de administrador. Lleva el exe de siempre (un solo fichero),
; adb (adb.exe, AdbWinApi.dll y AdbWinUsbApi.dll de las platform-tools de Google, con su licencia) y, opcionalmente, el TestClient.
; El host busca adb junto a su exe antes que en el PATH.
;
; Parámetros: se pueden pasar con /D (ISCC /DAppVersion=... ) o con variables de entorno, que es lo que hace CI porque no obliga a
; entrecomillar valores con puntos, guiones o barras:
;   AppVersion / MYMOUSE_VERSION        versión (p. ej. 5.0 o 0.0-dev.42)
;   SourceDir  / MYMOUSE_SOURCE_DIR     carpeta con MyMouse.exe y MyMouse.TestClient.exe publicados
;   AdbDir     / MYMOUSE_ADB_DIR        carpeta con adb.exe, AdbWinApi.dll, AdbWinUsbApi.dll y NOTICE.txt
;   OutputDir  / MYMOUSE_OUTPUT_DIR     dónde deja MyMouse-Setup-<versión>.exe

#ifndef AppVersion
  #define AppVersion GetEnv("MYMOUSE_VERSION")
  #if AppVersion == ""
    #undef AppVersion
    #define AppVersion "0.0-dev"
  #endif
#endif
#ifndef SourceDir
  #define SourceDir GetEnv("MYMOUSE_SOURCE_DIR")
  #if SourceDir == ""
    #undef SourceDir
    #define SourceDir "..\out\MyMouse"
  #endif
#endif
#ifndef AdbDir
  #define AdbDir GetEnv("MYMOUSE_ADB_DIR")
  #if AdbDir == ""
    #undef AdbDir
    #define AdbDir "..\out\adb"
  #endif
#endif
#ifndef OutputDir
  #define OutputDir GetEnv("MYMOUSE_OUTPUT_DIR")
  #if OutputDir == ""
    #undef OutputDir
    #define OutputDir "..\out\installer"
  #endif
#endif

[Setup]
; No cambiar nunca: identifica la app para actualizar y desinstalar. (La llave doble es la forma de escribir una { literal.)
AppId={{779D29A3-9DE7-4C7F-B206-F386F45F1C5F}
AppName=MyMouse
AppVersion={#AppVersion}
AppPublisher=MyMouse
DefaultDirName={localappdata}\Programs\MyMouse
DefaultGroupName=MyMouse
DisableProgramGroupPage=yes
; Sin administrador: todo va a la carpeta del usuario y a HKCU.
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir={#OutputDir}
OutputBaseFilename=MyMouse-Setup-{#AppVersion}
SetupIconFile=..\src\MyMouse.Host\Assets\MyMouse.ico
UninstallDisplayIcon={app}\MyMouse.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
; Si MyMouse está en marcha (vive en la bandeja), se espera a que se cierre antes de sustituir el exe.
AppMutex=MyMouse.Host
CloseApplications=yes
; Actualización en silencio (v5.1): el propio MyMouse lanza "MyMouse-Setup-<v>.exe /SILENT /CLOSEAPPLICATIONS /RELAUNCH=1" y se cierra.
; No se confía en RestartApplications: el Restart Manager de Windows solo reinicia las apps que se registraron con
; RegisterApplicationRestart, y MyMouse no lo hace. El relanzado lo hace la entrada [Run] de abajo, que solo actúa con /RELAUNCH=1.
RestartApplications=no

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Types]
Name: "normal"; Description: "Instalación normal"
Name: "custom"; Description: "Personalizada"; Flags: iscustom

[Components]
Name: "main"; Description: "MyMouse (con adb para el cable USB)"; Types: normal custom; Flags: fixed
Name: "testclient"; Description: "Cliente de pruebas (solo para desarrolladores)"; Types: custom

[Tasks]
Name: "desktopicon"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: unchecked
Name: "autostart"; Description: "Iniciar MyMouse con Windows"; GroupDescription: "Opciones:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\MyMouse.exe"; DestDir: "{app}"; Components: main; Flags: ignoreversion
Source: "{#SourceDir}\MyMouse.TestClient.exe"; DestDir: "{app}"; Components: testclient; Flags: ignoreversion
; adb junto al exe: el host lo busca primero ahí. Licencia de las platform-tools incluida.
Source: "{#AdbDir}\adb.exe"; DestDir: "{app}"; Components: main; Flags: ignoreversion
Source: "{#AdbDir}\AdbWinApi.dll"; DestDir: "{app}"; Components: main; Flags: ignoreversion
Source: "{#AdbDir}\AdbWinUsbApi.dll"; DestDir: "{app}"; Components: main; Flags: ignoreversion
Source: "{#AdbDir}\NOTICE.txt"; DestDir: "{app}"; DestName: "adb-NOTICE.txt"; Components: main; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\MyMouse"; Filename: "{app}\MyMouse.exe"
Name: "{autodesktop}\MyMouse"; Filename: "{app}\MyMouse.exe"; Tasks: desktopicon

[Registry]
; "Iniciar con Windows": el mismo valor Run de HKCU que escribe el icono de la bandeja (Autostart.cs): nombre MyMouse y la ruta del exe entre comillas.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "MyMouse"; ValueData: """{app}\MyMouse.exe"""; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{app}\MyMouse.exe"; Description: "Abrir MyMouse"; Flags: nowait postinstall skipifsilent
; Tras una actualización en silencio (/RELAUNCH=1) MyMouse vuelve a abrirse solo. Si ya hubiera una instancia, el mutex de MyMouse la deja en nada.
Filename: "{app}\MyMouse.exe"; Flags: nowait skipifnotsilent; Check: RelaunchRequested

[Code]
// "/RELAUNCH=1" lo pasa la actualización automática de MyMouse (docs/spec-v5.1-actualizaciones.md §4).
function RelaunchRequested: Boolean;
begin
  Result := ExpandConstant('{param:RELAUNCH|0}') = '1';
end;

// Desinstalar quita también el valor Run aunque se activara después desde la bandeja (no lo creó el instalador).
// %APPDATA%\MyMouse (la configuración: gestures.json, formas, config.json) se conserva a propósito.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    RegDeleteValue(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Run', 'MyMouse');
end;
