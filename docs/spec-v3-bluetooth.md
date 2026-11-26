# MyMouse — Especificación v3: sin cable / sin fricción

> Objetivos: [00-roadmap.md](00-roadmap.md#v3-sin-cable--sin-fricción) · Base: [spec-v0.md](spec-v0.md), [spec-v1-gestos.md](spec-v1-gestos.md), [spec-v2-widgets.md](spec-v2-widgets.md)
>
> Estado: **implementada** (release `v3.0`), sin probar en dispositivos reales. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Qué cambia

Dos formas de conectar el móvil, a elegir en Ajustes → **Conexión**:

| | **USB** (como hasta ahora) | **Bluetooth** (nuevo) |
|---|---|---|
| En el PC | MyMouse (bandeja) + adb | **Nada**: el móvil se empareja como ratón y teclado Bluetooth |
| Cable | Sí, con depuración USB | No |
| Trackpad, clics, scroll, zoom | Sí | Sí |
| Modo gestos | Sí (acciones en el PC) | No *(decisión: las acciones viven en el PC; ver §5)* |
| Widgets | Los configurados en el PC | Panel fijo: ⏮ ⏯ ⏭, Vol −, Vol +, Silencio |
| Funciona con | Windows | Windows, y en principio cualquier sistema con Bluetooth HID (macOS, Linux, tablets…) |

Además, en el PC (modo USB): opción de bandeja **"Iniciar con Windows"**.

*(decisión)* **AOA (Android Open Accessory) queda descartado**: obliga a instalar un driver WinUSB en el PC, que es justo la fricción que esta versión quiere quitar. Bluetooth HID no necesita nada en el PC.

## 2. Bluetooth HID en el móvil

- API `BluetoothHidDevice` (Android 9+). El móvil registra un dispositivo HID compuesto con tres informes:

| Report ID | Qué | Contenido (bytes tras el ID) |
|---|---|---|
| 1 | Ratón | `u8 botones` (bit 0 izq., 1 der., 2 central), `i8 x`, `i8 y`, `i8 rueda`, `i8 rueda horizontal` (AC Pan) |
| 2 | Teclado | `u8 modificadores`, `u8 reservado`, 6 × `u8 tecla` |
| 3 | Consumo (multimedia) | `u16 uso` (0 = soltar) |

- Traducción de las acciones del trackpad (lógica pura en `core`, `HidMapper`, con tests):
  - `MOVE`: se parte en trozos de ±127 por informe.
  - `BUTTON`: el estado de los botones se mantiene y se envía con movimiento 0.
  - `SCROLL`: las unidades de rueda (120 = 1 paso) se acumulan y se envían pasos enteros. *(decisión: sin scroll de alta resolución; Windows no lo activa sin un "feature report" de multiplicador, más complejo y poco fiable entre sistemas)*
  - `ZOOM`: Ctrl pulsado (informe de teclado) + rueda ± pasos + Ctrl soltado.
  - Widgets: uso de consumo pulsado y soltado: reproducir/pausar `0xCD`, siguiente `0xB5`, anterior `0xB6`, subir volumen `0xE9`, bajar `0xEA`, silencio `0xE2`.
- Velocidad: el ratón Bluetooth tiene más latencia que el USB (normalmente 7,5–20 ms por informe). Los movimientos se envían según llegan; no se agrupan.

### 2.1 Emparejar
- Ajustes → Conexión → **Bluetooth** → **Emparejar…** abre una pantalla con:
  1. Estado del Bluetooth (activado, permisos, registrado como ratón).
  2. Botón **"Hacer visible"** (120 s).
  3. Instrucciones: en Windows, Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth → elegir el móvil.
  4. Lista de dispositivos emparejados con **"Usar este"**: el móvil se conecta a ese al abrir la app. Si el PC se conecta por su cuenta, también vale.
- Permisos: `BLUETOOTH_CONNECT` y `BLUETOOTH_ADVERTISE` (Android 12+), pedidos al elegir Bluetooth.
- El registro HID se mantiene mientras la app está abierta (también en Ajustes), y se libera al cerrarla.
- El punto de estado del trackpad: verde = conectado al PC por Bluetooth; gris = no.

### 2.2 Limitaciones conocidas
- Algunos fabricantes desactivan el perfil HID Device. Si `getProfileProxy(HID_DEVICE)` falla, la pantalla de emparejar lo dice y se sigue usando USB. **Hay que comprobarlo en el P10 (EMUI)**; en el Poco (HyperOS, Android 15) debería funcionar.
- Sin MyMouse en el PC no hay modo gestos, ni widgets configurables, ni volumen absoluto (solo subir/bajar).

## 3. Host de Windows: iniciar con Windows
- Menú de la bandeja: **"Iniciar con Windows"** (casilla). Escribe o borra `HKCU\Software\Microsoft\Windows\CurrentVersion\Run\MyMouse` con la ruta del ejecutable entre comillas. Sin permisos de administrador.
- Si el ejecutable se mueve, la casilla aparece desmarcada (la ruta guardada no coincide) y al marcarla se corrige.

## 4. Criterios de "hecho" de la v3
1. Con Bluetooth: emparejar y mover el cursor sin cable y sin nada instalado en el PC.
2. Scroll, clics, arrastrar, zoom y multimedia funcionan por Bluetooth.
3. Cambiar entre USB y Bluetooth en Ajustes sin reinstalar nada.
4. Tests del traductor a informes HID.

## 5. Fuera de alcance (posibles v4)
- Modo gestos por Bluetooth con acciones guardadas en el móvil (solo atajos de teclado y multimedia).
- Scroll de alta resolución por Bluetooth.
- USB sin depuración (AOA) e instalador del host.
