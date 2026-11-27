# MyMouse — Especificación v5: Bluetooth completo e instalador

> Objetivos: [00-roadmap.md](00-roadmap.md) · Base: [spec-v1-gestos.md](spec-v1-gestos.md), [spec-v2-widgets.md](spec-v2-widgets.md), [spec-v3-bluetooth.md](spec-v3-bluetooth.md), [spec-v4.5-estilo.md](spec-v4.5-estilo.md) (§4, Bluetooth), [spec-v4.8-temas.md](spec-v4.8-temas.md)
>
> Estado: **especificada**. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

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
