# MyMouse — v6: modo teclado

> Base: [spec-v5-bluetooth-completo.md](spec-v5-bluetooth-completo.md) (§2.1 distribuciones, HID), [spec-v5.1-actualizaciones.md](spec-v5.1-actualizaciones.md), [spec-v4.8-temas.md](spec-v4.8-temas.md) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)
>
> Estado: **especificada** con el usuario. Las decisiones marcadas con *(decisión)* son de detalle y no se consultaron.

## 1. Qué se quiere
Un **teclado de ordenador de verdad** dibujado en el móvil (no el teclado del sistema), que funcione por cable y por Bluetooth:
- Con **bloques combinables** que se encienden y apagan rápido: fila Fn, flechas, navegación, numpad y "solo numpad".
- **Distribuciones**: Español (España) e Inglés (EE. UU.) de momento.
- **Dos teclas fijas en cualquier modo**: ⌨ *Modo* (cambiar bloques) y 🖱 *Mouse* (volver al trackpad).
- Sin bloque multimedia: ya está en los widgets del trackpad.

## 2. Cómo funciona por dentro: teclas físicas, no texto
- El móvil envía **pulsar/soltar de teclas físicas**, identificadas por su **uso HID** (página 0x07, la misma numeración en los dos transportes). Los modificadores son teclas normales (`0xE0`–`0xE7`): Ctrl izq. `0xE0`, Shift izq. `0xE1`, Alt `0xE2`, Win `0xE3`, AltGr = Alt dcha. `0xE6`.
- **Quien convierte teclas en caracteres es Windows**, con su propia distribución. Así funcionan cualquier combinación (Ctrl+C, Alt+Tab, Win+Shift+S, Ctrl+Shift+Esc…), las teclas muertas (´ + a = á) y todo lo que tenga el teclado del PC. La distribución elegida en el móvil **solo cambia las etiquetas** que se dibujan.
- **Por cable**: el PC traduce el uso HID a *scancode* (set 1) y lo inyecta con `SendInput` + `KEYEVENTF_SCANCODE` (con `KEYEVENTF_EXTENDEDKEY` en las teclas extendidas: flechas, bloque de navegación, Ctrl/Alt dcha., Win, Menú, `/` e Intro del numpad). La tabla HID → scancode es lógica pura en `HostCore`, con tests.
- **Por Bluetooth**: el informe del teclado HID que ya existe (id 2, 6 teclas + modificadores) con el estado real de las teclas pulsadas. Más de 6 teclas normales a la vez se ignoran.
- **Limitaciones conocidas** (se dicen en la ayuda): por cable Windows no deja inyectar Ctrl+Alt+Supr (sí funciona por Bluetooth). Tampoco se puede escribir en ventanas de administrador si el host no lo es (pasa ya con el ratón).

## 3. Entrar y salir
| Cómo | Qué hace |
|---|---|
| Widget **Teclado** del panel | Abre el teclado |
| **Doble toque con 4 dedos** en el trackpad | Abre el teclado |
| **Doble pulsación de volumen +** | Alterna teclado ⇄ mouse (el doble de volumen − sigue siendo bloquear/desbloquear) |
| Tecla **🖱 Mouse** del teclado | Vuelve al trackpad |
| **Atrás** (botón o gesto) | Vuelve al trackpad, también en modo bloqueado |

- **Doble toque de 4 dedos**: dos toques con 4 dedos.
  - Cada toque: los 4 dedos bajan, ninguno se mueve más que `touchSlopPx` y se levantan todos antes de `gestureHoldMs` (si siguen quietos más tiempo, entra el modo gesto como ahora).
  - El segundo toque empieza en menos de 350 ms desde que se levantó el primero *(decisión)*.
  - El primer toque no hace nada.
  - Va en `GestureInterpreter` como `OutputAction.FourFingerDoubleTap`, con tests (doble toque válido, toque lento, movimiento, un solo toque, hold → modo gesto sin cambios).
- **Widget Teclado**:
  - Por cable: acción nueva del PC `{"type":"mymouse","command":"keyboard"}`. El PC, al ejecutarla, envía `KEYBOARD_OPEN` al móvil.
    - Aparece en el selector de acciones del editor ("Abrir teclado del móvil").
    - Se añade al `widgets.json` **por defecto** (solo afecta a configuraciones nuevas).
  - Por Bluetooth: tipo de acción nuevo `keyboard` en `bt-widgets.json`, resuelto en el móvil. Se añade al panel por defecto y al formulario de acciones.
- La transición entre trackpad y teclado es una animación corta: el teclado sube desde abajo y el trackpad se funde, 200 ms *(decisión)*.

## 4. La pantalla del teclado
- El teclado ocupa el ancho entero y se ancla abajo. El alto depende del tamaño de tecla (§6) y de los bloques.
- El espacio libre de arriba muestra el fondo del tema con una **barra de estado**: conexión (con ping si es Bluetooth), distribución activa, luces **Mayús** y **Num** y los bloques encendidos.
- Con la **barra de trackpad** activada (§6, apagada por defecto), ese espacio libre es un trackpad: mover con un dedo, toque = clic, dos dedos = scroll. Usa el mismo `TrackpadEngine`, sin gestos de 3 o 4 dedos.
- Las teclas se dibujan con los tokens del tema activo (cristal), en una sola vista personalizada con multitáctil: cada dedo pulsa su tecla, así que Shift + letra manteniendo funciona.
- **Etiquetas como en un teclado real**: la principal grande y la de Shift/AltGr pequeña en las esquinas (p. ej. en ES la tecla `2` lleva `"` arriba y `@` abajo a la derecha). Con Shift (o Bloq Mayús en letras) activo se resaltan las etiquetas de Shift.

### 4.1 Bloques
| Bloque | Contenido | Posición |
|---|---|---|
| **Base** (siempre, salvo en *Solo numpad*) | Fila de números, letras, Tab, Bloq Mayús, Shift ×2, Ctrl, Win, Alt, AltGr, Menú, espacio, Intro, Retroceso, Esc. ES con la tecla `<>` (ISO). | Centro |
| **Fn** | Esc y F1–F12 (y Impr Pant) | Fila encima de la base |
| **Flechas** | ◀ ▲ ▼ ▶ en T invertida | Derecha de la base |
| **Navegación** | Insert, Supr, Inicio, Fin, RePág, AvPág **+ flechas** (incluye el bloque Flechas) | Derecha de la base |
| **Numpad** | Bloq Num, `/ * - +`, 0–9, `.`, Intro | Derecha del todo |
| **Solo numpad** | El numpad grande en toda la pantalla, sin la base | Exclusivo |

- **Las dos teclas fijas** van siempre a la izquierda de la fila inferior: `⌨` y `🖱`, antes de Ctrl. En *Solo numpad* van en una columna a la izquierda.
- **Combinar**: Fn se combina siempre con todo.
  - **En horizontal**, Flechas, Navegación y Numpad se suman.
  - **En vertical** solo cabe un bloque lateral: encender uno apaga el otro *(decisión)*.
  - Encender Navegación apaga Flechas y al revés, porque Navegación ya las lleva.
- La combinación se recuerda por orientación.
- **El modelo es lógica pura en `core`** (`KeyboardModel`): dada la distribución, los bloques y la orientación, devuelve las teclas con su rectángulo en unidades (1u = tecla normal), su uso HID y sus etiquetas. Tests:
  - Cada fila de un bloque suma el mismo ancho.
  - No hay usos repetidos, salvo Intro del numpad, que tiene su propio uso `0x58`.
  - Hay etiquetas ES y US correctas en las teclas de símbolos.
  - Las teclas fijas aparecen en todas las combinaciones.
  - Se cumplen las reglas de combinación en vertical y en horizontal.

### 4.2 Cambiar de bloques rápido
- **Toque en `⌨`**: despliega/oculta una **barra de chips** encima del teclado, con animación de 150 ms. Los chips son: `Fn`, `Flechas`, `Nav`, `Num`, `Solo num` y la distribución `ES / US`. Un toque en un chip lo enciende o lo apaga al momento, con animación del cambio de teclado. La barra se queda abierta hasta volver a tocar `⌨` o pulsar cualquier tecla.
- **Deslizar sobre `⌨`** hacia la derecha o hacia la izquierda: pasa a la combinación usada anteriormente y vuelve, como Alt+Tab entre las dos últimas. Es el atajo para alternar rápido, por ejemplo entre el teclado con numpad y Solo numpad *(decisión)*.

## 5. Comportamiento de las teclas
1. **Modificadores inteligentes** (Shift, Ctrl, Alt, AltGr, Win):
   - **Un toque**: queda "en espera" (resaltado) y se aplica a la siguiente tecla normal; se suelta justo después.
   - **Doble toque**: queda **fijado** (resaltado fuerte con candado) hasta otro toque.
   - **Mantener** mientras se pulsa otra tecla: funciona como en un teclado normal.
   - Los modificadores en espera también afectan a un clic de la barra de trackpad (Ctrl+clic).
   - Lógica pura (`ModifierState`) con tests.
   - En el envío, el modificador baja al tocarlo y sube cuando corresponde (tras la siguiente tecla, al quitar el fijado o al levantar el dedo).
2. **Repetición al mantener**: letras, números, Retroceso, Supr, flechas, espacio e Intro.
   - **Por cable**: el móvil repite el "pulsar" de la tecla tras 400 ms y luego cada 40 ms *(decisión)*, porque Windows no repite las teclas inyectadas.
   - **Por Bluetooth**: no se repite nada; lo hace Windows, como con un teclado real.
   - Los modificadores, Bloq Mayús/Num, `⌨` y `🖱` nunca se repiten.
3. **Respuesta al pulsar**: vibración corta (la misma que el resto de la app, se puede quitar en Ajustes) y la tecla se ilumina mientras está pulsada.
4. **Luces Bloq Mayús / Bloq Num**:
   - **Por cable**: el PC envía `KEYBOARD_LEDS` al entrar en modo teclado y cada vez que cambian. Lo comprueba con `GetKeyState` cada 250 ms mientras el modo teclado está activo.
   - **Por Bluetooth**: se añade al descriptor del teclado el informe de salida de LEDs estándar (5 bits + 3 de relleno). Windows lo escribe al cambiar (`onSetReport` de tipo *output*, o datos por el canal de interrupción). Como cambiar el descriptor puede requerir volver a emparejar, mientras no llegue ningún informe de LEDs el móvil **supone** el estado conmutándolo él mismo al pulsar Bloq Mayús/Num, y lo marca con un punto gris *(decisión)*. La pantalla de diagnóstico Bluetooth dice si se reciben LEDs y, si no, sugiere volver a emparejar.
   - Con Bloq Mayús encendido se resaltan las etiquetas en mayúscula.
5. **Seguridad: nada se queda pulsado.**
   - Al salir del modo teclado, al perder la conexión, al pasar a segundo plano o al cambiar de bloques con teclas pulsadas, el móvil envía "soltar" de todas las teclas que tenga abajo.
   - El PC, además, guarda qué teclas tiene pulsadas cada sesión y las suelta él solo si llega `KEYBOARD_MODE` apagado o se corta la conexión.
   - Por Bluetooth, al salir se envía el informe vacío del teclado.

## 6. Ajustes (móvil, sección nueva "Teclado")
- **Distribución**: *Automática (la del PC)*, *Español (España)* o *Inglés (EE. UU.)*.
  - Automática solo funciona por cable: el PC envía `KEYBOARD_LAYOUT` con la distribución activa de la ventana en primer plano (`GetKeyboardLayout`). Si es `es-ES`, se usa `es`; con cualquier otra, `us`.
  - Por Bluetooth, *Automática* usa el ajuste de distribución que ya existe para escribir texto (v5, §2.1). Es el mismo ajuste, no uno nuevo.
- **Tamaño de las teclas**: Pequeño / Mediano / Grande. Por defecto, Mediano.
- **Vibración al pulsar**: activada.
- **Repetir al mantener**: activada.
- **Barra de trackpad**: desactivada.
- Las chuletas de gestos (`CheatSheet`, `GestureSheet`) y `docs/probar.md` añaden "Doble toque con 4 dedos: teclado" y "2× vol +: teclado ⇄ mouse".

## 7. PC
- **Tarjeta de gesto en vivo**: al entrar en el modo teclado sale "⌨ Teclado"; al salir, "🖱 Mouse". Usa la animación de siempre y respeta "Mostrar gesto en vivo".
- **Toolbox**: la tarjeta de opciones muestra "Modo teclado" en la línea de estado del móvil mientras está activo.
- No hay ajustes nuevos en el PC.

## 8. Protocolo versión 7
| Tipo | Nombre | Sentido | Payload | Bytes |
|---|---|---|---|---|
| `0x15` | `KEY` | móvil → PC | `u8 usage` (HID, página 0x07), `u8 down` (1 pulsar, 0 soltar) | 3 |
| `0x16` | `KEYBOARD_MODE` | móvil → PC | `u8 on` (1 entra, 0 sale) | 2 |
| `0x8F` | `KEYBOARD_OPEN` | PC → móvil | — (el widget pide abrir el teclado) | 1 |
| `0x90` | `KEYBOARD_LEDS` | PC → móvil | `u8 bits`: 1 = Bloq Mayús, 2 = Bloq Num, 4 = Bloq Despl | 2 |
| `0x91` | `KEYBOARD_LAYOUT` | PC → móvil | `u16 len` + `str8 id` (`es`, `us`) | variable |

- Solo con la versión negociada ≥ 7. Si alguno de estos tipos llega con una versión menor, es un error de protocolo.
- El host sigue aceptando las versiones 1–6.
- **Un `KEY` con un uso desconocido para el PC se ignora**: es un valor, no un tipo de mensaje, y no rompe el flujo.
- La repetición por cable son `KEY down` repetidos sin `up` en medio, como hace un teclado real.
- El PC envía `KEYBOARD_LEDS` y `KEYBOARD_LAYOUT` al recibir `KEYBOARD_MODE` encendido y después solo cuando cambian.
- Hay que actualizar `PROTOCOL.md` y añadir vectores, por ejemplo `KEY(0x04, down)` = `15 04 01` y `KEYBOARD_LAYOUT("es")` = `91 03 00 02 65 73`. Tests en los dos lados con esos vectores.

## 9. Fuera de alcance
- El teclado del sistema (Gboard), predicciones o dictado.
- Más distribuciones (UK, AZERTY, QWERTZ): el modelo debe permitir añadirlas solo con una tabla nueva.
- Bloque multimedia, que ya está en los widgets.
- Modo teclado en el PC sin móvil conectado.

## 10. Entrega
- Tests de:
  - `KeyboardModel`, `ModifierState` y la repetición (lógica de tiempos pura).
  - Doble toque de 4 dedos.
  - Protocolo v7 en Kotlin y C#.
  - Tabla HID → scancode.
  - LEDs en el descriptor HID: el descriptor sigue siendo válido y el tamaño del informe de salida es correcto.
  - Acción `mymouse keyboard` y `keyboard` en la configuración BT.
- Documentación: `docs/00-roadmap.md` (sección v6), `PROTOCOL.md`, `docs/probar.md` (qué probar del teclado por cable y por Bluetooth) y §11 de esta spec con notas de implementación.
- La versión de la release será **v6.0**. Las notas de la release las escribe quien coordina, no la implementación.

## 11. Notas de implementación

Implementado todo lo de las §2 a §8 con los valores de las decisiones tal cual. Tests nuevos: en `core` (de 305 a 376: `KeyboardModelTest`, `ModifierAndRepeatTest`, `KeyboardControllerTest`, `HidKeyboardStateTest`, `FourFingerDoubleTapTest`, vectores v7 en `MessageCodecTest`, descriptor con LEDs, acción `keyboard` y diagnóstico), en `MyMouse.HostCore.Tests` (`KeyboardTests`: tabla HID → scancode, sesión con `KEY` y `KEYBOARD_*`, vigilancia de luces y distribución; más la tarjeta de aviso en `LiveStrokeModelTests`) y en `MyMouse.Protocol.Tests` (vectores v7; 147 en total). `MyMouse.HostCore.Tests` pasa de 345 a 426. Todo pasa. **No se ha podido ejecutar** nada de `SendInput`, `GetKeyState` ni `GetKeyboardLayout` (el host solo se ha compilado), ni el Bluetooth real, ni el módulo `app` más allá del compilador de Kotlin: ver "Riesgos y cosas sin probar".

### Qué hay dónde
- **Protocolo v7** (`protocol/PROTOCOL.md`): `KEY` `0x15`, `KEYBOARD_MODE` `0x16`, `KEYBOARD_OPEN` `0x8F`, `KEYBOARD_LEDS` `0x90`, `KEYBOARD_LAYOUT` `0x91`. Vectores: `15 04 01`, `16 01`, `8F`, `90 03`, `91 03 00 02 65 73`. En Kotlin los mensajes son `Message.Key`, `KeyboardMode`, `KeyboardOpen`, `KeyboardLeds` y `KeyboardLayoutId` (`KeyboardLayout` ya era la clase de la distribución de texto de la v5); en C#, `KeyMessage`, `KeyboardModeMessage`, `KeyboardOpenMessage`, `KeyboardLedsMessage` y `KeyboardLayoutMessage`.
- **`core`, paquete `keyboard`:**
  - `KeyboardModel` construye las teclas (`KeyDef`: uso HID, rectángulo en unidades, etiquetas principal / Shift / AltGr, tipo y zona) para una distribución, una `KeyboardCombo` y la orientación. Las distribuciones son tablas (`KeyboardModel.ES`, `US`, `LAYOUTS`): añadir otra es añadir un `KeyLabels`. `KeyboardCombo` lleva las reglas de combinación (`toggled`, `normalized`, `encode`/`decode` para los ajustes) y `ComboHistory` el "Alt+Tab" entre las dos últimas.
  - `ModifierState` (modificadores inteligentes), `KeyRepeat` (400 ms y 40 ms, con tope de 3 repeticiones atrasadas), `KeyboardController` (une los dos, los dedos y las luces con `KeyboardLeds`) y `KeyboardSettings` (distribución resuelta, tamaños de tecla).
- **`core`, resto:** `OutputAction.FourFingerDoubleTap` y `TrackpadConfig.fourFingerDoubleTapMs` (`GestureInterpreter`); `HidKeyboardState` y `HidLeds` (informe de teclado con las teclas pulsadas y conversión del informe de salida de LEDs); informe de salida de LEDs en `HidDescriptor` (`LED_REPORT_SIZE`); `BtAction.Keyboard` (`{"type":"keyboard"}`) y el botón *Teclado* en `BtWidgetConfig.DEFAULT`; `keyboardLedsLine` en `BluetoothDiagnostics`.
- **`HostCore`:** `Keyboard/HidScancodes.cs` (tabla HID → scancode del conjunto 1, y `KeyboardLayoutIds`), `Keyboard/IKeyboardState.cs` (`IKeyboardState` y `KeyboardStateMonitor`), `ClientSession` (`KEY`, `KEYBOARD_MODE`, teclas pulsadas por sesión), `PhoneHub` (`KeyboardMode`, `KeyboardModeChanged`), `MyMouseCommand.Keyboard` (acción `mymouse keyboard`) y `LiveStrokeModel.ShowNotice`.
- **PC** (`MyMouse.Host`): `SendInputInjector.SetKey` (scancode + `KEYEVENTF_SCANCODE` + `KEYEVENTF_EXTENDEDKEY`), `WindowsKeyboardState` (`GetKeyState`, `GetKeyboardLayout`), `WindowsActionExecutor` (la acción envía `KEYBOARD_OPEN`), tarjeta "⌨ Teclado" / "🖱 Mouse" (`LiveGestureWindow` con `LiveResultKind.Notice`) y "Modo teclado" en `StatusText`.
- **Móvil** (`app`): `KeyboardView` (una vista con multitáctil, chips, barra de trackpad y animaciones), `MainActivity` (entrar y salir, Vol+ ×2, Atrás, combinaciones por orientación), `Transport` (`sendKey`, `setKeyboardMode`, `onKeyboardOpen`/`Leds`/`Layout`), `HostConnection`, `BluetoothHidTransport` (informe de teclado, LEDs por `onSetReport` y `onInterruptData`, botón *Teclado*), `SettingsActivity` ("Teclado"), `BluetoothActivity` (línea de luces), `CheatSheet` y `GestureSheet`.

### Decisiones de detalle y desviaciones
- **Teclas**: la base mide **16 u de ancho** (no 15) porque Esc va dentro de la base, como pide la §4.1 ("… Retroceso, Esc"), y así no se repite con la fila Fn. Por eso la **fila Fn lleva F1–F12 e Impr Pant pero no Esc** (la spec decía "Esc y F1–F12"): no puede haber dos usos iguales (§4.1) y Esc ya está en la base. La Intro ISO es un rectángulo de 2 u × 2 filas (en vez de la forma en L) para que cada fila sume lo mismo. No hay Ctrl derecho ni Win derecho (la §4.1 lista un Ctrl y un Win); AltGr es el Alt derecho (`0xE6`, también en US, donde se llama "Alt").
- **Por defecto**: vertical, solo la base; horizontal, base con Flechas. En **Solo numpad se ignora Fn** (la spec dice que "Fn se combina siempre con todo", pero también que el numpad es exclusivo y que va sin la base): encender Fn en Solo numpad sale del Solo numpad. El tamaño de teclas cambia solo el **alto** de las filas (×0,8 / ×1 / ×1,25 sobre 46 dp); el ancho lo da la pantalla.
- **Repetible** (§5.2): letras, números (también los del numpad), Retroceso, Supr, flechas, espacio e Intro (también la del numpad). Los símbolos (`, . ; - …`) no repiten, como dice la lista de la spec.
- **Modificadores** (decisiones propias, la spec no las fijaba): el segundo toque que **fija** debe llegar en **400 ms**; pasado ese tiempo el segundo toque quita el modificador en espera. Un modificador mantenido más de **600 ms** sin pulsar otra tecla no queda en espera al soltarlo (se suelta, como en un teclado normal). Fijar y quitar el fijado ocurren al **bajar** el dedo (el "soltar" posterior no hace nada). Un clic de la barra de trackpad cuenta como tecla: los modificadores en espera se aplican y se sueltan tras el clic.
- **Doble toque de 4 dedos**: cada toque debe empezar con los 4 dedos dentro de `gestureWindowMs` (150 ms, la misma ventana de los gestos de varios dedos), no moverse más que `touchSlopPx` y terminar antes de `gestureHoldMs` (medido desde que baja el cuarto dedo). Los 350 ms del segundo toque se miden **desde que se levantó el último dedo del primero hasta que baja el primer dedo del segundo**. Un toque de otra cosa entre los dos, un deslizamiento, un pellizco, un quinto dedo o el modo gestos lo anulan. El tercer toque empieza de nuevo.
- **Luces por cable**: el PC las envía al entrar (junto con la distribución) y vigila cada 250 ms mientras el modo está activo; con la sesión en pausa también. Si `GetKeyboardLayout` no puede leerse (sin ventana en primer plano), `KEYBOARD_LAYOUT` no se envía y el móvil usa el ajuste de texto. **El idioma `es` se decide por el identificador de idioma del HKL** (`0x040A` español tradicional y `0x0C0A` español moderno), no por el nombre de la cultura (`es-ES` en .NET sería `0x0C0A`, pero la distribución "Español" de Windows suele ser `0x040A`); el resto, `us`.
- **Luces por Bluetooth**: el descriptor del teclado lleva siempre el informe de salida de LEDs (también con el scroll fino apagado: no hay un descriptor "antiguo" aparte para esto). Por eso `legacyDescriptorIsExactlyTheOneFromV4` pasó a "el de la v4 más el informe de LEDs". El móvil acepta el informe por `onSetReport` (con o sin el id delante) y por `onInterruptData`, y responde un `GET_REPORT` de salida con ceros. Mientras no llegue ninguno en esa conexión, el estado de Mayús y Num se supone conmutando al pulsar las teclas (punto gris); al desconectar vuelve a suponerse.
- **Chip ES/US**: cambia la distribución dibujada **solo mientras dure la sesión** (se pierde al cambiar un ajuste del teclado o al reiniciar la app); la elección de siempre está en Ajustes → Teclado. La spec no decía si debía guardarse.
- **Vibración**: la misma vía que el resto de la app (`Vibrator`, un pulso), pero de **20 ms** como la de los widgets (el de 40 ms del modo gestos se sentiría pesado en cada tecla).
- **`KEYBOARD_MODE` y reconexión**: si el móvil pierde la conexión con el teclado abierto, al reconectar vuelve a enviar `KEYBOARD_MODE(1)` y el PC repite luces y distribución. Al pasar a segundo plano el móvil solo suelta las teclas (sigue en modo teclado); salir del todo es `🖱`, Atrás o Vol+ ×2.
- **Widget Teclado**: por USB es un botón nuevo del `gestures.json` **por defecto** (solo para configuraciones nuevas) y un chip más en el selector de acciones del editor. Por Bluetooth es un sexto botón del panel por defecto y un tipo "Abrir teclado" en el formulario de acciones, que también vale para una forma de `bt-gestures.json`.
- **Tarjeta del PC**: reutiliza la tarjeta del gesto en vivo (mismo tamaño y animación, 1,2 s) con un resultado nuevo `Notice`; no interrumpe un gesto en curso. "Modo teclado" va al final del texto de estado ("Conectado · Pixel · Modo teclado"), que también sale en el tooltip de la bandeja.

### Riesgos y cosas sin probar
- **Nada de Windows se ha ejecutado**: `SendInput` con scancodes (sobre todo las teclas extendidas y AltGr, que Windows convierte en Ctrl+Alt si la distribución lo pide), `GetKeyState` y `GetKeyboardLayout`. La tabla HID → scancode está probada contra los valores esperados del conjunto 1, no contra un Windows real. La tecla Pausa (`E1 1D 45`) no se puede inyectar con un solo scancode y no está. Los números del numpad dependen de Bloq Num del PC.
- **Bluetooth**: el informe de salida de LEDs cambia el descriptor; hasta volver a emparejar, Windows usa el viejo y las luces se suponen. No se sabe si todos los móviles/Windows envían el informe por `SET_REPORT` o por la interrupción: se aceptan los dos y el diagnóstico dice si ha llegado alguno. Más de 6 teclas normales a la vez se ignoran (sin aviso).
- **`KeyboardView`** solo se ha pasado por el compilador: la geometría, las animaciones (interpolación de rectángulos al cambiar de bloques, chips, entrada y salida), el multitáctil y el tamaño de las etiquetas en pantallas pequeñas no se han visto en un móvil. En vertical las teclas miden unos 22 dp de ancho (16 unidades en 360 dp): es lo normal en un teclado completo en un móvil, pero conviene probarlo con el pulgar. El texto de las etiquetas se encoge hasta caber.
- **Mensaje de error del widget por USB**: con un móvil de versión < 7 (o sin móvil) la acción "Abrir teclado del móvil" falla en el PC con un mensaje en la toolbox y `WIDGET_RESULT` de error.
- **Tests en Windows**: `KeyboardTests` usa tuberías anónimas en memoria para simular la conexión; el resto es independiente del sistema.

