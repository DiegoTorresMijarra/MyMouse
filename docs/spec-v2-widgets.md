# MyMouse — Especificación v2: widgets

> Objetivos: [00-roadmap.md](00-roadmap.md#v2-widgets) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md) · Base: [spec-v0.md](spec-v0.md), [spec-v1-gestos.md](spec-v1-gestos.md)
>
> Estado: **implementada** (pendiente de probar en el PC y el móvil reales; se publica como v2.0). Las decisiones marcadas con *(decisión)* se tomaron sin el usuario y se pueden revisar tras la prueba.

## 1. Qué es

Un **panel de widgets** junto al trackpad: controles de un toque que hoy requieren el teclado o buscar en la interfaz. Igual que las formas de la v1, **qué widgets hay y qué hacen se configura en el PC**; el móvil solo los dibuja y avisa de qué se ha pulsado.

Tres tipos:

| Tipo | Qué es | En el móvil | En el PC |
|---|---|---|---|
| `button` | Botón de acción | Tecla con icono (emoji o texto corto) y etiqueta | Ejecuta una acción de la v1: `keys`, `launch`, `text`, `media`, `mymouse` |
| `media` | Control multimedia | Título y artista de lo que suena + botones ⏮ ⏯ ⏭ | Teclas multimedia; lee "lo que suena" de Windows |
| `volume` | Volumen del sistema | Barra deslizante + botón de silencio | Fija el volumen del altavoz por defecto y avisa cuando cambia |

## 2. Disposición en el móvil

- El panel ocupa una franja **abajo** en vertical y a la **derecha** en horizontal. El trackpad usa el resto de la pantalla. *(decisión: siempre a la vez, sin modos alternables; es lo más sencillo de usar sin mirar)*
- Tamaño del panel en Ajustes: pequeño / mediano / grande = 22 % / 30 % / 40 % del lado correspondiente. Por defecto, mediano.
- Ajuste "Mostrar panel de widgets" (activado por defecto). El panel **solo aparece si el PC ha enviado al menos un widget**; con un host v1/v2 o sin conexión no se dibuja y el trackpad ocupa toda la pantalla.
- El panel se separa del trackpad con una línea fina. **Los toques que empiezan en el panel nunca llegan al trackpad**, y viceversa (un dedo que empieza en el trackpad y entra en el panel sigue siendo del trackpad).
- Orden: el de la configuración del PC. Distribución en rejilla: los `button` ocupan 1 celda; `media` y `volume`, una fila entera cada uno. Columnas: 4 en vertical, 2 en horizontal (el panel es estrecho). Si no cabe todo, el panel hace scroll vertical con un dedo.
- Funciona igual en modo bloqueado y en modo ventana. La chuleta y el modo depuración se dibujan dentro del área del trackpad.

### 2.1 Aspecto de cada widget
- **button:** fondo gris oscuro con esquinas redondeadas; icono grande arriba (si `icon` está vacío, las 2 primeras letras de la etiqueta) y etiqueta abajo, recortada con "…". Al pulsar: se ilumina 150 ms y vibración corta (20 ms). Se ejecuta al **levantar** el dedo dentro del botón (si se arrastra fuera, se cancela).
- **media:** línea 1: título (negrita); línea 2: artista. Debajo, tres botones ⏮ ⏯ ⏭. Si no suena nada: "Nada en reproducción" y los botones siguen activos. El icono de ⏯ refleja el estado (▶ en pausa, ⏸ reproduciendo).
- **volume:** icono 🔈/🔇 (pulsar = silenciar/quitar silencio) + barra 0–100 + número. Mientras se arrastra, el móvil envía `VOLUME_SET` como mucho cada 50 ms y al soltar. Cuando cambia el volumen en el PC (teclado, otra app), la barra se actualiza, salvo mientras el usuario la está arrastrando.

## 3. Configuración en el PC

En el mismo `%APPDATA%\MyMouse\gestures.json`, una lista nueva `"widgets"` junto a `"bindings"`. El menú de la bandeja pasa a llamarse **"Editar gestos y widgets…"**. Se recarga al guardar, igual que las formas.

```json
{
  "bindings": [ ... ],
  "widgets": [
    { "type": "media" },
    { "type": "volume" },
    { "type": "button", "label": "Chrome",   "icon": "🌐", "action": { "type": "launch", "target": "chrome.exe", "focusIfRunning": true } },
    { "type": "button", "label": "Copiar",   "icon": "📋", "action": { "type": "keys", "keys": "Ctrl+C" } },
    { "type": "button", "label": "Pegar",    "icon": "📄", "action": { "type": "keys", "keys": "Ctrl+V" } },
    { "type": "button", "label": "Captura",  "icon": "✂️", "action": { "type": "keys", "keys": "Win+Shift+S" } },
    { "type": "button", "label": "Bloquear", "icon": "🔒", "action": { "type": "mymouse", "command": "lock" } }
  ]
}
```

- `button`: `label` obligatorio (hasta 40 caracteres), `icon` opcional (hasta 8 bytes UTF-8: un emoji o 1–3 letras), `action` con el mismo formato que en `bindings`.
- `media` y `volume` no llevan más campos. Como mucho uno de cada; si se repiten, se ignora el segundo con aviso.
- Máximo 32 widgets. Los que sobran se ignoran con aviso.
- Si falta `"widgets"`, no hay panel. **El `gestures.json` de ejemplo que se crea la primera vez incluye la lista de arriba.** Un `gestures.json` de la v1 ya existente no se toca: el usuario añade los widgets a mano (los avisos de la bandeja lo explican).
- Las reglas mal escritas se ignoran con aviso, como en la v1.

## 4. Protocolo (versión 3)

El protocolo sube a la **versión 3**. La negociación es la misma: el host responde `HELLO_ACK(min(cliente, host))`; con versión < 3 no hay widgets.

### Cliente → host

| Tipo | Nombre | Payload | Bytes |
|---|---|---|---|
| `0x08` | `WIDGET_PRESS` | `u8 id` | 2 |
| `0x09` | `VOLUME_SET` | `u8 nivel` (0–100) | 2 |
| `0x0A` | `MEDIA_COMMAND` | `u8 orden`: 0 = reproducir/pausar, 1 = siguiente, 2 = anterior | 2 |
| `0x0B` | `VOLUME_MUTE` | `u8 silenciado` (0/1) | 2 |

### Host → cliente

| Tipo | Nombre | Payload | Bytes |
|---|---|---|---|
| `0x88` | `WIDGET_LAYOUT` | `u16 len` + `u8 n`, `n` × (`u8 id`, `u8 tipo` (0 = button, 1 = media, 2 = volume), `str8 etiqueta`, `str8 icono`) | variable |
| `0x89` | `VOLUME_STATE` | `u8 nivel` (0–100), `u8 silenciado` (0/1) | 3 |
| `0x8A` | `MEDIA_STATE` | `u16 len` + `u8 reproduciendo` (0/1), `str8 título`, `str8 artista` | variable |
| `0x8B` | `WIDGET_RESULT` | `u8 id`, `u8 estado` (mismos valores que `GESTURE_RESULT`) | 3 |

Reglas:
- `id` = posición del widget en la lista (0, 1, 2…). Un `WIDGET_LAYOUT` nuevo invalida los ids anteriores.
- El host envía `WIDGET_LAYOUT` justo después del `GESTURE_LIST` del handshake y cada vez que se recarga la configuración. Después, si hay widget `volume`, un `VOLUME_STATE`; si hay `media`, un `MEDIA_STATE`.
- `VOLUME_STATE` y `MEDIA_STATE` se envían cuando cambian en el PC, como mucho cada 100 ms (el último gana).
- `WIDGET_PRESS` de un `id` inexistente o que no es `button`: el host responde `WIDGET_RESULT(id, 2)`; no es error de protocolo.
- `VOLUME_SET` con nivel > 100: el host lo recorta a 100.
- En pausa (bandeja → Pausar), el host responde `WIDGET_RESULT(id, 2)` y no cambia volumen ni música.
- Vectores (añadir a PROTOCOL.md y a los tests de ambos lados):
  - `WIDGET_PRESS(3)` = `08 03`
  - `VOLUME_SET(55)` = `09 37`
  - `MEDIA_COMMAND(1)` = `0A 01`
  - `VOLUME_MUTE(1)` = `0B 01`
  - `VOLUME_STATE(40, 0)` = `89 28 00`
  - `WIDGET_RESULT(2, 0)` = `8B 02 00`
  - `WIDGET_LAYOUT([0, volume, "", ""])` = `88 05 00 01 00 02 00 00`
  - `MEDIA_STATE(1, "A", "B")` = `8A 05 00 01 01 41 01 42`

## 5. Host de Windows

- **Volumen:** Core Audio sobre el dispositivo de salida por defecto (`IAudioEndpointVolume`): leer, fijar (escalar 0–100 ↔ 0.0–1.0), silenciar y notificación de cambios. Se permite el paquete NuGet **NAudio.Wasapi** (MIT). Si cambia el dispositivo por defecto, se vuelve a enlazar.
- **Música:** `GlobalSystemMediaTransportControlsSessionManager` (WinRT) para título, artista y estado (requiere `TargetFramework` `net10.0-windows10.0.19041.0` en `MyMouse.Host`). Las órdenes ⏮ ⏯ ⏭ se envían como teclas multimedia (`MediaPlayPause`, `MediaNext`, `MediaPrevious`), que funcionan aunque la API de sesiones falle.
- Si la API de música o de volumen no está disponible, el widget se envía igual y el estado queda vacío ("Nada en reproducción" / volumen 0); se apunta en el log, sin avisos repetidos.
- La lógica que no depende de Windows va en `MyMouse.HostCore` con tests: parseo de `"widgets"`, `WidgetService` (layout, ejecución de `WIDGET_PRESS` con el mismo `IActionExecutor` de la v1, envío de estados con límite de frecuencia) detrás de interfaces `IVolumeController` y `IMediaController` con implementaciones falsas en los tests.

## 6. App Android

- `core`: mensajes nuevos en `Protocol.kt` (versión 3) con los vectores en `MessageCodecTest`; `WidgetLayout` (rejilla: posición y tamaño de cada widget dado el ancho, columnas y tipos) como lógica pura con tests.
- `app`: `WidgetPanelView` que dibuja el panel y gestiona sus toques; `MainActivity` coloca trackpad y panel (franja abajo/derecha según orientación) y respeta las barras del sistema como en la v0.5; `HostConnection` recibe `WIDGET_LAYOUT`, `VOLUME_STATE`, `MEDIA_STATE`, `WIDGET_RESULT` y envía los mensajes nuevos (solo con versión ≥ 3).
- Ajustes: "Mostrar panel de widgets" y "Tamaño del panel".
- Modo depuración: anota `Widget: Chrome`, `Volumen 55`, `⏭ Siguiente`… usando `ActionDescriber` o una función hermana.
- `WIDGET_RESULT` con estado ≠ 0: aviso de 1,5 s como en la v1 ("Chrome: sin acción en el PC" / "El PC no lo ha ejecutado").

## 7. Criterios de "hecho" de la v2

1. Abrir una aplicación configurada con un solo toque, sin mirar el PC.
2. Pausar, saltar canción y cambiar el volumen sin teclado; el volumen del móvil y el del PC coinciden en menos de 0,3 s en ambos sentidos.
3. Ningún toque del panel mueve el cursor ni hace clic (y al revés).
4. Tests de protocolo (ambos lados), del parseo de widgets, de `WidgetService` y de `WidgetLayout`.

## 8. Fuera de alcance
Editar widgets desde el móvil, widgets con estado propio (interruptores), varias páginas de widgets, iconos de imagen, control de volumen por aplicación.

## 9. Notas de implementación (desviaciones y detalles)

- **Mensajes sin widget.** `VOLUME_SET`, `VOLUME_MUTE` y `MEDIA_COMMAND` no se responden si salen bien, y se ignoran si el host no tiene el widget `volume` / `media`. En pausa, el host responde `WIDGET_RESULT` con el id del widget `volume` o `media` (la spec solo fija el id para `WIDGET_PRESS`).
- **Validación estricta al decodificar.** `MEDIA_COMMAND` con orden > 2, `VOLUME_STATE` con nivel > 100 o tipos de widget desconocidos son errores de protocolo. `VOLUME_SET` > 100 se acepta y el host lo recorta, como pide la spec.
- **Layout siempre.** Con versión >= 3, el host envía `WIDGET_LAYOUT` aunque esté vacío (así el móvil retira el panel si se borran los widgets).
- **Aviso al actualizar.** Un `gestures.json` de la v1 (sin `"widgets"`) no se toca; al arrancar, la bandeja avisa una vez de que existe el panel. Con `"widgets": []` no se vuelve a avisar.
- **Límites de configuración.** Etiqueta > 40 caracteres o icono > 8 bytes: el widget se ignora con aviso (no se recorta).
- **Multitoque.** El contenedor del móvil desactiva el reparto de toques entre vistas: todos los dedos van a la vista que recibió el primero.
- **Iconos del multimedia y del volumen** se dibujan con trazados (no con ⏮ ⏯ ⏭ 🔈), para no depender de la fuente del móvil. Los de los botones son texto del PC.
- **Lógica en `core`.** La rejilla (`WidgetLayout`) y la máquina de toques del panel (`WidgetPanelController`: soltar dentro, scroll, arrastre de volumen cada 50 ms) están en `core` con tests; `WidgetPanelView` solo dibuja.
- **No verificado sin dispositivos:** el volumen (NAudio) y la música (sesiones multimedia de Windows) solo se han compilado, no ejecutado.
