# MyMouse — Especificación v5: Bluetooth completo e instalador

> Objetivos: [00-roadmap.md](00-roadmap.md) · Base: [spec-v1-gestos.md](spec-v1-gestos.md), [spec-v2-widgets.md](spec-v2-widgets.md), [spec-v3-bluetooth.md](spec-v3-bluetooth.md), [spec-v4.5-estilo.md](spec-v4.5-estilo.md) (§4, Bluetooth), [spec-v4.8-temas.md](spec-v4.8-temas.md)
>
> Estado: **implementada en la rama de desarrollo** (release `v5.0` pendiente; ver las [notas de implementación](#8-notas-de-implementación)). Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Objetivo

1. Que el modo **Bluetooth** (sin programa en el PC) sea casi tan completo como el USB:
   - Gestos con acciones guardadas en el móvil.
   - Botones de atajo propios.
   - Scroll y zoom más finos.
2. Que instalar MyMouse en Windows sea un **instalador** de verdad.

El modo USB no cambia: allí mandan el PC y su `gestures.json`.

## 2. Gestos por Bluetooth

### 2.1 Qué se puede hacer sin el PC
Por Bluetooth el móvil solo puede "teclear" (informes HID). Las acciones posibles son:

| Tipo | Por Bluetooth | Cómo |
|---|---|---|
| `keys` (atajo) | Sí | Informe de teclado: pulsar modificadores + tecla, soltar |
| `media` | Sí | Informe de consumo (los usos de la v3) |
| `text` | Sí, con límites | Tecla a tecla según la **distribución de teclado del PC** (ajuste: Español (España) por defecto, o Inglés (EE. UU.)). Caracteres que no existen en esa distribución se saltan y se avisa en el editor |
| `launch`, `mymouse` (pausar, bloquear), reglas por app | No | Necesitan el programa del PC: solo por USB |

*(decisión)* "Bloquear el PC" se ofrece por Bluetooth como atajo `Win+L`, porque es solo una tecla.

### 2.2 Dónde se guardan
- En el móvil, en `bt-gestures.json`, un fichero aparte del mapa del PC: forma → acción (`keys`/`media`/`text`), texto de ayuda y "fijar en la chuleta".
- **Valores por defecto** la primera vez, iguales en espíritu a los del PC:

  | Forma | Acción | Ayuda |
  |---|---|---|
  | `z` | `Ctrl+Z` | Deshacer |
  | `line-left` | `Alt+Left` | Atrás |
  | `line-right` | `Alt+Right` | Adelante |
  | `line-down` | `Win+D` | Escritorio |
  | `line-up` | `Win+Tab` | Vista de tareas |
  | `v` | reproducir/pausar | Reproducir / pausar |
  | `chevron-right` | siguiente | Siguiente canción |
  | `chevron-left` | anterior | Canción anterior |
  | `check` | `Ctrl+S` | Guardar |

  Hay que ajustar los ids a los reales de `ShapeCatalog`.
- Las formas (plantillas) son las mismas de siempre (`shapes.json`). Este fichero solo guarda las acciones.

### 2.3 Funcionamiento
- **Modo gestos por Bluetooth**: 4 dedos quietos, como por USB. Ya no se fuerza `gestureFingers = 0` en Bluetooth.
- **Al reconocer una forma**:
  - Se busca su acción en `bt-gestures.json` y se ejecuta con `HidMapper`. Las teclas van en orden y después del movimiento pendiente, usando el agrupador de la v4.5.
  - Se muestra el mismo resultado visual que por USB: la acción, "sin acción" o "no reconocida".
- La **hoja de formas** del modo gestos y la **chuleta** muestran, por Bluetooth, las acciones del móvil.
- **Lógica pura en `core`, con tests**:
  - Parseo y validación del fichero.
  - Traducción de acción a informes HID, ampliando `HidMapper`.
  - Tablas de distribución de teclado (carácter → uso HID + Shift/AltGr) para ES y US. Por ejemplo, en ES: `ñ`, `@` = AltGr+2, `¿`, `á` como tecla muerta + vocal.

### 2.4 Editor en el móvil
- Ajustes → Modo gestos → **"Acciones por Bluetooth…"**: una pantalla nueva con el estilo glass y el tema.
  - Lista de formas (miniatura, ayuda y chip de acción, como en el PC) y una pantalla de detalle.
  - Selector de tipo: Atajo / Multimedia / Texto / Ninguna.
  - **Atajo**: casillas Ctrl, Alt, Shift y Win, más un selector de tecla (letras, números, F1–F12, flechas, Inicio/Fin, RePág/AvPág, Supr, Retroceso, Intro, Esc, Tab, Espacio, Impr Pant). Se muestra como `Ctrl+Shift+T`.
  - **Multimedia**: lista de reproducir/pausar, siguiente, anterior, subir, bajar y silencio.
  - **Texto**: campo de texto, con aviso de caracteres no disponibles en la distribución elegida.
  - Texto de ayuda y "Fijar en la chuleta".
  - Botón **Probar** (solo si está conectado por Bluetooth): ejecuta la acción en el PC al momento.
- En la misma pantalla, el ajuste **"Distribución del teclado del PC"** (ES / US).

## 3. Widgets propios por Bluetooth
- El panel de Bluetooth tiene ahora: multimedia (⏮ ⏯ ⏭) + volumen (Vol −, Vol +, Silencio, como en la v3) + **botones propios**.
- Botones propios en `bt-widgets.json`: etiqueta, icono (emoji o 1–3 letras) y acción (`keys`/`media`/`text`). Máximo 16.
  - Por defecto: Copiar `Ctrl+C` 📋, Pegar `Ctrl+V` 📄, Captura `Win+Shift+S` ✂️, Deshacer `Ctrl+Z` ↩️, Bloquear `Win+L` 🔒.
- Editor: Ajustes → Widgets → **"Botones por Bluetooth…"**, con lista ordenable (↑ ↓), añadir, borrar y el mismo formulario de acción del §2.4.
- La disposición en rejilla y la animación del panel son las de siempre (`WidgetLayout`).

## 4. Scroll y zoom finos por Bluetooth
- **Scroll de alta resolución**: el descriptor HID del ratón añade un **Resolution Multiplier**, un informe de tipo *feature* para la rueda y la rueda horizontal, como los ratones de "scroll suave" de Windows.
  - Si Windows activa el multiplicador (lo hace con un `SET_REPORT` *feature*, que llega en `onSetReport`), las unidades de rueda pasan a ser finas: el multiplicador es ×120, o el que el host acepte. El `HidMotionCoalescer` acumula y envía las unidades finas.
  - Si el host no lo activa, se sigue con pasos enteros, como ahora.
  - Se responde a `onGetReport` *feature* con el valor actual.
- El **zoom** (Ctrl + rueda) usa las mismas unidades finas cuando el multiplicador está activo.
- En el diagnóstico de Bluetooth aparece la línea "Scroll fino: activado por el PC" o "no".
- **Riesgo**: cambiar el descriptor obliga a **volver a emparejar** (Windows guarda en caché el descriptor). Se avisa en las notas de la release y en la pantalla Bluetooth: "Si actualizas desde una versión anterior, quita el móvil en Windows y vuelve a emparejarlo".
  - *(decisión)* Hay un ajuste avanzado "Scroll fino por Bluetooth" (activado por defecto) que, al desactivarlo, registra el descriptor antiguo, por si algún PC se porta mal.
- La lógica del descriptor (bytes) y del multiplicador va en `core`, con tests: descriptor con y sin multiplicador, escalado de unidades y estado del *feature report*.

## 5. Instalador de Windows
- **Inno Setup** (gratuito), con un script `windows/installer/MyMouse.iss` compilado en CI (job `windows`; si el runner no lo trae, se instala con `choco install innosetup -y`).
- Contenido:
  - El exe *single-file* de siempre.
  - **adb** (las *platform-tools* de Google, descargadas en CI desde `https://dl.google.com/android/repository/platform-tools-latest-windows.zip`; solo `adb.exe`, `AdbWinApi.dll` y `AdbWinUsbApi.dll`, con su licencia).
  - El `TestClient`, opcional.
- Instala en `%LOCALAPPDATA%\Programs\MyMouse` (sin administrador; `PrivilegesRequired=lowest`).
  - Crea un acceso directo en el menú Inicio y, opcionalmente, en el escritorio.
  - Casilla "Iniciar con Windows" (escribe la misma clave `Run` que la bandeja).
  - Al final, "Abrir MyMouse".
- **El host busca adb primero junto al exe** y, si no está, en el PATH, como ahora.
- **Desinstalar** desde Configuración → Aplicaciones: borra el programa y la clave `Run`. *(decisión: `%APPDATA%\MyMouse`, la configuración, se conserva)*
- La release publica `MyMouse-Setup-<versión>.exe` **además** del zip (portátil) y el APK.
- El instalador **no está firmado**: SmartScreen avisará ("Windows protegió su PC → Más información → Ejecutar de todas formas"). Se explica en `probar.md`.

## 6. Criterios de "hecho"
1. Por Bluetooth: dibujar `z` deshace en el PC sin programa en el PC; asignar desde el móvil un atajo nuevo y probarlo.
2. Botones propios por Bluetooth (Copiar, Pegar, Captura…) que funcionan.
3. Scroll fino por Bluetooth en un Windows que acepte el multiplicador, y vuelta a pasos enteros si no.
4. Instalar con `MyMouse-Setup.exe` sin descomprimir nada; adb incluido; desinstalar limpio.
5. Tests: acciones por Bluetooth, distribuciones ES/US, descriptor con multiplicador y escalado.

## 7. Fuera de alcance
- Abrir apps por Bluetooth (no hay forma de hacerlo sin el PC).
- Firmar el instalador.
- macOS y Linux (por Bluetooth deberían funcionar, pero no se prueban).

## 8. Notas de implementación

Implementado: toda la lista de la §6. La lógica nueva tiene tests: 66 más en `core` (279 en total: acciones, distribuciones, ficheros, descriptor, multiplicador, mapeador y coalescer, `MiniJson`) y 8 más en `MyMouse.HostCore.Tests` (245 en total: dónde se busca adb).

### Qué hay dónde
- **`core` (con tests):**
  - `btactions/BtAction.kt`: `BtAction` (`Keys`, `Media`, `Text`) y `BtActions` (lectura y escritura de atajos `Ctrl+Shift+T`, nombres multimedia, JSON de una acción y texto de lo que hace). Los nombres de las teclas son los del `gestures.json` del PC, con sus alias en español.
  - `btactions/KeyboardLayout.kt`: `KeyboardLayouts.ES` y `.US` (carácter → pulsaciones: modificadores + uso HID). El español lleva AltGr (`@ # ~ € \ | [ ] { }`), `ñ`, `ç`, `¿ ¡`, `º ª · ¬`, `< >` en la tecla ISO extra y las teclas muertas (acento agudo, diéresis, grave y circunflejo) combinadas con vocal (`á`, `Ü`…), más cada acento suelto (tecla muerta + Espacio). `unsupported(texto)` lista lo que no se puede escribir (para el aviso del editor).
  - `btactions/BtGestureConfig.kt` (`bt-gestures.json`) y `btactions/BtWidgetConfig.kt` (`bt-widgets.json`): valores por defecto, lectura tolerante (lo inválido se descarta con un aviso por regla o botón), escritura, edición inmutable (`with`, `without`, `add`, `move`…), lista para la hoja de formas (`listEntries`) y los widgets del panel (`entries`, `buttonFor`).
  - `hid/HidKeys.kt` (usos HID y bits de modificadores, lista del selector), `hid/HidDescriptor.kt` (descriptor con y sin *Resolution Multiplier*; el antiguo es byte a byte el de la v4, con un test que lo fija), `hid/ScrollResolution.kt` (estado del multiplicador y del informe *feature*), `HidMapper.action(...)` (atajo, multimedia y texto), `HidMotionCoalescer.dropScroll()` y la línea de diagnóstico `BluetoothDiagnostics.fineScrollLine`.
  - `json/MiniJson.kt`: el lector de JSON mínimo que estaba dentro de `ThemeJson`, ahora compartido y con escritor (`ThemeJson` lo usa).
- **Móvil (`app`):** `BtActionStore` (los dos ficheros en `filesDir`, escritura por fichero temporal y renombrado; sin fichero o ilegible, valores por defecto), `BtActionForm` (el formulario común de acción), `BtGesturesActivity` ("Acciones por Bluetooth…", con la distribución del teclado), `BtWidgetsActivity` ("Botones por Bluetooth…"), y cambios en `BluetoothHidTransport`, `MainActivity`, `TrackpadView`, `SettingsActivity`, `BluetoothActivity` y `AppSettings` (`btKeyboardLayout`, `btFineScroll`).
- **PC:** `AdbReverseKeeper.FindAdb` busca primero en `config.json`, después **junto al exe**, luego en el PATH y por último en el SDK (con tests); `installer/MyMouse.iss` y los pasos nuevos del job `windows` de `build.yml`.

### Decisiones y desviaciones
- **Ids de las formas:** `>` es `gt` y `<` es `lt` en `ShapeCatalog` (la tabla de la §2.2 dice `chevron-*`, que no existen). Por defecto se fijan en la chuleta `z` y `v`, como en el PC.
- **Ficheros:** `bt-gestures.json` = `{"version": 1, "bindings": [{"gesture", "action", "help", "pin"}]}` y `bt-widgets.json` = `{"version": 1, "buttons": [{"label", "icon", "action"}]}`, con acciones como en el `gestures.json` del PC (`{"type": "keys", "keys": "Ctrl+Z"}`, `{"type": "media", "key": "PlayPause"}`, `{"type": "text", "text": "…"}`). No se escribe nada hasta que se guarda un cambio: sin fichero valen los valores por defecto. "Ninguna" borra la regla.
- **Solo una combinación por atajo:** las secuencias (`Ctrl+K, Ctrl+C`) no se ofrecen por Bluetooth. Un atajo puede ser solo un modificador (`Win`). Teclas del selector: las de la §2.4; el descriptor del teclado llega al uso 0x65, así que no hay F13–F24.
- **La distribución del teclado y el scroll fino** son ajustes de `AppSettings` (la distribución se elige dentro de "Acciones por Bluetooth…"; "Scroll fino por Bluetooth" está en Ajustes → Conexión).
- **Texto:** máximo 300 caracteres por acción; sale tecla a tecla (pulsar y soltar cada una) y, si pasa de 4 teclas, con 5 ms entre informes para no desbordar la pila Bluetooth. Los caracteres que la distribución no tiene se saltan; el editor los avisa. Una acción de texto sin ningún carácter escribible no se puede guardar. Los emoji no se pueden escribir.
- **Scroll fino:**
  - El informe *feature* tiene el id del ratón (1) y un byte: bits 0–1 = multiplicador de la rueda y bits 2–3 = el de la horizontal (0 = ×1, 1 = ×120). Si Android entrega el dato con el id delante, también se entiende.
  - Las unidades de `OutputAction.Scroll` ya eran de 1/120 de paso: con el multiplicador activo pasan casi tal cual (en pasos de ≤ 127 por informe; el coalescer las suma); sin él, pasos enteros como antes. Al cambiar el multiplicador se descarta el resto acumulado y lo pendiente en el coalescer (estaba en otras unidades). Al desconectar vuelve a ×1.
  - **Zoom fino:** con el multiplicador activo, Ctrl se queda pulsado entre un gesto de zoom y el siguiente (si no, cada zoom costaría tres informes de teclado/ratón y a 60 Hz saturaría el enlace) y el transporte lo suelta 150 ms después del último; cualquier otra acción lo suelta antes. Sin multiplicador, el zoom es el de siempre (Ctrl, rueda, soltar).
  - `onGetReport`/`onSetReport` *feature* se contestan con `replyReport`/`reportError(ERROR_RSP_SUCCESS)`; con el ajuste apagado o un id ajeno, `ERROR_RSP_INVALID_RPT_ID`. Si se cambia el ajuste con la app registrada, se vuelve a registrar el descriptor al abrir la siguiente pantalla (Windows igualmente pide volver a emparejar).
- **Hoja de formas y resultados:** por Bluetooth, `MainActivity` da al trackpad una lista de formas con acción (`GestureList` "por Bluetooth") construida desde `bt-gestures.json`, así que la hoja, la chuleta y el resultado ("Deshacer", "sin acción") salen del mismo código que por USB. Una forma sin acción avisa "sin acción (Ajustes → Acciones por Bluetooth)". La toolbox y los gestos con 4 dedos ↑ ↓ siguen siendo solo del USB.
- **Botones propios:** ids de widget 0–3 = multimedia y volumen (como en la v3); los propios, 10 + posición. Icono: un emoji (puede llevar selector de variante) o 1–3 letras (hasta 4 puntos de código); etiqueta hasta 20 caracteres.
- **Probar:** los editores abren el transporte Bluetooth (como la pantalla de emparejar) solo si la conexión elegida es Bluetooth; si no hay PC conectado, avisan.
- **Instalador:**
  - Parámetros por variables de entorno (`MYMOUSE_VERSION`, `MYMOUSE_SOURCE_DIR`, `MYMOUSE_ADB_DIR`, `MYMOUSE_OUTPUT_DIR`, leídas con `GetEnv` en el `.iss`) y, si se prefiere, con `/D` (`AppVersion`, `SourceDir`, `AdbDir`, `OutputDir`): una versión como `0.0-dev.42` o una ruta con barras no se puede pasar por `/D` sin entrecomillar, y las comillas en PowerShell son frágiles.
  - La casilla "Iniciar con Windows" empieza **desactivada**. El instalador cierra la app si está en marcha (`AppMutex=MyMouse.Host` + Restart Manager). Desinstalar borra el valor `Run` de HKCU aunque se hubiera activado después desde la bandeja, y deja `%APPDATA%\MyMouse`.
  - El zip portátil no cambia (sin adb: usa el del PATH).

### Sin verificar
- **El módulo `app` no se ha compilado ni ejecutado** (sin Android SDK local). Se ha pasado el compilador de Kotlin sobre todas las fuentes con `core`, el `android.jar` completo (`android-all`, API 34) y unos stubs de `androidx` en el classpath: la única referencia sin resolver es `R` (recursos generados). No se comprueban las restricciones de nivel de API de lint (las clases nuevas solo llaman a `BluetoothHidTransport` con `Build.VERSION.SDK_INT >= P`).
- **El scroll fino contra un Windows real:** el descriptor sigue el patrón documentado por Microsoft para *Resolution Multiplier* (colección lógica por eje, lógico 0–1, físico 1–120, informe *feature* con relleno hasta el byte), pero no se ha probado que Windows lo acepte con un dispositivo HID de Android ni que mande el `SET_REPORT` de la forma esperada. Si no lo hace, queda en pasos enteros (diagnóstico: "Scroll fino: no"); el ajuste apagado devuelve el descriptor de la v4.
- **La distribución española:** las posiciones de teclas (en especial `ç` en el uso 0x32 de la tecla ISO, `€` con AltGr+E y las teclas muertas) siguen el teclado ISO español de Windows, pero no se han probado contra uno. Las teclas muertas dependen de que Windows no tenga un método de entrada que las cambie.
- **El instalador no se ha compilado** (ni Inno Setup ni PowerShell de Windows disponibles). Riesgos: que `Spanish.isl` no esté en la instalación de Inno Setup del runner (viene con el compilador 6.x), `ArchitecturesAllowed=x64` (obsoleto desde la 6.3, pero válido), la sintaxis del `.iss` (`GetEnv`, `#undef`), y que el zip de platform-tools cambie de nombre de carpeta o de ficheros. El paso falla la build si algo no cuadra.
- **Todo lo que dibuja en el móvil:** las dos pantallas nuevas y el formulario no se han visto en un dispositivo (chips, `AlertDialog` de teclas, campos de texto con el estilo de cristal, orden con ↑ ↓, pantalla de detalle con `Screen` reconstruido al cambiar de estado).
