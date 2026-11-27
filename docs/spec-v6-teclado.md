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
*(Las rellena quien implemente.)*
