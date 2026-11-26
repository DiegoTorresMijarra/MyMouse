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
| [protocol/PROTOCOL.md](protocol/PROTOCOL.md) | Protocolo móvil ↔ PC (fuente de verdad) |

## Puesta en marcha

**Sin compilar nada:** descarga el APK y el host de Windows de la [última release](https://github.com/DiegoTorresMijarra/MyMouse/releases) y sigue [docs/probar.md](docs/probar.md).

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
El host busca `adb.exe` en el `PATH` o en `%LOCALAPPDATA%\Android\Sdk\platform-tools`. También se puede indicar su ruta en `%APPDATA%\MyMouse\config.json`.

**Recomendado:** desactiva "Mejorar la precisión del puntero" en la configuración del ratón de Windows, porque la aceleración ya la aplica el móvil.

### Android
Abre la carpeta `android/` en Android Studio y ejecuta la configuración `app` con el móvil conectado. Los tests de la lógica pura se ejecutan sin emulador:
```bash
cd android
./gradlew :core:test
```

## Licencia
[MIT](LICENSE)
