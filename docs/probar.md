# Cómo probar una release

Cada release (`v0.0`, `v0.1`, …) añade una etapa encima de la anterior. Si algo falla, baja a la release anterior hasta encontrar la primera en la que falla y apunta cuál es.

## Una sola vez

1. **Móvil (P10):**
   - Ajustes → Sistema → Información del teléfono → toca 7 veces "Número de compilación".
   - Ajustes → Sistema → Opciones de desarrollador → activa **Depuración USB** y **Permitir depuración ADB en modo solo carga**.
2. **PC (Windows):**
   - Instala [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (descomprimir y añadir la carpeta al `PATH`, o dejarla en `%LOCALAPPDATA%\Android\Sdk\platform-tools`).
   - Si `adb devices` no ve el móvil, instala el driver de Huawei (viene con **HiSuite**).
   - Conecta el cable, acepta en el móvil el aviso "¿Permitir depuración USB?" (marca "Permitir siempre") y comprueba que `adb devices` muestra el móvil como `device`.
   - Recomendado: desactiva "Mejorar la precisión del puntero" (Configuración del mouse → Opciones adicionales → Opciones de puntero).

## Para cada release

De la página de la release descarga:
- `MyMouse-<versión>.apk`: la app del móvil.
- `MyMouse-Windows-<versión>.zip`: `MyMouse.exe` (el host de bandeja) y `MyMouse.TestClient.exe` (simula el móvil). No necesitan instalar .NET.

1. **Cerrar el host anterior:** icono de MyMouse en la bandeja → Salir.
2. **Instalar la app** (sustituye a la anterior y conserva los ajustes, también al bajar de versión):
   ```powershell
   adb install -r -d MyMouse-<versión>.apk
   ```
3. **Arrancar el host:** descomprime el zip y ejecuta `MyMouse.exe`. Windows SmartScreen puede avisar porque no está firmado: "Más información" → "Ejecutar de todas formas".
4. Abre **MyMouse** en el móvil. El punto de la esquina se pone verde al conectar.

Si algo no conecta:
- Pasa el ratón por el icono de la bandeja: el tooltip dice si encuentra adb y el móvil.
- Logs del host: icono de la bandeja → "Abrir carpeta de logs".
- Prueba solo el PC con `MyMouse.TestClient.exe` (con el host abierto): el cursor debe dibujar un círculo.
- Logs del móvil: `adb logcat -s MyMouse`.

## Modo gestos (desde la v1.0)

1. En el móvil: modo ventana → **Ajustes → Formas…** para ver y activar las formas. Lo que hace cada una se configura en el PC.
2. En el PC: icono de MyMouse en la bandeja → **Editar gestos…** (desde la v4: toolbox → **Gestos y widgets**, ver más abajo). Se abre `gestures.json` con ejemplos (círculo = abrir Chrome, Z = deshacer, V = reproducir/pausar…). Al guardar, MyMouse lo recarga solo.
3. En el trackpad: apoya **4 dedos a la vez y déjalos quietos** hasta que vibre. Levántalos y dibuja la forma con un dedo.
4. Activa el **modo depuración** (Ajustes) para ver qué forma se ha reconocido y con qué puntuación.

## Widgets (desde la v2.0)

1. **Actualiza las dos partes** (APK y host): el panel solo aparece si el host es de la v2.0 o posterior. Con un host anterior, o sin conexión, el trackpad ocupa toda la pantalla.
2. **Activa los widgets en el PC.** Si ya tenías `gestures.json` de la v1, no se toca: icono de la bandeja → **Editar gestos y widgets…** (desde la v4, el editor de la toolbox lo hace por ti) y añade, junto a `"bindings"`:
   ```json
   "widgets": [
     { "type": "media" },
     { "type": "volume" },
     { "type": "button", "label": "Chrome",   "icon": "🌐", "action": { "type": "launch", "target": "chrome.exe", "focusIfRunning": true } },
     { "type": "button", "label": "Copiar",   "icon": "📋", "action": { "type": "keys", "keys": "Ctrl+C" } },
     { "type": "button", "label": "Pegar",    "icon": "📄", "action": { "type": "keys", "keys": "Ctrl+V" } },
     { "type": "button", "label": "Captura",  "icon": "✂️", "action": { "type": "keys", "keys": "Win+Shift+S" } },
     { "type": "button", "label": "Bloquear", "icon": "🔒", "action": { "type": "mymouse", "command": "lock" } }
   ]
   ```
   (Una instalación nueva ya trae este ejemplo.) Al guardar, el panel del móvil se actualiza solo.
3. **Panel:** aparece abajo (móvil en vertical) o a la derecha (horizontal). Tamaño y visibilidad en Ajustes → Widgets.
4. **Qué comprobar:**
   - Un botón se ejecuta al **levantar** el dedo dentro de él; si arrastras fuera, se cancela.
   - Música: reproduce algo en el PC; el título y el artista salen en el móvil y ⏮ ⏯ ⏭ funcionan.
   - Volumen: arrastra la barra y comprueba el volumen de Windows; cámbialo con el teclado del PC y mira que la barra lo sigue (menos de 0,3 s). El altavoz silencia.
   - Ningún toque del panel mueve el cursor ni hace clic, y al revés.
   - Si el panel no cabe, desplázalo con un dedo.
   - Con el **modo depuración** verás anotadas las acciones (`Widget: Chrome`, `Volumen 55`, `⏭ Siguiente`).
5. `MyMouse.TestClient.exe` (con el host abierto) imprime los widgets configurados y el estado actual de volumen y música.

## Ajustes de la v3.1

Actualiza el APK y el host (los gestos de 3 dedos por USB necesitan el host 3.1; por Bluetooth funcionan sin host).
1. **Gestos de Windows con 3 dedos:** ↑ vista de tareas, ↓ escritorio, ← → cambiar de app (se mantiene Alt y avanza cada ~120 px; al levantar los dedos se suelta). Se desactivan en Ajustes.
   - **Xiaomi/HyperOS:** el sistema usa 3 dedos para la captura. Desactiva en Ajustes → Ajustes adicionales → Atajos de gestos: *Captura de pantalla (deslizar 3 dedos hacia abajo)* y el recorte parcial (mantener 3 dedos). Una app no puede bloquearlos.
2. **Modo gestos:** ahora con **4 dedos quietos** (300 ms).
3. **Panel de widgets:** 4 dedos **juntar** lo muestra y **separar** lo oculta, con animación; el estado se recuerda. Sin widgets en el PC, juntar avisa "No hay widgets".
4. **Zoom:** más fino y rápido; "Velocidad del zoom" en Ajustes (×0,5 a ×4). Comprueba que el pellizco no se queda en scroll.
5. **Inercia al hacer scroll:** activada por defecto; se corta con cualquier toque.
6. Con el modo depuración, 4 dedos deslizando ↑/↓ se anota ("4 dedos ↑: abrir la toolbox del PC"); desde la v4 abre y cierra la toolbox.

## La toolbox del PC (desde la v4.0)

Actualiza el APK y el host: el host de la v4 es una app WPF (el mismo `MyMouse.exe`, más grande) y la toolbox desde el móvil necesita las dos partes en la v4 (protocolo 5). Con una parte antigua, 4 dedos ↑ / ↓ avisan "Actualiza MyMouse en el PC" y lo demás funciona como antes. Por Bluetooth no hay toolbox.
1. **Abrir y cerrar:** clic izquierdo en el icono de la bandeja (otro clic la cierra); o **4 dedos hacia arriba** en el trackpad del móvil. Se cierra con **4 dedos hacia abajo**, `Esc` o un clic fuera. Abierta desde el móvil no te quita el foco: sigue escribiendo en la app en la que estabas; al hacer clic en la toolbox ya se comporta como una ventana normal. Clic derecho en el icono: Abrir MyMouse, Pausar, Salir.
2. **Tarjetas:** Opciones (estado, Pausar, Gestos y widgets, Ayuda, Ajustes, Salir), Música (carátula, ⏮ ⏯ ⏭, volumen y silencio), Gestos y Widgets del móvil (oculta por defecto). Ajustes → *Personalizar tarjetas…* para quitar, añadir y reordenar (se guarda en `%APPDATA%\MyMouse\toolbox.json`; Opciones no se puede quitar). La toolbox funciona sin móvil.
3. **Gesto en vivo:** activa el modo gestos en el móvil (4 dedos quietos) y mira el PC: sube una tarjeta con una cuenta atrás; dibuja y verás el trazo en directo. Al terminar se colorea: **verde** = reconocida y ejecutada (ayuda y acción), **ámbar** = reconocida sin acción (con botón *Asignar*), **gris** = no reconocida o cancelada, **rojo** = la acción falló. Baja sola al cabo de ~1,5 s. Se desactiva en Ajustes → *Mostrar el gesto en vivo*.
4. **Editor** (Opciones → *Gestos y widgets*, o clic en una forma de la tarjeta Gestos):
   - Pestaña **Gestos:** elige la forma, el tipo de acción (atajo: haz clic en el campo y pulsa la combinación, también con la tecla Win; abrir app: *Elegir programa…* o la lista de apps del menú Inicio con buscador; texto; multimedia; MyMouse), el texto de ayuda y si va fijada en la chuleta. *Probar* la ejecuta (las teclas y el texto, a los 3 s para que cambies de ventana). *Guardar* escribe `gestures.json` con formato limpio (la primera vez deja una copia `gestures.json.bak`; los comentarios se pierden) y el móvil recibe la lista al momento.
   - Si `gestures.json` tiene un error de sintaxis, el editor lo dice y ofrece abrirlo en el Bloc de notas en vez de pisarlo.
   - Pestaña **Widgets:** añadir, ordenar (↑ ↓) y borrar; un botón tiene etiqueta, icono (emoji con `Win+.`) y acción; vista previa del panel.
5. **Forma nueva desde el PC:** *+ Nueva forma* → nombre → 3 muestras con el ratón o el touchpad → *Guardar en el móvil*. Si se confunde con otra, te lo dice. Después asígnale una acción (p. ej. abrir Chrome). Con el móvil sin conectar, o con un host/móvil antiguo, el botón queda desactivado: "Conecta el móvil por USB para grabar formas".
6. **Forma nueva desde el móvil:** Ajustes → Formas → Nueva forma. Al volver al trackpad, el PC avisa con un globo "Nueva forma «x»: ¿qué quieres que haga?"; al pulsarlo se abre el editor con esa forma.
7. **`MyMouse.TestClient.exe`** (con el host abierto) abre y cierra la toolbox, simula un trazo y envía una forma de prueba; con `--formas` se queda 60 s contestando a lo que el editor mande al móvil (probar *+ Nueva forma* sin móvil).
8. **Qué comprobar con más cuidado** (no se ha probado en Windows real): colocación sobre la bandeja con varios monitores o escalas distintas, que abrir desde el móvil no robe el foco, el campo de atajos con la tecla Win, el selector de emojis y la carátula de la música. Si algo falla, apunta qué hiciste y el log del día.

## Bluetooth (desde la v3.0)

No hace falta MyMouse ni adb en el PC.
1. En el móvil: modo ventana → Ajustes → **Conexión: Bluetooth** → **Emparejar por Bluetooth…** → dar permiso → **Hacer visible**.
2. En Windows: Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth → elegir el móvil y aceptar el código.
3. Vuelve al trackpad: el punto de estado se pone verde al conectar.
4. Si la pantalla de emparejar dice que el perfil HID no está disponible, ese móvil no puede hacer de ratón Bluetooth: apúntalo.
5. Logs: `adb logcat -s MyMouse` (líneas "Bluetooth: …").

## Qué anotar si algo falla
- Release en la que falla y la última en la que funcionaba.
- Qué hiciste y qué pasó (y qué esperabas).
- El log del host del día (`%APPDATA%\MyMouse\logs`) y, si puedes, `adb logcat -s MyMouse -d > movil.txt`.
