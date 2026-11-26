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
- Scroll con 2 dedos (vertical y horizontal).
- Doble toque y arrastrar = arrastrar con el botón pulsado.
- Ajustes básicos en el móvil: sensibilidad, aceleración y velocidad de scroll.
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
- Activación con N dedos (N configurable; 3 por defecto) mantenidos un instante.
- Trazado con feedback visual en pantalla; reconocimiento con el algoritmo $1 (o $P) en el móvil.
- Mapa configurable `forma → acción` (atajo, abrir app/URL, texto, multimedia), que se guarda **en el PC**. El móvil solo envía el nombre de la forma reconocida.
- Grabación de formas propias (de 1 a 3 ejemplos por forma).
- Chuleta en el centro del trackpad con las formas fijadas y su texto de ayuda; al activar el modo gestos, la lista completa.
- Un catálogo de 16 formas incluidas (líneas, ángulos, círculo, triángulo, cuadrado, check, Z, S, L).
- Mensaje de protocolo `GESTURE`.
- Detalle: [spec-v1-gestos.md](spec-v1-gestos.md).

**Criterios de "hecho":**
1. Al menos un 90 % de aciertos con las formas propias, en una prueba de 50 trazos.
2. Cero activaciones accidentales durante una jornada de uso normal como trackpad.
3. Crear una forma nueva y asignarle un atajo lleva menos de un minuto.

**Fuera de alcance:** formas de varios trazos, comandos o scripts arbitrarios. Las reglas por aplicación activa son opcionales.

---

## v2: Widgets

**Objetivo:** aprovechar la pantalla para controles de un toque que hoy requieren el teclado o buscar en la interfaz.

**Alcance (provisional; se concreta al cerrar la v1):**
- Disposición configurable: zona de trackpad más zona de widgets, o modos alternables.
- Widgets: botón de atajo, controles multimedia, volumen.
- Posible canal de vuelta PC → móvil (estado del volumen, canción en reproducción…).

**Criterio de "hecho":** por definir.

---

## v3: Sin cable / sin fricción

**Objetivo:** usar MyMouse sin depuración USB y, a ser posible, sin instalar nada en el PC.

**Opciones a evaluar:**
- **Bluetooth HID** (Android 9+): el móvil se anuncia como ratón y teclado; Windows no necesita programa. Depende de que EMUI lo permita.
- **AOA por USB**: sin depuración USB, pero con un driver WinUSB y el programa en el PC.

**Criterio de "hecho":** por definir.

---

## Ideas sin versión
Scripts desde gestos · teclado remoto · gestos del sistema (zoom, cambio de escritorio) · instalador y arranque con Windows.
