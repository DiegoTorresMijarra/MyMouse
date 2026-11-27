# Cómo probar una release

Cada release (`v0.0`, `v0.1`, …) añade una etapa encima de la anterior. Si algo falla, baja a la release anterior hasta encontrar la primera en la que falla y apunta cuál es.

## Una sola vez

1. **Móvil (P10):**
   - Ajustes → Sistema → Información del teléfono → toca 7 veces "Número de compilación".
   - Ajustes → Sistema → Opciones de desarrollador → activa **Depuración USB** y **Permitir depuración ADB en modo solo carga**.
2. **PC (Windows):**
   - Instala [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (descomprimir y añadir la carpeta al `PATH`, o dejarla en `%LOCALAPPDATA%\Android\Sdk\platform-tools`).
   - Si `adb devices` no ve el móvil, instala el driver de Huawei (viene con **HiSuite**).
   - Conecta el cable, acepta en el móvil el aviso "¿Permitir depuración USB?" (marca "Permitir siempre") y comprueba que `adb devices` muestra el móvil como `device`.
   - Recomendado: desactiva "Mejorar la precisión del puntero" (Configuración del mouse → Opciones adicionales → Opciones de puntero).

## Para cada release

De la página de la release descarga:
- `MyMouse-<versión>.apk`: la app del móvil.
- `MyMouse-Setup-<versión>.exe` (desde la v5): el **instalador** de Windows. Incluye `MyMouse.exe` (el host de bandeja) y adb. Es lo más cómodo; ver [Instalador (v5)](#instalador-de-windows-v5).
- `MyMouse-Windows-<versión>.zip`: la versión portátil, con `MyMouse.exe` y `MyMouse.TestClient.exe` (simula el móvil). No necesitan instalar .NET. No lleva adb (usa el del `PATH`).

1. **Cerrar el host anterior:** icono de MyMouse en la bandeja → Salir.
2. **Instalar la app** (sustituye a la anterior y conserva los ajustes, también al bajar de versión):
   ```powershell
   adb install -r -d MyMouse-<versión>.apk
   ```
3. **Arrancar el host:** con el instalador, ejecútalo (o, si ya está instalado, ábrelo desde el menú Inicio); con el zip, descomprímelo y ejecuta `MyMouse.exe`. Windows SmartScreen puede avisar porque no está firmado: "Más información" → "Ejecutar de todas formas".
4. Abre **MyMouse** en el móvil. El punto de la esquina se pone verde al conectar.

Si algo no conecta:
- Pasa el ratón por el icono de la bandeja: el tooltip dice si encuentra adb y el móvil.
- Logs del host: icono de la bandeja → "Abrir carpeta de logs".
- Prueba solo el PC con `MyMouse.TestClient.exe` (con el host abierto): el cursor debe dibujar un círculo.
- Logs del móvil: `adb logcat -s MyMouse`.

## Modo gestos (desde la v1.0)

1. En el móvil: modo ventana → **Ajustes → Formas…** para ver y activar las formas. Lo que hace cada una se configura en el PC.
2. En el PC: icono de MyMouse en la bandeja → **Editar gestos…** (desde la v4: toolbox → **Gestos y widgets**, ver más abajo). Se abre `gestures.json` con ejemplos (círculo = abrir Chrome, Z = deshacer, V = reproducir/pausar…). Al guardar, MyMouse lo recarga solo.
3. En el trackpad: apoya **4 dedos a la vez y déjalos quietos** hasta que vibre. Levántalos y dibuja la forma con un dedo.
4. Activa el **modo depuración** (Ajustes) para ver qué forma se ha reconocido y con qué puntuación.

## Widgets (desde la v2.0)

1. **Actualiza las dos partes** (APK y host): el panel solo aparece si el host es de la v2.0 o posterior. Con un host anterior, o sin conexión, el trackpad ocupa toda la pantalla.
2. **Activa los widgets en el PC.** Si ya tenías `gestures.json` de la v1, no se toca: icono de la bandeja → **Editar gestos y widgets…** (desde la v4, el editor de la toolbox lo hace por ti) y añade, junto a `"bindings"`:
   ```json
   "widgets": [
     { "type": "media" },
     { "type": "volume" },
     { "type": "button", "label": "Chrome",   "icon": "🌐", "action": { "type": "launch", "target": "chrome.exe", "focusIfRunning": true } },
     { "type": "button", "label": "Copiar",   "icon": "📋", "action": { "type": "keys", "keys": "Ctrl+C" } },
     { "type": "button", "label": "Pegar",    "icon": "📄", "action": { "type": "keys", "keys": "Ctrl+V" } },
     { "type": "button", "label": "Captura",  "icon": "✂️", "action": { "type": "keys", "keys": "Win+Shift+S" } },
     { "type": "button", "label": "Bloquear", "icon": "🔒", "action": { "type": "mymouse", "command": "lock" } }
   ]
   ```
   (Una instalación nueva ya trae este ejemplo.) Al guardar, el panel del móvil se actualiza solo.
3. **Panel:** aparece abajo (móvil en vertical) o a la derecha (horizontal). Tamaño y visibilidad en Ajustes → Widgets.
4. **Qué comprobar:**
   - Un botón se ejecuta al **levantar** el dedo dentro de él; si arrastras fuera, se cancela.
   - Música: reproduce algo en el PC; el título y el artista salen en el móvil y ⏮ ⏯ ⏭ funcionan.
   - Volumen: arrastra la barra y comprueba el volumen de Windows; cámbialo con el teclado del PC y mira que la barra lo sigue (menos de 0,3 s). El altavoz silencia.
   - Ningún toque del panel mueve el cursor ni hace clic, y al revés.
   - Si el panel no cabe, desplázalo con un dedo.
   - Con el **modo depuración** verás anotadas las acciones (`Widget: Chrome`, `Volumen 55`, `⏭ Siguiente`).
5. `MyMouse.TestClient.exe` (con el host abierto) imprime los widgets configurados y el estado actual de volumen y música.

## Ajustes de la v3.1

Actualiza el APK y el host (los gestos de 3 dedos por USB necesitan el host 3.1; por Bluetooth funcionan sin host).
1. **Gestos de Windows con 3 dedos:** ↑ vista de tareas, ↓ escritorio, ← → cambiar de app (se mantiene Alt y avanza cada ~120 px; al levantar los dedos se suelta). Se desactivan en Ajustes.
   - **Xiaomi/HyperOS:** el sistema usa 3 dedos para la captura. Desactiva en Ajustes → Ajustes adicionales → Atajos de gestos: *Captura de pantalla (deslizar 3 dedos hacia abajo)* y el recorte parcial (mantener 3 dedos). Una app no puede bloquearlos.
2. **Modo gestos:** ahora con **4 dedos quietos** (300 ms).
3. **Panel de widgets:** 4 dedos **juntar** lo muestra y **separar** lo oculta, con animación; el estado se recuerda. Sin widgets en el PC, juntar avisa "No hay widgets".
4. **Zoom:** más fino y rápido; "Velocidad del zoom" en Ajustes (×0,5 a ×4). Comprueba que el pellizco no se queda en scroll.
5. **Inercia al hacer scroll:** activada por defecto; se corta con cualquier toque.
6. Con el modo depuración, 4 dedos deslizando ↑/↓ se anota ("4 dedos ↑: abrir la toolbox del PC"); desde la v4 abre y cierra la toolbox.

## La toolbox del PC (desde la v4.0)

Actualiza el APK y el host: el host de la v4 es una app WPF (el mismo `MyMouse.exe`, más grande) y la toolbox desde el móvil necesita las dos partes en la v4 (protocolo 5). Con una parte antigua, 4 dedos ↑ / ↓ avisan "Actualiza MyMouse en el PC" y lo demás funciona como antes. Por Bluetooth no hay toolbox.
1. **Abrir y cerrar:** clic izquierdo en el icono de la bandeja (otro clic la cierra); o **4 dedos hacia arriba** en el trackpad del móvil. Se cierra con **4 dedos hacia abajo**, `Esc` o un clic fuera. Abierta desde el móvil no te quita el foco: sigue escribiendo en la app en la que estabas; al hacer clic en la toolbox ya se comporta como una ventana normal. Clic derecho en el icono: Abrir MyMouse, Pausar, Salir.
2. **Tarjetas:** Opciones (estado, Pausar, Gestos y widgets, Ayuda, Ajustes, Salir), Música (carátula, ⏮ ⏯ ⏭, volumen y silencio), Gestos y Widgets del móvil (oculta por defecto). Ajustes → *Personalizar tarjetas…* para quitar, añadir y reordenar (se guarda en `%APPDATA%\MyMouse\toolbox.json`; Opciones no se puede quitar). La toolbox funciona sin móvil.
3. **Gesto en vivo:** activa el modo gestos en el móvil (4 dedos quietos) y mira el PC: sube una tarjeta con una cuenta atrás; dibuja y verás el trazo en directo. Al terminar se colorea: **verde** = reconocida y ejecutada (ayuda y acción), **ámbar** = reconocida sin acción (con botón *Asignar*), **gris** = no reconocida o cancelada, **rojo** = la acción falló. Baja sola al cabo de ~1,5 s. Se desactiva en Ajustes → *Mostrar el gesto en vivo*.
4. **Editor** (Opciones → *Gestos y widgets*, o clic en una forma de la tarjeta Gestos):
   - Pestaña **Gestos:** elige la forma, el tipo de acción (atajo: haz clic en el campo y pulsa la combinación, también con la tecla Win; abrir app: *Elegir programa…* o la lista de apps del menú Inicio con buscador; texto; multimedia; MyMouse), el texto de ayuda y si va fijada en la chuleta. *Probar* la ejecuta (las teclas y el texto, a los 3 s para que cambies de ventana). *Guardar* escribe `gestures.json` con formato limpio (la primera vez deja una copia `gestures.json.bak`; los comentarios se pierden) y el móvil recibe la lista al momento.
   - Si `gestures.json` tiene un error de sintaxis, el editor lo dice y ofrece abrirlo en el Bloc de notas en vez de pisarlo.
   - Pestaña **Widgets:** añadir, ordenar (↑ ↓) y borrar; un botón tiene etiqueta, icono (emoji con `Win+.`) y acción; vista previa del panel.
5. **Forma nueva desde el PC:** *+ Nueva forma* → nombre → 3 muestras con el ratón o el touchpad → *Guardar en el móvil*. Si se confunde con otra, te lo dice. Después asígnale una acción (p. ej. abrir Chrome). Con el móvil sin conectar, o con un host/móvil antiguo, el botón queda desactivado: "Conecta el móvil por USB para grabar formas".
6. **Forma nueva desde el móvil:** Ajustes → Formas → Nueva forma. Al volver al trackpad, el PC avisa con un globo "Nueva forma «x»: ¿qué quieres que haga?"; al pulsarlo se abre el editor con esa forma.
7. **`MyMouse.TestClient.exe`** (con el host abierto) abre y cierra la toolbox, simula un trazo y envía una forma de prueba; con `--formas` se queda 60 s contestando a lo que el editor mande al móvil (probar *+ Nueva forma* sin móvil).
8. **Qué comprobar con más cuidado** (no se ha probado en Windows real): colocación sobre la bandeja con varios monitores o escalas distintas, que abrir desde el móvil no robe el foco, el campo de atajos con la tecla Win, el selector de emojis y la carátula de la música. Si algo falla, apunta qué hiciste y el log del día.

## Estilo y toolbox de la v4.5

Misma actualización de APK y host que la v4.0. Ninguna ventana de Windows se ha ejecutado antes de esta versión: es la primera prueba del estilo.
1. **Cristal:** (sustituido en la v4.7: ya no hay Acrylic; ver más abajo "Universo, icono y retoques".) En la v4.5, en Windows 11 22H2 o posterior la toolbox se veía translúcida con esquinas redondeadas del sistema.
2. **Barra superior:** logo y "MyMouse" a la izquierda; ⚙ (ajustes), ⤢ (maximizar) y ✕ (cerrar) a la derecha. Anclada, arrastrar la barra no la mueve.
3. **Márgenes:** 8 px entre la toolbox y la barra de tareas, y el mismo aire arriba y abajo dentro del panel. Si el contenido es corto, el panel no se estira.
4. **Maximizar (⤢):** ventana centrada de 960 × 720 (o el 85 % del escritorio), redimensionable y movible desde la barra, con Opciones y Música a la izquierda y Gestos a la derecha. ⤢ otra vez (o 4 dedos ↓ / `Esc` para cerrarla) y vuelve al panel anclado. Al reabrir, recuerda si estaba maximizada.
5. **Tarjeta Gestos:** filas alineadas a la izquierda con baldosa (forma con el inicio en azul), título y chip con la acción; para "abrir app" el chip solo lleva el nombre del fichero y la ruta sale al dejar el ratón encima. Secciones *Fijadas*, *Asignadas* y *Sin acción · N* (plegada; ábrela con clic). La lupa filtra. *+ Nueva forma* es un botón de cristal. La barra de scroll es fina y solo aparece al pasar el ratón.
6. **Gesto en vivo y editor:** el mismo cristal (el editor con Mica); el resultado del gesto sale en un chip de color. La lista del editor usa la misma fila.
7. **Móvil:** fondo degradado, trackpad como panel de cristal, botones redondos con iconos (ajustes, bloquear, salir), chuleta y panel de widgets de cristal, y Ajustes, Formas, Grabar forma y Bluetooth con tarjetas y barra superior propia (flecha de volver). Debe comportarse igual que antes.
8. **"Sin dispositivo" en el PC:** con el cable desconectado, Opciones muestra debajo "¿Usas el modo Bluetooth? …" (es normal estar así en ese modo).

## Universo, icono y retoques de la v4.7

Misma actualización de APK y host. Es la primera vez que se ven en un PC y un móvil reales: apunta lo que no case.
1. **Universo (PC):** la toolbox, el gesto en vivo y el editor tienen detrás un fondo violeta oscuro con nebulosas y estrellas (unas pocas parpadean despacio), y las tarjetas son de cristal claro encima: se debe distinguir bien una tarjeta de otra y no debe haber gris plano. *Ajustes (⚙) → Efecto cristal* desmarcado pone las tarjetas sólidas (`#1A1830`) sobre el mismo universo. Ya no hay Acrylic ni Mica. En Windows 11 las esquinas y la sombra son las del sistema; en Windows 10, esquinas de radio 16 y sombra propia.
2. **Texto nítido:** la línea "Conecta el cable y activa la depuración USB. ¿Usas el modo Bluetooth? …" debe leerse bien, y la píldora de estado ("Sin dispositivo", "Conectado · modelo", "Esperando al móvil", "En pausa") debe ser un óvalo limpio con punto de color, no una elipse borrosa.
3. **Icono:** el exe, la toolbox maximizada y el editor muestran el icono nuevo (trackpad de cristal sobre violeta y azul con un trazo luminoso) en la barra de tareas y el selector de ventanas; en la bandeja, el mismo icono con un punto de estado abajo a la derecha (verde conectado, ámbar esperando, rojo sin dispositivo, gris en pausa). A 16 px no lleva estrellas. En el móvil, el icono del lanzador es el mismo (adaptativo: prueba con distintas formas de icono) y aparece en la barra superior de Ajustes.
4. **Icono siempre visible (Windows 11):** *Ajustes (⚙) → Mostrar siempre el icono en la barra de tareas* viene activado: tras arrancar la v4.7 (puede tardar 10 s), el icono debe estar junto al reloj y no en la flecha ˄. Desmárcalo y debe volver a la zona oculta. En Windows 10 la opción no puede actuar: explica que hay que arrastrar el icono desde la flecha ˄. Si no funciona en Windows 11, mira el log: "Icono visible en la barra de tareas: NoKey" significa que Windows aún no había registrado el icono.
5. **Editor, "← Volver":** abre el editor, cambia algo sin guardar y pulsa *← Volver* (o `Esc` fuera de un campo de texto): pregunta "¿Guardar los cambios?" con Guardar / Descartar / Cancelar. Con Guardar o Descartar cierra el editor y vuelve a abrir la toolbox; con Cancelar se queda.
6. **Móvil, universo:** todas las pantallas (Ajustes, Formas, Bluetooth…) tienen el universo detrás, con las estrellas parpadeando; el trackpad lo tiene estático.
7. **Hoja de gestos (modo gestos):** en horizontal siempre en dos columnas; en vertical, en dos solo si cada columna tiene 150 dp o más (si no, una). Si no cabe a lo alto, se encoge entera en vez de recortarse.
8. **Widget de volumen:** una fila con el altavoz a la izquierda (tocarlo silencia), la pista fina ocupando todo el ancho, un pulgar blanco de 18 dp y el número legible a la derecha. En un panel estrecho (horizontal, 2 columnas) el número pasa debajo de la pista antes de que ésta se haga corta. Silenciado: altavoz con una × gris y la pista llena en gris.
9. **Chuleta y depuración:** con *Modo depuración* activado, la chuleta no tapa la minipantalla: va a su derecha en horizontal y debajo en vertical (más pequeña si hace falta).

## Temas de la v4.8

Misma actualización de APK y host. Móvil y PC guardan su tema por separado.
1. **Por defecto:** tras actualizar, el móvil y el PC se ven en *Universo*, ya sin el violeta intenso: casi negro azulado con dos nebulosas apagadas (índigo y azul pizarra) y menos estrellas, con un parpadeo suave. Si no te gusta el nuevo, *Nebulosa* es el de la v4.7.
2. **Móvil:** *Ajustes → Tema*. La fila de muestras se desplaza de lado; cada muestra dibuja el fondo de verdad, con el nombre debajo, y la elegida lleva un borde de acento. Al tocar otra, la pantalla de Ajustes cambia al momento (fondo, interruptores, deslizadores y botones toman el acento). Al volver al trackpad, el fondo y el acento (borde del modo gestos, volumen) son los nuevos. Comprueba los siete temas: *Medianoche*, *Grafito* y *Negro (OLED)* no llevan estrellas (la casilla desaparece); *Océano* y *Aurora* llevan menos.
3. **Acento (móvil):** *Auto* es el del tema; prueba los otros siete. Los botones primarios son el acento oscurecido, con texto blanco: apunta si alguno se lee mal (sobre todo *Blanco* y *Grafito*).
4. **Estrellas (móvil):** *Estrellas* las quita o las pone; *Animación de estrellas* las hace parpadear en Ajustes, Formas y Bluetooth; *Animación también en el trackpad* (apagada por defecto) las hace parpadear en el trackpad, con algo más de gasto de batería.
5. **PC:** *Ajustes (⚙) → Tema…* abre un panel con la rejilla de cuatro columnas, el acento y las estrellas. La toolbox se queda abierta mientras el panel está abierto y cambia al momento; abre también el gesto en vivo (dibuja en el móvil) y el editor para ver que toman el tema y el acento. *Efecto cristal* sigue funcionando sobre cualquier tema.
6. **Independientes:** pon el móvil en *Grafito* y el PC en *Universo*: cada uno se queda como lo dejaste, también tras reiniciar la app o el host.
7. **Migración:** quien venía de la v4.7 empieza en *Universo* (sobrio). El `toolbox.json` guarda la elección en `"theme"`.

## Bluetooth: diagnóstico y volver a emparejar (v4.5)

Si el móvil dice **"Esperando al PC"** y el PC no lo usa como ratón:
1. En el móvil, abre la pantalla Bluetooth (Ajustes → Emparejar por Bluetooth…, o, ya en modo Bluetooth, un **toque en el punto de estado** del trackpad). Mira la sección **Diagnóstico**: las comprobaciones deben salir con ✓ (Bluetooth, permisos, perfil HID, app registrada, PC elegido y emparejado, conexión HID).
2. Pulsa **Reintentar conexión** y mira los últimos eventos (`connect(...) devolvió true/false`, `onConnectionStateChanged`).
3. Si todo está bien en el móvil pero Windows no conecta, lo más probable es que se emparejara **antes** de elegir Bluetooth en MyMouse, y Windows guardó el móvil como un teléfono. Para volver a emparejar:
   - En Windows: Configuración → Bluetooth y dispositivos → el móvil → **Quitar dispositivo**.
   - En el móvil, **con MyMouse abierto en la pantalla Bluetooth** (déjala abierta durante todo el emparejado), pulsa **Hacer visible** (mantiene el registro HID 3 minutos).
   - En Windows: Agregar dispositivo → Bluetooth → el móvil; acepta el PIN en los dos lados.
4. Que Windows muestre el móvil con **icono de teléfono** mientras empareja es normal. Al terminar, debe aparecer **como conectado**; si solo pone "Emparejado", quítalo y repite el paso 3.
5. **Retraso del puntero (v4.5):** el movimiento por Bluetooth se envía agrupado, un informe cada 8 ms. En el trackpad, junto al punto de estado, se lee "Bluetooth · 120 inf/s · 8 ms": informes por segundo e **intervalo real entre informes** (no es un ping; Bluetooth HID no confirma nada). También sale en el diagnóstico y en el modo depuración. Si aún se nota retraso, prueba *Ajustes → Intervalo Bluetooth* con 12–16 ms y compara; apunta cuál va mejor.
6. Pulsa **Copiar diagnóstico** y pégalo al contarlo: incluye las comprobaciones y los últimos 30 eventos, con los cambios de emparejado y si la app HID estaba registrada en ese momento.

## Bluetooth (desde la v3.0)

No hace falta MyMouse ni adb en el PC.
1. En el móvil: modo ventana → Ajustes → **Conexión: Bluetooth** → **Emparejar por Bluetooth…** → dar permiso → **Hacer visible**.
2. En Windows: Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth → elegir el móvil y aceptar el código.
3. Vuelve al trackpad: el punto de estado se pone verde al conectar.
4. Si la pantalla de emparejar dice que el perfil HID no está disponible, ese móvil no puede hacer de ratón Bluetooth: apúntalo.
5. Logs: `adb logcat -s MyMouse` (líneas "Bluetooth: …").

## Instalador de Windows (v5)

1. Descarga `MyMouse-Setup-<versión>.exe` de la release y ejecútalo. **SmartScreen avisará** ("Windows protegió su PC") porque el instalador no está firmado: pulsa **Más información** → **Ejecutar de todas formas**. Lo mismo puede pasar con `MyMouse.exe` la primera vez.
2. El asistente no pide administrador: instala en `%LOCALAPPDATA%\Programs\MyMouse`. Casillas: acceso directo en el escritorio (apagada) e **Iniciar MyMouse con Windows** (apagada). Al terminar, "Abrir MyMouse".
3. Comprueba que existen `MyMouse.exe`, `adb.exe`, `AdbWinApi.dll` y `AdbWinUsbApi.dll` en esa carpeta y que el acceso directo del menú Inicio abre MyMouse. **Sin instalar platform-tools** y con el cable puesto, el host debe encontrar adb y conectar el móvil (tooltip de la bandeja: "Conectado").
4. Con "Iniciar con Windows" marcada, `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` tiene el valor `MyMouse` con la ruta entre comillas, y la casilla de la bandeja (Iniciar con Windows) sale marcada.
5. Instalar encima de una versión anterior con MyMouse abierto: el instalador pide cerrarlo o lo cierra; los ajustes se conservan.
6. **Desinstalar** (Configuración → Aplicaciones): borra la carpeta de instalación y el valor `Run`, pero **conserva `%APPDATA%\MyMouse`** (gestos, formas, configuración y logs).

## Bluetooth completo (v5)

**Actualizar desde una versión anterior obliga a volver a emparejar:** el descriptor HID del ratón cambió (lleva scroll fino) y Windows guarda en caché el de la primera vez. En Windows: Configuración → Bluetooth y dispositivos → el móvil → **Quitar dispositivo**; en el móvil, pantalla Bluetooth abierta → **Hacer visible**; y vuelve a añadirlo desde Windows (los pasos de abajo, "volver a emparejar"). Sin hacerlo, todo funciona como antes pero sin scroll fino.

Con la conexión **Bluetooth** elegida y el móvil conectado al PC:
1. **Gestos:** 4 dedos quietos → dibuja una `Z`: el PC debe hacer **Deshacer** sin tener MyMouse instalado. La hoja de formas (y la chuleta del modo gestos) muestra las acciones del móvil con su ayuda ("Deshacer", "Escritorio"…) y fija arriba `Z` y `V`. Prueba también `←` (atrás), `↓` (escritorio), `↑` (vista de tareas), `V` (reproducir/pausar), `>` (siguiente), `<` (anterior) y `✓` (guardar). Una forma sin acción avisa "sin acción".
2. **Editor de acciones:** *Ajustes → Modo gestos → Acciones por Bluetooth…*. Toca una forma: elige *Atajo* (marca Ctrl/Alt/Shift/Win y elige la tecla; sale "Hará: Ctrl+Shift+T"), *Multimedia*, *Texto* o *Ninguna*; escribe una ayuda y, si quieres, *Fijar en la chuleta*; **Probar** ejecuta la acción en el PC al momento (solo conectado); **Guardar**. Comprueba que lo guardado se usa al dibujar la forma.
3. **Texto:** crea una acción de texto con `Hola ¿qué tal? ñandú @ café` y pruébala con la distribución **Español (España)**: debe salir idéntico (acentos, `ñ`, `¿`, `@`). Con **Inglés (EE. UU.)** el editor avisa de los caracteres que no puede escribir (`ñ`, `¿`, `é`…) y se saltan. Si Windows usa otra distribución, el texto sale mal: apúntalo. Los emoji no se pueden escribir.
4. **Bloquear el PC:** un atajo `Win+L` (por defecto, el botón *Bloquear* del panel).
5. **Botones propios:** el panel de widgets lleva ⏮ ⏯ ⏭, Vol −, Vol +, Silencio y después Copiar, Pegar, Captura, Deshacer y Bloquear. Pruébalos (Copiar/Pegar en un editor de texto; Captura abre el recorte de Windows). *Ajustes → Widgets → Botones por Bluetooth…*: añade, edita, borra y reordena con ↑ ↓ (máximo 16); al volver al trackpad, el panel cambia.
6. **Scroll fino:** con el móvil recién emparejado, haz scroll con 2 dedos despacio en un navegador o un documento largo: debe moverse **suave, sin saltos de línea en línea**; el zoom con 2 dedos (Ctrl + rueda) también más fino. En la pantalla Bluetooth → Diagnóstico debe poner **"Scroll fino: activado por el PC"**. Si pone "no" tras volver a emparejar, apunta el PC y la versión de Windows y copia el diagnóstico: el scroll sigue funcionando en pasos enteros. *Ajustes → Conexión → Scroll fino por Bluetooth (avanzado)* lo apaga (vuelve el descriptor antiguo; hay que volver a emparejar).
7. **Qué sigue siendo solo del USB:** abrir programas, pausar/bloquear MyMouse, reglas por aplicación, la toolbox del PC y el volumen deslizante.

## Avisos de versión nueva (v5.1)

**La v5.1 es la primera versión con buscador de actualizaciones.** El aviso solo sale cuando hay una release **más alta** que la instalada, así que con la v5.1 recién instalada y sin nada posterior en GitHub no aparecerá nada (es lo correcto: "Buscar ahora" dice "Ya tienes la última versión"). El primer aviso real llegará con la release siguiente (p. ej. al publicar la v5.2, desde la v5.1). Las compilaciones de rama (`0.0-dev.N`) y las de compilación local **nunca** avisan: para probar el aviso hace falta una release instalada (`5.0`, `5.1`…).

Cómo probarlo sin esperar a la release siguiente:
- **Con la v5.0 o anterior no sirve** (no tienen el buscador). Instala la **v5.1** en un lado y, cuando exista una release con etiqueta más alta (`v5.1.1`, `v5.2`…), comprueba el flujo completo. Mientras tanto se pueden probar a mano los extras que no necesitan una release nueva, de arriba abajo:
1. **Buscar ahora:** PC → icono de la bandeja → ⚙ → **Buscar actualizaciones ahora**: debe decir "Ya tienes la última versión (MyMouse 5.1)". Móvil → Ajustes → **Actualizaciones** → **Buscar ahora**: "Tienes la última versión." Sin Internet: en el PC sale el cuadro "No se pudo comprobar…" y en el móvil "Aún no se ha comprobado"; nunca un error en pantalla al arrancar. La versión instalada se ve en ⚙ ("Versión instalada: 5.1") y en Ajustes del móvil.
2. **Aviso cruzado** (necesita USB y dos releases distintas, p. ej. el APK de la v5.1 y el PC con una versión posterior): conecta el móvil; en el PC el banner de la toolbox dice "El móvil tiene MyMouse 5.1 y este PC la 5.0…" (o "…una versión anterior… actualízalo desde el móvil") y en el móvil sale la píldora "PC desactualizado" / "Actualizar a…". Con una versión `0.0-dev.N` en cualquiera de los dos, no sale nada.
3. **Cuando exista una release más nueva:**
   - **PC instalado con el instalador:** a los 10 s de abrir MyMouse sale un globo "Nueva versión … disponible" (una vez por versión), el icono de la bandeja lleva un **punto azul** arriba a la izquierda y la toolbox tiene el banner arriba con **Novedades** (abre la página de la release), **Actualizar** y **Omitir**. **Actualizar**: barra de progreso, MyMouse se cierra, el instalador corre en silencio y MyMouse **se vuelve a abrir solo** con la versión nueva (⚙ → "Versión instalada") y la misma configuración (tema, gestos, "Iniciar con Windows" si estaba marcado). Si falla la descarga, el banner lo dice y se puede reintentar.
   - **PC portátil (zip):** el botón dice "Ver descarga" y abre la página de la release en el navegador.
   - **Omitir:** el banner desaparece y no vuelve para esa versión; sí para la siguiente. "Buscar ahora" la vuelve a mostrar.
   - **Móvil:** en modo ventana, la píldora "Actualizar a …" (los otros botones quedan solo con icono) abre la hoja con las novedades: **Descargar e instalar** / **Omitir** / Más tarde. La primera vez Android pide permitir a MyMouse **instalar apps desconocidas**: se abre la pantalla del sistema; activa el interruptor y vuelve atrás: el instalador de Android se abre con el APK nuevo. Se instala encima y conserva los ajustes. La píldora muestra "Descargando N %" y hay una notificación del sistema. En modo bloqueado no se muestra nada.
4. **Apagar las búsquedas:** ⚙ → "Buscar actualizaciones automáticamente" (PC) y el interruptor de Ajustes → Actualizaciones (móvil): sin él no se busca solo (sigue "Buscar ahora").

Notas: las llamadas van a `api.github.com` (límite de 60 por hora y por IP, de sobra) y el log del PC (`%APPDATA%\MyMouse\logs`, líneas "Buscar actualizaciones: instalada …, última …") y `adb logcat -s MyMouse` dicen qué versión vio cada lado.

## Modo teclado (v6)

**Actualizar desde una versión anterior obliga a volver a emparejar por Bluetooth** si quieres las luces de Bloq Mayús y Bloq Num: el descriptor HID del teclado ganó un informe de salida de LEDs y Windows guarda en caché el de la primera vez. En Windows: Configuración → Bluetooth y dispositivos → el móvil → **Quitar dispositivo**; en el móvil, pantalla Bluetooth abierta → **Hacer visible**; y vuelve a añadirlo desde Windows. Sin hacerlo, todo funciona pero las luces se suponen (punto gris).

**Abrir y cerrar** (por USB y por Bluetooth):
1. **Doble toque con 4 dedos** en el trackpad: el teclado sube desde abajo (200 ms) y el trackpad se funde. Un solo toque no hace nada; dos toques lentos (más de ~⅓ de segundo entre uno y otro) tampoco; mantener los 4 dedos quietos sigue activando el modo gestos.
2. **Vol+ ×2** alterna teclado ⇄ mouse. **Vol− ×2** sigue bloqueando y desbloqueando.
3. **Widget Teclado** (⌨): por USB es el botón del panel que viene en un `gestures.json` nuevo (si ya tenías el tuyo, añádelo en la toolbox → Gestos y widgets → acción "Abrir teclado del móvil"); por Bluetooth, el botón *Teclado* de *Botones por Bluetooth…* (también hay un tipo "Abrir teclado" en el editor de acciones).
4. La tecla **🖱** y el botón **Atrás** (también en modo bloqueado) vuelven al trackpad. Con un PC con MyMouse 5.1 o anterior (protocolo < 7), el móvil avisa "Actualiza MyMouse en el PC (modo teclado)" y no abre nada.
5. En el PC: al entrar aparece la tarjeta "⌨ Teclado" y al salir "🖱 Mouse" (si *Mostrar gesto en vivo* está activado), y el estado del móvil dice "Conectado · … · Modo teclado" mientras está abierto.

**Teclado:**
6. **Letras y símbolos:** abre un Bloc de notas con la distribución del PC en Español. Escribe `Hola, ¿qué tal? ñandú @ #` (Shift para mayúsculas, AltGr para `@` y `#`). Las etiquetas son como en un teclado real: en ES la tecla `2` lleva `"` arriba y `@` abajo a la derecha, y con Shift o Bloq Mayús se resaltan las letras.
7. **Teclas muertas:** `´` y luego `a` debe dar `á` (es Windows quien las resuelve, no el móvil).
8. **Modificadores:** un toque en Shift lo deja resaltado y se aplica a la **siguiente** tecla; doble toque lo fija (candado) hasta otro toque; mantenerlo mientras pulsas otra tecla funciona como en un teclado normal. Prueba **Ctrl+C / Ctrl+V**, **Win+Shift+S**, **Alt+Tab** (Alt fijado + Tab) y **Ctrl+Shift+Esc**. Por cable, **Ctrl+Alt+Supr no funciona** (Windows no deja inyectarlo); por Bluetooth sí.
9. **Varios dedos:** Shift mantenido con un dedo y letras con otro; dos teclas a la vez.
10. **Repetición:** mantén una letra, Retroceso o una flecha. Por USB empieza a los 400 ms y repite cada 40 ms (se puede apagar en Ajustes → Teclado); por Bluetooth repite Windows (con su propio retardo). Shift, Ctrl, Bloq Mayús, `⌨` y `🖱` nunca repiten.
11. **Luces:** pulsa Bloq Mayús: la tecla y la barra de estado ("Mayús") se encienden en **verde** (el PC lo ha confirmado). Por Bluetooth, tras volver a emparejar, igual; si el diagnóstico (Bluetooth → Diagnóstico → "Luces del teclado") dice "no recibidas", el estado es supuesto y se marca con un punto **gris**. Cambia Bloq Mayús desde el teclado físico del PC: por USB el móvil se entera en menos de un segundo.
12. **Nada se queda pulsado:** con Shift fijado y una letra mantenida, sal con `🖱` (o Atrás), cambia a otra app, gira el móvil, desconecta el cable o apaga el Bluetooth: en el PC no debe quedar ninguna tecla ni modificador pulsado (prueba a escribir en el Bloc de notas después).

**Bloques** (toca `⌨` para desplegar la barra de chips; se cierra al volver a tocarlo o al pulsar una tecla):
13. **Fn**, **Flechas**, **Nav** (con las flechas), **Num** y **Solo num** se encienden y apagan al momento con una animación de 150 ms. En **vertical** solo cabe un bloque lateral (encender uno apaga los demás); en **horizontal** se suman. **Solo num** quita todo lo demás. El chip **ES/US** cambia la distribución dibujada solo para esta sesión.
14. **Deslizar sobre `⌨`** a derecha o izquierda alterna entre las dos últimas combinaciones (p. ej. teclado con numpad ⇄ Solo numpad). La combinación se recuerda por orientación.
15. **Numpad:** con Bloq Num encendido escribe números; apagado, mueve el cursor (Inicio, Fin, flechas…).

**Ajustes → Teclado:** distribución (*Automática* usa la del PC por USB —por Bluetooth, la de escribir texto—, *Español* o *Inglés*), tamaño de teclas, vibración, repetición y **barra de trackpad**. Con la barra activada, el hueco libre de arriba mueve el puntero (un dedo), hace clic (toque) y scroll (dos dedos); un Ctrl pendiente se aplica al clic (Ctrl+clic).

**Límites conocidos:** por cable no se pueden escribir teclas en ventanas de administrador si MyMouse no lo es (pasa ya con el ratón); más de 6 teclas normales a la vez por Bluetooth se ignoran; la tecla Pausa no está.

## Qué anotar si algo falla
- Release en la que falla y la última en la que funcionaba.
- Qué hiciste y qué pasó (y qué esperabas).
- El log del host del día (`%APPDATA%\MyMouse\logs`) y, si puedes, `adb logcat -s MyMouse -d > movil.txt`.
