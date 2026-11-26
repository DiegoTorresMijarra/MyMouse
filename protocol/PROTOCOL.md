# Protocolo MyMouse

Versión actual: **2** (modo gestos). El host acepta también clientes de versión 1. Este documento es la **fuente de verdad**: las implementaciones de Android (`android/`) y de Windows (`windows/`) deben seguirlo, y sus tests usan los vectores de ejemplo del final.

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
- La mayoría de tipos tienen un payload de tamaño fijo, sin campo de longitud. Los de tamaño variable (`GESTURE`, `GESTURE_LIST`) llevan delante la longitud del payload en un `u16`.
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

### Host → cliente

| Tipo | Nombre | Payload | Bytes totales |
|---|---|---|---|
| `0x81` | `HELLO_ACK` | `u8 version` | 2 |
| `0x85` | `PONG` | — | 1 |
| `0x86` | `GESTURE_RESULT` (v2) | `u8 status`: 0 = ejecutado, 1 = sin asignar, 2 = error | 2 |
| `0x87` | `GESTURE_LIST` (v2) | `u16 len` + `str8 contexto`, `u8 n`, `n` × (`u8 flags`, `str8 nombre`, `str8 ayuda`) | variable |

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
  - El cliente lo envía en múltiplos de 120 (pasos enteros): muchas aplicaciones hacen un paso de zoom por evento, sea cual sea el valor.
  - `delta` en unidades de rueda: positivo = acercar. `120` equivale a un paso.
  - El host lo ejecuta como **Ctrl + rueda**: pulsa Ctrl, envía `WHEEL` con `delta` y suelta Ctrl. Funciona en navegadores, Office, VS Code, el Explorador y la mayoría de visores.
- **GESTURE** (v2). El móvil envía el nombre de la forma reconocida (`circle`, `line-right`, `custom:firma`…); las acciones se definen en el PC ([spec-v1-gestos.md](../docs/spec-v1-gestos.md)). El host responde siempre con un `GESTURE_RESULT`.
- **GESTURE_LIST** (v2). Las formas con acción asignada, para la chuleta del móvil. El host la envía entera justo después del `HELLO_ACK`, al recargar su configuración y al cambiar la ventana activa si hay reglas por aplicación.
  - `contexto`: nombre de la aplicación activa si hay reglas para ella; vacío si no.
  - `flags`: bit 0 = fijada (`pin`).
  - Como máximo 64 entradas; las cadenas, como máximo 255 bytes (el host recorta).

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
| `PING` | `05` |
| `PONG` | `85` |
