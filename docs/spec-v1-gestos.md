# MyMouse — Especificación v1: modo gestos

> Objetivos: [00-roadmap.md](00-roadmap.md#v1-modo-gestos) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)
>
> Estado: **propuesta**, pendiente de validar con la prueba de uso de la v0.

## 1. Flujo de uso

1. **Activar:** apoyar **3 dedos** a la vez (con menos de 120 ms de diferencia) y mantenerlos quietos **300 ms**. El móvil vibra y la pantalla muestra un borde de color: el modo gestos está activo, y la chuleta del centro se amplía con todas las formas disponibles (ver §6).
2. **Levantar** los 3 dedos.
3. **Dibujar** la forma con **un dedo**, de un solo trazo, en menos de 2 s desde la activación. El trazo se dibuja en pantalla mientras se hace.
4. **Levantar** el dedo. El móvil reconoce la forma y muestra durante 1 s su nombre y la acción asignada, o "No reconocido".
5. El modo gestos termina solo; se vuelve al trackpad.

**Cancelar:** no dibujar nada en 2 s, o tocar con 2 dedos durante el modo gestos.

Por qué así:
- **3 dedos mantenidos** no choca con nada de la v0 (1 dedo = mover, 2 = scroll/clic derecho; 3 ya está reservado). Mantenerlos quietos evita activaciones accidentales al apoyar la mano. N es configurable (3 o 4).
- **Dibujar con 1 dedo** es más preciso que con 3 y el algoritmo trabaja con una sola línea.
- **La pantalla del móvil sirve de feedback**: ves el trazo y lo que se ha reconocido antes de que pase nada en el PC.

## 2. Catálogo de formas incluidas

Todas son de **un solo trazo**. El catálogo trae 16; se recomienda **activar 8–10 a la vez**, porque cuantas menos haya, menos se confunden.

| Grupo | Formas | Id | Notas |
|---|---|---|---|
| Líneas | → ← ↑ ↓ | `line-right`, `line-left`, `line-up`, `line-down` | Las más rápidas. Se detectan por geometría, no por plantilla (ver §4). |
| Ángulos | V, Λ, <, > | `v`, `caret`, `lt`, `gt` | Dos segmentos con un vértice. |
| Cerradas | Círculo, triángulo, cuadrado | `circle`, `triangle`, `square` | Da igual el sentido de giro y el punto de inicio. |
| Símbolos | ✓ (check), Z, S, L | `check`, `z`, `s`, `l` | |
| Propias | Las que grabes | `custom:<nombre>` | Ver §5. |

**Pares que se confunden** (el editor avisa si activas los dos):
- `<` con `C` (propia) y con `circle` si se dibuja abierto.
- `check` con `v` y con `l`.
- `z` con `s` si se dibujan redondeados.
- `triangle` con `circle` si se dibuja rápido y con esquinas suaves.

**Formas que no entran en v1:**
- **De varios trazos** (X, +, =, flecha con cabeza): obligan a esperar para saber si viene otro trazo, lo que añade retraso y falsos positivos. Alternativa: dibujarlas de un trazo (la X como "α").
- **Dependientes del tamaño o la posición** (un círculo pequeño frente a uno grande, un gesto en una esquina): el reconocedor normaliza tamaño y posición a propósito.
- **Dependientes de la velocidad**: solo cuenta el dibujo, no el ritmo.

## 3. Acciones: qué puede hacer una forma

Las acciones se **definen y ejecutan en el PC**. El móvil solo dice qué forma ha reconocido (`GESTURE "circle"`) y el host decide qué hacer.

Razones:
- **Seguridad:** si el móvil pudiera mandar "ejecuta este programa", cualquier cosa conectada al puerto podría lanzar comandos en tu PC. Así, solo se ejecuta lo que tú has escrito en la configuración del PC.
- **Comodidad:** elegir un `.exe` o escribir rutas es mucho más fácil en el PC que en el móvil.
- **Contexto:** solo el PC sabe qué ventana está activa (necesario para acciones por aplicación).

### 3.1 Tipos de acción

| Tipo | Qué hace | Ejemplo |
|---|---|---|
| `keys` | Pulsa una combinación de teclas, o una secuencia de ellas. | `Ctrl+Shift+T` · `Win+D` · `Ctrl+K, Ctrl+C` |
| `launch` | Abre un programa, archivo, carpeta o URL, como si le hicieras doble clic. | `chrome.exe` · `C:\Tools\app.exe` · `https://github.com` · `ms-settings:` |
| `text` | Escribe un texto. | Tu email, una firma, un snippet |
| `media` | Teclas multimedia y de volumen. | `PlayPause`, `Next`, `VolumeUp`, `Mute` |
| `mymouse` | Acciones de la propia app. | `pause` (pausar MyMouse) |

**Respuesta directa: sí, se puede abrir Chrome u otra app.** Con `launch` y `chrome.exe` basta, porque Windows encuentra los programas registrados aunque no estén en el `PATH`. También vale cualquier ruta a un `.exe`, un acceso directo `.lnk`, un documento o una URL. Las apps de la Microsoft Store se abren con `shell:AppsFolder\<id de la app>`.

Opción de `launch`: **`focusIfRunning`**. Si el programa ya está abierto, trae su ventana al frente en lugar de abrir otra. Funciona en la mayoría de casos, pero Windows a veces lo impide (ver §7).

### 3.2 Por aplicación (opcional en v1)

Una misma forma puede hacer cosas distintas según la ventana activa. Por ejemplo, `check` guarda (`Ctrl+S`) en VS Code y recarga (`F5`) en Chrome. Se busca primero una regla para la app activa y, si no hay, la general.

### 3.3 Configuración

Fichero `%APPDATA%\MyMouse\gestures.json`. Al principio se edita a mano; el host lo recarga al guardarlo. Más adelante se puede añadir una ventana de edición en el PC.

```json
{
  "bindings": [
    { "gesture": "circle",     "action": { "type": "launch", "target": "chrome.exe", "focusIfRunning": true },
      "help": "Abrir Chrome", "pin": true },
    { "gesture": "line-right", "action": { "type": "keys", "keys": "Ctrl+Tab" } },
    { "gesture": "line-left",  "action": { "type": "keys", "keys": "Ctrl+Shift+Tab" } },
    { "gesture": "z",          "action": { "type": "keys", "keys": "Ctrl+Z" }, "help": "Deshacer", "pin": true },
    { "gesture": "check",      "action": { "type": "keys", "keys": "Ctrl+S" }, "app": "Code.exe" },
    { "gesture": "check",      "action": { "type": "keys", "keys": "F5" },     "app": "chrome.exe" },
    { "gesture": "v",          "action": { "type": "media", "key": "PlayPause" } },
    { "gesture": "custom:firma", "action": { "type": "text", "text": "Un saludo,\nDiego" } }
  ]
}
```

Campos opcionales de cada regla:
- `help`: texto de ayuda que se muestra en la chuleta del móvil (hasta 40 caracteres). Si falta, se genera uno a partir de la acción ("Ctrl+Tab", "Abrir chrome.exe").
- `pin`: `true` para que la forma aparezca en la chuleta fija del trackpad (ver §6).

Sintaxis de `keys`: modificadores `Ctrl`, `Alt`, `Shift` y `Win`, unidos con `+`, más una tecla: `A`–`Z`, `0`–`9`, `F1`–`F24`, `Enter`, `Tab`, `Esc`, `Space`, `Backspace`, `Delete`, `Home`, `End`, `PageUp`, `PageDown`, `Up`, `Down`, `Left`, `Right`, `PrintScreen`… Las secuencias van separadas por comas. Se envían como teclas virtuales (VK), así que **no dependen de la distribución del teclado** para letras y teclas especiales. Los símbolos (`ñ`, `@`) se escriben mejor con `text`.

Si llega una forma sin acción asignada, el host lo apunta en el log y muestra un aviso en la bandeja ("Forma `custom:estrella` sin asignar").

## 4. Reconocimiento (en el móvil)

1. **Filtro previo:** si el trazo mide menos de 40 px de ancho y de alto, se trata como un toque y se descarta.
2. **Líneas:** si el trazo es casi recto (longitud del recorrido < 1,15 × distancia entre extremos), se clasifica por el ángulo en ↑ ↓ ← →. Es más fiable que una plantilla y distingue el sentido.
3. **Resto:** algoritmo **$1 / Protractor** contra las plantillas activas.
   - Normaliza tamaño, posición y número de puntos.
   - **Sensible a la rotación** (si no, `<` y `>`, o `v` y `caret`, serían la misma forma).
   - Las formas cerradas llevan plantillas en ambos sentidos de giro.
4. **Rechazo:** la forma solo se acepta si su puntuación supera un umbral **y** saca suficiente ventaja a la segunda. Si no, "No reconocido". Es preferible no hacer nada a hacer algo equivocado.

Todo esto es lógica pura en `android/core`, testeable en la JVM con trazos grabados.

## 5. Formas propias

- En los ajustes del móvil: "Nueva forma" → nombre → dibujarla **3 veces**.
- Al guardar, se compara con las formas activas. Si se parece demasiado a alguna, avisa ("Se confunde con `check`") y deja guardarla igualmente o repetirla.
- También se pueden **regrabar** las formas incluidas con tu propia letra, lo que suele mejorar mucho la tasa de aciertos.
- Se guardan en el móvil. El PC solo conoce su nombre (`custom:<nombre>`).

## 6. Chuleta

Un recuadro en el centro del trackpad recuerda las formas y lo que hace cada una.

**En reposo (modo trackpad):**
- Recuadro gris oscuro con esquinas redondeadas, centrado, de poco contraste para que no distraiga.
- Muestra las formas con `pin: true`, hasta 6. Cada una con un **dibujo en miniatura** de la forma (generado a partir de su plantilla) y su **texto de ayuda**.
- Es solo dibujo: toda la superficie, recuadro incluido, sigue funcionando como trackpad.
- Se atenúa con el resto de la pantalla y se puede ocultar en ajustes.

**Al activar el modo gestos:**
- El recuadro crece y muestra **todas** las formas con acción asignada, no solo las fijadas, en una cuadrícula de miniatura + texto.
- Si hay reglas por aplicación, muestra las que valen para la **ventana activa** del PC, con su nombre arriba ("Chrome").
- El trazo se dibuja por encima. Al soltar, se resalta la forma reconocida (o se indica "No reconocido") y la chuleta vuelve a su tamaño.

**De dónde salen los datos:**
- Las acciones y los textos de ayuda viven en el PC (`gestures.json`), las plantillas en el móvil. El host envía la lista `forma → texto de ayuda, fijada` al móvil:
  - justo después del handshake,
  - al recargar `gestures.json`,
  - al cambiar la ventana activa, si hay reglas por aplicación.
- El móvil guarda la última lista recibida, para mostrarla aunque se desconecte (atenuada y con "sin conexión").
- Una forma que el host conoce pero el móvil no (p. ej. una propia borrada) aparece como "plantilla no encontrada" en lugar de en miniatura.

## 7. Límites (qué no se puede hacer o puede fallar)

| Límite | Causa | Consecuencia |
|---|---|---|
| No se envían teclas a ventanas de **administrador** | UIPI: un proceso normal no puede inyectar entrada en uno elevado. | Los atajos no llegan a esas ventanas. Solución: ejecutar el host como administrador (no recomendado por defecto). |
| **Ctrl+Alt+Supr** imposible | Es una secuencia de seguridad que solo genera el teclado físico. | Sin solución. **Bloquear el PC** sí se puede con una acción `mymouse: lock` (API `LockWorkStation`). |
| Nada funciona en la **pantalla de UAC** ni en la de inicio de sesión | Escritorio seguro, aislado del resto. | Sin solución. |
| Programas que piden **administrador** | Windows muestra el aviso de UAC al abrirlos. | Funciona, pero hay que aceptar el aviso con el ratón. |
| `focusIfRunning` falla a veces | Windows limita qué procesos pueden pasar una ventana al frente. | En el peor caso la ventana parpadea en la barra de tareas en vez de aparecer. |
| Algunos **juegos** ignoran la entrada | Antitrampas que descartan la entrada simulada. | Sin solución con `SendInput`. |
| Atajos que ya usa Windows | P. ej. `Win+L` lo intercepta el sistema. | Usar la acción equivalente (`mymouse: lock`) cuando exista. |
| Cantidad de formas | Más formas = más confusiones. | Recomendado: hasta 10 activas. |

Además, **no se ejecutan comandos arbitrarios** (PowerShell, scripts) en v1. Se puede llegar a lo mismo con `launch` sobre un `.bat` o un `.lnk` que tú hayas creado, lo cual deja la decisión en tus manos y en tu PC.

## 8. Protocolo

Mensaje nuevo, cliente → host:

| Tipo | Nombre | Payload |
|---|---|---|
| `0x06` | `GESTURE` | `u8 longitud` + nombre en UTF-8 (1–64 bytes) |

Es el primer mensaje de tamaño variable. Sustituye al `KEYCOMBO` que estaba reservado: el móvil ya no decide la acción.

Mensajes nuevos, host → cliente:

| Tipo | Nombre | Payload |
|---|---|---|
| `0x86` | `GESTURE_RESULT` | `u8 estado`: 0 = ejecutado, 1 = sin asignar, 2 = error |
| `0x87` | `GESTURE_LIST` | `u8 longitud` + contexto UTF-8 (nombre de la app activa, puede ir vacío), `u8 n`, y `n` entradas: `u8 flags` (bit 0 = fijada), `u8 longitud` + nombre, `u8 longitud` + texto de ayuda |

Como máximo 64 entradas y 255 bytes por cadena, así que un `GESTURE_LIST` cabe siempre en menos de 25 KB. El host envía una lista nueva entera cada vez, no cambios parciales.

Sube la versión del protocolo a 2. Un móvil con v2 puede conectarse a un host v1, pero no tendrá el modo gestos.

## 9. Testing

- **Reconocedor:** un conjunto de trazos grabados en el P10 (al menos 20 por forma) como tests en la JVM. La tasa de aciertos y de rechazos se mide en cada cambio.
- **Activación:** tests del intérprete con secuencias de 3 dedos (mantenidos, quietos o moviéndose, desfasados) y con apoyos accidentales de la palma.
- **Host:** parser de `gestures.json`, parser de `keys`, selección por aplicación activa y un ejecutor de acciones falso.
- **Manual:** los criterios de "hecho" de la [hoja de ruta](00-roadmap.md#v1-modo-gestos).
