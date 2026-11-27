# MyMouse

Convierte un móvil Android viejo en un **trackpad para Windows**, conectado por USB. Más adelante tendrá un modo de gestos (dibujar formas para lanzar atajos) y widgets.

Proyecto personal y open source. Estado: **v0 en desarrollo** (esqueleto funcional, falta probarlo en el dispositivo real).

## Cómo funciona (v0)

```
Móvil (app Android) ──USB / adb reverse──► Windows (app de bandeja) ──SendInput──► cursor
```

El móvil interpreta los toques (mover, clic, scroll, arrastrar) y envía órdenes simples por TCP. El PC solo las ejecuta.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/00-roadmap.md](docs/00-roadmap.md) | Objetivos y criterios de "hecho" de cada versión |
| [docs/01-concepto.md](docs/01-concepto.md) | La idea y su alcance |
| [docs/02-tecnico.md](docs/02-tecnico.md) | Limitaciones técnicas y decisiones |
| [docs/03-plan-v0.md](docs/03-plan-v0.md) | Hitos de la v0 y su estado |
| [docs/probar.md](docs/probar.md) | Cómo instalar y probar una release |
| [docs/spec-v0.md](docs/spec-v0.md) | Especificación funcional de la v0 |
| [docs/spec-v1-gestos.md](docs/spec-v1-gestos.md) | Modo gestos: formas, acciones y límites |
| [docs/spec-v2-widgets.md](docs/spec-v2-widgets.md) | Panel de widgets: botones, multimedia y volumen |
| [docs/spec-v3-bluetooth.md](docs/spec-v3-bluetooth.md) | Modo Bluetooth (sin cable ni programa en el PC) e inicio con Windows |
| [docs/spec-v3.1-ajustes.md](docs/spec-v3.1-ajustes.md) | Gestos de Windows con 3 dedos, widgets con 4 dedos, zoom e inercia |
| [docs/spec-v4-toolbox.md](docs/spec-v4-toolbox.md) | La toolbox del PC (WPF): panel, gesto en vivo, editor y formas sincronizadas |
| [docs/spec-v4.5-estilo.md](docs/spec-v4.5-estilo.md) | Estilo glass en el PC y el móvil, tarjeta Gestos rediseñada y diagnóstico de Bluetooth |
| [docs/spec-v4.7-universo.md](docs/spec-v4.7-universo.md) | Fondo "universo", icono propio de la app (`tools/icon/make_icons.py`), icono siempre visible y retoques del móvil |
| [docs/spec-v4.8-temas.md](docs/spec-v4.8-temas.md) | Temas elegibles en el móvil y en el PC |
| [docs/spec-v5-bluetooth-completo.md](docs/spec-v5-bluetooth-completo.md) | Bluetooth completo (gestos, botones propios, scroll fino) e instalador de Windows |
| [protocol/PROTOCOL.md](protocol/PROTOCOL.md) | Protocolo móvil ↔ PC (fuente de verdad) |

## Puesta en marcha

**Sin compilar nada:** descarga el APK y el instalador de Windows (`MyMouse-Setup-<versión>.exe`, con adb incluido; o el zip portátil) de la [última release](https://github.com/DiegoTorresMijarra/MyMouse/releases) y sigue [docs/probar.md](docs/probar.md). El instalador no está firmado: SmartScreen avisará ("Más información" → "Ejecutar de todas formas"). Por Bluetooth (desde la v3, completo en la v5) el PC no necesita nada instalado.

### Requisitos
- **PC:** Windows 10/11, [.NET 10 SDK](https://dotnet.microsoft.com/download) y [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`). En móviles Huawei, el driver ADB suele venir con HiSuite.
- **Móvil:** Android 8 o superior, con las opciones de desarrollador y la depuración USB activadas.
- **Para compilar la app:** Android Studio.

### Windows
```powershell
cd windows
dotnet test                                   # tests del protocolo y del host
dotnet run --project src/MyMouse.Host         # app de bandeja
dotnet run --project src/MyMouse.TestClient   # (otra terminal) simula el móvil: mueve el cursor en círculo
```
El host busca `adb.exe` en la ruta de `%APPDATA%\MyMouse\config.json` (si se indica), **junto a su propio exe** (el instalador lo trae), en el `PATH` y en `%LOCALAPPDATA%\Android\Sdk\platform-tools`.

**Recomendado:** desactiva "Mejorar la precisión del puntero" en la configuración del ratón de Windows, porque la aceleración ya la aplica el móvil.

### Android
Abre la carpeta `android/` en Android Studio y ejecuta la configuración `app` con el móvil conectado. Los tests de la lógica pura se ejecutan sin emulador:
```bash
cd android
./gradlew :core:test
```

## Licencia
[MIT](LICENSE)
