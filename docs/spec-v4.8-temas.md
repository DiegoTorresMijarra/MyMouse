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
