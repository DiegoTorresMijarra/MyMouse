# MyMouse — Plan v0

> Objetivo: trackpad básico por USB, usable durante un día entero sin frustración.
> Contexto: [01-concepto.md](01-concepto.md) · [02-tecnico.md](02-tecnico.md)

## Estructura del repositorio

```
MyMouse/
├── android/            # App Android (Kotlin, Gradle)
├── windows/            # Host para Windows (.NET 10, WinForms, bandeja)
│   └── MyMouse.Host/
├── protocol/
│   └── PROTOCOL.md     # Especificación del protocolo (fuente de verdad)
└── docs/
```

## Protocolo v0

TCP. El móvil se conecta a `127.0.0.1:47800` y `adb reverse` lo redirige al PC. Se usa `TCP_NODELAY`. Los mensajes son binarios y little-endian: 1 byte de tipo seguido de un payload de tamaño fijo.

| Tipo | Nombre | Payload | Notas |
|---|---|---|---|
| `0x01` | HELLO | `u8 version` | Primer mensaje. El host cierra la conexión si no soporta la versión. |
| `0x02` | MOVE | `i16 dx, i16 dy` | Píxeles ya acelerados por el móvil. |
| `0x03` | BUTTON | `u8 button (0=izq, 1=der, 2=medio), u8 down (0/1)` | Down y up por separado, para permitir arrastrar. |
| `0x04` | SCROLL | `i16 vertical, i16 horizontal` | En unidades de rueda; 120 = un "clic" de rueda. |
| `0x05` | PING | — | Keepalive cada segundo; el host responde PING. |

v1 añadirá `0x06 KEYCOMBO` (modificadores + tecla virtual) para el modo gestos.

## Hitos

### M0: entorno (manual, en tu PC y móvil)
- [ ] Confirmar la versión de Android del P10.
- [ ] Revisar el estado físico de la batería.
- [ ] Activar las opciones de desarrollador (tocar 7 veces "Número de compilación"), la depuración USB y "depuración ADB en modo solo carga".
- [ ] Instalar el driver ADB (HiSuite) y platform-tools en Windows.
- [ ] Comprobar que `adb devices` lista el móvil como `device`, no como `unauthorized`.
- [ ] (Opcional) Probar el Bluetooth HID con una app de la Play Store.

### M1: host de Windows
- [ ] App de bandeja en .NET 10 con icono, menú (Salir, Pausar) y estado de conexión.
- [ ] Servidor TCP en `127.0.0.1:47800` que acepta un único cliente.
- [ ] Parser del protocolo, con tests unitarios.
- [ ] Inyección con `SendInput`: MOVE, BUTTON y SCROLL.
- [ ] Ejecutar `adb reverse tcp:47800 tcp:47800` al arrancar y repetirlo cada pocos segundos, por si el móvil se desconecta y vuelve a conectarse.
- [ ] Cliente de prueba (consola) que envía movimientos, para validar sin el móvil.

### M2: app Android mínima
- [ ] Una sola Activity a pantalla completa, en modo inmersivo, fondo negro y `KEEP_SCREEN_ON`.
- [ ] Cliente TCP con reconexión automática y un indicador de estado (conectado o no).
- [ ] Un dedo mueve el cursor, leyendo también las muestras históricas de `MotionEvent`.

### M3: interacciones de trackpad
- [ ] Tocar = clic izquierdo (con umbral de tiempo y distancia).
- [ ] Tocar con dos dedos = clic derecho.
- [ ] Arrastrar con dos dedos = scroll vertical y horizontal.
- [ ] Doble toque y arrastrar = mantener el clic pulsado (seleccionar, mover ventanas).

### M4: ajuste fino
- [ ] Curva de aceleración propia y sensibilidad configurables.
- [ ] Velocidad de scroll configurable.
- [ ] Atenuar la pantalla tras X segundos sin uso.
- [ ] Prueba de un día entero y lista de molestias, que alimentará la v1.

## Fuera de v0
Modo gestos, widgets, conexión inalámbrica, AOA, instalador, arranque automático con Windows.
