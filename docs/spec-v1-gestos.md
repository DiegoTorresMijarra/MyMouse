# MyMouse — Especificación v1: modo gestos

> Objetivos: [00-roadmap.md](00-roadmap.md#v1-modo-gestos) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)
>
> Estado: **implementada** (release `v1.0`). Pendiente de validar en uso real; los umbrales pueden cambiar tras la prueba.

## 1. Flujo de uso

1. **Activar:** apoyar **3 dedos** a la vez (con menos de 150 ms entre el primero y el último) y mantenerlos quietos **300 ms**. El móvil vibra y la pantalla muestra un borde de color: el modo gestos está activo, y la chuleta del centro se amplía con todas las formas disponibles (ver §6).
2. **Levantar** los 3 dedos.
3. **Dibujar** la forma con **un dedo**, de un solo trazo, empezando en menos de 3 s desde que se levantan los dedos. El trazo se dibuja en pantalla mientras se hace.
4. **Levantar** el dedo. El móvil reconoce la forma y muestra durante 1,5 s su nombre y la acción asignada, o "No reconocido".
5. El modo gestos termina solo; se vuelve al trackpad.

**Cancelar:** no empezar a dibujar en 3 s, o apoyar un segundo dedo en cualquier momento del modo gestos (incluido volver a apoyar los 3).

Por qué así:
- **3 dedos mantenidos** no choca con nada de la v0 (1 dedo = mover, 2 = scroll/clic derecho; 3 ya está reservado). Mantenerlos quietos evita activaciones accidentales al apoyar la mano. N es configurable (3 o 4).
- **Dibujar con 1 dedo** es más preciso que con 3 y el algoritmo trabaja con una sola línea.
- **La pantalla del móvil sirve de feedback**: ves el trazo y lo que se ha reconocido antes de que pase nada en el PC.

## 2. Catálogo de formas incluidas

Todas son de **un solo trazo**. El catálogo trae 15; se recomienda **activar 8–10 a la vez**, porque cuantas menos haya, menos se confunden.

| Grupo | Formas | Id | Notas |
|---|---|---|---|
| Líneas | → ← ↑ ↓ | `line-right`, `line-left`, `line-up`, `line-down` | Las más rápidas. Se detectan por geometría, no por plantilla (ver §4). |
| Ángulos | V, Λ, <, > | `v`, `caret`, `lt`, `gt` | Dos segmentos con un vértice. |
| Cerradas | Círculo, triángulo, cuadrado | `circle`, `triangle`, `square` | Da igual el sentido de giro y el punto de inicio. |
| Símbolos | ✓ (check), Z, S, L | `check`, `z`, `s`, `l` | |
| Propias | Las que grabes | `custom:<nombre>` | Ver §5. |

**Pares que se confunden** (al grabar una forma, el móvil avisa si se confunde con otra activa):
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
- `pin`: `true` para destacar la forma en la sección "Tus formas" de la chuleta del modo gestos (ver §6).

Sintaxis de `keys`: modificadores `Ctrl`, `Alt`, `Shift` y `Win`, unidos con `+`, más una tecla: `A`–`Z`, `0`–`9`, `F1`–`F24`, `Enter`, `Tab`, `Esc`, `Space`, `Backspace`, `Delete`, `Home`, `End`, `PageUp`, `PageDown`, `Up`, `Down`, `Left`, `Right`, `PrintScreen`… Las secuencias van separadas por comas. Se envían como teclas virtuales (VK), así que **no dependen de la distribución del teclado** para letras y teclas especiales. Los símbolos (`ñ`, `@`) se escriben mejor con `text`.

Si llega una forma sin acción asignada, el host lo apunta en el log y muestra un aviso en la bandeja ("Forma `custom:estrella` sin asignar").

## 4. Reconocimiento (en el móvil)

1. **Filtro previo:** si el trazo mide menos de 40 px de ancho y de alto, se trata como un toque y se descarta.
2. **Líneas:** si el trazo es casi recto (recorrido suavizado < 1,15 × distancia entre extremos, o muy estrecho: lado corto < 20 % del largo), se clasifica por la dirección en ↑ ↓ ← →. Es más fiable que una plantilla y distingue el sentido. Si esa línea está desactivada, se prueba con las plantillas.
3. **Resto:** algoritmo **$1 / Protractor** contra las plantillas activas.
   - Normaliza tamaño, posición y número de puntos.
   - **Sensible a la rotación** (si no, `<` y `>`, o `v` y `caret`, serían la misma forma).
   - Tolera ±30° de giro, no más.
   - Todas las formas llevan plantilla en los dos sentidos; las cerradas, además, con 8 puntos de inicio distintos.
   - **Círculo, triángulo y cuadrado** se parecen mucho para Protractor: si la mejor es una de las tres y el trazo es cerrado, se decide por el número de esquinas (0–1 círculo, 3 triángulo, 4–5 cuadrado).
4. **Rechazo:** la forma solo se acepta si su puntuación (coseno) llega a 0,85 **y** saca al menos 0,02 a la segunda. Si no, "No reconocido". Es preferible no hacer nada a hacer algo equivocado.

Todo esto es lógica pura en `android/core`, testeable en la JVM con trazos grabados.

## 5. Formas propias

- En el móvil: modo ventana → Ajustes → **Formas…** → "Nueva forma" → nombre → dibujarla **3 veces**.
- En la misma pantalla se activan y desactivan las formas incluidas.
- Al guardar, se compara con las formas activas. Si se parece demasiado a alguna, avisa ("Se confunde con `check`") y deja guardarla igualmente o repetirla.
- También se pueden **regrabar** las formas incluidas con tu propia letra, lo que suele mejorar mucho la tasa de aciertos.
- Se guardan en el móvil. El PC solo conoce su nombre (`custom:<nombre>`).

## 6. Chuleta

El recuadro gris del centro del trackpad cambia según el modo.

**En reposo (modo trackpad):**
- Muestra los **gestos básicos del trackpad**: mover, clic, clic derecho, scroll, zoom, arrastrar, "3 dedos = modo gestos" y cómo salir del bloqueo. Está definida en la [spec de la v0](spec-v0.md#212-chuleta).

**Al activar el modo gestos:**
- El recuadro crece y cambia de contenido: muestra las formas con acción asignada, en una cuadrícula de **dibujo en miniatura** (generado a partir de la plantilla) + **texto de ayuda** ("Abrir Chrome").
- Arriba, la sección **"Tus formas"**: las marcadas con `pin: true`, destacadas. Debajo, el resto.
- Si hay reglas por aplicación, muestra las que valen para la **ventana activa** del PC, con su nombre arriba ("Chrome").
- El trazo se dibuja por encima. Al soltar, se resalta la forma reconocida (o se indica "No reconocido") y vuelve la chuleta de reposo.

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

Protocolo **versión 2**: `GESTURE` (móvil → PC), `GESTURE_RESULT` y `GESTURE_LIST` (PC → móvil). Son los primeros mensajes de tamaño variable y llevan delante la longitud en un `u16`. Formato exacto y vectores de prueba en [PROTOCOL.md](../protocol/PROTOCOL.md).

- `GESTURE` sustituye al `KEYCOMBO` que estaba reservado: el móvil ya no decide la acción.
- El host negocia la versión: un móvil v2 con un host v1 funciona como trackpad, sin modo gestos (la chuleta lo avisa).

## 8.1 Host de Windows

- `%APPDATA%\MyMouse\gestures.json`. Se crea con un ejemplo la primera vez; acepta comentarios `//` y comas finales.
- Se recarga solo al guardarlo. Si tiene un error de JSON se mantiene la versión anterior; las reglas sueltas mal escritas se ignoran. En ambos casos, aviso en la bandeja y detalle en el log.
- Menú de la bandeja → **Editar gestos…** abre el fichero en el Bloc de notas.
- La ventana activa se mira cada 0,5 s; si cambia entre apps con reglas propias, se envía un `GESTURE_LIST` nuevo.
- Forma sin regla: aviso en la bandeja (como mucho uno cada 30 s por forma).

## 9. Testing

- **Reconocedor:** un conjunto de trazos grabados en el P10 (al menos 20 por forma) como tests en la JVM. La tasa de aciertos y de rechazos se mide en cada cambio.
- **Activación:** tests del intérprete con secuencias de 3 dedos (mantenidos, quietos o moviéndose, desfasados) y con apoyos accidentales de la palma.
- **Host:** parser de `gestures.json`, parser de `keys`, selección por aplicación activa y un ejecutor de acciones falso.
- **Manual:** los criterios de "hecho" de la [hoja de ruta](00-roadmap.md#v1-modo-gestos).
