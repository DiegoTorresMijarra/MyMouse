# MyMouse — v4.7: fondo "universo", icono de la app y retoques

> Base: [spec-v4.5-estilo.md](spec-v4.5-estilo.md) (§6, notas de implementación), [spec-v4-toolbox.md](spec-v4-toolbox.md)
>
> Origen: prueba de la v4.5/v4.6 en el PC real. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Problema

En Windows la toolbox se ve **demasiado gris y difuminada**:
- El Acrylic del sistema más las tarjetas blancas translúcidas dan un gris plano, y el cristal no se aprecia.
- El texto secundario apenas se lee; por ejemplo, la línea "¿Usas el modo Bluetooth?…".
- La píldora de estado está mal dibujada: sale como una elipse borrosa.

El usuario pide un **fondo de "universo"** detrás, para que se aprecie el cristal. Como referencia dio un elemento de uiverse.io (`sinful_9361/proud-kangaroo-29`), que no se ha podido descargar desde aquí. Se hace un fondo propio en esa línea: espacio profundo, nebulosas de color y estrellas.

## 2. Fondo universo (Windows y móvil)

Es un componente reutilizable: `UniverseBackground`, un control WPF en `Ui/` y una `View` o dibujable en Android.

- **Base**: degradado lineal diagonal `#070616` → `#120D2E` → `#1B0F36`.
- **Nebulosas**: 3 degradados radiales grandes y suaves, sumados encima de la base:
  - violeta `#7C3AED` al 35 % arriba a la izquierda (radio ~70 % del ancho);
  - azul `#2563EB` al 28 % abajo a la derecha (radio ~60 %);
  - magenta `#DB2777` al 18 % en el centro-derecha (radio ~40 %).
- **Estrellas**: unas 90 por cada 400 × 600 px.
  - Posiciones pseudoaleatorias con semilla fija, para que no "salten" al redimensionar: se generan en coordenadas 0–1.
  - Radio de 0,5–1,6 px y opacidad del 35–100 %.
  - El 10 % lleva un halo suave, un radial de 3–4 px.
  - Unas 12 estrellas parpadean: la opacidad oscila con periodos de 2–5 s y fases distintas. Solo mientras la ventana está visible, con `CompositionTarget.Rendering` o un `DispatcherTimer` de ~30 fps.
- La lógica pura va en `HostCore` y en `core`, con tests: generar estrellas con semilla (determinista, densidad por área, rangos) y calcular la opacidad del parpadeo en el instante t.

## 3. Windows

### 3.1 Cristal sobre el universo
- La toolbox, el gesto en vivo y el editor pintan `UniverseBackground` como fondo de toda la ventana, dentro de las esquinas redondeadas.
- *(decisión)* **Se deja de usar Acrylic/Mica del sistema**: es lo que daba el gris. Las ventanas mantienen las esquinas redondeadas del sistema (`DWMWA_WINDOW_CORNER_PREFERENCE`) y la sombra.
- Ajuste "Efecto cristal":
  - Activado: tarjetas translúcidas sobre el universo.
  - Desactivado: tarjetas sólidas `#1A1830` sobre el mismo universo.
- **Tokens nuevos de cristal**, sustituyen a los de la v4.5 en Windows y Android:

  | Token | Valor |
  |---|---|
  | Tarjeta | Blanco al 7 % + borde de 1 px blanco al 16 % + brillo superior (blanco del 10 % al 0 % en el 35 % de arriba). Sin sombra. |
  | Elevado/hover | Blanco al 12 % |
  | Texto | `#F4F5FA` |
  | Texto secundario | `#B9BDD0` (mínimo, contraste 4,5:1 sobre la tarjeta) |
  | Texto terciario | `#8C90A8` |
  | Acento | `#7C8CFF` (azul lavanda, combina con el universo). Para botones primarios, degradado `#6D5BFF` → `#3B82F6` |

- Nada de desenfoque sobre el propio universo (sería caro y no hace falta): el "cristal" es translucidez, borde claro y brillo.

### 3.2 Píldora de estado
- Una `Border` con `CornerRadius` igual a la mitad de su alto real: alto fijo de 26 px y radio 13. Relleno de 10 px en horizontal y fondo del color de estado al 16 % con borde al 40 %.
- Punto de 8 px (verde, ámbar, rojo o gris) con un halo suave del mismo color.
- Texto corto en 12 px semibold: "Conectado · <modelo>", "Esperando al móvil", "Sin dispositivo", "En pausa".
- **La explicación larga va debajo, como texto secundario** legible y no dentro de la píldora. Por ejemplo: "Conecta el cable y activa la depuración USB. ¿Usas el modo Bluetooth? Entonces el PC no necesita MyMouse".
- Sin desenfoque ni opacidad sobre el texto. Ningún elemento de la toolbox debe tener `Effect = BlurEffect` sobre texto.

### 3.3 Icono de la app
Nuevo icono propio, igual en Windows y Android:
- Cuadrado redondeado (radio del 22 %) con el fondo universo en miniatura: degradado violeta → azul, con 3 o 4 estrellas.
- Encima, un "trackpad" de cristal: un rectángulo redondeado blanco al 18 % con borde blanco al 60 %.
- Un trazo curvo luminoso que termina en un punto (el dedo o cursor), blanco con un halo lavanda.
- Legible a 16 px: a ese tamaño se simplifica, sin estrellas y con un trazo más grueso.

Dónde se usa:
- **Windows**: `MyMouse.ico` multi-tamaño (16, 20, 24, 32, 40, 48, 64 y 256) como `ApplicationIcon` del exe. Las ventanas toolbox y editor lo usan (`Window.Icon`), y aparece en la barra de tareas cuando el editor está abierto.
- **Bandeja**: el mismo icono (16/20/24/32 según DPI) con el punto de estado superpuesto abajo a la derecha (verde, ámbar, rojo o gris), en sustitución de los iconos dibujados de antes.
- **Android**:
  - Icono adaptativo (`mipmap-anydpi-v26/ic_launcher.xml` con primer plano y fondo como `VectorDrawable`), más PNG de respaldo en `mipmap-*dpi` y `android:icon` y `roundIcon` en el manifiesto.
  - El icono de la app y el de la barra superior de la app usan el mismo dibujo.
- Se generan con un script reproducible (`tools/icon/make_icons.py`, con Pillow y SVG a mano) que escribe los ficheros. Los ficheros generados se suben al repositorio; el script queda para regenerarlos.

### 3.4 Icono visible en la barra de tareas
- Windows no deja que una app saque su icono de la bandeja "oculta" (la flecha ˄) por API pública.
- En Windows 11 existe el valor de registro `HKCU\Control Panel\NotifyIconSettings\<id>\IsPromoted` (DWORD 1 = visible), por icono y con `ExecutablePath`.
- Se añade la opción **"Mostrar siempre el icono en la barra de tareas"** en Ajustes de la toolbox:
  - Busca las subclaves cuyo `ExecutablePath` coincide (sin distinguir mayúsculas) con la ruta del exe actual y pone `IsPromoted = 1`. Desactivarla pone 0.
  - Se activa sola la primera vez que arranca la v4.7 *(decisión)*. Si no hay clave (Windows 10, o el icono aún no se ha registrado), se reintenta a los 10 s y luego se rinde, con una línea en el log.
  - En Windows 10, el texto de ayuda explica el paso manual: arrastrar el icono desde la flecha ˄ a la barra.
- La lógica de buscar y seleccionar la subclave va detrás de una interfaz (`IRegistry`) con tests en `HostCore`.

### 3.5 Editor: botón de volver
- En la barra superior del editor, a la izquierda, un botón **"← Volver"**: cierra el editor y vuelve a abrir la toolbox anclada. Si hay cambios sin guardar, pregunta "¿Guardar los cambios?" (Guardar / Descartar / Cancelar).
- `Esc` hace lo mismo cuando el foco no está en un campo de texto.
- El editor también usa el fondo universo y los tokens nuevos. Su lista de gestos ya está bien según la captura; se mantiene.

## 4. Android
- **Fondo**: el mismo universo detrás del trackpad, los Ajustes y el resto de pantallas, en lugar del degradado gris. Las estrellas parpadean solo en pantallas que no sean el trackpad *(decisión: en el trackpad, estático, para no gastar batería ni distraer)*.
- **Tokens**: los nuevos tokens de cristal del §3.1. Con el universo detrás, el desenfoque de Android 12+ ya se notará.
- **Hoja de gestos** (la lista de formas del modo gestos, `GestureSheet`): en **dos columnas**:
  - Siempre en horizontal.
  - En vertical, solo si cada columna tiene al menos 150 dp de ancho; si no, una columna.
  - Si no cabe a lo alto, se encoge entera, como la chuleta en la v4.6.
- La chuleta básica ya va en dos columnas en horizontal desde la v4.6, y se mantiene.
- **Icono nuevo de la app** (§3.3).
- **Widget de volumen** (captura del usuario por USB, en horizontal):
  - La barra sale diminuta: el pulgar es enorme y la pista mide unos pocos píxeles, y el número apenas se lee.
  - Rediseño: fila con el icono de altavoz (44 dp, toque = silenciar) a la izquierda y la pista ocupando todo el ancho restante, de 4 dp de alto, con radio, la parte llena con el acento y el resto blanco al 18 %. El pulgar mide 18 dp, blanco, con sombra suave. El número va a la derecha en texto normal (no terciario), con ancho fijo para 3 cifras.
  - Si el panel es estrecho (2 columnas en horizontal), el número pasa debajo o se oculta antes de encoger la pista por debajo de 96 dp.
  - Silenciado: icono con una × en el color de texto secundario (no rojo chillón), y la parte llena de la pista en gris.
  - La lógica de la geometría (pista, pulgar y posición del número según el ancho) va en `core`, con tests.
- **Chuleta y modo depuración**:
  - En horizontal, con depuración activada, la mini pantalla de depuración se dibuja encima de la chuleta y la tapa.
  - La chuleta tiene que colocarse en el espacio libre: a la derecha de la mini pantalla en horizontal, debajo en vertical. Si no cabe, se encoge.
  - Ningún elemento del trackpad se solapa con otro. Va como una función pura de disposición en `core`, con tests de los dos casos.

## 5. Criterios de "hecho"
1. Toolbox, gesto en vivo y editor con el universo de fondo y tarjetas de cristal claramente distinguibles, sin el gris plano.
2. Píldora de estado nítida y explicación legible.
3. Icono propio en el exe, las ventanas, la barra de tareas, la bandeja (con punto de estado) y el lanzador de Android.
4. Opción de icono siempre visible en Windows 11.
5. Botón Volver en el editor.
6. Hoja de gestos del móvil en dos columnas.
7. Tests: generación de estrellas y parpadeo (los dos lados) y selección de la clave `NotifyIconSettings`.

## 6. Notas de implementación

Implementado: toda la lista de la §5. La lógica nueva tiene tests en los dos lados: estrellas con semilla y parpadeo (`StarField` en `core` y en `MyMouse.HostCore.Universe`, con los mismos valores de referencia en los dos), selección de la clave `NotifyIconSettings` (`TaskbarIconPromotion` con `IRegistry`, en `HostCore`), geometría del volumen (`VolumeGeometry`) y disposición de las hojas del trackpad (`SheetLayout`, en `core`). Desviaciones y lo que **no se ha podido verificar**:

### Qué hay dónde
- **Estrellas:** el mismo generador (mulberry32, semilla `0x4D794D73`) en Kotlin y en C#, de modo que el cielo es idéntico en el PC y en el móvil. Por estrella se consumen siempre siete números (x, y, radio, opacidad, halo, periodo y fase). Una de cada 7 estrellas parpadea (13 de 90, "unas 12"); ~8 % llevan halo en 90 (la spec dice 10 %, es una probabilidad). Número de estrellas = 90 por cada 400 × 600 dp (mínimo 12, máximo 220); al redimensionar se añaden o quitan estrellas por el final sin mover las demás. Opacidad del parpadeo: oscila entre el 35 % de la suya y la suya con una senoide de 2–5 s.
- **PC:** `Ui/UniverseBackground.cs` (control), `Ui/Theme.cs` (tokens nuevos, `StatusPill`), `Ui/Controls.cs` (`FloatingWindow` sin Acrylic), `Ui/AppIcon.cs` y `TrayIcons.cs` (icono embebido), `WindowsRegistry.cs`, `HostApp.cs` (estado corto + explicación, reintento a los 10 s).
- **Móvil:** `UniverseDrawable.kt` (`Drawable`, con parpadeo solo si `animated`), `GlassTheme.kt` (tokens, `GlassCardDrawable`, logo en la barra superior), `GestureSheet.kt`, `CheatSheet.kt`, `DebugOverlay.occupiedArea`, `WidgetPanelView.drawVolume`.
- **Icono:** `tools/icon/make_icons.py` (Pillow) escribe `windows/src/MyMouse.Host/Assets/MyMouse.ico`, los PNG de `mipmap-*dpi`, los dos XML de `mipmap-anydpi-v26`, los vectores `drawable/ic_launcher_{background,foreground}.xml` y `drawable-nodpi/ic_app_logo.png` (la barra superior de la app). El mismo dibujo se define una vez en coordenadas 0–1; los vectores se escriben a mano a partir de esos parámetros. Todo lo generado está en el repositorio.

### Desviaciones
- **"Efecto cristal" ya no recrea ventanas:** como el Acrylic desaparece, el ajuste solo cambia el fondo de las tarjetas (`Theme.GlassEffect`) y se reconstruye la toolbox. El editor abierto no se entera hasta que se reabre. `RecreateGlassWindows` se ha quitado.
- **Marco de la ventana según el sistema, no según el ajuste:** Windows 11 (compilación 22000 o posterior) usa el marco del sistema (`WindowChrome` con marco de cristal, sin `AllowsTransparency`) con `DWMWA_WINDOW_CORNER_PREFERENCE` y su sombra; antes, la ventana transparente con esquinas de radio 16 y una sombra propia **en un elemento aparte**, para que ningún texto pase por un `DropShadowEffect`. No hay `BlurEffect` en ninguna parte.
- **Estado más corto:** "Conectado · <modelo>" ya no lleva "por USB" y "Sin dispositivo" ya no lleva el paréntesis; la explicación (cable, depuración USB, adb, Bluetooth) pasó a `StatusHint`. "Esperando al móvil" lleva ahora "Abre la app MyMouse en el móvil." debajo.
- **Icono visible:** `AlwaysShowIcon` está en `toolbox.json` y vale `true` por defecto, así que el primer arranque de la v4.7 (el fichero aún no lo tiene) lo activa; si el usuario lo quita, se guarda `false` y no se vuelve a poner. En cada arranque con el ajuste activo se aplica de nuevo (es idempotente: no escribe si ya vale 1). La coincidencia de `ExecutablePath` es por la ruta normalizada (sin mayúsculas, con `\` y sin comillas); no resuelve los GUID de carpetas conocidas que Windows pone a veces como prefijo (`{...}\MyMouse\MyMouse.exe`).
- **Editor, avisos de cambios:** el diálogo de cambios sin guardar pasó a "¿Guardar los cambios?" con Guardar / Descartar / **Cancelar** (antes "Cambios sin guardar" y "Seguir editando"), tanto en "← Volver" como al cerrar la ventana o cambiar de pestaña. "← Volver" reabre la toolbox con el modo (anclada o maximizada) que tuviera guardado.
- **Hoja de gestos:** antes eran 2 o 3 celdas por fila según el ancho; ahora 1 o 2 columnas (regla de la §4). El ancho máximo sigue siendo 560 dp. La hoja se coloca con la misma función que la chuleta, así que tampoco se solapa con la depuración.
- **Depuración:** su "espacio ocupado" (para colocar la chuleta) es la minipantalla más sitio fijo para seis líneas de texto, no el que haya en ese momento, para que la chuleta no se mueva al aparecer o desaparecer líneas. En horizontal, los textos de la depuración se recortan con "…" al ancho de la minipantalla. En vertical, con la depuración, la chuleta queda más pequeña (hasta la mitad en un móvil de 640 dp de alto).
- **Volumen:** la zona táctil de la pista es solo su recorrido (de 0 a 100), sin ampliarla a los lados. Con el número debajo (panel estrecho) la fila no crece: la pista sube y el número queda en una línea de 18 dp bajo ella.
- **Brillo de la tarjeta en el móvil:** ahora al 35 % exacto (`GlassCardDrawable` dibujada a mano; antes el 50 % con `GradientDrawable`).
- **Estrellas, halo:** el halo es un radial blanco de 3,5 dp más el radio de la estrella (3–4 px de la spec), con la opacidad de la estrella.
- **Icono adaptativo:** no lleva capa `monochrome` (iconos temáticos de Android 13). El PNG de respaldo no se usa nunca con `minSdk` 26, pero se genera como pide la spec.

### Sin verificar
- **Todo lo que dibuja en Windows:** ninguna ventana se ha ejecutado. En concreto: `UniverseBackground` (nebulosas con `RadialGradientBrush` de coordenadas absolutas, `DispatcherTimer` de 33 ms y el coste de redibujar el fondo 30 veces por segundo), que `WindowChrome` con `GlassFrameThickness = -1` dé la sombra y las esquinas redondeadas con una ventana opaca y sin backdrop, el borde de la ventana en Windows 10, el aspecto de la píldora (`CornerRadius` 13 con alto 26), `Window.Icon` desde un `IconBitmapDecoder` con PNG dentro del .ico y la elección del tamaño del icono de la bandeja (`SystemInformation.SmallIconSize`).
- **`ApplicationIcon`:** `dotnet publish -r win-x64 --self-contained -p:PublishSingleFile=true` funciona desde Linux con el icono, sin el aviso NETSDK1074; no se ha abierto el `.exe` en Windows para ver el icono.
- **`IsPromoted`:** no se ha probado contra un Windows 11 real. Si Windows no hace caso al valor o crea la clave tarde (más de 10 s), el icono sigue en la zona oculta y el log dice "NoKey"; la ayuda del diálogo explica el paso manual.
- **El módulo `app` de Android no se ha compilado** (sin Android SDK local): solo se ha pasado el compilador de Kotlin sobre las fuentes para descartar errores de sintaxis (los únicos errores son los de las clases de Android que faltan). Riesgos: `Drawable.setVisible`/`scheduleSelf` en `UniverseDrawable` (el parpadeo depende de que la vista sea el `callback` del dibujable), la firma de `onBoundsChange` y `getOpacity` en `GlassCardDrawable`, el degradado radial con `aapt:attr` en los vectores del icono, y que `RenderEffect` desenfoque con estrellas detrás se vea bien.
- **Rendimiento del parpadeo en el móvil:** cada pantalla con estrellas animadas se redibuja ~30 veces por segundo (solo con la pantalla visible). Si gasta batería de más, bajar a `FRAME_MS` mayor en `UniverseDrawable`.
