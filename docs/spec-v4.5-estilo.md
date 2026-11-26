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

### 4.2 Latencia por Bluetooth
Tercera prueba: el emparejado ya funciona, pero el puntero va con retraso comparado con un ratón real. Causa probable: `BluetoothHidTransport.send()` llamaba a `sendReport` por cada `PointerMove` en el hilo principal, y los informes se acumulan en la pila Bluetooth más deprisa que el intervalo del enlace, de modo que el retraso crece.
- **Envío agrupado.** Un hilo emisor propio (`HandlerThread`) acumula dx, dy, rueda y desplazamiento horizontal y saca **un solo informe de ratón por tic** (8 ms por defecto) con el último estado de los botones. Cambios de botones, teclado y multimedia salen enseguida y en orden, soltando antes el movimiento pendiente (un clic cae donde estaba el puntero). Si `sendReport` devuelve false, el movimiento no se pierde: vuelve a la cuenta para el siguiente tic. La lógica es `HidMotionCoalescer` (en `core`, pura, con tests: sumas, troceado en ±127 a lo largo de varios tics, orden de botones, rueda, reintento). El tic solo corre mientras hay movimiento pendiente (sin despertar el hilo en reposo).
- **Ajuste avanzado "Intervalo Bluetooth"** (Ajustes → Conexión): 6–20 ms, 8 por defecto.
- **QoS.** `registerApp` recibe `BluetoothHidDeviceAppQosSettings` para el canal de salida (interrupción): mejor esfuerzo, tasa de fichas 800 B/s, cubo 9, ancho de banda de pico 0, latencia 11 250 µs, variación de retardo `MAX`; la de entrada sigue en `null`. El registro de eventos anota los valores usados.
- **Métrica honesta.** Por HID no hay confirmaciones, así que no existe un ping real. Se muestra lo que sí se puede medir: informes por segundo, el **intervalo real entre informes** y lo que tarda cada `sendReport`, en el último segundo (`HidSendStats` en `core`, con tests). En modo Bluetooth aparece junto al punto de estado ("Bluetooth · 120 inf/s · 8 ms"), en la sección Diagnóstico (con "Copiar diagnóstico") y en el modo depuración. Es el intervalo entre informes, no un ping.

## 5. Criterios de "hecho" de la v4.5
1. Toolbox: barra superior con ⚙ ⤢ ✕; maximizar a ventana con dos columnas y restaurar; 8 px sobre la barra de tareas.
2. Tarjeta Gestos con filas alineadas, chips y secciones; sin rutas largas.
3. Cristal en Windows 11 (Acrylic/Mica) con alternativa sólida en Windows 10.
4. El móvil con la misma capa de estilo en todas sus pantallas.
5. Diagnóstico de Bluetooth con registro de eventos y "Copiar diagnóstico".
6. Tests de la lógica nueva (diagnóstico de Bluetooth, agrupación de la tarjeta Gestos en secciones, formato de los chips de acción).

## 6. Notas de implementación

Implementado: la lógica nueva con tests (diagnóstico de Bluetooth y registro de eventos en `core`; chips de acción, secciones de la tarjeta Gestos, reparto en dos columnas, tamaño de la ventana maximizada y ajustes `glass`/`maximized` de `toolbox.json` en `MyMouse.HostCore`), el estilo glass en Windows y en el móvil y las ampliaciones §4.1 y §4.2 (`HidMotionCoalescer`, `HidSendStats`). Desviaciones y lo que **no se ha podido verificar**:

### Desviaciones
- **Esquinas del Acrylic:** con el cristal del sistema las esquinas las redondea Windows (`DWMWCP_ROUND`, ~8 px), no los 16 px del token de panel. Con la alternativa sólida (Windows 10 o "Efecto cristal" desactivado) sí se dibuja el radio 16 y la sombra anterior (blur 18, con 16 px de margen transparente). La sombra "0 12 40" de la tabla solo se aproxima ahí.
- **Animación de entrada con Acrylic:** solo fundido. Deslizar el contenido dentro de una ventana con fondo del sistema se vería desplazado respecto al cristal; el deslizamiento de 24 px se mantiene en la alternativa sólida.
- **Cambiar "Efecto cristal"** destruye y vuelve a crear la toolbox y la ventana del gesto en vivo (la transparencia por capas se decide al crear la ventana). Si la toolbox estaba abierta, se vuelve a abrir.
- **Maximizar** en la alternativa sólida deja el borde de redimensionado dentro del margen transparente de la sombra; el cambio de tamaño con el ratón no se ha probado ahí.
- **Letra:** WPF no tiene espaciado de letras, así que los títulos de tarjeta van en mayúsculas pequeñas sin el +0,06 em (en Android sí: `letterSpacing = 0.06`). Los iconos de la barra y de las filas son glifos de Segoe Fluent Icons / Segoe MDL2 Assets, no dibujos propios.
- **Tarjeta Gestos:** la sección "Sin acción" incluye también las formas del catálogo (antes, una sola fila resumen "N formas más…"), plegada por defecto con el contador. La búsqueda (icono de lupa) filtra por título, nombre de la forma o acción y abre la sección al escribir. Las filas sin acción de formas propias muestran "Asignar" en acento, pero el botón es solo visual: toda la fila abre el editor con el campo de acción enfocado.
- **Editor:** la lista de la izquierda usa la misma fila (baldosa, título y chip), sin secciones; Mica con `DWMSBT_MAINWINDOW` si el "Efecto cristal" está activado y es Windows 11 22H2 o posterior.
- **Android, desenfoque:** `BackdropBlur` (en `GlassTheme.kt`) redibuja el fondo (degradado y trazo del modo gestos) en un `RenderNode` con `RenderEffect.createBlurEffect` y lo pinta recortado con la tarjeta; vale para la chuleta y el panel de widgets. Como detrás solo hay un degradado suave, la diferencia con el color translúcido apenas se nota hasta que haya algo más (trazo, depuración). Por debajo de Android 12, color `#1E2128` al 92 %.
- **Android, brillo superior:** el degradado de brillo de la tarjeta ocupa el 50 % de arriba en las vistas (`GradientDrawable` sin posiciones; el 40 % solo es posible con la API 29) y el 40 % en la chuleta, que se dibuja a mano.
- **Android, iconos:** los botones de control (ajustes, bloquear, salir) dibujan su icono con trazos (`GlassTheme.IconButton`); "Salir" usa el icono de apagado.
- **Android, botón "Hacer visible":** 3 minutos (antes 2) y mantiene el registro HID 180 s (§4.1).
- **Ayuda de Bluetooth:** además del texto de la spec se muestra el aviso de la §4.1 sobre el icono de teléfono y el estado "Emparejado".

- **Latencia por Bluetooth (§4.2):** el "8 ms" de la etiqueta es el intervalo real medio entre informes, y el tiempo de cada `sendReport` solo se ve en el diagnóstico. El envío del movimiento corre en el hilo emisor, pero el `HidMapper` (con sus acumuladores de rueda y zoom) sigue en el hilo principal, que es de donde llegan las acciones.

### Sin verificar
- **Latencia por Bluetooth (§4.2):** nada de esto se ha probado en un móvil. Que el agrupado y el QoS quiten el retraso es una hipótesis: si no, pegar "Copiar diagnóstico" (informes por segundo, intervalo real y `sendReport` medio) y probar con intervalos de 12–16 ms. El constructor de `BluetoothHidDeviceAppQosSettings` y `registerApp` con QoS de salida no se han compilado.
- **Todo lo que dibuja en Windows:** ninguna ventana se ha ejecutado. En concreto: que `WindowChrome` con `GlassFrameThickness = -1` + `DWMWA_SYSTEMBACKDROP_TYPE` dé Acrylic en una ventana sin `AllowsTransparency`; el aspecto de las esquinas y el borde; la plantilla de la barra de desplazamiento (se carga de una cadena XAML; si falla, se apunta en el log y se usa la de Windows); `DragMove` en la barra de la toolbox maximizada; el reparto en dos columnas medido antes de mostrar la ventana; el hover del "›" y de las filas; Mica en el editor con marco normal.
- **El módulo `app` de Android no se ha compilado** (sin Android SDK local): solo se ha pasado el compilador de Kotlin sobre las fuentes para descartar errores de sintaxis (los únicos errores son de las clases de Android que faltan). Riesgos: `RenderNode`/`drawRenderNode`, `ViewCompat` con `setDecorFitsSystemWindows(false)` en versiones < 15, los atributos de `themes.xml`, y el aspecto de los diálogos con `Theme.DeviceDefault.Dialog.Alert`.
- **Bluetooth:** que mantener el registro HID 60 s/180 s resuelva el "Esperando al PC"; es una hipótesis (§4.1). El diagnóstico existe para comprobarlo: pide a quien lo pruebe que pegue el resultado de "Copiar diagnóstico".

