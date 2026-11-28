# MyMouse — v7: guía para empezar y Play Store

> Base: todo hasta la [v6.1](spec-v6.1-tema-sincronizado.md).
>
> Origen: la app va a publicarse en Play Store. Un usuario nuevo tiene que poder conectarla y aprender a usarla sin leer `docs/probar.md`. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario. Las marcadas con *(comprobar)* dependen de reglas de Google que cambian: hay que verificarlas en la documentación oficial al implementar.

## 0. Fases
Cada fase es una release que se cierra antes de empezar la siguiente.

| Fase | Objetivo | Release |
|---|---|---|
| **v7.0** | Textos traducibles (español e inglés) y dos variantes de la app: Play y GitHub | Sin cambios visibles salvo el idioma |
| **v7.1** | La guía: bienvenida, conexión, práctica y consejos | La guía completa |
| **v7.2** | Modo demo, material de la ficha de Play y primera subida a Play (test cerrado) | Lista para Play |

---

## 1. v7.0: idiomas y variantes

### 1.1. Español e inglés
- **Móvil:** todos los textos visibles pasan a `res/values/strings.xml` (inglés, el idioma por defecto) y `res/values-es/strings.xml` (español). Hoy hay unos 240 textos en el código Kotlin.
  - El idioma sigue al del sistema. Si no es español, la app se muestra en inglés.
  - Los textos de `core` que llegan a la pantalla (diagnóstico del Bluetooth, etc.) dejan de devolver texto y devuelven un código o enum. La app lo traduce.
- **PC:** igual, en `MyMouse.Host`, con ficheros `.resx` (`Strings.resx` en inglés y `Strings.es.resx`) *(decisión)*. Hoy hay unos 170 textos.
  - El idioma sigue al de Windows.
  - Los logs siguen en español: no son para el usuario.
- Los nombres de los temas y acentos (ids) no cambian. Solo cambia su texto visible.
- Un test en cada lado comprueba que las dos traducciones tienen las mismas claves.

### 1.2. Dos variantes de la app (product flavors)
| | `github` | `play` |
|---|---|---|
| Autoactualizador (`Updater`, `ApkInstaller`, píldora de versión nueva) | Sí | **No**: Play no permite que una app de la tienda se actualice por su cuenta |
| Permiso `REQUEST_INSTALL_PACKAGES` | Sí | **No** |
| Modo quiosco (`AdminReceiver`, device owner) | Sí | **No** *(decisión: Play revisa con lupa la administración del dispositivo, y es un modo para expertos)* |
| Firma | `mymouse-dev.keystore` (el del repo) | Clave de subida **privada**, fuera de git: ruta y contraseñas en `local.properties` o en variables de entorno |
| `applicationId` | `dev.mymouse` | `dev.mymouse` (las dos versiones no conviven en el mismo móvil) *(decisión)* |

- El código específico de cada variante va en `src/github/` y `src/play/`. La interfaz común (por ejemplo, `UpdateChecker`) va en `main`, con una implementación vacía en `play`.
- La CI construye las dos. La release de GitHub publica solo el APK `github`. El AAB `play` se sube a mano a Play Console *(decisión: no automatizamos la subida todavía)*.
- Antes de cerrar la fase, hay que revisar el `targetSdk` mínimo que pide Play en ese momento *(comprobar)*.
  - Comprobado el 28-11-2026 en <https://developer.android.com/google/play/requirements/target-sdk>: desde el 31 de agosto de 2026, las apps nuevas y las actualizaciones tienen que apuntar a **Android 16 (API 36)** o superior (con prórroga a petición hasta el 1 de noviembre de 2026, ya pasada). Las apps existentes deben apuntar a API 35 o más para seguir visibles a usuarios nuevos en Android más recientes.
  - La app sigue en `targetSdk 35` en la v7.0 (el variante `github` se comporta como la v6.1). **Hay que subir a 36 antes de la primera subida a Play (v7.2)**, y probarlo en un móvil real. *(comprobar)*

### 1.3. Criterios de "hecho"
1. Con el móvil en inglés, toda la app está en inglés; en español, toda en español. Igual en el PC con Windows.
2. El APK `play` no tiene `REQUEST_INSTALL_PACKAGES` ni receptor de administración. Se comprueba con `aapt dump permissions` o un test de manifiesto.
3. El APK `github` se comporta exactamente como la v6.1.

---

## 2. v7.1: la guía

Se abre **sola la primera vez** que se arranca la app, y se puede repetir desde **Ajustes → Guía** (cada parte por separado).
- Cada pantalla tiene "Saltar". Saltar la guía entera la marca como vista.
- Se guarda qué partes se completaron, en `AppSettings`: `guideSeen`, `practiceDone` y la lista de retos superados.

### 2.1. Bienvenida (tour)
De 3 a 4 pantallas que se pasan deslizando, con ilustración y una frase cada una:
1. "Tu móvil es el ratón y el teclado de tu PC."
2. "Gestos como en un portátil": mover, clic, scroll, pellizco, tres y cuatro dedos.
3. "Dibuja formas para lanzar atajos" y el panel de widgets.
4. "Elige cómo conectar", que lleva a §2.2.

Las ilustraciones se dibujan con `Canvas` y el tema elegido, como las chuletas, no con imágenes *(decisión: siguen el tema y no pesan)*.

### 2.2. Elegir cómo conectar
Tres caminos en una pantalla. El primero va destacado:

| Camino | Para quién | Qué consigue |
|---|---|---|
| **Emparejar rápido (Bluetooth)** | Todos. No hace falta instalar nada en el PC | Ratón, teclado, gestos y botones propios definidos en el móvil |
| **App del PC** | Quien quiera todo | Formas con atajos del PC, widgets, toolbox y tema sincronizado |
| **Cable USB** | Quien quiera la menor latencia y carga mientras usa | Lo mismo que la app del PC, por cable |

Hoy la app del PC solo habla con el móvil por cable: por Bluetooth el móvil es un ratón y teclado y no hay canal de datos. Por eso **"App del PC" y "Cable USB" son dos pasos del mismo camino completo**:
- "App del PC" instala el programa.
- "Cable USB" prepara el móvil.
- La pantalla lo explica así: "Para usar la app del PC, conecta por cable" *(decisión)*.

### 2.3. Emparejar rápido (Bluetooth)
Asistente paso a paso, encima de lo que ya hace `BluetoothActivity`:
1. Pedir los permisos de Bluetooth, explicando para qué sirven.
2. Hacer el móvil visible y mostrar: "En el PC: Configuración → Bluetooth → Agregar dispositivo → elige *<nombre del móvil>*". Debajo, una ilustración de esa pantalla de Windows.
3. Esperar a que el PC conecte. Se pasa solo al siguiente paso cuando el transporte informa de que está conectado (comprobación real, no "pulsa cuando acabes").
4. "Mueve el dedo: ¿se mueve el cursor del PC?" con dos botones, "Sí" y "No".
   - "Sí" lleva a la práctica (§2.6).
   - "No" lleva a los consejos de diagnóstico que ya existen.
5. Si en 60 s no conecta, se muestran las causas habituales y el botón "Diagnóstico" (la pantalla actual).

### 2.4. App del PC
1. Explica qué añade la app del PC y que es gratis y para Windows 10/11.
2. **Cómo llegar al instalador:**
   - Se muestra un enlace corto y fácil de teclear al instalador (la release más reciente de GitHub).
   - Si el Bluetooth ya está conectado, hay un botón **"Escríbelo en el PC por mí"**: el móvil, como teclado, pulsa Win+R, teclea el enlace y pulsa Intro *(decisión: aprovecha el emparejado rápido)*.
     - Usa la distribución de teclado configurada en el modo teclado.
     - Antes avisa: "Va a abrirse el navegador en tu PC".
3. Aviso de **SmartScreen** con su captura: "Más información → Ejecutar de todas formas", porque el instalador no está firmado.
4. Siguiente paso: Cable USB (§2.5).

### 2.5. Cable USB
1. **Activar las opciones de desarrollador:**
   - Ilustración genérica de "toca 7 veces Número de compilación".
   - Botón que abre Ajustes → Información del teléfono.
   - La app detecta cuándo están activadas (`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`) y avanza sola.
2. **Activar la depuración USB:**
   - Botón que abre las opciones de desarrollador (`ACTION_APPLICATION_DEVELOPMENT_SETTINGS`).
   - Detección con `Settings.Global.ADB_ENABLED`.
3. **Conectar el cable con la app del PC abierta** y aceptar en el móvil "¿Permitir depuración USB?", con "Permitir siempre" marcado.
4. Esperar a que la conexión con el PC esté lista: el punto se pone verde.
5. Si en 60 s no conecta, se muestran las causas habituales:
   - El cable solo carga.
   - Falta el driver del fabricante.
   - La app del PC está cerrada.
   - Se rechazó el aviso de depuración.
   - Se indica dónde mirar: el tooltip del icono de la bandeja.
- Las ilustraciones son genéricas, sin capturas de una marca concreta. Si hiciera falta algo específico por marca (por ejemplo, Xiaomi), va en una línea de texto.

### 2.6. Zona de práctica
Un **escritorio simulado dentro del móvil**. No necesita el PC.
- **Mitad superior:** un "monitor" en miniatura con cursor, dos ventanas, un texto largo con scroll, un icono que arrastrar y un menú contextual.
- **Mitad inferior:** el trackpad. Usa **el mismo `TrackpadEngine`** que el trackpad real, conectado a un destino falso en lugar de al transporte.
  - Lo que se practica se comporta exactamente igual que en el PC.
- En el lateral, o como tarjeta plegable, una lista de **retos** que se marcan al conseguirlos:

| Reto | Cómo se supera |
|---|---|
| Mover | Llevar el cursor a un punto marcado |
| Clic | Tocar con el cursor sobre un botón |
| Clic derecho | Tocar con 2 dedos sobre una ventana y abrir su menú |
| Scroll | Bajar el texto hasta el final |
| Arrastrar | Llevar el icono a una carpeta |
| Zoom | Pellizcar sobre una imagen |
| 3 dedos | Arriba, abajo y a los lados (cambiar de ventana simulada) |
| 4 dedos | Abrir el teclado y volver |
| Forma | Dibujar una forma activada (solo si hay alguna) |

- Cada reto tiene una animación corta de "cómo se hace", con dedos dibujados.
- Al terminar todos, se muestra una pantalla de enhorabuena y se pasa al trackpad real. Se puede salir en cualquier momento.
- La lógica de los retos (qué evento del motor supera cada uno) va en `core`, con tests. La app solo dibuja.

### 2.7. Consejos al vuelo
Avisos cortos que aparecen sobre el trackpad (en la práctica y en el uso real) cuando se detecta un problema:
- Cada consejo aparece **como mucho una vez** y se puede desactivar en Ajustes → "Mostrar consejos".
- Además, se pueden ver todos en Ajustes → Guía → Consejos.

| Consejo | Se detecta cuando |
|---|---|
| "Separa un poco los dedos: si están muy juntos, el móvil puede verlos como uno" | Dos dedos bajan a menos de ~12 mm *(decisión: ajustar probando)* o un gesto de 2 dedos pasa a 1 dedo a mitad |
| "Apoyar la palma o el borde de la mano mete toques" | Toques en el borde de la pantalla durante un gesto |
| "Prueba la zona de práctica" | El mismo tipo de gesto se cancela o falla 3 veces seguidas |
| "Para scroll, mueve los dos dedos a la vez" | Un scroll empieza y se convierte en pellizco varias veces |
| "Sin cursor: desactiva *Mejorar la precisión del puntero*" | *(solo texto en la guía, no se detecta)* |

La detección va en `core`, alimentada por el `TrackpadEngine`, con tests. Los umbrales van en mm y se convierten con la densidad.

### 2.8. Criterios de "hecho"
1. Un usuario que no conoce la app, con un PC y un móvil sin preparar, llega a mover el cursor por Bluetooth sin ayuda y sin salir de la app, salvo a los Ajustes del sistema.
2. Con el camino completo, llega a tener la app del PC conectada por cable siguiendo solo la guía.
3. La práctica funciona sin PC, y todos sus retos se pueden superar.
4. Cada consejo de §2.7 aparece al provocar su causa, y no vuelve a salir.
5. Tests en `core` de los retos y los detectores de consejos.

---

## 3. v7.2: demos y Play Store

### 3.1. Modo demo
- Es la zona de práctica sin los retos, a pantalla completa y con el tema elegido. Se activa desde Ajustes → Guía → **Modo demo**.
- Sirve para hacer capturas y vídeos del móvil sin depender del PC.
- **Reproducción guionizada** *(decisión)*: un guion fijo (mover, clic, scroll, arrastrar, forma) se reproduce solo, con dedos dibujados.
  - Así las capturas y el vídeo salen iguales cada vez.
  - El guion inyecta eventos en el `TrackpadEngine`, no toques reales.

### 3.2. Vídeo "cómo queda en el escritorio"
Script `tools/demo/record.ps1`:
- Graba a la vez el móvil (`adb exec-out screenrecord`) y la pantalla de Windows (ffmpeg `gdigrab`).
- Los monta lado a lado (ffmpeg `hstack`, con el móvil a escala) en `out/demo-<fecha>.mp4`.
- Se usa con uso real, no simulado: la persona hace los gestos y el script solo graba.
- ffmpeg no va en el repo; el script dice cómo instalarlo si falta.

### 3.3. Ficha de Play Store
Todo en `store/` (en inglés y español), versionado:

| Elemento | Formato *(comprobar en Play Console)* |
|---|---|
| Icono | 512×512 PNG |
| Gráfico destacado | 1024×500 |
| Capturas del móvil | De 2 a 8. Proponemos 6: trackpad, práctica, formas, widgets, teclado y temas |
| Vídeo (opcional) | YouTube, a partir de §3.2 |
| Descripción corta | ≤ 80 caracteres |
| Descripción larga | ≤ 4000 caracteres |
| Política de privacidad | Página pública. La app no recoge datos: se publica en GitHub Pages desde `docs/privacy.md` |
| Seguridad de los datos | "No se recogen ni comparten datos" (revisar que es verdad: el autoactualizador no va en `play`) |

### 3.4. Primera subida
- Cuenta de desarrollador de Play y, si la cuenta personal es nueva, el **test cerrado** previo que exija Google *(comprobar número de testers y días)*.
- Se crea la clave de subida y se activa Play App Signing.
- Checklist en `docs/play-store.md` con cada paso y su estado.

### 3.5. Criterios de "hecho"
1. El modo demo reproduce el guion igual cada vez.
2. `tools/demo/record.ps1` produce un vídeo lado a lado.
3. `store/` tiene todo el material de §3.3 en los dos idiomas.
4. El AAB `play` está subido a un test cerrado.

---

## 4. Fuera de alcance
- Conexión por Wi-Fi.
- Firmar el instalador de Windows: cuesta dinero; se valorará si SmartScreen frena a mucha gente.
- Canal de datos por Bluetooth entre el móvil y la app del PC.
- Más idiomas que español e inglés.
