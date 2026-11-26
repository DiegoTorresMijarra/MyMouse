# MyMouse — Especificación v0

> Objetivos: [00-roadmap.md](00-roadmap.md#v0-trackpad) · Protocolo: [../protocol/PROTOCOL.md](../protocol/PROTOCOL.md)

## 1. Componentes

| Componente | Ubicación | Responsabilidad |
|---|---|---|
| App Android | `android/app` | UI, captura táctil, ajustes, conexión. |
| Núcleo Android | `android/core` | **Kotlin puro, sin dependencias de Android.** Intérprete de gestos, aceleración y codificación del protocolo. Testeable en la JVM. |
| Protocolo .NET | `windows/src/MyMouse.Protocol` | Decodificación y codificación. Multiplataforma y testeable. |
| Host Windows | `windows/src/MyMouse.Host` | Bandeja, servidor TCP, gestión de adb, inyección `SendInput`. |
| Cliente de prueba | `windows/src/MyMouse.TestClient` | Consola que simula el móvil para probar el host. |

## 2. App Android

### 2.1 Pantalla del trackpad
- Una Activity a pantalla completa en **modo inmersivo**: barras del sistema ocultas, que reaparecen al deslizar desde el borde.
- Fondo negro. Un indicador de estado discreto en una esquina: gris = desconectado, verde = conectado.
- `FLAG_KEEP_SCREEN_ON` mientras la app está en primer plano.
- Orientación **bloqueada** (configurable: vertical u horizontal).
- **Botón de ajustes:** icono pequeño en una esquina. Para no abrirlo sin querer mientras se usa el trackpad, se abre con una pulsación larga sobre él.

### 2.2 Interpretación de gestos

El intérprete es una **máquina de estados pura** en `core`. Recibe eventos táctiles abstractos (`down`, `move` y `up` por puntero, con su marca de tiempo) y emite acciones (`Move`, `Button`, `Scroll`). No sabe nada de Android ni de sockets.

| Gesto | Condición | Acción |
|---|---|---|
| **Mover** | 1 dedo se desplaza más de `TOUCH_SLOP` | `MOVE` con delta acelerado |
| **Clic** | 1 dedo, pulsar y levantar en menos de `TAP_TIMEOUT` y sin pasar de `TOUCH_SLOP` | `BUTTON left ↓` + `↑` (diferido, ver arrastre) |
| **Clic derecho** | 2 dedos tocan y levantan en menos de `TAP_TIMEOUT`, sin pasar de `TOUCH_SLOP` | `BUTTON right ↓↑` |
| **Scroll** | 2 dedos se desplazan más de `TOUCH_SLOP` | `SCROLL` con el delta del centroide × `scrollSpeed` |
| **Arrastrar** | Clic y, antes de `DOUBLE_TAP_TIMEOUT`, otro toque que se mueve | `BUTTON left ↓`, `MOVE`…, `BUTTON left ↑` al levantar |
| **Doble clic** | Dos clics seguidos | Dos clics completos (Windows decide si es doble clic) |

Valores por defecto (configurables internamente, no en la UI de v0):

| Constante | Valor |
|---|---|
| `TOUCH_SLOP` | 8 px |
| `TAP_TIMEOUT` | 180 ms |
| `DOUBLE_TAP_TIMEOUT` | 250 ms |

**Decisión sobre el clic diferido.** Para poder detectar el arrastre, el `BUTTON ↑` del primer toque espera hasta `DOUBLE_TAP_TIMEOUT`, o se envía en cuanto se descarta el arrastre. Así se evita el "clic fantasma" al empezar a arrastrar. El precio es que **el clic llega con hasta 250 ms de retraso**. Si molesta en la prueba de uso, se puede desactivar el arrastre por doble toque.

Reglas:
- Pasar de 1 a 2 dedos en mitad de un movimiento cambia a scroll. Pasar de 2 a 1 no vuelve a mover hasta que se levantan todos los dedos (evita saltos).
- Con 3 o más dedos no se hace nada en v0; quedan reservados para el modo gestos de v1.
- Se procesan **todas las muestras históricas** de cada `MotionEvent`.

### 2.3 Aceleración

- `delta_salida = delta_entrada × sensitivity × curva(velocidad)`.
- `curva(v) = 1 + accel × max(0, v − v0) / v0`, con un tope de `maxGain`.
- El resultado se redondea a entero **acumulando el resto** para no perder los movimientos lentos.
- Valores por defecto: `sensitivity = 1.0`, `accel = 0.6`, `v0 = 0.3 px/ms`, `maxGain = 3.0`.

El scroll usa el mismo acumulador fraccionario.

### 2.4 Conexión
- Bucle de reconexión: intentarlo cada 1 s mientras no haya conexión.
- Al conectar, enviar `HELLO(1)` y esperar `HELLO_ACK` (timeout de 2 s).
- Enviar `PING` cada 1 s; dar la conexión por perdida tras 3 s sin recibir nada.
- Envío desde un único hilo con cola. Los `MOVE` pendientes se **fusionan**: si la cola se atasca, se suman los deltas en lugar de acumular mensajes.

### 2.5 Ajustes (v0)
Sensibilidad, aceleración, velocidad de scroll, invertir scroll ("natural"), orientación, segundos hasta atenuar la pantalla.

## 3. Host de Windows

### 3.1 Bandeja
- Icono en la bandeja del sistema que indica el estado: sin móvil / esperando / conectado / pausado.
- Menú: **Pausar o Reanudar** (en pausa sigue conectado pero ignora la entrada), **Abrir carpeta de logs**, **Salir**.
- Una única instancia (mutex con nombre).

### 3.2 Servidor
- Escucha en `127.0.0.1:47800`, **nunca** en `0.0.0.0`.
- Aplica las reglas de HELLO, PING y desconexión de [PROTOCOL.md](../protocol/PROTOCOL.md).
- Al desconectarse el cliente, suelta los botones que sigan pulsados.

### 3.3 Gestión de adb
- Busca `adb.exe` en este orden: el valor de configuración, `PATH`, y `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
- Cada 3 s ejecuta `adb reverse tcp:47800 tcp:47800`. La operación es idempotente y barata, y así cubre las reconexiones del cable sin necesidad de vigilar eventos USB.
- Si no encuentra adb, lo indica en el tooltip del icono y en el log.

### 3.4 Inyección
- `SendInput` con `MOUSEEVENTF_MOVE` (relativo), `LEFTDOWN`/`UP`, `RIGHTDOWN`/`UP`, `MIDDLEDOWN`/`UP`, `WHEEL` y `HWHEEL`.
- La inyección se aísla tras la interfaz `IInputInjector`, para poder testear el resto con un falso.
- **Recomendación al usuario:** desactivar "Mejorar la precisión del puntero" en Windows, porque la aceleración ya la aplica el móvil.

### 3.5 Configuración y logs
- `%APPDATA%\MyMouse\config.json`, con el puerto y la ruta de adb.
- Log rotativo en `%APPDATA%\MyMouse\logs\`.

## 4. Seguridad
- El host solo escucha en loopback. A ese puerto solo llegan procesos locales y el móvil a través de `adb reverse`.
- Aun así, cualquier proceso local podría conectarse y mover el ratón. Se acepta en v0: un proceso local ya podría usar `SendInput` por su cuenta.

## 5. Testing
- **Protocolo:** los vectores de PROTOCOL.md se prueban en .NET y en Kotlin.
- **Intérprete de gestos:** tests de la máquina de estados con secuencias de eventos sintéticas (tap, doble tap y arrastre, scroll, cambios en el número de dedos).
- **Host:** tests de la sesión (HELLO, mensajes, desconexión que suelta los botones) usando un `IInputInjector` falso.
- **Manual:** la prueba de una jornada.
