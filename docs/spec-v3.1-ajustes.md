# MyMouse — v3.1: ronda de ajustes tras la primera prueba

> Base: [spec-v0.md](spec-v0.md), [spec-v1-gestos.md](spec-v1-gestos.md), [spec-v2-widgets.md](spec-v2-widgets.md), [spec-v3-bluetooth.md](spec-v3-bluetooth.md) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)
>
> Origen: la primera prueba real (Poco X8 Pro Max con HyperOS y PC con Windows 11). Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Gestos con varios dedos: nuevo reparto

**Motivo:** en HyperOS, 3 dedos chocan con la captura del sistema (arrastrar hace una captura y mantener abre el recorte).

| Dedos | Gesto | Acción |
|---|---|---|
| 3 | deslizar arriba | Vista de tareas de Windows (`Win+Tab`) |
| 3 | deslizar abajo | Mostrar el escritorio (`Win+D`) |
| 3 | deslizar a izquierda o derecha | Cambiar de app, como en el touchpad de Windows: se mantiene `Alt` pulsado, se envía `Tab` (derecha) o `Shift+Tab` (izquierda) cada ~120 px de recorrido y se suelta `Alt` al levantar los dedos |
| 4 | quietos 300 ms | Modo gestos (antes con 3) |
| 4 | juntar (pellizcar hacia dentro) | Mostrar el panel de widgets del móvil, con animación |
| 4 | separar (pellizcar hacia fuera) | Ocultar el panel de widgets, con animación |
| 4 | deslizar arriba | Reservado para la v4 (mostrar la "toolbox" del PC). En la v3.1 solo se anota en depuración ("4 dedos ↑") |
| 4 | deslizar abajo | Reservado para la v4 (ocultar la toolbox). Igual que arriba |

**Reglas:**
- Por defecto, el modo gestos se activa con **4 dedos**. El ajuste de 3/4 dedos desaparece, salvo la opción "Desactivado". Un valor 3 guardado de antes pasa a 4.
- Clasificación de un gesto de N dedos:
  - Hay que superar un umbral de recorrido (unos 48 px del centro de los dedos, o un cambio de unos 20 % en la distancia media al centro).
  - Si la distancia cambia más que el centro se mueve, es un pellizco (juntar o separar).
  - Si no, es un deslizamiento en la dirección dominante.
  - Si los dedos siguen quietos 300 ms sin superar el umbral (solo con 4), es el modo gestos.
  - Un gesto ya decidido no cambia de tipo hasta levantar todos los dedos.
- Los dedos llegan uno a uno. Hay que mantener la ventana de 150 ms para contar los dedos, como en la v1: un gesto de 3 dedos no se decide mientras pueda llegar el cuarto.
- *(decisión)* Los gestos de 3 dedos funcionan siempre (USB y Bluetooth). Por USB los ejecuta el host. Por Bluetooth, el móvil envía los informes de teclado HID directamente (`HidMapper`).
- Debe existir un ajuste "Gestos de Windows con 3 dedos" (activado por defecto).
- **Aviso al usuario** (en `docs/probar.md` y en Ajustes, como texto de ayuda): para usar 3 dedos hay que desactivar en HyperOS *Ajustes → Ajustes adicionales → Atajos de gestos → Captura de pantalla (deslizar 3 dedos hacia abajo)* y el recorte parcial (mantener 3 dedos). Una app no puede bloquear estos gestos del sistema.

### 1.1 Panel de widgets con 4 dedos
- El ajuste "Mostrar panel de widgets" sigue existiendo y marca el estado inicial. Juntar y separar con 4 dedos lo cambia, y el nuevo estado se guarda.
- **Animación** (cuidarla, el usuario la pidió expresamente):
  - El panel entra deslizándose desde su borde (abajo en vertical, derecha en horizontal) mientras el trackpad se encoge a la vez. Dura unos 280 ms con un `DecelerateInterpolator` o un muelle (`SpringAnimation`, amortiguación ~0,8), sin rebote exagerado.
  - Al salir hace lo contrario, con aceleración.
  - Los widgets aparecen con un leve fundido y escalonado (~30 ms entre filas).
  - Durante la animación no se aceptan toques nuevos en el panel.
  - Si el panel está vacío (no hay widgets del PC), juntar los 4 dedos muestra un aviso corto: "No hay widgets: configúralos en el PC".

## 2. Zoom fino, como en el touchpad del PC

**Problemas:**
- Va a saltos de 120 (un paso de rueda cada 40 px) y es lento incluso al máximo.
- A veces el pellizco se queda como scroll.

**Cambios:**
- El delta del zoom es proporcional al **logaritmo del cociente de distancias** (`ln(spread / spreadInicioTramo)`) y no a los píxeles. Así se comporta igual en cualquier tamaño de pantalla. Se envían **deltas finos** (no múltiplos de 120), igual que un touchpad de precisión que manda `Ctrl` + rueda con deltas pequeños.
- Se envía, como mucho, cada 16 ms y se acumula el resto.
- Hay un ajuste "Velocidad del zoom" con un rango que llega claramente por encima del actual (×0,5 a ×4). Por defecto es aproximadamente ×1,5 del actual, en unidades equivalentes.
- La decisión scroll/zoom con 2 dedos:
  - Se usa el **cociente** de distancias (umbral de unos 8 %) frente al movimiento del centro, y se decide antes.
  - Una vez en zoom, se queda en zoom hasta levantar.
  - Una vez en scroll, se pasa a zoom si después el cociente cambia más de un 25 %. *(decisión)*
- Por Bluetooth (HID) no hay deltas finos: se mantiene la acumulación por pasos, pero con la nueva fórmula.
- Scroll con 2 dedos por USB: hay que comprobar que se envían deltas finos (no redondeados a 120). Si se redondean, se corrige.
- Scroll **con inercia** opcional:
  - Ajuste "Inercia al hacer scroll", **desactivado** por defecto *(decisión: pendiente de que el usuario aclare qué es "scroll suelto")*.
  - Al levantar los 2 dedos con velocidad, el móvil sigue enviando scroll con fricción exponencial (~300 ms de vida media).
  - Cualquier toque nuevo la corta.

## 3. Protocolo
Si hace falta un mensaje nuevo para los gestos de Windows por USB:
- El protocolo sube a la **versión 4** (negociación igual que antes).
- Mensaje propuesto: `0x0C SYSTEM_GESTURE` `u8 orden`: 0 = vista de tareas, 1 = escritorio, 2 = empezar cambio de app, 3 = siguiente app, 4 = app anterior, 5 = terminar cambio de app.
- Con un host de versión < 4, los gestos de 3 dedos no hacen nada (se anota en depuración).
- Añadir los vectores a `PROTOCOL.md` y tests en ambos lados.
- El host debe soltar `Alt` si la sesión se corta a mitad de un cambio de app.

## 4. Tests y documentación
- Tests en `core`:
  - Clasificación de 3 y 4 dedos (deslizar en 4 direcciones, juntar, separar, quietos, cuarto dedo que llega tarde).
  - Zoom por cociente.
  - Bloqueo scroll/zoom.
  - Inercia.
  - `HidMapper` con los atajos de Windows.
- Tests del host para `SYSTEM_GESTURE`.
- Actualizar `spec-v0.md` (gestos básicos), `spec-v1-gestos.md` (activación con 4), `probar.md`, la chuleta (nuevos iconos y textos: "3 dedos ↑ Vista de tareas", "4 dedos juntar: widgets"…) y `ActionDescriber`.
