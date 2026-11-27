# Cómo instalar y probar MyMouse

Guía para instalar una release y comprobar cada función a mano. Los detalles de diseño de cada función están en las specs de `docs/`.

## Instalar

De la [página de releases](https://github.com/DiegoTorresMijarra/MyMouse/releases) descarga:
- `MyMouse-<versión>.apk`: la app del móvil (Android 8 o superior).
- `MyMouse-Setup-<versión>.exe`: el **instalador** de Windows. Incluye `MyMouse.exe` (la app de bandeja) y adb.
- `MyMouse-Windows-<versión>.zip` (opcional): versión portátil, con `MyMouse.exe` y `MyMouse.TestClient.exe` (simula el móvil). No lleva adb: usa el del `PATH`.

### Móvil
- Instala el APK. Desde el ordenador: `adb install -r -d MyMouse-<versión>.apk` (sustituye a la anterior y conserva los ajustes, también al bajar de versión).
- **Solo para la conexión por cable:** Ajustes → Información del teléfono → toca 7 veces "Número de compilación"; después, en Opciones de desarrollador, activa **Depuración USB**.

### PC
1. Ejecuta `MyMouse-Setup-<versión>.exe`. **SmartScreen avisará** porque el instalador no está firmado: **Más información** → **Ejecutar de todas formas**.
2. No pide administrador: instala en `%LOCALAPPDATA%\Programs\MyMouse`. Casillas: acceso directo en el escritorio e **Iniciar MyMouse con Windows** (las dos apagadas).
3. Recomendado: desactiva "Mejorar la precisión del puntero" (Configuración del mouse → Opciones adicionales → Opciones de puntero). La aceleración ya la aplica el móvil.

Comprobar el instalador:
- La carpeta tiene `MyMouse.exe`, `adb.exe`, `AdbWinApi.dll` y `AdbWinUsbApi.dll`, y el acceso directo del menú Inicio abre MyMouse.
- Con "Iniciar con Windows", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` tiene el valor `MyMouse` con la ruta entre comillas.
- Instalar encima con MyMouse abierto: el instalador lo cierra y los ajustes se conservan.
- **Desinstalar** borra la carpeta y el valor `Run`, pero **conserva `%APPDATA%\MyMouse`** (gestos, formas, configuración y logs).

## Conectar

### Por cable (USB)
1. Abre MyMouse en el PC y conecta el cable. Acepta en el móvil "¿Permitir depuración USB?" (marca "Permitir siempre").
2. Abre MyMouse en el móvil (Ajustes → **Conexión: USB**). El punto de la esquina se pone verde al conectar.

Si no conecta:
- Pasa el ratón por el icono de la bandeja: el tooltip dice si encuentra adb y el móvil.
- Si Windows no ve el móvil, falta el driver USB del fabricante.
- Logs del host: icono de la bandeja → "Abrir carpeta de logs". Logs del móvil: `adb logcat -s MyMouse`.
- Prueba solo el PC con `MyMouse.TestClient.exe` (con el host abierto): el cursor debe dibujar un círculo.

### Por Bluetooth
No hace falta MyMouse ni adb en el PC.
1. En el móvil: modo ventana → Ajustes → **Conexión: Bluetooth** → **Emparejar por Bluetooth…** → dar permiso → **Hacer visible**.
2. En Windows: Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth → el móvil; acepta el código en los dos lados.
3. Vuelve al trackpad: el punto de estado se pone verde al conectar. Si la pantalla dice que el perfil HID no está disponible, ese móvil no puede hacer de ratón Bluetooth.

Si el móvil dice **"Esperando al PC"** y el PC no lo usa como ratón:
1. Abre la pantalla Bluetooth (Ajustes → Emparejar por Bluetooth…, o un **toque en el punto de estado** del trackpad). En **Diagnóstico** todo debe salir con ✓.
2. Pulsa **Reintentar conexión** y mira los últimos eventos.
3. Si todo está bien en el móvil pero Windows no conecta, lo más probable es que se emparejara **antes** de elegir Bluetooth en MyMouse. Para volver a emparejar:
   - En Windows: el móvil → **Quitar dispositivo**.
   - En el móvil, **con la pantalla Bluetooth abierta** durante todo el emparejado, pulsa **Hacer visible**.
   - En Windows: Agregar dispositivo → el móvil.
4. Que Windows muestre un icono de teléfono mientras empareja es normal. Al terminar debe aparecer **como conectado**; si solo pone "Emparejado", repite el paso 3.
5. **Copiar diagnóstico** copia las comprobaciones y los últimos 30 eventos, para contarlo.

Retraso del puntero: junto al punto de estado se lee "Bluetooth · 120 inf/s · 8 ms" (informes por segundo e intervalo real entre informes). Si se nota retraso, prueba *Ajustes → Intervalo Bluetooth* con 12–16 ms.

## Trackpad
- Mover con un dedo; **tocar** = clic; **tocar con 2 dedos** = clic derecho; **doble toque y arrastrar** = arrastrar.
- **Scroll** con 2 dedos (vertical y horizontal), con **inercia** (se corta con cualquier toque). **Pellizco** = zoom (Ctrl + rueda), con "Velocidad del zoom" en Ajustes.
- **3 dedos** (gestos de Windows): ↑ vista de tareas, ↓ escritorio, ← → cambiar de app (mantiene Alt y avanza cada ~120 px; al levantar se suelta). Se desactivan en Ajustes.
  - **Xiaomi/HyperOS:** el sistema usa 3 dedos para la captura. Desactívalo en Ajustes → Ajustes adicionales → Atajos de gestos (captura con 3 dedos y recorte parcial).
- **4 dedos:** quietos (300 ms) = modo gestos; juntar / separar = mostrar / ocultar el panel de widgets; ↑ / ↓ = abrir / cerrar la toolbox del PC (solo por cable); doble toque = teclado.
- **Vol− ×2** alterna modo bloqueado y modo ventana; **Vol+ ×2** alterna teclado y trackpad.
- El **modo depuración** (Ajustes) muestra los dedos y las acciones enviadas.

## Modo gestos (formas)
1. En el móvil: Ajustes → **Formas…** para ver, activar y grabar formas.
2. En el trackpad: apoya **4 dedos quietos** hasta que vibre, levántalos y dibuja la forma con un dedo.
3. **Por cable**, lo que hace cada forma se configura en el PC (toolbox → Gestos y widgets). **Por Bluetooth**, en el móvil: *Ajustes → Modo gestos → Acciones por Bluetooth…* (atajo, multimedia, texto o ninguna; **Probar** la ejecuta en el PC).
4. Comprobar por Bluetooth: dibuja una `Z` y el PC debe hacer **Deshacer** sin tener MyMouse instalado. Prueba también `←` (atrás), `↓` (escritorio), `↑` (vista de tareas), `V` (reproducir/pausar), `>` / `<` (siguiente / anterior) y `✓` (guardar).
5. **Texto por Bluetooth:** con la distribución **Español (España)**, `Hola ¿qué tal? ñandú @ café` debe salir idéntico. Con **Inglés (EE. UU.)** el editor avisa de lo que no puede escribir. Los emoji no se pueden escribir.

## Panel de widgets
- Aparece abajo (vertical) o a la derecha (horizontal). Tamaño y visibilidad en Ajustes → Widgets.
- **Por cable**, los widgets se configuran en el PC (toolbox → Gestos y widgets → Widgets). **Por Bluetooth**, el panel lleva ⏮ ⏯ ⏭, Vol −, Vol +, Silencio y los botones propios de *Ajustes → Widgets → Botones por Bluetooth…* (máximo 16).
- Comprobar:
  - Un botón se ejecuta al **levantar** el dedo dentro de él; si arrastras fuera, se cancela.
  - Música: el título y el artista del PC salen en el móvil y ⏮ ⏯ ⏭ funcionan.
  - Volumen: la barra mueve el volumen de Windows y sigue los cambios hechos en el PC (menos de 0,3 s). El altavoz silencia.
  - Ningún toque del panel mueve el cursor, y al revés.

## La toolbox del PC
1. **Abrir y cerrar:** clic en el icono de la bandeja, o **4 dedos ↑** en el móvil. Se cierra con **4 dedos ↓**, `Esc` o un clic fuera. Abierta desde el móvil no quita el foco a la app en la que estabas. Clic derecho en el icono: Abrir MyMouse, Pausar, Salir.
2. **Tarjetas:** Opciones, Música, Gestos y Widgets del móvil. Ajustes → *Personalizar tarjetas…* para quitar, añadir y reordenar.
3. **Maximizar (⤢):** ventana de dos columnas, redimensionable; al reabrir, recuerda cómo estaba.
4. **Gesto en vivo:** con el modo gestos activo en el móvil, sube una tarjeta con cuenta atrás y el trazo en directo. Al terminar: **verde** = ejecutada, **ámbar** = sin acción (con botón *Asignar*), **gris** = no reconocida o cancelada, **rojo** = la acción falló.
5. **Editor** (Opciones → *Gestos y widgets*):
   - **Gestos:** forma, tipo de acción (atajo, abrir app, texto, multimedia, MyMouse), ayuda y si va fijada en la chuleta. *Probar* la ejecuta a los 3 s. *Guardar* escribe `gestures.json` y el móvil recibe la lista al momento.
   - **Widgets:** añadir, ordenar y borrar, con vista previa del panel.
   - **← Volver** con cambios sin guardar pregunta Guardar / Descartar / Cancelar.
6. **Forma nueva desde el PC:** *+ Nueva forma* → nombre → 3 muestras con el ratón → *Guardar en el móvil*. Sin el móvil conectado por cable, el botón queda desactivado.
7. **Forma nueva desde el móvil:** Ajustes → Formas → Nueva forma. Al volver al trackpad, el PC avisa "Nueva forma «x»: ¿qué quieres que haga?".
8. **Icono siempre visible (Windows 11):** *Ajustes (⚙) → Mostrar siempre el icono en la barra de tareas* (activado por defecto) lo saca de la flecha ˄.

## Modo teclado
**Abrir y cerrar:** doble toque con 4 dedos, **Vol+ ×2** o el widget Teclado (⌨). La tecla **🖱** y el botón **Atrás** vuelven al trackpad. En el PC sale la tarjeta "⌨ Teclado" / "🖱 Mouse" y el estado dice "Modo teclado".

Comprobar:
1. **Letras y símbolos:** con el PC en español, `Hola, ¿qué tal? ñandú @ #` sale bien (Shift para mayúsculas, AltGr para `@` y `#`).
2. **Teclas muertas:** `´` y luego `a` da `á`.
3. **Modificadores:** un toque en Shift se aplica a la siguiente tecla; doble toque lo fija; mantenerlo funciona como en un teclado normal. Prueba **Ctrl+C / Ctrl+V**, **Win+Shift+S**, **Alt+Tab** y **Ctrl+Shift+Esc**. Por cable, **Ctrl+Alt+Supr no funciona** (Windows no deja inyectarlo); por Bluetooth sí.
4. **Repetición:** por cable empieza a los 400 ms y repite cada 40 ms (se apaga en Ajustes → Teclado); por Bluetooth repite Windows.
5. **Luces:** Bloq Mayús se enciende en **verde** cuando el PC lo confirma; un punto **gris** significa que el estado es supuesto. Si por Bluetooth el diagnóstico dice "Luces del teclado: no recibidas", vuelve a emparejar.
6. **Nada se queda pulsado:** con Shift fijado y una letra mantenida, sal con `🖱`, cambia de app, gira el móvil o desconecta: en el PC no debe quedar ninguna tecla pulsada.
7. **Bloques** (toca `⌨`): Fn, Flechas, Nav, Num y Solo num. En vertical solo cabe un bloque lateral; en horizontal se suman. Deslizar sobre `⌨` alterna entre las dos últimas combinaciones.

**Ajustes → Teclado:** distribución (*Automática* usa la del PC por cable), tamaño de teclas, vibración, repetición y **barra de trackpad** (el hueco de arriba mueve el puntero, hace clic y scroll).

**Límites:** por cable no se puede escribir en ventanas de administrador si MyMouse no lo es; por Bluetooth se ignoran más de 6 teclas normales a la vez; la tecla Pausa no está.

## Temas
- **Móvil:** *Ajustes → Tema* (tema, acento y estrellas). **PC:** *Ajustes (⚙) → Tema…*.
- **Por cable se sincronizan** el tema, el acento y las estrellas: gana el último cambio. Un cambio en el PC llega al móvil al momento; uno en el móvil, al volver al trackpad. La animación de las estrellas es de cada lado.
- *Medianoche*, *Grafito* y *Negro (OLED)* no llevan estrellas. *Efecto cristal* (PC) funciona sobre cualquier tema.

## Actualizaciones
- **Buscar ahora:** PC → ⚙ → **Buscar actualizaciones ahora**; móvil → Ajustes → **Actualizaciones** → **Buscar ahora**. Las compilaciones `0.0-dev.N` nunca avisan.
- Con una release más nueva:
  - **PC:** globo "Nueva versión … disponible", punto azul en el icono y banner en la toolbox con **Novedades**, **Actualizar** y **Omitir**. **Actualizar** instala en silencio y vuelve a abrir MyMouse con la misma configuración. Con el zip, el botón abre la página de la release.
  - **Móvil:** en modo ventana, la píldora "Actualizar a …" descarga e instala el APK (la primera vez Android pide permitir instalar apps desconocidas).
- **Aviso cruzado:** por cable, si el móvil y el PC tienen versiones distintas, el que va por detrás lo dice.
- Las búsquedas automáticas se apagan en ⚙ (PC) y en Ajustes → Actualizaciones (móvil).

## Qué anotar si algo falla
- Versión del móvil y del PC, y la última con la que funcionaba.
- Qué hiciste, qué pasó y qué esperabas.
- El log del host del día (`%APPDATA%\MyMouse\logs`) y, si puedes, `adb logcat -s MyMouse -d > movil.txt`.
