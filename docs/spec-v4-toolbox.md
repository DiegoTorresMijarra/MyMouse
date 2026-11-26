# MyMouse — Especificación v4: la toolbox del PC

> Objetivos: [00-roadmap.md](00-roadmap.md) · Base: [spec-v1-gestos.md](spec-v1-gestos.md), [spec-v2-widgets.md](spec-v2-widgets.md), [spec-v3.1-ajustes.md](spec-v3.1-ajustes.md) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)
>
> Estado: **especificada**. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Qué es

Hasta la v3, MyMouse en Windows era un icono en la bandeja con un menú contextual básico. En la v4 pasa a ser **una app de verdad**: un panel flotante anclado a la bandeja, al estilo de la Toolbox de JetBrains. Desde él se controla MyMouse, se ve lo que suena y se configuran los gestos y los widgets sin tocar `gestures.json` a mano.

Tiene dos ventanas:
1. **La toolbox**: un panel con tarjetas, anclado sobre la bandeja.
2. **El gesto en vivo**: una tarjeta pequeña que sube desde la misma esquina mientras dibujas en modo gestos.

## 2. La toolbox

### 2.1 Abrir y cerrar
- **Abrir**:
  - Clic izquierdo en el icono de la bandeja (otro clic la cierra).
  - Desde el móvil, **4 dedos hacia arriba** (gesto reservado en la v3.1).
- **Cerrar**:
  - **4 dedos hacia abajo** desde el móvil.
  - `Esc`.
  - Clic fuera del panel (pierde el foco), salvo que haya un diálogo del editor abierto.
- Abrir desde el móvil **no roba el foco** a la app en la que estás trabajando (ventana `WS_EX_NOACTIVATE` hasta que el usuario hace clic en ella). Así puedes mirar la música o el estado y cerrarla con 4 dedos ↓ sin perder el foco.
- El clic derecho en el icono mantiene un menú mínimo: Abrir MyMouse, Pausar, Salir.

### 2.2 Aspecto y posición
- Ventana sin bordes con esquinas redondeadas (radio de 12 px), sombra y tema oscuro como el de la captura de JetBrains:
  - Fondo `#1E1F22`, tarjetas `#2B2D30`, acento `#3574F0`.
  - Texto `#DFE1E5`, texto secundario `#868A91`.
  - Fuente Segoe UI Variable o, si no está, Segoe UI.
- Ancho fijo de 400 px (escalado por DPI). El alto se ajusta al contenido hasta el 85 % del área de trabajo; a partir de ahí, la lista de tarjetas hace scroll.
- **Posición**: esquina inferior derecha del área de trabajo del monitor donde está la bandeja, a 12 px de los bordes. Si la barra de tareas está en otro lado, se pega a ella.
- **Animación**:
  - Al abrir, sube desde abajo unos 24 px mientras aparece con fundido: 220 ms, suavizado *cubic out*.
  - Al cerrar hace lo inverso en 160 ms.

### 2.3 Tarjetas
El panel es una columna de **tarjetas** ("celdas"). Orden por defecto:

| Tarjeta | Contenido | ¿Se puede quitar? |
|---|---|---|
| **Opciones** | Cabecera: logo, "MyMouse" y estado (punto de color y texto: "Conectado · Poco X8 Pro Max por USB", "Esperando al móvil", "Sin dispositivo", "En pausa"). Fila de botones: Pausar/Reanudar, Gestos y widgets (abre el editor, §4), Ayuda, Ajustes (iniciar con Windows, abrir carpeta de logs, personalizar tarjetas) y Salir. | **No** |
| **Música** | Carátula (de la sesión multimedia de Windows, si la hay), título, artista, ⏮ ⏯ ⏭ y barra de volumen del sistema con silencio. Si no suena nada: "Nada en reproducción". | Sí |
| **Gestos** | Lista compacta de las formas con acción: miniatura de la forma, texto de ayuda (o acción si no hay ayuda) y una chincheta si está fijada. Arriba, "+ Nueva forma". Clic en una fila: abre el editor de esa forma. Las formas sin acción aparecen al final, atenuadas, con "Sin acción · Asignar". | Sí |
| **Widgets del móvil** | Lista de los widgets del panel del móvil, en orden, con "+ Añadir". Clic: editor del widget. | Sí (oculta por defecto) *(decisión)* |

- **Personalizar**: en Ajustes → "Personalizar tarjetas…" se marca o desmarca cada tarjeta (salvo Opciones) y se reordena con flechas ↑ ↓. Se guarda en `%APPDATA%\MyMouse\toolbox.json`.
- La toolbox funciona **aunque no haya móvil conectado**: se pueden editar gestos y widgets, y la música funciona igual.

## 3. El gesto en vivo

Una tarjeta pequeña e independiente de la toolbox: 260 × 300 px, mismo tema, siempre encima, sin foco y transparente para el ratón (no intercepta clics).

**Flujo:**
1. **Al activar el modo gestos en el móvil**, la tarjeta sube desde la esquina inferior derecha (misma animación que la toolbox). Si la toolbox está abierta, sube justo encima de ella. Muestra un lienzo vacío con el texto "Dibuja una forma…" y una cuenta atrás fina con el tiempo para empezar a dibujar (el ajuste de la v1).
2. **Mientras dibujas**, el trazo aparece en directo:
   - Línea de 4 px en color de acento, con un punto en la cabeza del trazo.
   - Escalado para encajar en el lienzo, manteniendo proporciones; el trazo se re-encaja de forma suave a medida que crece.
3. **Al terminar**, debajo del lienzo aparece el resultado y el trazo se colorea:

   | Resultado | Color del trazo | Texto |
   |---|---|---|
   | Forma reconocida con acción | Verde `#5FB865` | Miniatura de la forma ideal superpuesta tenue, la ayuda en grande ("Abrir Chrome") y la acción en pequeño (`launch chrome.exe`) |
   | Reconocida sin acción | Ámbar `#E5A50A` | "Forma «Z»: sin acción" y un botón "Asignar" (abre el editor si el usuario hace clic) |
   | No reconocida | Gris | "?" grande y "No se ha reconocido la forma" |
   | Cancelada o tiempo agotado | — | "Cancelado" |
   | La acción falló | Rojo | El error |

4. **Pasados ~1,5 s** (2,5 s si hay botón "Asignar"), la tarjeta baja y desaparece. Si el usuario vuelve a activar el modo gestos antes, se reutiliza sin animación de cierre.

- Se puede desactivar en Ajustes de la toolbox ("Mostrar el gesto en vivo", activado por defecto).
- Los avisos tipo globo de la bandeja que había para formas sin acción se sustituyen por esta tarjeta.

## 4. Editor de gestos y widgets

Ventana normal (con foco), mismo tema, que se abre desde la toolbox. Tiene dos pestañas: **Gestos** y **Widgets**. Reemplaza a "Editar gestos y widgets…" en el Bloc de notas. El fichero sigue existiendo (y se puede editar a mano: "Abrir el fichero" en un menú ⋯).

### 4.1 Pestaña Gestos
- A la izquierda, la lista de formas: las 15 del catálogo activas y las propias. Cada una con su miniatura, nombre y estado de la acción. Filtro "solo sin acción".
- A la derecha, el detalle de la forma seleccionada:
  - **Acción**, con un selector de tipo y un formulario para cada uno:
    - **Atajo de teclado**: un campo "Pulsa la combinación…" que **graba** la combinación pulsada (incluida la tecla Win, mediante un hook de teclado de bajo nivel solo mientras el campo está activo). Se muestra como `Ctrl+Shift+T`. También se puede escribir a mano.
    - **Abrir app, archivo o web**:
      - Botón "Elegir programa…" (diálogo de archivo, filtro `.exe`).
      - Lista "Apps instaladas" (accesos directos del menú Inicio, de usuario y comunes) con buscador.
      - Campo libre para una URL o una ruta.
      - Argumentos (opcional) y casilla "Si ya está abierta, traerla al frente".
    - **Escribir texto**: un cuadro de texto multilínea.
    - **Multimedia**: un desplegable (reproducir/pausar, siguiente, anterior, subir, bajar, silencio).
    - **MyMouse**: un desplegable (pausar, bloquear el PC).
    - **Ninguna**.
  - **Texto de ayuda** (el que sale en la chuleta y en el gesto en vivo).
  - Casilla **"Fijar en la chuleta del móvil"**.
  - **Reglas por app** *(decisión: avanzado, plegado)*: lista de "en `<proceso>.exe` → otra acción".
  - Botón **Probar** (ejecuta la acción ahora) y **Guardar**. Guardar escribe `gestures.json`: el host lo vuelve a cargar y el móvil recibe la nueva lista al momento.
- **Guardar el fichero**:
  - Se reescribe con formato limpio. *(decisión: los comentarios escritos a mano se pierden)* Antes de la primera reescritura se guarda una copia, `gestures.json.bak`.
  - Si el fichero tiene errores de sintaxis, el editor lo dice y ofrece abrirlo en el Bloc de notas en vez de pisarlo.

### 4.2 Formas nuevas
Las formas se pueden grabar **en el móvil** (como hasta ahora) o **en el PC con el ratón o el touchpad del portátil**.

- **En el PC**: "+ Nueva forma" abre un lienzo de 300 × 300 px.
  - El usuario da un nombre y dibuja **3 muestras** manteniendo pulsado el botón izquierdo del ratón (o con el touchpad).
  - Se ve cada muestra y se puede repetir. Al terminar, se envía al móvil.
  - Si el móvil no está conectado (o es una versión antigua), el botón se desactiva con el texto "Conecta el móvil por USB para grabar formas".
- **El móvil es el dueño de las formas**: las plantillas viven en `shapes.json` del móvil, porque el reconocedor está allí y debe funcionar también por Bluetooth sin el PC.
  - El PC guarda una copia (`%APPDATA%\MyMouse\shapes-cache.json`) para dibujar miniaturas y editar sin conexión.
  - Las formas que se crean en el PC se mandan al móvil (§5). El móvil comprueba si se confunden con otra (el mismo aviso que al grabarlas allí) y contesta.
- **Aviso de forma nueva**: cuando llega del móvil una forma propia que el PC no conocía (o una forma sin acción), el PC muestra una notificación de Windows ("Nueva forma «café»: ¿qué quieres que haga?"). Al pulsarla, se abre el editor con esa forma seleccionada y el foco en "Acción".
- Borrar una forma propia desde el editor: se pide confirmación, se borra en el móvil y su regla en `gestures.json` se elimina.

### 4.3 Pestaña Widgets
- Lista ordenable (arrastrar o ↑ ↓) de los widgets del móvil, con "+ Añadir" (botón, música, volumen) y borrar.
- Un botón tiene etiqueta, icono (campo de texto con un botón que abre el selector de emojis de Windows con `Win+.`) y la misma acción que los gestos.
- Hay una vista previa del panel del móvil (rejilla de 4 columnas).
- Se guarda en `"widgets"` de `gestures.json`.

## 5. Protocolo (versión 5)

Sube a la versión 5 (la 4 es la de la v3.1). Mismo formato de longitud variable y negociación. Con un host o un móvil < 5 no hay toolbox desde el móvil, ni gesto en vivo, ni sincronización de formas: lo demás funciona.

### Móvil → PC
| Tipo | Nombre | Payload |
|---|---|---|
| `0x0D` | `TOOLBOX` | `u8` 1 = mostrar, 0 = ocultar (4 dedos ↑ / ↓) |
| `0x0E` | `GESTURE_MODE` | `u8` 1 = activado (empieza a esperar trazo), 0 = salido; `u16` tiempo para empezar a dibujar en ms |
| `0x0F` | `STROKE_POINTS` | `u16 len` + `u8 n`, `n` × (`u16 x`, `u16 y`). Coordenadas normalizadas 0–65535 del área del trackpad. Se envían en lotes de como mucho 16 ms. El primer lote de un trazo lleva el primer punto |
| `0x10` | `STROKE_END` | `u8` resultado: 0 = reconocida (le sigue `GESTURE` como hasta ahora), 1 = no reconocida, 2 = cancelada, 3 = tiempo agotado |
| `0x11` | `SHAPE_INFO` | `u16 len` + `str8 nombre`, `u8 muestras`, por muestra `u8 n` + `n` × (`u16 x`, `u16 y`) normalizados. Una forma por mensaje; los puntos se remuestrean a 32 por muestra en el móvil |
| `0x12` | `SHAPE_LIST_END` | `u8` número total de formas propias enviadas |
| `0x13` | `SHAPE_RESULT` | `u16 len` + `str8 nombre`, `u8` estado (0 = guardada, 1 = se confunde con otra, 2 = nombre repetido, 3 = no válida, 4 = borrada, 5 = no existe), `str8` forma con la que se confunde (o vacío) |

### PC → móvil
| Tipo | Nombre | Payload |
|---|---|---|
| `0x8C` | `SHAPE_ADD` | Mismo formato que `SHAPE_INFO`. `u8 force` al final: 1 = guardar aunque se confunda |
| `0x8D` | `SHAPE_DELETE` | `u16 len` + `str8 nombre` |

**Reglas:**
- Tras el handshake (versión ≥ 5), el móvil envía un `SHAPE_INFO` por cada forma propia y luego `SHAPE_LIST_END`. Lo repite entero cada vez que cambian sus formas.
- El catálogo de 15 no se envía: el PC tiene sus propias miniaturas ideales.
- `GESTURE_RESULT` sigue llegando al móvil igual que antes. El PC usa el resultado de ejecutar la acción para el gesto en vivo.
- Escribir los vectores en `PROTOCOL.md` y probarlos en los dos lados.

## 6. Arquitectura en Windows

- `MyMouse.Host` pasa a **WPF** (`UseWPF` y `UseWindowsForms`, el segundo solo para `NotifyIcon`).
  - La toolbox, el gesto en vivo y el editor son ventanas WPF con estilos propios (sin librerías externas de UI). *(decisión: nada de WinUI 3 ni WebView2, para no añadir dependencias de ejecución)*
- **Lógica sin UI en `MyMouse.HostCore`**, con tests:
  - Edición y serialización de `GestureConfig`, de ida y vuelta: parsear → editar → escribir → parsear da lo mismo.
  - `ShapeSyncService`: caché, formas nuevas, añadir y borrar, resultados.
  - `LiveStrokeModel`: acumula puntos, calcula el encaje, estados y tiempos de cierre.
  - `ToolboxLayout`: tarjetas visibles y orden, cargar y guardar.
  - Formato de combinaciones grabadas.
  - Lista de apps del menú Inicio, detrás de una interfaz.
- Las miniaturas de las 15 formas del catálogo se generan desde las mismas definiciones que en el móvil. Se portan a C# las coordenadas de `ShapeCatalog`, con un test que compara con los vectores de Kotlin.
- La ventana del gesto en vivo usa `WS_EX_NOACTIVATE | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_TOPMOST`.
- Notificaciones: toast de Windows (`Microsoft.Toolkit.Uwp.Notifications` / `CommunityToolkit.WinUI.Notifications`, MIT) o, si da problemas con un ejecutable sin empaquetar, el globo de `NotifyIcon` con clic que abre el editor.

## 7. Android
- **4 dedos ↑ / ↓**: envían `TOOLBOX(1/0)` si el host es ≥ 5. Si no, aviso corto: "Actualiza MyMouse en el PC".
- **Modo gestos**: `GESTURE_MODE`, `STROKE_POINTS` (lotes de como mucho 16 ms) y `STROKE_END` desde `TrackpadEngine` (lógica en `core`, con tests).
- **`ShapeStore`**:
  - Envía la lista al conectar y al cambiar.
  - Aplica `SHAPE_ADD` (comprobando confusión con la misma lógica de `RecordShapeActivity`, que se mueve a `core`) y `SHAPE_DELETE`.
  - Responde `SHAPE_RESULT`.
  - Si la pantalla de formas está abierta, se refresca.

## 8. Criterios de "hecho" de la v4
1. Abrir y cerrar la toolbox con 4 dedos ↑ / ↓ desde el móvil, y con clic en la bandeja.
2. Ver el trazo en vivo en el PC al dibujar, con el resultado, sin mirar el móvil.
3. Crear una forma nueva con el ratón del PC y asignarle "abrir Chrome" sin tocar el fichero, en menos de un minuto.
4. Grabar una forma en el móvil hace que aparezca el aviso en el PC y se pueda asignar desde él.
5. Quitar y reordenar tarjetas; la de Opciones no se puede quitar.
6. Tests de protocolo (en los dos lados), de la edición de la configuración, de la sincronización de formas y del modelo del trazo en vivo.

## 9. Fuera de alcance (v5)
- Gestos por Bluetooth con acciones guardadas en el móvil.
- Instalador de Windows con adb incluido.
- Temas claros.
- Varios móviles a la vez.
