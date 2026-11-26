# MyMouse — Hoja de ruta y objetivos por versión

Cada versión tiene **un objetivo principal**, un alcance cerrado y un **criterio de "hecho"** comprobable. No se empieza una versión hasta cerrar la anterior.

| Versión | Objetivo en una frase | Transporte |
|---|---|---|
| **v0** | Sustituir al ratón como trackpad de escritorio | USB (ADB) |
| **v1** | Lanzar atajos dibujando formas | USB (ADB) |
| **v2** | Panel de widgets junto al trackpad | USB (ADB) |
| **v3** | Quitar el cable y la fricción de instalación | Bluetooth HID y/o AOA |

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

## Ideas sin versión
Scripts desde gestos · teclado remoto · gestos del sistema (zoom, cambio de escritorio) · instalador y arranque con Windows.
