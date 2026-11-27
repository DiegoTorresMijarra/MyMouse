# Protocolo MyMouse

Versión actual: **8** (v5 = toolbox del PC: panel, gesto en vivo y formas sincronizadas; v6 = `APP_VERSION`, avisos de versión cruzados; v7 = modo teclado: `KEY`, `KEYBOARD_MODE`, `KEYBOARD_OPEN`, `KEYBOARD_LEDS`, `KEYBOARD_LAYOUT`; v8 = `THEME`, tema sincronizado). El host acepta también clientes de versión 1, 2, 3, 4, 5, 6 y 7. Este documento es la **fuente de verdad**: las implementaciones de Android (`android/`) y de Windows (`windows/`) deben seguirlo, y sus tests usan los vectores de ejemplo del final.

## Transporte

- TCP, sobre un único flujo de bytes.
- El host (Windows) escucha en `127.0.0.1:47800`.
- El cliente (Android) se conecta a `127.0.0.1:47800`. En v0, `adb reverse tcp:47800 tcp:47800` redirige esa conexión al PC.
- Ambos extremos activan `TCP_NODELAY`.
- El host acepta **un único cliente** a la vez. Una conexión nueva sustituye a la anterior.

## Formato de los mensajes

```
Tamaño fijo:      +--------+-----------------------+
                  | type   | payload               |
                  | 1 byte | tamaño fijo por tipo  |
                  +--------+-----------------------+

Tamaño variable:  +--------+-----------+---------------------+
                  | type   | u16 len   | payload (len bytes) |
                  +--------+-----------+---------------------+
```

- Todos los enteros multibyte van en **little-endian**.
- La mayoría de tipos tienen un payload de tamaño fijo, sin campo de longitud. Los de tamaño variable (`GESTURE`, `GESTURE_LIST`, `WIDGET_LAYOUT`, `MEDIA_STATE`, `STROKE_POINTS`, `SHAPE_INFO`, `SHAPE_RESULT`, `SHAPE_ADD`, `SHAPE_DELETE`, `APP_VERSION`, `KEYBOARD_LAYOUT`, `THEME`) llevan delante la longitud del payload en un `u16`.
- `str8` = `u8` longitud en bytes + texto UTF-8.
- Si un extremo recibe un tipo desconocido, **cierra la conexión**: no es posible resincronizar el flujo.

## Mensajes

### Cliente → host

| Tipo | Nombre | Payload | Bytes totales |
|---|---|---|---|
| `0x01` | `HELLO` | `u8 version` | 2 |
| `0x02` | `MOVE` | `i16 dx`, `i16 dy` | 5 |
| `0x03` | `BUTTON` | `u8 button`, `u8 pressed` | 3 |
| `0x04` | `SCROLL` | `i16 vertical`, `i16 horizontal` | 5 |
| `0x05` | `PING` | — | 1 |
| `0x06` | `GESTURE` (v2) | `u16 len` + nombre de la forma en UTF-8 (1–64 bytes) | variable |
| `0x07` | `ZOOM` | `i16 delta` | 3 |
| `0x08` | `WIDGET_PRESS` (v3) | `u8 id` | 2 |
| `0x09` | `VOLUME_SET` (v3) | `u8 nivel` (0–100) | 2 |
| `0x0A` | `MEDIA_COMMAND` (v3) | `u8 orden`: 0 = reproducir/pausar, 1 = siguiente, 2 = anterior | 2 |
| `0x0B` | `VOLUME_MUTE` (v3) | `u8 silenciado` (0/1) | 2 |
| `0x0C` | `SYSTEM_GESTURE` (v4) | `u8 orden`: 0 = vista de tareas, 1 = escritorio, 2 = empezar cambio de app, 3 = siguiente app, 4 = app anterior, 5 = terminar cambio de app | 2 |

| `0x0D` | `TOOLBOX` (v5) | `u8`: 1 = mostrar, 0 = ocultar (4 dedos ↑ / ↓) | 2 |
| `0x0E` | `GESTURE_MODE` (v5) | `u8` 1 = activado, 0 = terminado; `u16` tiempo para empezar a dibujar, en ms | 4 |
| `0x0F` | `STROKE_POINTS` (v5) | `u16 len` + `u8 n`, `n` × (`u16 x`, `u16 y`) | variable |
| `0x10` | `STROKE_END` (v5) | `u8`: 0 = reconocida (le sigue `GESTURE`), 1 = no reconocida, 2 = cancelada, 3 = tiempo agotado | 2 |
| `0x11` | `SHAPE_INFO` (v5) | `u16 len` + `str8 nombre`, `u8 muestras`, por muestra `u8 n` + `n` × (`u16 x`, `u16 y`) | variable |
| `0x12` | `SHAPE_LIST_END` (v5) | `u8` número de formas propias enviadas | 2 |
| `0x13` | `SHAPE_RESULT` (v5) | `u16 len` + `str8 nombre`, `u8 estado` (0 = guardada, 1 = se confunde, 2 = nombre repetido, 3 = no válida, 4 = borrada, 5 = no existe), `str8` forma con la que se confunde o vacío | variable |
| `0x14` | `APP_VERSION` (v6) | `u16 len` + `str8 versión` de la app del móvil (p. ej. `5.0` o `0.0-dev.42`) | variable |
| `0x15` | `KEY` (v7) | `u8 usage` (uso HID, página 0x07), `u8 down` (1 pulsar, 0 soltar) | 3 |
| `0x16` | `KEYBOARD_MODE` (v7) | `u8 on` (1 entra en el modo teclado, 0 sale) | 2 |
| `0x17` | `THEME` (v8) | `u16 len` + `str8 tema`, `str8 acento`, `u8 estrellas` (0/1), `i64 instante` del último cambio (ms desde 1970, UTC) | variable |

### Host → cliente

| Tipo | Nombre | Payload | Bytes totales |
|---|---|---|---|
| `0x81` | `HELLO_ACK` | `u8 version` | 2 |
| `0x85` | `PONG` | — | 1 |
| `0x86` | `GESTURE_RESULT` (v2) | `u8 status`: 0 = ejecutado, 1 = sin asignar, 2 = error | 2 |
| `0x87` | `GESTURE_LIST` (v2) | `u16 len` + `str8 contexto`, `u8 n`, `n` × (`u8 flags`, `str8 nombre`, `str8 ayuda`) | variable |
| `0x88` | `WIDGET_LAYOUT` (v3) | `u16 len` + `u8 n`, `n` × (`u8 id`, `u8 tipo` (0 = button, 1 = media, 2 = volume), `str8 etiqueta`, `str8 icono`) | variable |
| `0x89` | `VOLUME_STATE` (v3) | `u8 nivel` (0–100), `u8 silenciado` (0/1) | 3 |
| `0x8A` | `MEDIA_STATE` (v3) | `u16 len` + `u8 reproduciendo` (0/1), `str8 título`, `str8 artista` | variable |
| `0x8B` | `WIDGET_RESULT` (v3) | `u8 id`, `u8 estado` (mismos valores que `GESTURE_RESULT`) | 3 |
| `0x8C` | `SHAPE_ADD` (v5) | Como `SHAPE_INFO` y, al final, `u8 force` (1 = guardar aunque se confunda) | variable |
| `0x8D` | `SHAPE_DELETE` (v5) | `u16 len` + `str8 nombre` | variable |
| `0x8E` | `APP_VERSION` (v6) | Igual que `0x14`, con la versión de la app del PC | variable |
| `0x8F` | `KEYBOARD_OPEN` (v7) | — (el widget Teclado pide abrir el teclado del móvil) | 1 |
| `0x90` | `KEYBOARD_LEDS` (v7) | `u8 bits`: 1 = Bloq Mayús, 2 = Bloq Num, 4 = Bloq Despl | 2 |
| `0x91` | `KEYBOARD_LAYOUT` (v7) | `u16 len` + `str8 id` de la distribución (`es`, `us`) | variable |
| `0x92` | `THEME` (v8) | Igual que `0x17`, con el tema del PC | variable |

### Detalle

- **HELLO.** Es obligatorio como primer mensaje del cliente, con la versión más alta que conoce. El host responde `HELLO_ACK` con la versión que se usará: la menor entre la del cliente y la suya. Si la del cliente es 0, cierra la conexión. El cliente acepta cualquier versión entre 1 y la suya; con versión 1 no usa el modo gestos. Cualquier otro mensaje recibido antes de `HELLO` también provoca el cierre.
- **MOVE.** Desplazamiento relativo en píxeles de pantalla, **ya acelerado** por el cliente. Positivo = derecha / abajo. El host no aplica escalado.
- **BUTTON.**
  - `button`: `0` = izquierdo, `1` = derecho, `2` = central. Otros valores son un error de protocolo.
  - `pressed`: `1` = pulsar, `0` = soltar.
  - Un clic son dos mensajes (pulsar y soltar).
  - Al cerrarse la conexión, el host **suelta cualquier botón que siga pulsado**.
- **SCROLL.** En unidades de rueda de Windows: `120` equivale a un paso de rueda.
  - `vertical` positivo = scroll hacia arriba (contenido baja), como `WHEEL_DELTA` en Windows.
  - `horizontal` positivo = scroll hacia la derecha.
  - Se permiten valores menores que 120 (scroll suave).
- **PING / PONG.** El cliente envía `PING` cada segundo y el host responde `PONG`. Si cualquiera de los dos extremos pasa **3 s sin recibir nada**, da la conexión por muerta.
- **ZOOM.** Pellizco en el trackpad. Entra en la versión 1 porque se añadió antes de publicar ninguna.
  - Hasta la v3.0 el cliente lo enviaba en múltiplos de 120 (pasos enteros). Desde la v3.1 envía deltas finos (como un touchpad de precisión), como mucho cada 16 ms.
  - `delta` en unidades de rueda: positivo = acercar. `120` equivale a un paso.
  - El host lo ejecuta como **Ctrl + rueda**: pulsa Ctrl, envía `WHEEL` con `delta` y suelta Ctrl. Funciona en navegadores, Office, VS Code, el Explorador y la mayoría de visores.
- **GESTURE** (v2). El móvil envía el nombre de la forma reconocida (`circle`, `line-right`, `custom:firma`…); las acciones se definen en el PC ([spec-v1-gestos.md](../docs/spec-v1-gestos.md)). El host responde siempre con un `GESTURE_RESULT`.
- **GESTURE_LIST** (v2). Las formas con acción asignada, para la chuleta del móvil. El host la envía entera justo después del `HELLO_ACK`, al recargar su configuración y al cambiar la ventana activa si hay reglas por aplicación.
  - `contexto`: nombre de la aplicación activa si hay reglas para ella; vacío si no.
  - `flags`: bit 0 = fijada (`pin`).
  - Como máximo 64 entradas; las cadenas, como máximo 255 bytes (el host recorta).

- **Widgets** (v3). Panel de controles de un toque junto al trackpad ([spec-v2-widgets.md](../docs/spec-v2-widgets.md)). Solo se envían y aceptan con versión negociada ≥ 3: un cliente v2 contra el host nuevo funciona como antes, y un mensaje de widgets con versión < 3 es un error de protocolo.
  - `id` = posición del widget en la lista (0, 1, 2…). Un `WIDGET_LAYOUT` nuevo invalida los ids anteriores. Como máximo 32 widgets; los textos, como máximo 255 bytes (el host recorta).
  - El host envía `WIDGET_LAYOUT` justo después del `GESTURE_LIST` del handshake (aunque esté vacío) y cada vez que recarga su configuración. Después, si hay widget `volume`, un `VOLUME_STATE`; si hay `media`, un `MEDIA_STATE`.
  - `VOLUME_STATE` y `MEDIA_STATE` se envían cuando cambian en el PC, como mucho cada 100 ms (el último gana). También como eco de lo que pide el propio móvil.
  - `WIDGET_PRESS` de un `id` inexistente o que no es `button`: el host responde `WIDGET_RESULT(id, 2)`; no es error de protocolo. En los demás casos responde `WIDGET_RESULT(id, estado)` con el resultado de la acción.
  - `VOLUME_SET` con nivel > 100: el host lo recorta a 100. `MEDIA_COMMAND` con una orden desconocida es un error de protocolo. `VOLUME_SET`, `VOLUME_MUTE` y `MEDIA_COMMAND` no se responden si se ejecutan, y se ignoran si el host no tiene el widget correspondiente.
  - En pausa, el host responde `WIDGET_RESULT(id, 2)` a `WIDGET_PRESS` y, a las órdenes de volumen o música, `WIDGET_RESULT` con el id de su widget (`volume` o `media`); no cambia volumen ni música.
  - `VOLUME_STATE` con un nivel > 100 es un error de protocolo.

- **Gestos de Windows** (v4). Los gestos de 3 dedos del trackpad ([spec-v3.1-ajustes.md](../docs/spec-v3.1-ajustes.md)). Solo se envían y aceptan con versión negociada ≥ 4: un mensaje `SYSTEM_GESTURE` con versión < 4 es un error de protocolo, y con un host de versión < 4 el móvil no los envía (los anota en el modo depuración). Una orden desconocida (> 5) también es un error de protocolo. No se responden.
  - `0` vista de tareas: el host pulsa y suelta `Win+Tab`. `1` escritorio: `Win+D`.
  - Cambio de app: `2` pulsa `Alt` y lo mantiene; `3` envía `Tab` y `4` envía `Shift+Tab` con `Alt` pulsado; `5` suelta `Alt`. Un `3` o `4` sin un `2` previo se ignora, igual que un `2` repetido o un `5` sin cambio en curso.
  - **El host debe soltar `Alt`** si la sesión termina (cierre, timeout o error) o si el PC está en pausa a mitad de un cambio de app. En pausa, los `SYSTEM_GESTURE` se descartan.
  - Por Bluetooth no se usa este mensaje: el móvil envía los informes de teclado HID equivalentes.
- **ZOOM y SCROLL con deltas finos** (v4, sin cambio de formato). Desde la v3.1 el cliente envía `ZOOM` con deltas proporcionales (no múltiplos de 120) y `SCROLL` con valores pequeños; el host los pasa tal cual a la rueda (`Ctrl` + rueda en el zoom). Un host anterior los acepta igual.

- **Toolbox del PC** (v5). Panel flotante, gesto en vivo y formas sincronizadas ([spec-v4-toolbox.md](../docs/spec-v4-toolbox.md)). Todos estos mensajes solo se envían y aceptan con versión negociada ≥ 5: con un host o un móvil anterior no hay toolbox desde el móvil, ni gesto en vivo, ni sincronización de formas (lo demás funciona). Un mensaje de la v5 con versión < 5 es un error de protocolo.
  - `TOOLBOX`: el móvil lo envía con 4 dedos hacia arriba (1) o hacia abajo (0). No se responde. En pausa, el host lo atiende igual (la toolbox sigue siendo útil).
  - **Gesto en vivo.** Al activarse el modo gestos el móvil envía `GESTURE_MODE(1, tiempo)`; mientras se dibuja, `STROKE_POINTS` en lotes de como mucho 16 ms (el primer lote de un trazo lleva el primer punto); al terminar, `STROKE_END` y, si se reconoció, `GESTURE` como hasta ahora (el `GESTURE_RESULT` llega igual). Por último, `GESTURE_MODE(0, 0)`. Un trazo termina siempre con `STROKE_END`, también si se cancela (2) o se agota el tiempo (3).
  - Las coordenadas de `STROKE_POINTS` y de las muestras de las formas están normalizadas a 0–65535 **con la misma escala en los dos ejes** (el lado largo del área, o de la caja de la forma, ocupa todo el rango; el corto, menos), de modo que el trazo conserva sus proporciones. El origen es la esquina superior izquierda. Lotes de 1 a 255 puntos.
  - **Formas.** El móvil es el dueño de las formas. Tras el handshake (versión ≥ 5) envía un `SHAPE_INFO` por cada forma propia (`custom:<nombre>`; el catálogo de 15 no se envía) y luego `SHAPE_LIST_END` con el total; lo repite entero cada vez que cambian sus formas. Cada muestra se remuestrea a 32 puntos. Como máximo 255 formas.
  - `SHAPE_ADD` (PC → móvil): el móvil valida (nombre `custom:` + texto, ≤ 64 bytes; al menos una muestra con 2 o más puntos y un tamaño mínimo), comprueba que no se confunda con otra forma activa salvo que `force` sea 1, la guarda y responde `SHAPE_RESULT`: 0 guardada, 1 se confunde (con la forma del último campo), 2 nombre repetido, 3 no válida. Si se guarda, además reenvía la lista completa.
  - `SHAPE_DELETE`: borra una forma propia; responde `SHAPE_RESULT` con 4 (borrada) o 5 (no existe) y, si borró, reenvía la lista.

- **Versión de la app** (v6). Avisos de versión cruzados ([spec-v5.1-actualizaciones.md](../docs/spec-v5.1-actualizaciones.md) §6). Cada lado envía su versión (`versionName` del móvil, versión del ensamblado del PC) **una vez, justo tras el handshake**, con versión negociada ≥ 6; con una versión negociada < 6 no se envía y un `APP_VERSION` recibido con versión < 6 es un error de protocolo. Es solo informativo y no se responde. El payload es un `str8` de hasta 64 bytes UTF-8 (cadena vacía permitida: "desconocida"). Los dos tipos tienen el mismo formato pero números distintos (`0x14` móvil → PC, `0x8E` PC → móvil) porque los tipos de cada sentido no se solapan. Las versiones `0.0-dev.N` son de desarrollo y no provocan avisos.

- **Modo teclado** (v7). Un teclado de ordenador de verdad en el móvil ([spec-v6-teclado.md](../docs/spec-v6-teclado.md)). Los cinco mensajes solo se envían y aceptan con versión negociada ≥ 7: con una versión menor, recibir cualquiera de ellos es un error de protocolo, y un cliente o host anterior sigue funcionando como siempre. `KEYBOARD_OPEN`, `KEYBOARD_LEDS` y `KEYBOARD_LAYOUT` son solo del PC al móvil: si llegan al host, es un error de protocolo.
  - **`KEY`.** El móvil envía pulsar y soltar de **teclas físicas** por su uso HID (página 0x07 de teclado): letras `0x04`–`0x1D`, números `0x1E`–`0x27`, `0x28`–`0x39`, F1–F12 `0x3A`–`0x45`, edición y flechas `0x46`–`0x52`, numpad `0x53`–`0x63`, `<>` de la ISO `0x64`, Menú `0x65` y los modificadores `0xE0`–`0xE7` (Ctrl izq. `0xE0`, Shift izq. `0xE1`, Alt `0xE2`, Win `0xE3`, Alt dcha. = AltGr `0xE6`). Quien convierte teclas en caracteres es **Windows**, con su distribución: así funcionan cualquier combinación y las teclas muertas. El host las inyecta con `SendInput` y `KEYEVENTF_SCANCODE` (con `KEYEVENTF_EXTENDEDKEY` en las teclas extendidas), a partir de la tabla HID → scancode (conjunto 1) de `HostCore`.
  - **Un `KEY` con un uso que el PC no conoce se ignora** (es un valor, no un tipo de mensaje: no rompe el flujo). Un `KEY` con `down` distinto de 0 o 1 es un error de protocolo. Un "soltar" de una tecla que no está pulsada se ignora.
  - **Repetición.** Windows no repite las teclas inyectadas: por cable el móvil repite el "pulsar" de la tecla mantenida (a los 400 ms y luego cada 40 ms) con `KEY down` repetidos **sin `up` en medio**, como hace un teclado real; el host inyecta cada uno.
  - **`KEYBOARD_MODE`.** El móvil lo envía al entrar (1) y al salir (0) del modo teclado. No se responde. En pausa, el host lo atiende igual pero no inyecta ninguna tecla.
  - **Nada se queda pulsado.** El host guarda qué teclas tiene pulsadas cada sesión y las **suelta él solo** si llega `KEYBOARD_MODE` apagado, si el PC pasa a pausa o si la sesión termina (cierre, timeout o error): primero las normales y después los modificadores. El móvil, por su parte, envía "soltar" de todo lo que tenga pulsado al salir, al perder el foco o al cambiar de bloques.
  - **`KEYBOARD_LEDS` y `KEYBOARD_LAYOUT`.** Al recibir `KEYBOARD_MODE` encendido, el host envía las luces (`KEYBOARD_LEDS`, con `GetKeyState`) y la distribución activa de la ventana en primer plano (`KEYBOARD_LAYOUT`, con `GetKeyboardLayout`: `es` si es español de España, `us` con cualquier otra), y después solo cuando cambian (comprueba cada 250 ms mientras el modo está activo). `KEYBOARD_LAYOUT` lleva un `str8` de hasta 16 bytes.
  - **`KEYBOARD_OPEN`.** Lo envía el host cuando el widget Teclado (acción `{"type":"mymouse","command":"keyboard"}`) se ejecuta; el móvil abre su teclado. Con un móvil de versión < 7 la acción falla en el PC (no se envía nada).
  - Por Bluetooth no se usa nada de esto: el móvil envía el informe de teclado HID (id 2) con las teclas pulsadas (hasta 6 normales a la vez) y recibe las luces por el informe de salida de LEDs del mismo teclado.
- **Tema sincronizado** (v8). El tema, el acento y las estrellas se sincronizan entre móvil y PC ([spec-v6.1-tema-sincronizado.md](../docs/spec-v6.1-tema-sincronizado.md)). Solo con versión negociada ≥ 8: con una versión menor, recibir `THEME` es un error de protocolo, y un `0x92` que llega al host también.
  - Cada lado envía su `THEME` **una vez justo tras el handshake** y después solo cuando el usuario lo cambia en ese lado. Lo que se adopta del otro lado no se reenvía.
  - El instante es el del último cambio del tema, el acento o las estrellas (ms desde 1970, UTC; 0 = nunca). **Gana el más reciente**; con el mismo instante y distinto aspecto, gana el móvil. Lo adoptado se guarda con su instante.
  - Ids de hasta 32 bytes UTF-8; un id desconocido se resuelve como en la v4.8 (Universo, Automático). Un instante negativo es un error de protocolo.

## Versionado

- Si se añade un tipo nuevo o cambia un payload, **se incrementa la versión**.
- El host puede soportar varias versiones; el cliente envía la más alta que conoce.

## Vectores de ejemplo (hex)

| Mensaje | Bytes |
|---|---|
| `HELLO(1)` | `01 01` |
| `HELLO_ACK(1)` | `81 01` |
| `MOVE(dx=5, dy=-3)` | `02 05 00 FD FF` |
| `MOVE(dx=-300, dy=1000)` | `02 D4 FE E8 03` |
| `BUTTON(left, pressed)` | `03 00 01` |
| `BUTTON(right, released)` | `03 01 00` |
| `SCROLL(v=120, h=-40)` | `04 78 00 D8 FF` |
| `ZOOM(-120)` | `07 88 FF` |
| `GESTURE("z")` | `06 01 00 7A` |
| `GESTURE_RESULT(1)` | `86 01` |
| `GESTURE_LIST("", [fijada "z" "Deshacer"])` | `87 0E 00 00 01 01 01 7A 08 44 65 73 68 61 63 65 72` |
| `WIDGET_PRESS(3)` | `08 03` |
| `VOLUME_SET(55)` | `09 37` |
| `MEDIA_COMMAND(1)` | `0A 01` |
| `VOLUME_MUTE(1)` | `0B 01` |
| `VOLUME_STATE(40, 0)` | `89 28 00` |
| `WIDGET_RESULT(2, 0)` | `8B 02 00` |
| `WIDGET_LAYOUT([0, volume, "", ""])` | `88 05 00 01 00 02 00 00` |
| `MEDIA_STATE(1, "A", "B")` | `8A 05 00 01 01 41 01 42` |
| `SYSTEM_GESTURE(vista de tareas)` | `0C 00` |
| `SYSTEM_GESTURE(empezar cambio de app)` | `0C 02` |
| `SYSTEM_GESTURE(terminar cambio de app)` | `0C 05` |
| `TOOLBOX(mostrar)` | `0D 01` |
| `TOOLBOX(ocultar)` | `0D 00` |
| `GESTURE_MODE(activado, 3000 ms)` | `0E 01 B8 0B` |
| `STROKE_POINTS([(0,0), (65535,32768)])` | `0F 09 00 02 00 00 00 00 FF FF 00 80` |
| `STROKE_END(reconocida)` | `10 00` |
| `STROKE_END(tiempo agotado)` | `10 03` |
| `SHAPE_INFO("a", [[(1,2), (3,4)]])` | `11 0C 00 01 61 01 02 01 00 02 00 03 00 04 00` |
| `SHAPE_LIST_END(3)` | `12 03` |
| `SHAPE_RESULT("a", se confunde, "z")` | `13 05 00 01 61 01 01 7A` |
| `SHAPE_ADD("a", [[(1,2), (3,4)]], force)` | `8C 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 01` |
| `SHAPE_DELETE("a")` | `8D 02 00 01 61` |
| `APP_VERSION("5.0")` (móvil) | `14 04 00 03 35 2E 30` |
| `APP_VERSION("5.0")` (PC) | `8E 04 00 03 35 2E 30` |
| `KEY(0x04, down)` | `15 04 01` |
| `KEY(0xE1, up)` | `15 E1 00` |
| `KEYBOARD_MODE(on)` | `16 01` |
| `KEYBOARD_MODE(off)` | `16 00` |
| `KEYBOARD_OPEN` | `8F` |
| `KEYBOARD_LEDS(Mayús + Num)` | `90 03` |
| `KEYBOARD_LAYOUT("es")` | `91 03 00 02 65 73` |
| `KEYBOARD_LAYOUT("us")` | `91 03 00 02 75 73` |
| `THEME("graphite", "auto", estrellas, 1000)` (móvil) | `17 17 00 08 67 72 61 70 68 69 74 65 04 61 75 74 6F 01 E8 03 00 00 00 00 00 00` |
| `THEME("universe", "steel", sin estrellas, 0x0102030405060708)` (PC) | `92 18 00 08 75 6E 69 76 65 72 73 65 05 73 74 65 65 6C 00 08 07 06 05 04 03 02 01` |
| `PING` | `05` |
| `PONG` | `85` |
