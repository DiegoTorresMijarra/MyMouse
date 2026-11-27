# MyMouse — Ronda técnica

> Contexto: [01-concepto.md](01-concepto.md). Móvil Android viejo, PC con Windows, conexión v0 por USB, se acepta un programa pequeño en el PC.

## Arquitectura propuesta (v0)

```
┌──────────── Android ────────────┐   USB    ┌─────────── Windows ───────────┐
│ Vista a pantalla completa        │          │ App de bandeja (tray)          │
│  MotionEvent → interpretación    │  TCP por │  recibe eventos                │
│  de gestos (mover, clic, scroll, │ ───────► │  → SendInput (ratón/teclado)   │
│  modo formas)                    │ adb rev. │  configuración, arranque auto  │
└──────────────────────────────────┘          └────────────────────────────────┘
```

**Idea clave:** el móvil hace casi todo el trabajo (interpretar toques y reconocer formas) y manda **eventos ya decididos**: `mover dx,dy`, `clic`, `scroll`, `atajo Ctrl+Shift+T`. El programa de Windows se limita a ejecutarlos. Así se mantiene "pequeñito".

---

## 1. Transporte por USB

El punto crítico. Android **no** puede presentarse como ratón USB sin root. Las opciones:

| Opción | Root | Programa en PC | Driver en Windows | Complejidad | Notas |
|---|---|---|---|---|---|
| **A. ADB + `adb reverse`** | No | Sí (+ `adb.exe`) | Driver ADB (genérico de Google u OEM) | **Baja** | Requiere tener activada la depuración USB. Ideal para desarrollar. |
| B. Android Open Accessory (AOA) | No | Sí (libusb) | WinUSB (instalable con libwdi o Zadig) | Media | No necesita depuración USB. Latencia mínima. Más trabajo en el PC. |
| C. Anclaje USB (RNDIS) + TCP | No | Sí | Nativo en Win10/11 | Baja | Crea una red: puede interferir con la conexión del PC y el móvil tiene que activarlo a mano cada vez. |
| D. USB gadget HID (configfs) | **Sí** | **No** | Ninguno (ratón estándar) | Alta | Depende del kernel de cada dispositivo. Poco portable. |

**Recomendación:**
- **v0 → A (ADB).** Arranca en una tarde. `adb reverse tcp:5555 tcp:5555` hace que la app del móvil se conecte a `localhost:5555` y llegue al PC. La latencia por USB es de 1-2 ms, despreciable.
- **Más adelante → B (AOA)**, si molesta tener la depuración USB activa o depender de adb.

El transporte debe quedar **detrás de una interfaz** (`Transport`) para poder cambiarlo sin tocar el resto.

### Bonus para el futuro inalámbrico: Bluetooth HID
Desde **Android 9 (API 28)** existe `BluetoothHidDevice`: el móvil se anuncia como **ratón + teclado Bluetooth** sin root, y Windows lo reconoce **sin instalar nada**. Como los atajos del modo gestos se pueden enviar como pulsaciones de teclado HID, este modo cubriría v0 y v1 completos sin programa en el PC.
- Inconvenientes: algunos fabricantes lo desactivan; requiere Android 9 o superior; el Bluetooth tiene algo más de latencia que el cable.
- Conviene **comprobar pronto si tu móvil lo soporta**: podría cambiar la estrategia.

---

## 2. Inyectar eventos en Windows

- **`SendInput`** (Win32, modo usuario). Cubre todo lo de v0 y v1:
  - Movimiento relativo, botones, rueda vertical y horizontal (`MOUSEEVENTF_WHEEL` / `HWHEEL`).
  - Teclado para los atajos.
- Limitaciones conocidas:
  - **UIPI:** un proceso sin elevar no puede inyectar eventos en ventanas que corren como administrador (por ejemplo, el Administrador de tareas). Solución: ejecutar el programa elevado, o firmarlo con `uiAccess`. Para uso personal basta con arrancarlo como administrador si hace falta.
  - **`Ctrl+Alt+Supr`** y algunas combinaciones de `Win` están protegidas o no se pueden inyectar.
  - El movimiento relativo **pasa por la velocidad y la aceleración de puntero de Windows** ("Mejorar la precisión del puntero"). Mejor desactivarla en el PC y aplicar una curva de aceleración propia en el móvil, para controlar la sensación.
  - **Scroll suave:** enviar valores de rueda menores que 120 (`WHEEL_DELTA`) da un scroll fino en la mayoría de aplicaciones modernas; algunas antiguas solo reaccionan a saltos de 120.
- **No se puede** emular un *Precision Touchpad* de Windows (con sus gestos nativos de 3-4 dedos) sin un driver virtual HID en modo kernel. Es complejo y requiere firmar el driver. **Descartado**: los gestos los resolvemos en el móvil y mandamos atajos equivalentes (por ejemplo, `Win+Ctrl+→` para cambiar de escritorio).

---

## 3. Sensación de trackpad (latencia y precisión)

- Usar `MotionEvent.getHistorical*()`: Android agrupa varias muestras táctiles por frame y, si solo lees la última, pierdes resolución.
- Llamar a `View.requestUnbufferedDispatch()` (Android 11+) para recibir los eventos en cuanto llegan, sin esperar al vsync.
- Enviar deltas **acumulando la parte fraccionaria** (subpíxel) para no perder movimientos lentos.
- En el socket: TCP con `TCP_NODELAY`; mensajes binarios de pocos bytes.
- Diferenciar *tap* de *arrastre* con umbrales de tiempo y distancia. También hace falta **tap-to-drag** (doble toque y arrastrar) para seleccionar o mover ventanas.
- Muchas pantallas de móvil muestrean a 60-120 Hz o más, frente a los 125+ Hz de un ratón. Para un trackpad es suficiente.

---

## 4. Modo gestos (v1)

- **Activación:** apoyar N dedos. Ojo con los conflictos: **2 dedos = scroll**, así que N ≥ 3. Otra opción: un toque largo con N dedos, que reduce las activaciones accidentales.
- **Trazado:** al pasar a modo formas se sigue el **centroide** de los dedos, o solo el primer dedo; puede que el usuario quiera levantar el resto. Esto hay que probarlo.
- **Reconocimiento:** **$1 Unistroke Recognizer** (formas de un solo trazo) o **$P Point-Cloud** (multitrazo). Son algoritmos de unas 100 líneas, sin ML, funcionan con 1-3 ejemplos por forma y permiten que el usuario **grabe sus propias formas**. Se ejecutan en el móvil sin problema.
- **Umbral de confianza:** si la mejor coincidencia no llega a un mínimo, no se hace nada y se avisa con una vibración o un aviso visual.
- **Salida:** el móvil manda `atajo <modificadores> <tecla>` y el PC lo ejecuta con `SendInput`.
- **Configuración:** el mapa `forma → atajo` vive en el móvil (JSON). El PC no necesita saber de formas.

---

## 5. El móvil viejo como hardware permanente

| Riesgo | Mitigación |
|---|---|
| **Batería siempre al 100 % enchufada** → degradación y posible **hinchazón** en baterías viejas | Revisar el estado físico de la batería. Si el sistema lo ofrece, usar un límite de carga al 80-85 % (Android 14+ en algunas marcas; con root, apps como ACC). Vigilar el móvil periódicamente. Es un riesgo real de seguridad. |
| **Pantalla siempre encendida** → consumo y desgaste | `FLAG_KEEP_SCREEN_ON` solo con la app abierta y conectada; brillo mínimo; atenuar tras X segundos sin uso. |
| **Marcas en pantallas OLED** | Fondo **negro puro**, sin elementos fijos brillantes; desplazar ligeramente lo que se muestre. |
| **Android antiguo** | Fijar la versión mínima según tu móvil. `adb reverse` necesita Android 5.0+; el Bluetooth HID, 9+; el dispatch sin búfer, 11+ (opcional). |

---

## 6. Stack propuesto

| Parte | Opción recomendada | Alternativa |
|---|---|---|
| App Android | **Kotlin**, vista nativa (sin Compose para la superficie táctil; Compose para ajustes si apetece) | — |
| Programa Windows | **C# / .NET 8**, app de bandeja con WinForms y `SendInput` vía P/Invoke | **Rust** (ejecutable de pocos MB, sin runtime) |
| Protocolo | Binario propio y mínimo (tipo + payload), versionado desde el día 1 | Protobuf (excesivo para esto) |
| adb | Se usa `adb.exe` de platform-tools; el programa de Windows lanza `adb reverse` al detectar el móvil | — |

---

## Conclusión de viabilidad (preliminar)

**Es viable.** No hay bloqueos técnicos para v0 ni v1:
- El USB sin root se resuelve con ADB (v0) o AOA (más adelante).
- `SendInput` cubre todo lo necesario en Windows.
- El reconocimiento de formas es un problema resuelto y ligero.

Puntos de incertidumbre que hay que validar con un prototipo:
1. **La sensación del trackpad.** La aceleración y el tap-to-drag son lo que decide si se usa o se abandona.
2. **La ergonomía del modo gestos con N dedos.**
3. **El estado de la batería del móvil concreto.**

Existen apps similares (Remote Mouse, Unified Remote, KDE Connect…). Su diferencia con MyMouse es que suelen ir por WiFi, no están pensadas como trackpad fijo y ninguna tiene un modo de formas configurable como el planteado. Para un proyecto personal y open source eso basta como motivo; no hay que competir con ellas.

## Decisiones de la ronda técnica

### Dispositivo de prueba
- El primer dispositivo de referencia fue un Huawei P10. Las pruebas actuales se hacen con un **Xiaomi (HyperOS)**.
- En Xiaomi, el sistema usa los gestos de 3 dedos para la captura de pantalla: hay que desactivarlos en *Ajustes → Ajustes adicionales → Atajos de gestos* para usar los gestos de Windows con 3 dedos.
- Algunos fabricantes cierran apps en segundo plano de forma agresiva. Como MyMouse está en primer plano mientras se usa, no suele afectar.

### Stack
- App Android: **Kotlin**, `minSdk 26` (Android 8).
- Programa Windows: **C# / .NET 10 (LTS)**, app de bandeja con WPF y `SendInput`.
- Transporte: **ADB + `adb reverse`** por cable, o **Bluetooth HID** sin programa en el PC.
