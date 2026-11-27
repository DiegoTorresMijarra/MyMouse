# MyMouse — v4.8: temas

> Base: [spec-v4.7-universo.md](spec-v4.7-universo.md) (§6, notas de implementación)
>
> Origen: al usuario el fondo universo del móvil le parece demasiado morado. Lo quiere más sobrio y oscuro, y quiere poder elegir el tema en el móvil y en el PC por separado. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Qué cambia
- **Sección "Tema"** en los Ajustes del móvil y en los Ajustes de la toolbox del PC.
- **Cada lado guarda su elección**: no se sincronizan. El móvil puede ir en "Grafito" y el PC en "Universo".
  - Móvil: en `AppSettings`.
  - PC: en `toolbox.json`.
- El tema por defecto sigue siendo **Universo**, pero con una paleta **más sobria y oscura**, sin el morado intenso.

## 2. Temas
Cada tema define:
- el degradado base (2–3 colores, diagonal);
- de 0 a 3 nebulosas (color, opacidad, centro y radio relativos);
- si hay estrellas (densidad y parpadeo);
- el acento por defecto.

Las tarjetas de cristal (blanco translúcido, borde y brillo) son las mismas para todos.

| Id | Nombre | Base | Nebulosas | Estrellas | Acento por defecto |
|---|---|---|---|---|---|
| `universe` | **Universo** (por defecto) | `#05070D` → `#0A0F1C` → `#0E1424` | índigo apagado `#2B3A67` al 22 % (arriba a la izquierda, r 0,7); azul pizarra `#1E3A5F` al 20 % (abajo a la derecha, r 0,6); sin magenta | Sí, densidad ×0,8, parpadeo suave | Azul acero `#7AA2F7` |
| `midnight` | Medianoche | `#0B0D12` → `#121620` | azul muy tenue `#1C2533` al 25 % (centro, r 0,8) | No | Azul `#5B8DEF` |
| `graphite` | Grafito | `#111214` → `#1B1C1F` | Ninguna | No | Gris azulado `#9AA4B2` |
| `ocean` | Océano | `#03090D` → `#071820` | verde azulado `#0F4C5C` al 25 %; azul `#123A5A` al 20 % | Sí, densidad ×0,5 | Turquesa `#4FD1C5` |
| `aurora` | Aurora | `#05080A` → `#0B1412` | verde `#14532D` al 20 % (arriba, ancha); verde azulado `#134E4A` al 18 % | Sí, densidad ×0,6 | Verde `#6EE7A8` |
| `oled` | Negro (OLED) | `#000000` | Ninguna | No | Blanco `#E5E7EB` |
| `nebula` | Nebulosa (la de la v4.7) | La paleta violeta de la v4.7 | La de la v4.7 | Sí | Lavanda `#7C8CFF` |

- **Acento**: en la sección Tema se puede elegir otro acento además del del tema: Automático (el del tema), Azul acero, Lavanda, Turquesa, Verde, Ámbar `#F5B94A`, Rosa `#F472B6` y Blanco.
- **Opciones extra**:
  - "Estrellas": casilla, solo en los temas que las tienen.
  - "Animación de estrellas" (parpadeo): en el móvil, desactivada por defecto en el trackpad, como en la v4.7, y activable para el resto de pantallas.

## 3. Interfaz
- **Móvil**: en Ajustes, una tarjeta "Tema" con:
  - Una fila horizontal desplazable de muestras de 72 × 96 dp que dibujan el fondo real en pequeño, con el nombre debajo. La elegida lleva un borde de acento de 2 dp.
  - Fila de círculos de color para el acento, con "Auto" como primera opción.
  - Las casillas de estrellas.
  - El cambio se aplica al momento, en Ajustes y al volver al trackpad.
- **PC**: en la toolbox, Ajustes → "Tema…" abre un panel con las mismas muestras (rejilla de 4 columnas), el acento y la casilla de estrellas. Se aplica al momento a la toolbox, al gesto en vivo y al editor.
- El icono de la app no cambia con el tema.

## 4. Implementación
- **Definiciones de temas como datos**:
  - Un fichero compartido, `shared/themes.json`, en la raíz del repositorio.
  - El build de Android lo copia a los recursos o assets de `core`, o el código Kotlin lo replica.
  - .NET lo incrusta como recurso.
  - *(decisión: es la misma fuente para los dos lados, para que "Universo" sea igual en PC y móvil, aunque cada lado elija el suyo)*
  - Si incrustar el fichero en Android complica el build, se aceptan definiciones duplicadas en Kotlin y C#, más un test en cada lado que las compara con `shared/themes.json`, como ya se hace con `ShapeCatalog`.
- **Lógica pura con tests**:
  - Parseo y validación de temas.
  - Resolución del acento (Automático → el del tema).
  - Que el tema por defecto es `universe`.
  - Migración: quien no tenía tema pasa a `universe`, con la nueva paleta sobria.
- `UniverseBackground` (WPF) y `UniverseDrawable` (Android) reciben un tema en vez de constantes fijas. Los tokens de acento de `Theme.cs` y `GlassTheme.kt` pasan a ser dinámicos.

## 5. Criterios de "hecho"
1. Universo por defecto, más sobrio y oscuro, sin el morado intenso.
2. Sección Tema en el móvil y en el PC, con 7 temas, acento y estrellas, aplicada al momento.
3. Las elecciones del móvil y del PC son independientes.
4. Tests de temas en los dos lados.

## 6. Notas de implementación

Implementado: toda la lista de la §5. La lógica nueva tiene tests en los dos lados: catálogo, validación de `themes.json`, resolución del acento, estrellas por tema y migración (`ThemesTest`, `ThemeJsonTest` y `ThemesFixtureTest` en `core`; `ThemeCatalogTests` y `ToolboxThemeTests` en `MyMouse.HostCore.Tests`), más la densidad y el parpadeo suave de `StarField` (`StarFieldThemeTest` y `ThemeTests.cs`, con los mismos valores de referencia).

### Qué hay dónde
- **Fuente compartida:** `shared/themes.json` (raíz del repositorio): acentos, tema por defecto y los siete temas (base de 1–3 colores, nebulosas con centro y radio relativos, `stars`, `starDensity`, `twinkleDepth` y acento por defecto).
  - **PC:** se incrusta como recurso de `MyMouse.HostCore` (`LogicalName="MyMouse.HostCore.themes.json"`) y `Theming/ThemeCatalog.cs` lo lee y valida al primer uso. El test enlaza el mismo fichero y comprueba que lo incrustado coincide.
  - **Móvil:** decisión de la §4 (el fichero no se incrusta en Android): las definiciones están replicadas en `core/theme/Themes.kt` y `ThemesFixtureTest` las compara con `themes.json`, que se añade **solo a los recursos de test** de `core` (`sourceSets.test.resources.srcDir(rootProject.file("../shared"))` en `android/core/build.gradle.kts`). `core/theme/ThemeJson.kt` es un lector y validador de JSON mínimo (sin dependencias) que solo usan los tests.
- **Lógica pura** (misma en Kotlin y C#): `ThemeDef`, `Nebula`, `AccentOption`, `ThemeChoice` (tema, acento, estrellas, animación; `Normalized()`, `ShowStars`, `Twinkles`), resolución del acento (`Automático` o un id desconocido = el del tema), colores derivados (`AccentStart/End` = acento oscurecido al 35 % y 55 %, `SurfaceSolid` = base media aclarada al 7 %) y `StarCount(theme, w, h)`.
- **Estrellas:** `StarField.countFor(w, h, density)` y `opacityAt(star, t, depth)` (en Kotlin y en C#). La generación (mulberry32) no cambia: el cielo sigue siendo el mismo en los dos lados.
- **Móvil:** `AppSettings.theme` (claves `theme`, `accent`, `themeStars`, `themeTwinkle`, `themeTwinkleTrackpad`); `GlassTheme` con tokens dinámicos (`accent`, `accentStart/End`, `selectedFill/Border`, `panelSolid`, `choice`; `applyTheme`/`refresh`) y `universe(context, trackpad)`; `UniverseDrawable(density, theme, showStars, animated)`; `ThemePicker.kt` (`ThemeSwatchView` de 72 × 96 dp y `AccentDotView`); `SettingsActivity.themeSection()`.
- **PC:** `ToolboxLayout.Theme` (en `toolbox.json`, clave `theme`), `Ui/Theme.cs` (`Theme.Set`, `Theme.Current`, `Theme.Changed`), `Ui/UniverseBackground.cs` (con tema; `Fixed` para las muestras) y `Ui/ThemeWindow.cs` (Ajustes → "Tema…").

### Decisiones y desviaciones
- **Valores que la spec no fija** (elegidos aquí, están en `themes.json`): posición y radio de las nebulosas de Océano (arriba a la izquierda, r 0,70; abajo a la derecha, r 0,60) y de Aurora (la verde, arriba en el centro con r 0,95; la verde azulada, abajo a la derecha con r 0,55); colores de acento "Verde" `#6EE7A8` y "Blanco" `#E5E7EB`; Medianoche y Grafito llevan sus acentos propios (`#5B8DEF`, `#9AA4B2`), que no están en la lista de siete (solo salen en Automático). "Parpadeo suave" del Universo = `twinkleDepth` 0,6 (la opacidad recorre el 60 % del camino de la de siempre); Océano y Aurora, 0,8; Nebulosa, 1.
- **Densidad ×0,5 en la muestra:** las muestras (72 × 96 dp) caen en el mínimo de 12 estrellas, igual que cualquier área pequeña.
- **Botones primarios:** el degradado es ahora el acento oscurecido (no dos tonos distintos), para que el texto blanco se lea con cualquier acento, incluidos Blanco y Grafito.
- **Fondos sólidos derivados del tema:** tarjeta sólida, menús y diálogos del PC y el panel sin desenfoque del móvil salen de la base media del tema aclarada (`#1A1830` ya no existe). Los diálogos del sistema de Android (`AlertDialog`) usan un color fijo neutro (`glass_dialog_background`, `#EB1A1F2B`), porque su fondo es un recurso XML y no sigue el tema.
- **Aplicación inmediata en el móvil:** en Ajustes, cada cambio guarda y **recrea la pantalla** (para que interruptores, deslizadores y botones tomen el acento nuevo); se conserva el desplazamiento vertical y el de la fila de muestras. El trackpad lo recoge en `onResume` (`GlassTheme.applyTheme` y `TrackpadView.onThemeChanged`).
- **Animación de estrellas en el móvil:** dos casillas, "Animación de estrellas (parpadeo)" (resto de pantallas, activada por defecto) y "Animación también en el trackpad" (desactivada por defecto, como en la v4.7). En el PC, una sola ("Animación de estrellas"), siempre activa por defecto.
- **Estrellas:** la casilla solo aparece en los temas que las tienen (en los demás sale "Este tema no lleva estrellas.").
- **PC, aplicación inmediata:** los pinceles de acento, fondo, selección y tarjeta sólida son pinceles sin congelar cuyo color cambia en sitio, así que las ventanas ya abiertas se actualizan solas; `UniverseBackground` escucha `Theme.Changed` mientras está cargado. Las plantillas de casilla y de cuadro de texto usan `DynamicResource MyMouseAccent`. Mientras el panel "Tema…" está abierto, la toolbox no se cierra al perder el foco (para verla cambiar); vuelve a la normalidad al cerrarlo.
- **Migración:** un `toolbox.json` o unas preferencias sin tema dan `universe` con acento automático y estrellas activadas; un tema o acento desconocido, lo mismo.
- **Sin sincronizar:** el tema no viaja por el protocolo ni se guarda en la configuración de gestos.

### Sin verificar
- **El módulo `app` de Android no se ha compilado** (sin Android SDK local): solo se ha pasado el compilador de Kotlin sobre las fuentes, con `core` en el classpath (los únicos errores son las clases de Android que faltan; ninguna referencia a símbolos nuevos queda sin resolver). Riesgos: `Drawable`/`Paint` de `UniverseDrawable` con un tema de base de un solo color (se pasa duplicado a `LinearGradient`), `recreate()` y la restauración del desplazamiento (`onSaveInstanceState` + `post`), y la fila de muestras dentro de un `HorizontalScrollView` dentro del `ScrollView` de Ajustes.
- **`sourceSets.test.resources.srcDir(rootProject.file("../shared"))`** no se ha podido ejecutar con el Gradle del proyecto (sin dependencias del Android Gradle Plugin en local); se ha probado con una copia de `core/src` más `themes.json` en `src/test/resources`. Si en CI falla, la alternativa es copiar `shared/themes.json` a `android/core/src/test/resources/` y mantener las dos copias iguales.
- **Todo lo que dibuja en Windows:** ninguna ventana se ha ejecutado. En concreto: que `UniverseBackground` con un `Fixed` dibuje bien las muestras (recorte con esquinas, estrellas en 72 × 96), el cambio de color en sitio de los pinceles sin congelar (`Theme.Set`) en ventanas abiertas, `DynamicResource` dentro de los `Setter` de las plantillas cargadas con `XamlReader`, el aspecto de los botones primarios con el acento oscurecido, y el foco del panel "Tema…" frente a la toolbox.
- La paleta y los acentos se han mirado en una imagen generada con Pillow (siete muestras), no en un móvil ni en un monitor reales; las nebulosas de los temas sobrios son tenues a propósito (20–25 %).
