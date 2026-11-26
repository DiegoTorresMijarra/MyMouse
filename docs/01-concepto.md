# MyMouse — Concepto (ronda 1)

> Estado: borrador cerrado tras la primera ronda. Siguiente paso: [ronda técnica](#siguiente-ronda-limitaciones-técnicas).

## Una frase

Reutilizar un móvil Android viejo como **trackpad permanente de escritorio**, con un **modo gestos** para lanzar acciones dibujando formas.

## Contexto

- Proyecto **personal y open source**. Prioridad: que funcione bien para el autor y que el código sea limpio. Facilidad de instalación para terceros: deseable, no bloqueante.
- Uso principal: **móvil fijo junto al teclado**, siempre conectado. No es un mando para el sofá ni un control de presentaciones (al menos de momento).

## Plataformas

| Lado | Decisión |
|---|---|
| Móvil | Android (versión mínima por definir en la ronda técnica) |
| Ordenador | **Pendiente** (Windows / macOS / Linux) |
| Conexión v0 | **Cable USB** |
| Conexión futura | A estudiar (WiFi / Bluetooth) |

Que el ordenador necesite o no un programa propio **no está decidido**. Depende de lo que permita el USB en Android; lo vemos en la ronda técnica.

## Alcance por versiones

### v0: prueba de concepto
- Conexión por USB.
- Mover el cursor con un dedo (movimiento relativo, como un trackpad).
- Tocar = clic izquierdo.
- Clic derecho (dos dedos o mantener pulsado).
- Scroll con dos dedos.

**Criterio de éxito:** usarlo un día entero como trackpad sin frustrarse por la latencia o la precisión.

### v1: modo gestos
- Se activa al apoyar **N dedos** (N configurable; por ejemplo, 3).
- Mientras está activo, el usuario **traza una forma** (letra, círculo, flecha, zigzag…).
- Al levantar el dedo, se reconoce la forma y se ejecuta su acción asociada: atajo de teclado, abrir una app, un comando…
- Configuración: tabla `forma → acción`, idealmente con formas propias grabadas por el usuario.
- Feedback visual: el trazo se dibuja en la pantalla del móvil.

### v2: panel de widgets
- Zonas de la pantalla con widgets: botones de atajos, controles multimedia, volumen…
- Convive con la zona de trackpad (pantalla dividida o modos alternables).

### Sin versión asignada (ideas)
- Gestos multitáctiles del sistema (pellizcar para zoom, tres dedos para cambiar de escritorio).
- Teclado remoto.
- Conexión inalámbrica.
- Perfiles de configuración por aplicación activa en el PC.

## Fuera de alcance (por ahora)
- iOS.
- Modo "ratón de aire" con el giroscopio.
- Publicarla en tiendas o monetizarla.

## Preguntas abiertas
1. **SO del ordenador.** Condiciona el lado PC.
2. **¿Programa en el PC: sí o no?** Se decide en la ronda técnica.
3. **Modo gestos:** ¿qué tipo de acciones? ¿Solo atajos de teclado o también comandos o scripts? (Si son scripts, hace falta un programa en el PC sí o sí.)

## Siguiente ronda: limitaciones técnicas

Temas a investigar:

1. **USB en Android.** ¿Puede el móvil presentarse como ratón USB (HID) ante el PC?
   - Sin root: en principio, no.
   - Con root y kernel compatible (USB gadget / configfs): sí, pero depende del dispositivo.
   - La alternativa es usar el USB como canal de datos (ADB + reenvío de puertos, o USB tethering) con un programa en el PC que mueva el cursor.
2. **Latencia y precisión.** Frecuencia de muestreo táctil, aceleración del puntero, sensación de "trackpad de verdad".
3. **Inyectar eventos en el PC.** APIs por SO (SendInput, CGEvent, uinput) y permisos necesarios.
4. **Reconocimiento de formas.** Algoritmos ligeros, como el $1 / $P recognizer, frente a otras opciones; dónde ejecutarlo (móvil o PC).
5. **Móvil viejo.** Versión mínima de Android, rendimiento, batería con el móvil siempre enchufado y pantalla siempre encendida (degradación y quemado de pantalla).
6. **Stack.** Lenguaje y framework de la app Android y del programa del PC.
