# MyMouse — v4.5: capa de estilo (glass) y depuración de Bluetooth

> Base: [spec-v4-toolbox.md](spec-v4-toolbox.md) (§10, notas de implementación), [spec-v3-bluetooth.md](spec-v3-bluetooth.md), [spec-v3.1-ajustes.md](spec-v3.1-ajustes.md)
>
> Origen: prueba de la v4.0 en el PC real. Al usuario le gusta el conjunto, pero pide pulir la estética. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Lenguaje visual común: "glass"

El estilo de los paneles translúcidos de Apple (glassmorphism), con la distribución sobria de la Toolbox de JetBrains. Lo mismo en el PC y en el móvil.

**Tokens**, en un único sitio por plataforma: `Theme.cs` en Windows y un `GlassTheme.kt` nuevo en Android.

| Token | Valor |
|---|---|
| Fondo base (detrás del cristal) | Degradado oscuro: `#14161B` → `#1B1E26` (diagonal) |
| Cristal: panel | Blanco al 6 % sobre el fondo con desenfoque. Sin desenfoque disponible: `#1E2128` al 92 % |
| Cristal: tarjeta | Blanco al 5 %, borde de 1 px blanco al 10 %, brillo superior (degradado blanco del 8 % al 0 % en los 40 % de arriba) |
| Cristal: elemento elevado / hover | Blanco al 9 % |
| Seleccionado | Acento al 22 % con borde de acento al 45 % |
| Acento | `#3574F0` (se mantiene); verde `#5FB865`, ámbar `#E5A50A`, rojo `#E5534B` |
| Texto | `#ECEEF2`; secundario `#9AA0AA`; terciario `#6B717C` |
| Radios | Panel 16 · tarjeta 12 · fila o botón 8 · chip 6 |
| Sombras | Panel: 0 12 40 negro al 45 % · tarjeta: ninguna (el cristal ya separa) |
| Tipografía | Windows: Segoe UI Variable (Display para títulos); Android: la del sistema. Títulos de tarjeta en mayúsculas pequeñas: 11 sp/px, espaciado de letras +0,06 em, color secundario |
| Espaciado | Rejilla de 4: 8 / 12 / 16 |

## 2. Windows (toolbox, gesto en vivo, editor)

### 2.1 Fondo de cristal real
- En Windows 11 22H2 y posteriores, el panel usa **Acrylic** del sistema: `DwmSetWindowAttribute(DWMWA_SYSTEMBACKDROP_TYPE = 38, DWMSBT_TRANSIENTWINDOW = 3)`.
  - Va con modo oscuro (`DWMWA_USE_IMMERSIVE_DARK_MODE = 20`) y esquinas redondeadas del sistema (`DWMWA_WINDOW_CORNER_PREFERENCE = 33, DWMWCP_ROUND`).
  - Esto obliga a no usar `AllowsTransparency=true` en esas ventanas: hay que usar `WindowChrome` con `GlassFrameThickness = -1` y fondo transparente, y la sombra la pone el sistema.
- En Windows 10 o si falla la llamada: color sólido translúcido del token "Cristal: panel" sin desenfoque y la sombra actual. *(decisión: nada de `SetWindowCompositionAttribute`, que es una API no documentada)*
- Tarjetas y filas, encima, con los tokens de cristal.
- Ajuste en Personalizar: "Efecto cristal" (activado por defecto) para quien prefiera el sólido.

### 2.2 Barra superior de la toolbox
Una barra superior de 40 px:
- Logo pequeño y "MyMouse" a la izquierda.
- A la derecha, tres iconos de 28 × 28 con hover de cristal: ⚙ ajustes, ⤢ maximizar/restaurar y ✕ cerrar.
- Arrastrar la barra no mueve el panel cuando está anclado; sí cuando está maximizado.

**Maximizar**:
- La toolbox pasa a una ventana normal centrada en el monitor, de 960 × 720 (o el 85 % del área de trabajo si es menor), redimensionable, con el mismo cristal.
- Las tarjetas se reparten en **dos columnas**: Opciones y Música a la izquierda, Gestos (más alta) a la derecha; Widgets debajo de la que tenga menos altura.
- Restaurar vuelve al panel anclado.
- Con la ventana maximizada, 4 dedos ↓ y `Esc` también la cierran.
- Se recuerda si estaba maximizada.

### 2.3 Márgenes del panel anclado
- Hay demasiado aire debajo del panel. La separación con la barra de tareas pasa a **8 px**, y los márgenes de sombra transparentes ya no ocupan espacio, porque con el cristal la sombra es del sistema.
- El alto se ajusta al contenido; el relleno interior inferior es igual al superior (12 px).
- Si el contenido no llena el 85 %, el panel no se estira.

### 2.4 Tarjeta Gestos (rediseño)
Ahora mismo las filas están centradas y desalineadas. Nuevo diseño:
- **Filas alineadas a la izquierda**, en una rejilla de tres columnas:

  | Columna | Contenido |
  |---|---|
  | Miniatura | Baldosa de 40 × 40 px con radio 8 y cristal elevado. La forma se dibuja en el centro, ajustada a 24 px, con trazo de 2 px de color texto y punto de inicio en acento |
  | Texto | Título (texto de ayuda o, si no hay, la acción en lenguaje humano), 13 px semibold, y debajo un **chip** con la acción técnica (`Ctrl+Z`, `chrome.exe`, `PlayPause`), en monoespaciado de 11 px con fondo de cristal |
  | Derecha | Icono de chincheta si está fijada (acento) y, al pasar el ratón, un "›" |

- Para las acciones de tipo `launch`, el chip muestra solo el nombre del fichero (`OUTLOOK.EXE`), nunca la ruta completa; la ruta va en el tooltip.
- Alto de fila 52 px y hover de cristal elevado con radio 8; sin separadores, solo 2 px de hueco.
- **Secciones**: "Fijadas" arriba, "Asignadas" después y "Sin acción" al final, plegada por defecto con un contador ("Sin acción · 5"). Las formas propias sin acción salen dentro de esa sección con un botón "Asignar" en acento.
- Cabecera de la tarjeta: "GESTOS" (título de tarjeta) y, a la derecha, un botón secundario de cristal "+ Nueva forma" (no azul sólido). Opcionalmente un icono de búsqueda que filtra la lista.
- La barra de scroll es fina (4 px) y solo se ve al pasar el ratón.
- El editor de gestos usa el mismo componente de fila en su lista de la izquierda.

### 2.5 Resto
- **Opciones**: los botones pasan a una fila de iconos con etiqueta (cristal elevado) en vez de botones de texto sueltos. El estado se muestra en una "píldora" con punto de color.
- **Música**: carátula de 56 px con radio 10, controles circulares de cristal y slider fino con el acento.
- **Gesto en vivo**: el mismo cristal (Acrylic si está disponible, con `WS_EX_NOACTIVATE` y click-through como ahora). El lienzo con un ligero brillo; el resultado en un chip de color.
- **Editor**: ventana con cristal (Mica, `DWMSBT_MAINWINDOW = 2`, si es Windows 11), lista y formularios con los mismos tokens.

## 3. Android: la misma capa de estilo

Pantallas afectadas: trackpad (fondo, botones de control, chuleta, panel de widgets, borde y toast del modo gestos, depuración), Ajustes, Formas, Grabar forma y Bluetooth.

- **Fondo del trackpad**: degradado base, con el área del trackpad como "cristal: panel" (radio 16 en modo ventana; en modo bloqueado, a pantalla completa sin radio).
- **Desenfoque**: con Android 12+ (`RenderEffect.createBlurEffect`) el panel de widgets y la chuleta desenfocan lo que tienen detrás (el degradado y el trazo); por debajo, color translúcido sin desenfoque. *(decisión: el desenfoque real de la ventana, `setBackgroundBlurRadius`, no se usa porque la app es opaca)*
- **Botones de control** (ajustes, bloquear, salir): círculos de cristal de 44 dp con icono dibujado con trazos y no con texto.
- **Chuleta**: tarjeta de cristal con radio 12, título en mayúsculas pequeñas, filas con icono en baldosa como en el PC.
- **Panel de widgets**: tarjetas de cristal; botones con radio 12; la animación de la v3.1 se mantiene.
- **Ajustes, Formas, Grabar y Bluetooth**:
  - Fondo degradado y secciones como tarjetas de cristal con título en mayúsculas pequeñas.
  - Interruptores, sliders y radios teñidos con el acento.
  - Las listas usan la misma fila que el PC (baldosa con la forma, título y chip).
  - Barra superior propia (sin ActionBar) con flecha de volver, respetando las barras del sistema.
- El tema se aplica con estilos (`themes.xml`/`styles.xml`) y dibujables propios cuando haga falta. Sin dependencias nuevas (ni Material Components ni Compose).
- Ningún cambio de comportamiento: solo estilo.

## 4. Bluetooth: depurar "Esperando al PC"

Síntoma en la prueba: el móvil y el PC están emparejados, pero el móvil dice "Esperando al PC" y el PC no lo usa como ratón.

Hay dos causas probables:
1. **Emparejado antes de activar el modo Bluetooth.** Windows guarda los servicios del dispositivo al emparejar; si entonces el móvil no anunciaba el servicio HID, Windows lo trata como un teléfono y nunca abre la conexión HID. Arreglo: quitar el móvil en Windows y volver a emparejarlo con la app abierta en modo Bluetooth.
2. **La conexión la tiene que abrir alguien.** El móvil llama a `connect()` con el PC preferido, pero el PC puede rechazarla.

**Lo que se añade:**
- **Pantalla Bluetooth → sección "Diagnóstico"** (también accesible desde un toque en el punto de estado del trackpad en modo Bluetooth):
  - Lista de comprobaciones con ✓/✗: Bluetooth activado, permisos, perfil HID disponible (`getProfileProxy`), app registrada como ratón (`onAppStatusChanged registered=true`), PC preferido elegido y emparejado, estado de conexión HID con ese PC (`getConnectionState`).
  - Registro de los últimos 30 eventos con hora: registro, `onConnectionStateChanged` con nombre y estado, `connect()` devuelto true/false, errores.
  - Botones "Reintentar conexión" (vuelve a llamar a `connect`) y "Volver a registrar".
  - Botón "Copiar diagnóstico" (al portapapeles, para pegarlo en el chat).
  - Texto de ayuda fijo con la causa 1 y los pasos: "Si emparejaste el móvil antes de elegir Bluetooth en MyMouse: en Windows, Configuración → Bluetooth → el móvil → Quitar dispositivo; aquí, Hacer visible; y vuelve a añadirlo desde Windows".
- **Lógica**: el registro de eventos y la evaluación de las comprobaciones van en `core` (clase pura con tests); la app solo los alimenta.
- **PC**: si la toolbox muestra "Sin dispositivo", añade una línea secundaria: "¿Usas el modo Bluetooth? En ese modo el móvil funciona como ratón sin MyMouse en el PC". Es normal que el host diga "Sin dispositivo" en ese modo.

### 4.1 Ampliación: que el registro HID no se corte mientras se empareja
Segunda prueba: "Agregar un dispositivo" de Windows muestra el Poco como teléfono con confirmación de PIN. Causa probable: durante el emparejado, los diálogos del sistema (hacerse visible, confirmar el PIN) o ir a los ajustes de Android mandan MyMouse a segundo plano y `BluetoothHidTransport` liberaba el registro HID a los 3 s, así que Windows no veía el servicio HID al consultar el móvil.
- **Retardo de liberación: 60 s** (antes 3 s).
- **"Hacer visible" mantiene el registro al menos 180 s** (`holdRegistration`), pase lo que pase con el ciclo de vida de las pantallas.
- **Registro de eventos ampliado:** cambios de emparejado (`ACTION_BOND_STATE_CHANGED`, con nombre del dispositivo y si la app HID estaba registrada en ese instante), registro y anulación del registro con su motivo, `getProfileProxy`, `registerApp`, `connect` y cambios de conexión. Así se ve si el servicio estaba registrado cuando Windows emparejó.
- **Ayuda:** que Windows muestre el móvil con icono de teléfono al emparejar es normal (usa la clase del dispositivo); al terminar debe aparecer como conectado; si solo pone "Emparejado", quitarlo y volver a emparejar con MyMouse abierto en la pantalla Bluetooth.

## 5. Criterios de "hecho" de la v4.5
1. Toolbox: barra superior con ⚙ ⤢ ✕; maximizar a ventana con dos columnas y restaurar; 8 px sobre la barra de tareas.
2. Tarjeta Gestos con filas alineadas, chips y secciones; sin rutas largas.
3. Cristal en Windows 11 (Acrylic/Mica) con alternativa sólida en Windows 10.
4. El móvil con la misma capa de estilo en todas sus pantallas.
5. Diagnóstico de Bluetooth con registro de eventos y "Copiar diagnóstico".
6. Tests de la lógica nueva (diagnóstico de Bluetooth, agrupación de la tarjeta Gestos en secciones, formato de los chips de acción).
