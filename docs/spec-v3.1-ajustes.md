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
- Debe existir un ajuste "Gestos de Windows con 3 dedos" (activado por defecto). Su texto de ayuda dice: "Pueden interferir con los gestos de 3 dedos de Xiaomi/HyperOS (captura y recorte). Si es así, desactívalos aquí o en los ajustes del sistema."
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
- Scroll **con inercia**, como el touchpad nativo:
  - Ajuste "Inercia al hacer scroll", **activado** por defecto (el usuario confirmó que la quiere como en el touchpad nativo); se puede desactivar.
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

## 5. Notas de implementación

Implementado en `core` (con tests), host y app. Desviaciones y puntos sin verificar:

- **Sin verificar en dispositivo ni en CI:** el módulo `app` no se ha compilado aquí (sin Android SDK). La animación del panel (`ValueAnimator` con `DecelerateInterpolator`/`AccelerateInterpolator`, traslación y `clipBounds`, sin `SpringAnimation` para no añadir dependencias), los iconos nuevos de la chuleta y el atajo Alt+Tab con `SendInput` en Windows están sin probar en real.
- **Alt+Tab por HID (Bluetooth):** Alt se mantiene con informes de teclado; no probado con un PC real.
- **Clasificación de 3 y 4 dedos:** la ventana de 150 ms cuenta desde el primer dedo. Con 3 dedos no se decide nada hasta cerrarse; con 4 se decide al instante. Para deslizar hay que recorrer 48 px del centro (no basta el 20 % de cambio de distancia si el centro se mueve más que la distancia); para pellizcar, 20 % de cambio y más cambio de distancia que desplazamiento del centro. Un 3 dedos que pellizca, o un 4 dedos que desliza a los lados, no hace nada.
- **Cambio de app:** al decidirse el deslizamiento se envía `START` y un primer paso (`Tab` o `Shift+Tab`); luego, un paso por cada 120 px. Levantar cualquier dedo termina el gesto (suelta Alt). El host ignora `3`/`4` sin `2` previo y un `2` repetido, y suelta Alt al cerrarse la sesión o con el PC en pausa.
- **Zoom:** unidades `660 × velocidad × ln(cociente)` (×1,5 por defecto, rango ×0,5 a ×4). Al decidirse el zoom, la referencia empieza en ese punto (sin salto). La decisión exige además ≥ 8 px de cambio. Por HID se acumula el delta fino y se envían pasos enteros de rueda.
- **Scroll por USB:** ya se enviaba con deltas finos (el host pasa el valor tal cual a la rueda); no hizo falta corregir nada.
- **Inercia:** activada por defecto (confirmado por el usuario); velocidad mínima de 0,2 unidades/ms y se descarta si los dedos llevaban parados más de 100 ms.
- **Ajuste de dedos:** solo "4 dedos quietos" o "Desactivado"; un 3 guardado de la v3.0 pasa a 4.
- **Aviso de host antiguo:** con un host < v4 los gestos de 3 dedos no se envían y se anota en depuración "el PC necesita MyMouse 3.1 o posterior".
- **Widgets con 4 dedos:** el estado se guarda en el ajuste "Mostrar panel de widgets". Por Bluetooth el panel fijo también se anima igual.
