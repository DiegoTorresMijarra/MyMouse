# MyMouse — Hoja de ruta y objetivos por versión

Cada versión tiene **un objetivo principal**, un alcance cerrado y un **criterio de "hecho"** comprobable. No se empieza una versión hasta cerrar la anterior.

| Versión | Objetivo en una frase | Transporte |
|---|---|---|
| **v0** | Sustituir al ratón como trackpad de escritorio | USB (ADB) |
| **v1** | Lanzar atajos dibujando formas | USB (ADB) |
| **v2** | Panel de widgets junto al trackpad | USB (ADB) |
| **v3** | Quitar el cable y la fricción de instalación | Bluetooth HID y/o AOA |
| **v4** | La toolbox del PC | USB (ADB) |
| **v4.5** | Estilo glass en el PC y el móvil, y depuración del Bluetooth | USB / Bluetooth |
| **v4.7** | Fondo "universo", icono propio y retoques | USB / Bluetooth |
| **v4.8** | Temas elegibles en el móvil y en el PC, con un universo más sobrio | USB / Bluetooth |
| **v5** | Bluetooth completo (gestos, botones propios, scroll fino) e instalador de Windows | Bluetooth / USB |
| **v5.1** | Avisos de versión nueva en el PC y el móvil, con actualización en un clic | USB / Internet |
| **v6** | Modo teclado: un teclado de ordenador de verdad en el móvil | USB / Bluetooth |

---

## v0: Trackpad

**Objetivo:** que el P10, fijo junto al teclado, se pueda usar como único dispositivo apuntador durante una jornada completa.

**Alcance:**
- Conexión por USB mediante ADB (`adb reverse`), con reconexión automática.
- Mover el cursor (relativo, con aceleración propia).
- Tocar = clic izquierdo; tocar con 2 dedos = clic derecho.
- Scroll con 2 dedos (vertical y horizontal); pellizco = zoom.
- Doble toque y arrastrar = arrastrar con el botón pulsado.
- Ajustes básicos en el móvil: sensibilidad, aceleración y velocidad de scroll.
- Chuleta con los gestos básicos en el centro del trackpad.
- Modo bloqueado (kiosco, pantalla siempre encendida) y modo ventana, que se alternan con una doble pulsación de volumen.
- Programa de bandeja en Windows: estado, pausar, salir.

**Criterios de "hecho":**
1. Usarlo una jornada laboral completa sin volver al ratón por frustración.
2. Latencia de extremo a extremo imperceptible (objetivo: menos de 20 ms entre el toque y el cursor).
3. Desenchufar y volver a enchufar el cable recupera la conexión sin tocar nada.
4. Tests unitarios del protocolo (ambos lados) y del intérprete de gestos.

**Fuera de alcance:** modo formas, widgets, conexión inalámbrica, instalador.

> Especificación: [spec-v0.md](spec-v0.md) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md) · Plan: [03-plan-v0.md](03-plan-v0.md)

---

## v1: Modo gestos

**Objetivo:** ejecutar atajos de teclado o abrir aplicaciones dibujando formas sobre el trackpad, sin interferir con el uso normal.

**Alcance:**
- Activación con N dedos (4 desde la v3.1; antes configurable, 3 por defecto) mantenidos un instante.
- Trazado con feedback visual en pantalla; reconocimiento con el algoritmo $1 (o $P) en el móvil.
- Mapa configurable `forma → acción` (atajo, abrir app/URL, texto, multimedia), que se guarda **en el PC**. El móvil solo envía el nombre de la forma reconocida.
- Grabación de formas propias (de 1 a 3 ejemplos por forma).
- Al activar el modo gestos, la chuleta muestra todas las formas con su texto de ayuda.
- Un catálogo de 15 formas incluidas (líneas, ángulos, círculo, triángulo, cuadrado, check, Z, S, L).
- Mensaje de protocolo `GESTURE`.
- Detalle: [spec-v1-gestos.md](spec-v1-gestos.md).

> Estado: implementada en la release `v1.0`. Los criterios de "hecho" están pendientes de comprobar en uso real.

**Criterios de "hecho":**
1. Al menos un 90 % de aciertos con las formas propias, en una prueba de 50 trazos.
2. Cero activaciones accidentales durante una jornada de uso normal como trackpad.
3. Crear una forma nueva y asignarle un atajo lleva menos de un minuto.

**Fuera de alcance:** formas de varios trazos, comandos o scripts arbitrarios. Las reglas por aplicación activa son opcionales.

---

## v2: Widgets

**Objetivo:** aprovechar la pantalla para controles de un toque que hoy requieren el teclado o buscar en la interfaz.

**Alcance:**
- Panel de widgets junto al trackpad (abajo en vertical, a la derecha en horizontal), de tamaño configurable.
- Widgets: botón de acción (las mismas acciones que la v1), control multimedia con lo que suena, volumen del sistema.
- Se configuran en el PC (`"widgets"` en `gestures.json`); canal de vuelta PC → móvil para el volumen y la canción.
- Protocolo versión 3.
- Detalle: [spec-v2-widgets.md](spec-v2-widgets.md).

> Estado: implementada en la rama de desarrollo (release `v2.0` pendiente). Los criterios de "hecho" están pendientes de comprobar en uso real.

**Criterios de "hecho":**
1. Abrir una aplicación configurada con un solo toque.
2. Pausar, saltar canción y cambiar el volumen sin teclado; volumen del móvil y del PC sincronizados en menos de 0,3 s.
3. Ningún toque del panel llega al trackpad, ni al revés.
4. Tests de protocolo, configuración, servicio de widgets y disposición del panel.

---

## v3: Sin cable / sin fricción

**Objetivo:** usar MyMouse sin depuración USB y, a ser posible, sin instalar nada en el PC.

**Alcance:**
- Modo **Bluetooth HID**: el móvil se empareja como ratón y teclado; el PC no necesita programa. Trackpad, clics, scroll, zoom y un panel fijo de multimedia y volumen.
- Elegir USB o Bluetooth en Ajustes; pantalla para emparejar.
- Host de Windows: "Iniciar con Windows".
- AOA descartado: exige un driver en el PC.
- Detalle: [spec-v3-bluetooth.md](spec-v3-bluetooth.md).

**Criterios de "hecho":**
1. Emparejar por Bluetooth y mover el cursor sin cable ni programa en el PC.
2. Scroll, clics, arrastrar, zoom y multimedia por Bluetooth.
3. Cambiar entre USB y Bluetooth sin reinstalar.
4. Tests del traductor a informes HID.

> Estado: implementada en la release `v3.0`, sin probar en dispositivos reales. Pendiente: comprobar que EMUI (P10) permite el perfil HID.

---

## v4: La toolbox del PC

**Objetivo:** que MyMouse en Windows sea una app de verdad (un panel flotante al estilo de la Toolbox de JetBrains) desde la que ver lo que suena, configurar gestos y widgets sin tocar `gestures.json` y ver el trazo de las formas en el PC.

**Alcance:**
- Host de Windows en **WPF**: toolbox anclada sobre la bandeja con tarjetas (Opciones, Música, Gestos, Widgets del móvil) personalizables; se abre con clic en el icono o con 4 dedos ↑ en el móvil y se cierra con 4 dedos ↓, Esc o un clic fuera.
- **Gesto en vivo:** tarjeta que sube desde la esquina y dibuja el trazo del móvil con el resultado (reconocida, sin acción, no reconocida, error).
- **Editor de gestos y widgets** con formularios (atajo grabado, abrir app con lista del menú Inicio, texto, multimedia…), botón Probar y reglas por app.
- **Formas nuevas** dibujadas con el ratón del PC, sincronizadas con el móvil (que sigue siendo su dueño); aviso en el PC al grabar una en el móvil.
- Protocolo versión 5: `TOOLBOX`, `GESTURE_MODE`, `STROKE_POINTS`, `STROKE_END`, `SHAPE_INFO`, `SHAPE_LIST_END`, `SHAPE_RESULT`, `SHAPE_ADD`, `SHAPE_DELETE`.
- Detalle: [spec-v4-toolbox.md](spec-v4-toolbox.md).

> Estado: implementada en la rama de desarrollo (release `v4.0` pendiente). La lógica tiene tests (protocolo en los dos lados, edición y escritura de `gestures.json`, sincronización de formas, modelo del trazo en vivo, tarjetas, atajos, apps), pero las ventanas de Windows y el módulo `app` no se han ejecutado ni compilado aquí: ver las notas de [§10 de la spec](spec-v4-toolbox.md#10-notas-de-implementación).

**Criterios de "hecho":**
1. Abrir y cerrar la toolbox con 4 dedos ↑ / ↓ desde el móvil, y con clic en la bandeja.
2. Ver el trazo en vivo en el PC al dibujar, con el resultado, sin mirar el móvil.
3. Crear una forma nueva con el ratón del PC y asignarle "abrir Chrome" sin tocar el fichero, en menos de un minuto.
4. Grabar una forma en el móvil hace que aparezca el aviso en el PC y se pueda asignar desde él.
5. Quitar y reordenar tarjetas; la de Opciones no se puede quitar.
6. Tests de protocolo (en los dos lados), de la edición de la configuración, de la sincronización de formas y del modelo del trazo en vivo.

---

## v4.5: Estilo glass y depuración de Bluetooth

**Objetivo:** pulir lo que se vio en la prueba de la v4.0 en el PC real (tarjeta Gestos desalineada, demasiado aire bajo la toolbox, sin barra superior) con un lenguaje visual común al PC y al móvil, y poder averiguar por qué el modo Bluetooth se queda en "Esperando al PC".

**Alcance:**
- **Tokens glass** (fondo degradado, cristal de panel y de tarjeta, radios, acento) en `Theme.cs` y en `GlassTheme.kt`.
- **Windows:** Acrylic del sistema con `WindowChrome` (alternativa sólida en Windows 10 y ajuste "Efecto cristal"), barra superior de 40 px (⚙ ⤢ ✕), maximizar a una ventana de dos columnas, 8 px sobre la barra de tareas, tarjeta Gestos rediseñada (filas alineadas, chips, secciones Fijadas / Asignadas / Sin acción), Opciones, Música, gesto en vivo y editor con el mismo cristal.
- **Móvil:** la misma capa en el trackpad, la chuleta, el panel de widgets, Ajustes, Formas, Grabar forma y Bluetooth (con desenfoque en Android 12+); solo estilo.
- **Bluetooth:** sección "Diagnóstico" (comprobaciones, últimos 30 eventos, reintentar, volver a registrar, copiar), el registro HID aguanta el emparejado (60 s; 180 s al hacerse visible) envío del movimiento agrupado y QoS para reducir el retraso, métrica de informes por segundo e intervalo real, y aviso en el PC si dice "Sin dispositivo" estando en modo Bluetooth.
- Detalle: [spec-v4.5-estilo.md](spec-v4.5-estilo.md).

> Estado: implementada en la rama de desarrollo (release `v4.5` pendiente). La lógica nueva tiene tests; las ventanas de Windows y el módulo `app` no se han ejecutado ni compilado aquí: ver las [notas de la spec](spec-v4.5-estilo.md#6-notas-de-implementación).

**Criterios de "hecho":** los de la [§5 de la spec](spec-v4.5-estilo.md#5-criterios-de-hecho-de-la-v45).

---

## v4.6: chuleta en dos columnas y botones con texto

- La chuleta de gestos básicos del móvil se reparte en **dos columnas en horizontal** (y se encoge entera si no cabe) y los botones del modo ventana son píldoras de cristal con icono y texto. Sin spec propia: [commit](https://github.com/DiegoTorresMijarra/MyMouse/commit/a1887c6).

---

## v4.7: Fondo "universo", icono de la app y retoques

**Objetivo:** lo que se vio probando la v4.5 y la v4.6 en el PC real: la toolbox salía demasiado gris y difuminada (el Acrylic más las tarjetas blancas daban un gris plano y la píldora de estado salía como una elipse borrosa), la app no tenía icono propio y algunos detalles del móvil (volumen, solape de la chuleta con la depuración, hoja de gestos) no estaban pulidos.

**Alcance:**
- **Fondo "universo"** (degradado, tres nebulosas y estrellas con semilla fija, algunas parpadean) detrás de la toolbox, el gesto en vivo y el editor del PC, y de todas las pantallas del móvil (estático en el trackpad). Se deja de usar Acrylic/Mica; se mantienen las esquinas y la sombra del sistema. Tokens de cristal más claros y acento lavanda.
- **Píldora de estado** nítida (radio = mitad del alto) con la explicación debajo como texto secundario.
- **Icono propio** generado con `tools/icon/make_icons.py` (`MyMouse.ico` multi-tamaño para el exe, las ventanas y la bandeja —con punto de estado—, e icono adaptativo en Android).
- **Icono siempre visible** en la barra de tareas de Windows 11 (`NotifyIconSettings`), activado solo la primera vez.
- **Editor:** botón "← Volver" (y `Esc`) con aviso de cambios sin guardar.
- **Móvil:** hoja de gestos en dos columnas, widget de volumen rediseñado (pista a todo el ancho, pulgar de 18 dp) y chuleta que no tapa la minipantalla de depuración.
- Detalle: [spec-v4.7-universo.md](spec-v4.7-universo.md).

> Estado: implementada en la rama de desarrollo (release `v4.7` pendiente). La lógica nueva tiene tests (estrellas y parpadeo en los dos lados, `NotifyIconSettings`, geometría del volumen, disposición de las hojas); las ventanas de Windows y el módulo `app` no se han ejecutado ni compilado aquí: ver las [notas de la spec](spec-v4.7-universo.md#6-notas-de-implementación).

**Criterios de "hecho":** los de la [§5 de la spec](spec-v4.7-universo.md#5-criterios-de-hecho).

---

## v4.8: Temas

**Objetivo:** el universo del móvil salía demasiado morado. Ahora es más sobrio y oscuro por defecto, y cada lado (móvil y PC) elige su tema por separado.

**Alcance:**
- **Siete temas:** Universo (por defecto, paleta sobria), Medianoche, Grafito, Océano, Aurora, Negro (OLED) y Nebulosa (la violeta de la v4.7). Definidos como datos en [`shared/themes.json`](../shared/themes.json): el PC lo incrusta y el móvil lo replica en Kotlin, con un test en cada lado que lo compara.
- **Acento:** Automático (el del tema) o uno de siete colores; los tokens de acento de `Theme.cs` y `GlassTheme.kt` son dinámicos.
- **Estrellas** y **animación de estrellas** (parpadeo) como casillas.
- **Móvil:** Ajustes → tarjeta "Tema" con muestras de 72 × 96 dp que dibujan el fondo real, círculos de acento y casillas, aplicado al momento. Se guarda en `AppSettings`.
- **PC:** Ajustes de la toolbox → "Tema…" con la rejilla de muestras, el acento y las estrellas; aplicado al momento a la toolbox, al gesto en vivo y al editor. Se guarda en `toolbox.json`.
- Las elecciones **no se sincronizan** entre el móvil y el PC.
- Detalle: [spec-v4.8-temas.md](spec-v4.8-temas.md).

> Estado: implementada en la rama de desarrollo (release `v4.8` pendiente). La lógica nueva tiene tests en los dos lados (catálogo, validación, acento, migración, estrellas con densidad y parpadeo suave); las ventanas de Windows y el módulo `app` no se han ejecutado ni compilado aquí: ver las [notas de la spec](spec-v4.8-temas.md#6-notas-de-implementación).

**Criterios de "hecho":** los de la [§5 de la spec](spec-v4.8-temas.md#5-criterios-de-hecho).

---

## v5: Bluetooth completo e instalador

**Objetivo:** que el modo Bluetooth (sin programa en el PC) sea casi tan completo como el USB, y que instalar MyMouse en Windows sea un instalador de verdad.

**Alcance:**
- **Gestos por Bluetooth:** las acciones de las formas viven en el móvil (`bt-gestures.json`: atajos, multimedia y texto; `Win+L` para bloquear). Valores por defecto como en el PC, hoja de formas y chuleta con las acciones del móvil, y editor **Ajustes → Modo gestos → "Acciones por Bluetooth…"** con "Probar" y la distribución del teclado del PC (Español o Inglés EE. UU., con AltGr y teclas muertas) para escribir texto.
- **Botones propios por Bluetooth:** hasta 16 en `bt-widgets.json` (por defecto Copiar, Pegar, Captura, Deshacer y Bloquear), editor **Widgets → "Botones por Bluetooth…"** con orden ↑ ↓.
- **Scroll y zoom finos:** el descriptor HID añade el *Resolution Multiplier* de Windows (unidades de 1/120 de paso si el PC lo activa; pasos enteros si no), línea "Scroll fino" en el diagnóstico y ajuste "Scroll fino por Bluetooth" (activado por defecto). **Obliga a volver a emparejar** si se actualiza desde una versión anterior.
- **Instalador de Windows** (Inno Setup): `MyMouse-Setup-<versión>.exe` en `%LOCALAPPDATA%\Programs\MyMouse` sin administrador, con adb incluido (el host lo busca junto al exe antes que en el PATH), acceso directo, "Iniciar con Windows" opcional y desinstalador que conserva la configuración. Sin firmar: SmartScreen avisará.
- Detalle: [spec-v5-bluetooth-completo.md](spec-v5-bluetooth-completo.md).

> Estado: implementada en la rama de desarrollo (release `v5.0` pendiente). La lógica nueva tiene tests (acciones, distribuciones ES/US, ficheros, descriptor y multiplicador, mapeador y coalescer, búsqueda de adb); el módulo `app` solo se ha pasado por el compilador de Kotlin y el instalador no se ha compilado aquí: ver las [notas de la spec](spec-v5-bluetooth-completo.md#8-notas-de-implementación).

**Criterios de "hecho":** los de la [§6 de la spec](spec-v5-bluetooth-completo.md#6-criterios-de-hecho).

---

## v5.1: avisos de versión nueva

**Objetivo:** que el PC y el móvil avisen cuando hay una release más nueva en GitHub y se actualicen con un botón.

**Alcance:**
- **PC:** banner en la toolbox ("Nueva versión 5.2 disponible": Novedades, Actualizar, Omitir), punto azul en el icono de la bandeja y un globo por versión. "Actualizar" descarga el instalador, comprueba tamaño y SHA-256, lo lanza en silencio y MyMouse se vuelve a abrir solo; la versión portátil (zip) abre la página de la release.
- **Móvil:** píldora "Actualizar a 5.2" en la barra del modo ventana, hoja con las novedades y "Descargar e instalar" (`DownloadManager` + instalador de paquetes con `FileProvider`, con el permiso "Instalar apps desconocidas"), y tarjeta "Actualizaciones" en Ajustes.
- **Avisos cruzados por USB:** protocolo v6 (`APP_VERSION`, `0x14` / `0x8E`): si móvil y PC tienen versiones distintas, los dos lo dicen (con botón en el que está atrasado si es el otro el nuevo).
- Búsqueda al arrancar (a los 10 s) y cada 12 h, "Buscar ahora" y "Omitir esta versión" en los dos lados. Las versiones `0.0-dev.N` no avisan.
- Detalle: [spec-v5.1-actualizaciones.md](spec-v5.1-actualizaciones.md).

> Estado: implementada en la rama de desarrollo (release `v5.1` pendiente). La lógica nueva tiene tests en `core` y `HostCore` (versiones, API de GitHub con fixture, recurso, omitir, avisos cruzados y vectores de `APP_VERSION`); `app` solo se ha pasado por el compilador de Kotlin y el instalador y la actualización en silencio no se han ejecutado aquí: ver las [notas de la spec](spec-v5.1-actualizaciones.md#8-notas-de-implementación). La v5.1 es la primera versión con avisos: el primer aviso real llegará con la release siguiente.

**Criterios de "hecho":** los de la [§7 de la spec](spec-v5.1-actualizaciones.md#7-criterios-de-hecho).

---

## v6: modo teclado

**Objetivo:** escribir en el PC con un teclado de ordenador de verdad dibujado en el móvil (no el teclado del sistema), por USB y por Bluetooth.

**Alcance:**
- **Teclas físicas, no texto:** el móvil envía pulsar/soltar por su uso HID (página 0x07) y Windows convierte las teclas en caracteres con su propia distribución, así que funcionan Ctrl+C, Alt+Tab, Win+Shift+S, las teclas muertas (´ + a = á)… Por USB, protocolo v7 (`KEY` `0x15`, `KEYBOARD_MODE` `0x16`, `KEYBOARD_OPEN` `0x8F`, `KEYBOARD_LEDS` `0x90`, `KEYBOARD_LAYOUT` `0x91`) con `SendInput` y scancodes; por Bluetooth, el informe de teclado HID de siempre con las teclas pulsadas.
- **Bloques combinables:** Fn, Flechas, Navegación (con flechas), Numpad y Solo numpad, que se encienden y apagan al momento desde una barra de chips (toque en `⌨`) o deslizando sobre `⌨` para alternar las dos últimas combinaciones. Se recuerdan por orientación. Distribuciones **Español (España)** e **Inglés (EE. UU.)** (solo cambian las etiquetas).
- **Dos teclas fijas:** `⌨` Modo y `🖱` Mouse (vuelve al trackpad).
- **Modificadores inteligentes** (un toque = en espera, doble toque = fijado con candado, mantener = como un teclado normal), repetición al mantener por USB, luces Bloq Mayús / Bloq Num (del PC por USB; informe de salida HID por Bluetooth, o supuestas con punto gris si Windows no las manda), vibración y nada se queda pulsado al salir, perder la conexión o pasar a segundo plano.
- **Cómo se abre:** widget **Teclado** (PC: acción `mymouse keyboard`; Bluetooth: acción `keyboard`), **doble toque con 4 dedos** en el trackpad y **Vol+ ×2** (alterna teclado ⇄ mouse). Atrás vuelve al trackpad.
- **Ajustes → Teclado** (distribución, tamaño de teclas, vibración, repetición y barra de trackpad opcional) y, en el PC, una tarjeta "⌨ Teclado" / "🖱 Mouse" y "Modo teclado" en el estado del móvil.
- Detalle: [spec-v6-teclado.md](spec-v6-teclado.md).

> Estado: implementada en la rama de desarrollo (release `v6.0` pendiente). La lógica nueva tiene tests en `core` (modelo del teclado, modificadores, repetición, controlador, doble toque de 4 dedos, protocolo v7, LEDs y descriptor HID, acción `keyboard`) y en `HostCore` (tabla HID → scancode, sesión con `KEY`/`KEYBOARD_*`, vigilancia de luces y distribución, vectores v7); el módulo `app` solo se ha pasado por el compilador de Kotlin y nada de lo que toca Windows (`SendInput`, `GetKeyState`) ni el Bluetooth real se ha ejecutado aquí: ver las [notas de la spec](spec-v6-teclado.md#11-notas-de-implementación). **Actualizar desde una versión anterior obliga a volver a emparejar** para recibir las luces por Bluetooth (el descriptor HID del teclado cambió).

**Criterios de "hecho":** los de la [§10 de la spec](spec-v6-teclado.md#10-entrega) y los del apartado "Modo teclado (v6)" de [probar.md](probar.md).

---

## Ideas sin versión
Scripts desde gestos · gestos del sistema (zoom, cambio de escritorio) · más distribuciones de teclado (UK, AZERTY, QWERTZ).
