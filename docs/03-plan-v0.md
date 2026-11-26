# MyMouse — Plan v0

> Objetivo: trackpad básico por USB, usable durante un día entero sin frustración.
> Contexto: [00-roadmap.md](00-roadmap.md) · [spec-v0.md](spec-v0.md) · [02-tecnico.md](02-tecnico.md)
>
> Leyenda: `[x]` hecho y probado · `[~]` código escrito, falta probar en el dispositivo real · `[ ]` pendiente

## Estructura del repositorio

```
MyMouse/
├── android/            # App Android (Kotlin, Gradle)
│   ├── core/           # Lógica pura: protocolo, gestos, aceleración (tests en JVM)
│   └── app/            # Activity, vista del trackpad, conexión
├── windows/            # Host para Windows (.NET 10)
│   ├── src/MyMouse.Protocol/    # Codec del protocolo
│   ├── src/MyMouse.HostCore/    # Sesión, servidor TCP, adb reverse
│   ├── src/MyMouse.Host/        # App de bandeja + SendInput
│   ├── src/MyMouse.TestClient/  # Simula el móvil
│   └── tests/
├── protocol/
│   └── PROTOCOL.md     # Especificación del protocolo (fuente de verdad)
└── docs/
```

## Protocolo

Especificado en [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md).

## Hitos

### M0: entorno (manual, en tu PC y móvil)
- [ ] Confirmar la versión de Android del P10.
- [ ] Revisar el estado físico de la batería.
- [ ] Activar las opciones de desarrollador (tocar 7 veces "Número de compilación"), la depuración USB y "depuración ADB en modo solo carga".
- [ ] Instalar el driver ADB (HiSuite) y platform-tools en Windows.
- [ ] Comprobar que `adb devices` lista el móvil como `device`, no como `unauthorized`.
- [ ] (Opcional) Probar el Bluetooth HID con una app de la Play Store.
- [ ] (Opcional) Comprobar que EMUI acepta `dpm set-device-owner` (necesario para el modo bloqueado completo).

### M1: host de Windows
- [~] App de bandeja en .NET 10 con menú (Pausar, Logs, Salir), estado en el tooltip e icono por estado (release v0.2).
- [x] Servidor TCP en `127.0.0.1:47800` que acepta un único cliente.
- [x] Parser del protocolo, con tests unitarios.
- [~] Inyección con `SendInput`: MOVE, BUTTON y SCROLL.
- [~] Ejecutar `adb reverse tcp:47800 tcp:47800` al arrancar y repetirlo cada 3 s, por si el móvil se desconecta y vuelve a conectarse.
- [x] Cliente de prueba (consola) que envía movimientos, para validar sin el móvil.

### M2: app Android mínima
- [~] Una sola Activity a pantalla completa, en modo inmersivo, fondo negro y `KEEP_SCREEN_ON`.
- [~] Cliente TCP con reconexión automática y un indicador de estado (conectado o no).
- [~] Un dedo mueve el cursor, leyendo también las muestras históricas de `MotionEvent`.

### M3: interacciones de trackpad
- [~] Tocar = clic izquierdo (con umbral de tiempo y distancia). La lógica está testeada.
- [~] Tocar con dos dedos = clic derecho. La lógica está testeada.
- [~] Arrastrar con dos dedos = scroll vertical y horizontal. La lógica está testeada.
- [~] Pellizco = zoom (Ctrl + rueda). La lógica está testeada. (release v0.1)
- [~] Doble toque y arrastrar = mantener el clic pulsado (seleccionar, mover ventanas). La lógica está testeada.

### M4: ajuste fino
- [~] Curva de aceleración propia (implementada y testeada). Falta la UI de ajustes y guardarlos.
- [ ] Velocidad de scroll configurable.
- [ ] Atenuar la pantalla tras X segundos sin uso (sin apagarla).
- [ ] Modo bloqueado básico (`startLockTask`) y salida con doble pulsación de Volumen −.
- [~] Chuleta de gestos básicos en el centro del trackpad (release v0.2).
- [ ] Modo ventana: trackpad con margen, botones Ajustes y Salir.
- [ ] (Opcional) Modo bloqueado completo como propietaria del dispositivo, arranque al encender.
- [ ] Prueba de un día entero y lista de molestias, que alimentará la v1.

## Fuera de v0
Modo gestos, widgets, conexión inalámbrica, AOA, instalador, arranque automático con Windows.
