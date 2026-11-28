# MyMouse — Hoja de ruta

Cada versión tiene **un objetivo principal**, un alcance cerrado y un **criterio de "hecho"** comprobable, que están en su spec. No se empieza una versión hasta cerrar la anterior.

## Hecho

Hasta la v6.1 el proyecto era una versión de desarrollo: esas releases sirvieron para probar y no se mantiene compatibilidad con ellas (el protocolo móvil ↔ PC volvió a empezar en la versión 1).

| Versión | Objetivo | Spec |
|---|---|---|
| **v0** | Sustituir al ratón como trackpad de escritorio, por USB | [spec-v0.md](spec-v0.md) |
| **v1** | Lanzar atajos dibujando formas | [spec-v1-gestos.md](spec-v1-gestos.md) |
| **v2** | Panel de widgets junto al trackpad | [spec-v2-widgets.md](spec-v2-widgets.md) |
| **v3** | Modo Bluetooth, sin cable ni programa en el PC | [spec-v3-bluetooth.md](spec-v3-bluetooth.md) |
| **v3.1** | Gestos de Windows con 3 dedos, zoom e inercia | [spec-v3.1-ajustes.md](spec-v3.1-ajustes.md) |
| **v4** | La toolbox del PC: panel, gesto en vivo, editor y formas sincronizadas | [spec-v4-toolbox.md](spec-v4-toolbox.md) |
| **v4.5** | Estilo glass en el PC y el móvil, y diagnóstico del Bluetooth | [spec-v4.5-estilo.md](spec-v4.5-estilo.md) |
| **v4.7** | Fondo "universo", icono propio y retoques | [spec-v4.7-universo.md](spec-v4.7-universo.md) |
| **v4.8** | Temas elegibles en el móvil y en el PC | [spec-v4.8-temas.md](spec-v4.8-temas.md) |
| **v5** | Bluetooth completo (gestos, botones propios, scroll fino) e instalador de Windows | [spec-v5-bluetooth-completo.md](spec-v5-bluetooth-completo.md) |
| **v5.1** | Avisos de versión nueva, con actualización en un clic | [spec-v5.1-actualizaciones.md](spec-v5.1-actualizaciones.md) |
| **v6** | Modo teclado: un teclado de ordenador de verdad en el móvil | [spec-v6-teclado.md](spec-v6-teclado.md) |
| **v6.1** | Tema sincronizado entre móvil y PC por cable | [spec-v6.1-tema-sincronizado.md](spec-v6.1-tema-sincronizado.md) |

Probado en un PC con Windows 11 y un Xiaomi real. Cómo probar cada función: [probar.md](probar.md).

## Siguiente: v7, lista para Play Store

Spec: [spec-v7-guia.md](spec-v7-guia.md).

| Fase | Objetivo |
|---|---|
| **v7.0** ✅ | Textos en español e inglés, y dos variantes de la app: Play (sin autoactualizador) y GitHub. Hecha, pendiente de probar en un móvil y un PC reales |
| **v7.1** | Guía para empezar: bienvenida, conexión paso a paso, zona de práctica y consejos |
| **v7.2** | Modo demo, material de la ficha de Play y primera subida a un test cerrado |

## Ideas sin versión
Scripts desde gestos · gestos del sistema (zoom, cambio de escritorio) · más distribuciones de teclado (UK, AZERTY, QWERTZ).
