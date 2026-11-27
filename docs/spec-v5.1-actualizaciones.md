# MyMouse — v5.1: avisos de nueva versión

> Base: [spec-v5-bluetooth-completo.md](spec-v5-bluetooth-completo.md) (§5 instalador, §8 notas), [spec-v4-toolbox.md](spec-v4-toolbox.md) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)
>
> Estado: **especificada**. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Situación de partida (v5.0)
- **El instalador no busca versiones nuevas.** Para actualizar hay que descargar el `MyMouse-Setup` nuevo y ejecutarlo. Al tener el mismo `AppId`, instala encima, conserva la configuración (`%APPDATA%\MyMouse`) y, si MyMouse está abierto, lo cierra (`CloseApplications`).
- **adb**: el instalador copia **siempre** su propio `adb.exe` en la carpeta de MyMouse y el host usa ese primero. No toca ningún adb que el usuario tenga instalado (Android SDK, PATH) ni lo sustituye; al actualizar, solo se reemplaza el de la carpeta de MyMouse.
- El móvil tampoco avisa de nada.

## 2. Qué se quiere
1. El aviso típico de **"Hay una versión nueva"**, en el PC y en el móvil, con un botón para instalarla.
2. **Avisos cruzados**: si el móvil y el PC están conectados por USB y uno de los dos tiene una versión más antigua que el otro, los dos lo dicen: "El PC tiene la v5.2 y este móvil la v5.0: actualízalo".

## 3. De dónde sale la última versión
- Las releases del repositorio público en GitHub: `GET https://api.github.com/repos/DiegoTorresMijarra/MyMouse/releases?per_page=10`, sin autenticación (límite de 60 peticiones por hora y por IP, de sobra).
- Todas las releases están marcadas como *prerelease*, así que **no** se usa `/releases/latest`: se toma la de **etiqueta más alta** por versión semántica (`v5.0` < `v5.0.1` < `v5.1` < `v10.0`). Se ignoran borradores y etiquetas que no son `vX.Y[.Z]`.
- **Versión local**:
  - Móvil: `versionName`. En CI es la etiqueta de la release (p. ej. `5.0`); en compilaciones de la rama, `0.0-dev.N`.
  - PC: la versión del ensamblado, que CI ya fija igual.
  - Una versión "dev" **no** avisa (es de desarrollo) *(decisión)*.
- **Comparación y elección del recurso** (lógica pura con tests en `core` y en `HostCore`):
  - Comparar versiones.
  - Parsear la respuesta JSON de la API (con un fixture guardado en los tests).
  - Elegir el fichero de la release: el `.apk` para el móvil; `MyMouse-Setup-*.exe` o el zip para el PC.
- **Cuándo se comprueba**:
  - Al arrancar (con 10 s de retraso para no frenar el inicio) y luego cada 12 h mientras está abierto.
  - "Buscar ahora" en Ajustes.
  - Si falla la red, en silencio: se anota en el log y se reintenta en la siguiente comprobación.
- **Ajuste** "Buscar actualizaciones automáticamente" (activado por defecto) en los dos lados. Botón **"Omitir esta versión"**: no vuelve a avisar de esa versión, sí de la siguiente.

## 4. PC
- **Aviso**:
  - En la toolbox, una tarjeta-banner arriba (encima de Opciones), con el acento del tema: "Nueva versión 5.2 disponible", un enlace "Novedades" (abre la página de la release) y los botones **"Actualizar"** y "Omitir".
  - El icono de la bandeja lleva un punto azul pequeño en la esquina contraria al de estado.
  - Un solo globo de la bandeja la primera vez que se detecta cada versión.
- **"Actualizar"**:
  - **Si MyMouse se instaló con el instalador** (el exe está en `%LOCALAPPDATA%\Programs\MyMouse`, o existe la clave de desinstalación del `AppId`):
    1. Descarga `MyMouse-Setup-<v>.exe` a `%TEMP%`, con barra de progreso en el banner.
    2. Comprueba que el tamaño coincide con el de la API.
    3. Lo ejecuta con `/SILENT /CLOSEAPPLICATIONS /RESTARTAPPLICATIONS` y se cierra. El instalador reemplaza los ficheros (también el adb de MyMouse) y vuelve a abrir MyMouse.
    - Las tareas elegidas en la primera instalación (escritorio, iniciar con Windows) se conservan: Inno las recuerda.
  - **Si es la versión portátil (zip)**: abre la página de la release en el navegador. *(decisión: no se sobrescribe un exe portátil en uso)*
- **Ajustes de la toolbox**: "Buscar actualizaciones automáticamente", "Buscar ahora" y la versión instalada.

## 5. Móvil
- **Aviso**:
  - Una píldora en la barra de botones del modo ventana ("Actualizar a 5.2") que abre una hoja con las novedades (el texto de la release, recortado) y los botones "Descargar e instalar" y "Omitir".
  - En Ajustes, una tarjeta "Actualizaciones" con el estado, "Buscar ahora" y el ajuste automático.
  - En modo bloqueado no se muestra nada (no molestar mientras se usa).
- **"Descargar e instalar"**:
  1. Descarga el `.apk` con `DownloadManager` a la carpeta privada de la app, con notificación de progreso del sistema.
  2. Lo abre con el instalador de paquetes mediante `FileProvider` (`content://`) e `Intent.ACTION_VIEW`.
  - Requiere el permiso `REQUEST_INSTALL_PACKAGES`. Si no está concedido, primero se abre la pantalla del sistema "Instalar apps desconocidas" para MyMouse, con una explicación.
  - Las versiones se firman siempre con la misma clave de desarrollo, así que la nueva se instala encima conservando los ajustes.
- La app ya tiene permiso de Internet.

## 6. Avisos cruzados (por USB)
- Protocolo **versión 6**: un mensaje nuevo en los dos sentidos, enviado justo tras el handshake.

  | Tipo | Nombre | Payload |
  |---|---|---|
  | `0x14` | `APP_VERSION` (móvil → PC) | `u16 len` + `str8 versión` (p. ej. `5.0`) |
  | `0x8E` | `APP_VERSION` (PC → móvil) | igual |

  Vectores en `PROTOCOL.md` y tests en los dos lados. Con versión de protocolo < 6 no se envía, y no hay aviso cruzado.
- **Si el otro lado tiene una versión más alta** que la propia (y ninguna es "dev"):
  - **PC**: banner en la toolbox: "El móvil tiene MyMouse 5.2 y este PC la 5.0" con "Actualizar" (el mismo flujo del §4, buscando esa versión en GitHub).
  - **Móvil**: píldora y hoja: "El PC tiene MyMouse 5.2 y este móvil la 5.0" con "Descargar e instalar".
- **Si el otro lado es más antiguo**, el aviso es informativo y sin botón, porque se actualiza desde el otro dispositivo: "El móvil tiene una versión anterior (5.0): actualízalo desde el móvil".
- Por Bluetooth no hay canal con el PC: solo el aviso del §3, por Internet.

## 7. Criterios de "hecho"
1. Con una release más nueva publicada, el PC y el móvil avisan en menos de 1 minuto tras arrancar.
2. "Actualizar" en el PC instalado: descarga, instala en silencio y vuelve a abrirse con la versión nueva y la misma configuración.
3. "Descargar e instalar" en el móvil abre el instalador de Android con el APK nuevo.
4. Con móvil y PC en versiones distintas por USB, ambos lo indican.
5. Tests: comparación de versiones, elección de release y recurso, parseo de la API (fixture) y `APP_VERSION` (vectores).

## 8. Notas de implementación

Implementado: todo lo de las §3 a §6. Tests nuevos: 26 en `core` (305 en total: versiones, parseo del fixture de la API, elección de release y recurso, omitir, avisos cruzados; los vectores de `APP_VERSION` entran en `MessageCodecTest`), 100 en `MyMouse.HostCore.Tests` (345 en total: lo mismo más el gestor, la descarga verificada con un `HttpMessageHandler` falso, `InstallDetector` y las sesiones v6) y 9 en `MyMouse.Protocol.Tests` (123: vectores de `APP_VERSION`). El módulo `app` de Android solo se ha pasado por el compilador de Kotlin (contra `android-all`), y ni el instalador ni el cliente de GitHub se han ejecutado de verdad aquí: ver "Riesgos".

### Qué hay dónde
- **Protocolo v6** (`protocol/PROTOCOL.md`): `0x14` (móvil → PC) y `0x8E` (PC → móvil), payload `str8` de hasta 64 bytes. Cada lado la envía una vez, justo tras el handshake, con versión negociada ≥ 6; un `0x14` con versión < 6 es un error de protocolo (el host lo cierra). No se responde. Vectores: `APP_VERSION("5.0")` = `14 04 00 03 35 2E 30` / `8E 04 00 03 35 2E 30`.
- **`core`** (`dev.mymouse.core.update`): `AppVersion`, `Releases` (parseo con `MiniJson`, la más alta, búsqueda por versión, elección del `.apk`, URL de descarga de confianza), `UpdatePolicy` (si ofrecer, omitir, avisos cruzados, notas recortadas, lista de avisos).
- **`HostCore`** (`MyMouse.HostCore.Updates`): lo mismo (`AppVersion`, `Releases`, `UpdatePolicy`) más `UpdateManager` (estado y avisos del PC, sin interfaz), `UpdateChecker` (HTTP y descarga verificada), `UpdateSettings` (`%APPDATA%\MyMouse\updates.json`) e `InstallDetector`.
- **Fixture compartido:** `android/core/src/test/resources/github-releases.json` (respuesta realista de la API con un borrador, una etiqueta `nightly`, una `v10.0-rc1` y `v5.0.1` listada después de `v5.0`); lo leen los tests de Kotlin y de C# (como `shape-catalog.txt`).
- **PC** (`MyMouse.Host`): `AppInfo` (versión), `UpdateController` (temporizadores, descarga, lanzar el instalador), `UpdateCard` en `ToolboxWindow.cs` (banner), menú de Ajustes, `TrayIcons.WithUpdateDot` (punto azul arriba a la izquierda) y globo una vez por versión.
- **Móvil** (`app`): `Updater` (estado único del proceso, hilo de red, `DownloadManager`, flujo de permisos), `ApkInstaller`, píldora y hoja en `MainActivity`, tarjeta "Actualizaciones" en `SettingsActivity`, `GlassTheme.IconButton.Kind.UPDATE`, `HostConnection` (envía y recibe `APP_VERSION`).

### Versiones: cómo se estampan
- `build.yml`: el job `meta` da `version` = la etiqueta sin `v` (`5.0`) en una release o `0.0-dev.<run>` en cualquier otra ejecución. Android lo recibe en `MYMOUSE_VERSION_NAME`. **El host de Windows solo recibía `-p:InformationalVersion`; ahora el paso "Publicar" pasa también `-p:Version`** (los dos con el mismo valor, que ya usaba el instalador).
- El host lee `AssemblyInformationalVersion` y le quita el `+<commit>` que añade el SDK (aun con `InformationalVersion` explícita). Sin estampar (compilación local) vale `0.0-dev` (`MyMouse.Host.csproj`), de desarrollo: no avisa.
- Una versión es "dev" si lleva un sufijo con `dev` o es `0.0`: no avisa de releases ni de diferencias con el otro dispositivo. Una versión ilegible se trata igual. `5.0` y `5.0.0` son la misma; el cuarto número (`1.0.0.0`) y los metadatos (`+hash`) se ignoran. Las etiquetas de release solo valen como `vX.Y` o `vX.Y.Z` (nada de `v5.1-rc1` ni `nightly`).

### PC
- **Instalada o portátil:** instalada = el exe está en `%LOCALAPPDATA%\Programs\MyMouse` o la clave de desinstalación de Inno (`…\Uninstall\{AppId}_is1`, valor `Inno Setup: App Path`) apunta a la carpeta del exe. (No basta que exista la clave: un zip descomprimido en otra carpeta de alguien que también tiene el instalador sigue siendo portátil.) Portátil: "Ver descarga" abre la página de la release.
- **Descarga:** `%TEMP%\MyMouse-Setup-<v>.exe` (primero `.part`), solo si la URL es `https://github.com/…`; se comprueba el tamaño con el de la API y, si la API trae `digest: sha256:…`, también el SHA-256. Si algo no cuadra se borra y el banner dice "La descarga no es válida". Al arrancar se borran los instaladores viejos de `%TEMP%`.
- **Banner:** una tarjeta por aviso encima de las Opciones (también maximizada, en la columna izquierda). Si hay una release que ofrecer, ese aviso cubre el cruzado "el móvil es más nuevo" (la release es al menos tan alta); el cruzado "el móvil es más antiguo" es solo informativo. Con el móvil más nuevo y sin release que ofrecer (p. ej. omitida), el aviso busca en GitHub la versión del móvil (`Releases.Find`) y, si no está, queda sin botón.
- **"Buscar ahora"** (Ajustes de la toolbox) muestra un cuadro con el resultado y **deja de omitir** una versión omitida (si no, no habría forma de volver a verla). Retraso de 10 s tras arrancar y cada 12 h; con el ajuste automático desactivado no busca solo.
- **Dónde se guardan los ajustes:** `updates.json` aparte de `toolbox.json` y `config.json` (`AutoCheck`, `SkippedVersion`, `NotifiedVersion`).

### Instalador: decisiones
- **Relanzado:** la actualización ejecuta `MyMouse-Setup-<v>.exe /SILENT /CLOSEAPPLICATIONS /RELAUNCH=1` y se cierra enseguida (así el `AppMutex` queda libre para el instalador). **No se pasa `/RESTARTAPPLICATIONS` ni se activa `RestartApplications=yes`:** el Restart Manager solo reinicia apps que se registraron con `RegisterApplicationRestart`, y MyMouse (una app de bandeja sin ventana principal) no lo hace, así que el reinicio no estaba garantizado. En su lugar, una segunda entrada `[Run]` (`nowait skipifnotsilent`, `Check: RelaunchRequested`, que lee `{param:RELAUNCH|0}`) abre `MyMouse.exe` tras una instalación en silencio con `/RELAUNCH=1`; la entrada de siempre (`postinstall skipifsilent`) sigue para el asistente. Si hubiera ya una instancia, el mutex de `Program.cs` la deja en nada. Se mantiene `AppMutex=MyMouse.Host` y `CloseApplications=yes`.
- Las tareas (escritorio, iniciar con Windows) y componentes se conservan: Inno recuerda lo elegido en la instalación anterior (`UsePreviousTasks`, por defecto).

### Móvil
- **Red:** `HttpURLConnection` en un hilo (sin librerías nuevas), `User-Agent: MyMouse-Updater`, tope de 2 MB de respuesta y tiempos de 10 s y 15 s; un fallo se anota en el log y se sigue. El hilo vuelve con un `Handler`. La primera comprobación es 10 s después de que se abra la pantalla principal y luego cada 12 h mientras el proceso viva (el planificador se reinicia con el proceso); no hay servicio en segundo plano.
- **Descarga:** `DownloadManager` con notificación del sistema y destino `getExternalFilesDir(DIRECTORY_DOWNLOADS)` (privada, sin permisos de almacenamiento); se sondea cada 0,5 s para el progreso de la píldora. Se verifica tamaño y SHA-256 (hilo aparte). Si ya hay un APK entero de esa release, se instala sin volver a bajarlo. Si no se puede (sin almacenamiento externo, `DownloadManager` desactivado, URL no válida o release sin `.apk`), se abre la página de la release en el navegador.
- **Instalación:** `androidx.core.content.FileProvider` **sí está disponible** (`core-ktx` trae `androidx.core`, que ya usa la app), así que no hace falta un `ContentProvider` propio: autoridad `${applicationId}.updates`, `exported=false`, `grantUriPermissions=true` y `res/xml/file_paths.xml` (solo `Download/` de los ficheros externos de la app). `Intent.ACTION_VIEW` con `application/vnd.android.package-archive` y `FLAG_GRANT_READ_URI_PERMISSION`. `REQUEST_INSTALL_PACKAGES` en el manifiesto. Con Android 8+ sin el permiso de "Instalar apps desconocidas", primero se explica con un cuadro y se abre `ACTION_MANAGE_UNKNOWN_APP_SOURCES` de MyMouse; al volver a la app (`onResume`) el APK pendiente se abre solo si ya se concedió.
- **Con la app en segundo plano** al terminar la descarga no se abre el instalador (Android 10+ lo bloquea): se abre al volver a la app.
- **Barra de botones:** la píldora lleva texto, así que mientras hay aviso los otros tres botones pasan a solo icono para que quepan en una fila (360 dp). Con el aviso de "PC desactualizado" la píldora solo abre una hoja informativa. En modo bloqueado la barra no existe y no se muestra nada.
- **Ajustes:** los de actualizaciones (`autoCheck`, versión omitida) van en sus propias `SharedPreferences` ("updates"), no en `AppSettings`, para que "Valores por defecto" no los toque.

### Riesgos y cosas sin probar
- **Sin firmar:** el instalador `.exe` no está firmado y lo que se descarga se ejecuta. La defensa es HTTPS a `github.com`, el tamaño y el SHA-256 de la API (solo si GitHub lo da en `digest`). Quien controle las releases del repositorio controla lo que se instala. Igual con el APK (firmado con la clave de desarrollo pública del repositorio, que Android exige que coincida: no se puede colar una app con otra firma).
- No se han ejecutado: el instalador (`[Run]` con `Check`, `{param:…}`), la actualización en silencio de punta a punta, `DownloadManager`, `FileProvider` ni el diálogo del permiso, ni el banner de WPF (solo compila). El manifiesto se validó como XML bien formado.
- La lista de releases se pide con `per_page=10`: si hubiera más de 10 releases más nuevas que la última que importa, solo se ven las 10 primeras por fecha (la API ordena por fecha de creación, no por versión).
- Sin comprobaciones en CI propias: `build.yml` ya corre `:core:test`, `dotnet test` y compila `app` y el instalador.
