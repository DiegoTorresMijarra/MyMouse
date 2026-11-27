# MyMouse — v6.1: tema sincronizado

> Base: [spec-v4.8-temas.md](spec-v4.8-temas.md) (que decía "cada lado guarda su elección: no se sincronizan").
>
> Origen: el usuario cambia el tema en un lado y espera verlo también en el otro. Las decisiones marcadas con *(decisión)* se tomaron sin el usuario.

## 1. Qué cambia
- **Por cable**, el tema, el acento y las estrellas se sincronizan entre el móvil y el PC.
  - Un cambio en un lado se ve en el otro al momento.
  - Al conectar, los dos lados se quedan con la elección más reciente.
- **La animación de las estrellas no se sincroniza** *(decisión)*: el móvil tiene dos ajustes (trackpad y resto de pantallas) y el PC uno.
- **Por Bluetooth no hay sincronización**: el móvil es un ratón y teclado HID y no habla con MyMouse del PC. Los cambios hechos por Bluetooth se sincronizan la próxima vez que se conecte por cable.
- No hay casilla para desactivarlo *(decisión)*.

## 2. Regla: gana el último cambio
Cada lado guarda, junto al tema, **cuándo se eligió** (ms desde 1970, UTC; 0 = nunca).
- Móvil: `themeChangedAt` en las preferencias.
- PC: `theme.changedAt` en `toolbox.json`.

Solo cuenta como cambio el del tema, el acento o las estrellas, no el de la animación. Al recibir el tema del otro lado:

| Caso | Resultado |
|---|---|
| El remoto es más reciente | Se adopta entero, **con su instante** (así no se reenvía) |
| El remoto es más antiguo | Se ignora |
| Mismo instante, distinto aspecto | **Gana el móvil** *(decisión: es el caso de dos lados sin tocar desde antes de la v6.1; el móvil ya es el dueño de las formas)* |
| Mismo aspecto | Solo se adopta el instante, si es más reciente |

- Los relojes de móvil y PC suelen ir sincronizados por red, así que la comparación de instantes de dos aparatos es fiable para cambios hechos a mano.
- Lógica pura y con los mismos tests en los dos lados: `ThemeSync.resolve` (Kotlin, `core/theme/ThemeSync.kt`) y `ThemeSync.Resolve` (C#, `HostCore/Theming/ThemeSync.cs`).

## 3. Protocolo v8
| Tipo | Nombre | Sentido | Payload |
|---|---|---|---|
| `0x17` | `THEME` | móvil → PC | `u16 len` + `str8 tema`, `str8 acento`, `u8 estrellas` (0/1), `i64 instante` (ms desde 1970, UTC) |
| `0x92` | `THEME` | PC → móvil | Igual |

- Ids de hasta 32 bytes UTF-8. Un instante negativo es un error de protocolo.
- Cada lado envía el suyo **una vez tras el handshake** (con versión negociada ≥ 8) y después **solo cuando cambia aquí**. Lo adoptado no se reenvía.
- Un tema o acento desconocido se trata como en la v4.8: Universo y Automático.
- Vectores: `THEME("graphite", "auto", estrellas, 1000)` = `17 17 00 08 67 72 61 70 68 69 74 65 04 61 75 74 6F 01 E8 03 00 00 00 00 00 00`.

## 4. Criterios de "hecho"
1. Cambiar el tema en el PC con el móvil conectado por cable lo cambia en el móvil, y al revés.
2. Al conectar, los dos lados acaban con la elección más reciente.
3. La animación de las estrellas sigue siendo de cada lado.
4. Tests del protocolo (vectores en los dos lados), de la regla y de la sesión del PC.
