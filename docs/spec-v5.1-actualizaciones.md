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
