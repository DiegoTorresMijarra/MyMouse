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
2. En el PC: icono de MyMouse en la bandeja → **Editar gestos…**. Se abre `gestures.json` con ejemplos (círculo = abrir Chrome, Z = deshacer, V = reproducir/pausar…). Al guardar, MyMouse lo recarga solo.
3. En el trackpad: apoya **4 dedos a la vez y déjalos quietos** hasta que vibre. Levántalos y dibuja la forma con un dedo.
4. Activa el **modo depuración** (Ajustes) para ver qué forma se ha reconocido y con qué puntuación.

## Widgets (desde la v2.0)

1. **Actualiza las dos partes** (APK y host): el panel solo aparece si el host es de la v2.0 o posterior. Con un host anterior, o sin conexión, el trackpad ocupa toda la pantalla.
2. **Activa los widgets en el PC.** Si ya tenías `gestures.json` de la v1, no se toca: icono de la bandeja → **Editar gestos y widgets…** y añade, junto a `"bindings"`:
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
6. Con el modo depuración, 4 dedos deslizando ↑/↓ solo se anota ("4 dedos ↑", reservado para la v4).

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
