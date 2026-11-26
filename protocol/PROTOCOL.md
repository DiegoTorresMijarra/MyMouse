# Protocolo MyMouse

Versión actual: **1**. Este documento es la **fuente de verdad**: las implementaciones de Android (`android/`) y de Windows (`windows/`) deben seguirlo, y sus tests usan los vectores de ejemplo del final.

## Transporte

- TCP, sobre un único flujo de bytes.
- El host (Windows) escucha en `127.0.0.1:47800`.
- El cliente (Android) se conecta a `127.0.0.1:47800`. En v0, `adb reverse tcp:47800 tcp:47800` redirige esa conexión al PC.
- Ambos extremos activan `TCP_NODELAY`.
- El host acepta **un único cliente** a la vez. Una conexión nueva sustituye a la anterior.

## Formato de los mensajes

```
+--------+-----------------------+
| type   | payload               |
| 1 byte | tamaño fijo por tipo  |
+--------+-----------------------+
```

- Todos los enteros multibyte van en **little-endian**.
- Cada tipo tiene un tamaño de payload fijo, así que no hace falta un campo de longitud.
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
| `0x06` | `GESTURE` (reservado, v1) | `u8 len`, nombre UTF-8 ([detalle](../docs/spec-v1-gestos.md#8-protocolo)) | variable |

### Host → cliente

| Tipo | Nombre | Payload | Bytes totales |
|---|---|---|---|
| `0x81` | `HELLO_ACK` | `u8 version` | 2 |
| `0x85` | `PONG` | — | 1 |

### Detalle

- **HELLO.** Es obligatorio como primer mensaje del cliente. Si el host soporta la versión, responde `HELLO_ACK` con la misma versión. Si no, cierra la conexión. Cualquier otro mensaje recibido antes de `HELLO` también provoca el cierre.
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
- **GESTURE** (v1, reservado). Sustituye al antiguo `KEYCOMBO`: las acciones se definen en el PC.
  - `modifiers` es una máscara de bits: `0x01` Ctrl, `0x02` Shift, `0x04` Alt, `0x08` Win.
  - `vkey` es un *virtual-key code* de Windows.
  - El host pulsa los modificadores y la tecla, y después los suelta en orden inverso.
  - Un host de versión 1 que lo reciba lo trata como tipo desconocido.

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
| `PING` | `05` |
| `PONG` | `85` |
